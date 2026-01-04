package com.vernont.workflow.flows.payment

import com.vernont.application.payment.PaymentIntentResult
import com.vernont.application.payment.StripeService
import com.vernont.repository.cart.CartRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Input for creating a Stripe PaymentIntent
 */
data class CreateStripePaymentIntentInput(
    val cartId: String,
    val email: String? = null,
    val metadata: Map<String, String>? = null
)

/**
 * Response containing Stripe PaymentIntent details for frontend
 */
data class StripePaymentIntentResponse(
    val paymentIntentId: String,
    val clientSecret: String,
    val publishableKey: String,
    val amount: Long,
    val currencyCode: String,
    val status: String
)

/**
 * Create Stripe PaymentIntent Workflow
 *
 * This workflow creates a Stripe PaymentIntent for cart checkout.
 * It validates the cart, updates cart with checkout info, and creates the PaymentIntent.
 *
 * Steps:
 * 1. Validate cart exists and is not empty
 * 2. Update cart with email/address if provided
 * 3. Create Stripe PaymentIntent with cart total
 * 4. Store PaymentIntent ID in cart metadata
 * 5. Return client_secret for frontend
 */
@Component
@WorkflowTypes(input = CreateStripePaymentIntentInput::class, output = StripePaymentIntentResponse::class)
class CreateStripePaymentIntentWorkflow(
    private val cartRepository: CartRepository,
    private val stripeService: StripeService
) : Workflow<CreateStripePaymentIntentInput, StripePaymentIntentResponse> {

    override val name = WorkflowConstants.CreateStripePaymentIntent.NAME

    @Transactional
    override suspend fun execute(
        input: CreateStripePaymentIntentInput,
        context: WorkflowContext
    ): WorkflowResult<StripePaymentIntentResponse> {
        logger.info { "Starting create Stripe PaymentIntent workflow for cart: ${input.cartId}" }

        try {
            // Step 1: Validate Stripe is available
            val validateStripeStep = createStep<CreateStripePaymentIntentInput, Unit>(
                name = "validate-stripe-available",
                execute = { _, _ ->
                    if (!stripeService.isAvailable()) {
                        throw IllegalStateException("Stripe payments are not configured")
                    }
                    logger.debug { "Stripe is available" }
                    StepResponse.of(Unit)
                }
            )

            // Step 2: Load and validate cart
            val loadCartStep = createStep<String, com.vernont.domain.cart.Cart>(
                name = "load-cart",
                execute = { cartId, ctx ->
                    logger.debug { "Loading cart: $cartId" }

                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
                        ?: throw IllegalArgumentException("Cart not found: $cartId")

                    if (cart.completedAt != null) {
                        throw IllegalStateException("Cart already completed: $cartId")
                    }

                    if (cart.isEmpty()) {
                        throw IllegalStateException("Cannot checkout empty cart: $cartId")
                    }

                    ctx.addMetadata("cart", cart)
                    logger.info { "Cart loaded: ${cart.id}, total: ${cart.total} ${cart.currencyCode}" }
                    StepResponse.of(cart)
                }
            )

            // Step 3: Update cart with checkout info
            val updateCartStep = createStep<CreateStripePaymentIntentInput, com.vernont.domain.cart.Cart>(
                name = "update-cart-checkout-info",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as com.vernont.domain.cart.Cart

                    // Update email if provided and not already set
                    if (!inp.email.isNullOrBlank() && cart.email.isNullOrBlank()) {
                        cart.email = inp.email
                        logger.debug { "Updated cart email: ${inp.email}" }
                    }

                    // Ensure we have an email for receipts
                    if (cart.email.isNullOrBlank()) {
                        throw IllegalStateException("Email is required for checkout")
                    }

                    val updatedCart = cartRepository.save(cart)
                    ctx.addMetadata("cart", updatedCart)
                    StepResponse.of(updatedCart)
                }
            )

            // Step 4: Create Stripe PaymentIntent
            val createPaymentIntentStep = createStep<com.vernont.domain.cart.Cart, PaymentIntentResult>(
                name = "create-stripe-payment-intent",
                execute = { cart, ctx ->
                    logger.debug { "Creating Stripe PaymentIntent for cart: ${cart.id}" }

                    // Convert cart total to Stripe amount (smallest currency unit)
                    val stripeAmount = stripeService.toStripeAmount(cart.total)

                    // Build metadata
                    val metadata = mutableMapOf(
                        "cart_id" to cart.id,
                        "region_id" to cart.regionId
                    )
                    cart.customerId?.let { metadata["customer_id"] = it }
                    input.metadata?.let { metadata.putAll(it) }

                    val paymentIntentResult = stripeService.createPaymentIntent(
                        amount = stripeAmount,
                        currencyCode = cart.currencyCode,
                        cartId = cart.id,
                        customerEmail = cart.email,
                        metadata = metadata
                    )

                    ctx.addMetadata("paymentIntentResult", paymentIntentResult)
                    logger.info {
                        "PaymentIntent created: ${paymentIntentResult.paymentIntentId} " +
                        "for amount: ${paymentIntentResult.amount} ${paymentIntentResult.currencyCode}"
                    }
                    StepResponse.of(paymentIntentResult)
                },
                compensate = { cart, ctx ->
                    // Cancel the PaymentIntent if workflow fails
                    val result = ctx.getMetadata("paymentIntentResult") as? PaymentIntentResult
                    if (result != null) {
                        try {
                            stripeService.cancelPaymentIntent(result.paymentIntentId)
                            logger.info { "Compensated: Canceled PaymentIntent ${result.paymentIntentId}" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to cancel PaymentIntent: ${result.paymentIntentId}" }
                        }
                    }
                }
            )

            // Step 5: Store PaymentIntent ID in cart metadata
            val updateCartMetadataStep = createStep<PaymentIntentResult, Unit>(
                name = "store-payment-intent-in-cart",
                execute = { paymentIntent, ctx ->
                    val cart = ctx.getMetadata("cart") as com.vernont.domain.cart.Cart

                    // Store PaymentIntent ID in cart metadata
                    val currentMetadata = cart.metadata?.toMutableMap() ?: mutableMapOf()
                    currentMetadata["stripe_payment_intent_id"] = paymentIntent.paymentIntentId
                    cart.metadata = currentMetadata

                    cartRepository.save(cart)
                    logger.debug { "Stored PaymentIntent ID in cart metadata" }
                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            validateStripeStep.invoke(input, context)
            val cart = loadCartStep.invoke(input.cartId, context).data
            updateCartStep.invoke(input, context)
            val paymentIntentResult = createPaymentIntentStep.invoke(cart, context).data
            updateCartMetadataStep.invoke(paymentIntentResult, context)

            // Build response
            val response = StripePaymentIntentResponse(
                paymentIntentId = paymentIntentResult.paymentIntentId,
                clientSecret = paymentIntentResult.clientSecret,
                publishableKey = stripeService.getPublishableKey(),
                amount = paymentIntentResult.amount,
                currencyCode = paymentIntentResult.currencyCode,
                status = paymentIntentResult.status
            )

            logger.info { "Create Stripe PaymentIntent workflow completed for cart: ${input.cartId}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Create Stripe PaymentIntent workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
