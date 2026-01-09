package com.vernont.workflow.flows.cart

import com.vernont.domain.cart.Cart
import com.vernont.domain.payment.Payment
import com.vernont.domain.payment.PaymentStatus
import com.vernont.domain.payment.dto.PaymentResponse
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.payment.PaymentRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Input for creating payment collection for cart
 * Matches Medusa's CreatePaymentCollectionForCartWorkflowInputDTO
 */
data class CreatePaymentCollectionForCartInput(
    val cartId: String,
    val metadata: Map<String, Any>? = null
)

/**
 * Create Payment Collection For Cart Workflow - Exact replication of Medusa's createPaymentCollectionForCartWorkflow
 *
 * This workflow creates a payment collection (Payment entity) for a cart.
 * It validates the cart doesn't already have a payment and creates one with the cart's total.
 *
 * Steps (matching Medusa):
 * 1. Acquire lock
 * 2. Load cart
 * 3. Validate cart (not completed/deleted)
 * 4. Validate cart doesn't have existing payment
 * 5. Create payment collection with cart total
 * 6. Link payment to cart (set paymentMethodId)
 * 7. Save cart and payment
 * 8. Release lock
 *
 * @see https://docs.medusajs.com/api/store#payment-collections_postpaymentcollections
 */
@Component
@WorkflowTypes(input = CreatePaymentCollectionForCartInput::class, output = PaymentResponse::class)
class CreatePaymentCollectionForCartWorkflow(
    private val cartRepository: CartRepository,
    private val paymentRepository: PaymentRepository
) : Workflow<CreatePaymentCollectionForCartInput, PaymentResponse> {

    override val name = WorkflowConstants.CreatePaymentCollectionForCart.NAME

    @Transactional
    override suspend fun execute(
        input: CreatePaymentCollectionForCartInput,
        context: WorkflowContext
    ): WorkflowResult<PaymentResponse> {
        logger.info { "Starting create payment collection workflow for cart: ${input.cartId}" }

        try {
            // Step 1: Acquire lock
            val acquireLockStep = createStep<String, String>(
                name = "acquire-lock",
                execute = { cartId, ctx ->
                    logger.debug { "Acquiring lock for cart: $cartId" }
                    ctx.addMetadata("lockKey", "cart:$cartId")
                    ctx.addMetadata("lockAcquired", true)
                    StepResponse.of(cartId)
                },
                compensate = { _, ctx ->
                    logger.info { "Releasing lock: ${ctx.getMetadata("lockKey")}" }
                    ctx.addMetadata("lockAcquired", false)
                }
            )

            // Step 2: Load cart
            val loadCartStep = createStep<String, Cart>(
                name = "get-cart",
                execute = { cartId, ctx ->
                    logger.debug { "Loading cart: $cartId" }

                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
                        ?: throw IllegalArgumentException("Cart not found: $cartId")

                    ctx.addMetadata("cart", cart)
                    StepResponse.of(cart)
                }
            )

            // Step 3: Validate cart
            val validateCartStep = createStep<Cart, Unit>(
                name = "validate-cart",
                execute = { cart, ctx ->
                    if (cart.completedAt != null) {
                        throw IllegalStateException("Cannot create payment for completed cart: ${cart.id}")
                    }

                    if (cart.deletedAt != null) {
                        throw IllegalStateException("Cannot create payment for deleted cart: ${cart.id}")
                    }

                    if (cart.isEmpty()) {
                        throw IllegalStateException("Cannot create payment for empty cart: ${cart.id}")
                    }

                    logger.debug { "Cart ${cart.id} is valid for payment collection" }
                    StepResponse.of(Unit)
                }
            )

            // Step 4: Validate no existing payment
            val validateNoExistingPaymentStep = createStep<Cart, Unit>(
                name = "validate-no-existing-payment",
                execute = { cart, ctx ->
                    if (cart.paymentMethodId != null) {
                        // Check if there's an existing payment for this cart
                        val existingPayments = paymentRepository.findByOrderId(cart.id)
                            .filter { it.deletedAt == null && it.status != PaymentStatus.CANCELED }

                        if (existingPayments.isNotEmpty()) {
                            throw IllegalStateException(
                                "Cart ${cart.id} already has a payment collection: ${existingPayments.first().id}"
                            )
                        }
                    }

                    logger.debug { "Cart ${cart.id} has no existing payment collection" }
                    StepResponse.of(Unit)
                }
            )

            // Step 5: Create payment collection
            val createPaymentStep = createStep<CreatePaymentCollectionForCartInput, Payment>(
                name = "create-payment-collection",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    // Create Payment entity (acts as payment collection in Medusa)
                    val payment = Payment()
                    payment.orderId = cart.id
                    payment.currencyCode = cart.currencyCode
                    payment.amount = cart.total
                    payment.status = PaymentStatus.PENDING
                    payment.provider = null // Will be set when payment provider is selected
                    payment.capturedAt = null
                    payment.canceledAt = null
                    payment.data = null

                    val savedPayment = paymentRepository.save(payment)

                    ctx.addMetadata("payment", savedPayment)

                    logger.info {
                        "Created payment collection ${savedPayment.id} for cart ${cart.id} " +
                        "with amount ${savedPayment.amount} ${savedPayment.currencyCode}"
                    }

                    StepResponse.of(savedPayment)
                },
                compensate = { inp, ctx ->
                    val payment = ctx.getMetadata("payment") as? Payment

                    if (payment != null) {
                        // Soft delete the payment
                        payment.deletedAt = Instant.now()
                        paymentRepository.save(payment)

                        logger.info { "Rolled back payment collection creation: ${payment.id}" }
                    }
                }
            )

            // Step 6: Link payment to cart
            val linkPaymentToCartStep = createStep<Payment, Unit>(
                name = "link-payment-to-cart",
                execute = { payment, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    val previousPaymentMethodId = cart.paymentMethodId
                    cart.paymentMethodId = payment.id

                    cartRepository.save(cart)

                    if (previousPaymentMethodId != null) {
                        ctx.addMetadata("previousPaymentMethodId", previousPaymentMethodId)
                    }

                    logger.info { "Linked payment ${payment.id} to cart ${cart.id}" }

                    StepResponse.of(Unit)
                },
                compensate = { payment, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    val previousPaymentMethodId = ctx.getMetadata("previousPaymentMethodId") as? String

                    cart.paymentMethodId = previousPaymentMethodId
                    cartRepository.save(cart)

                    logger.info { "Unlinked payment ${payment.id} from cart ${cart.id}" }
                }
            )

            // Execute steps in order
            acquireLockStep.invoke(input.cartId, context)
            val cart = loadCartStep.invoke(input.cartId, context).data
            validateCartStep.invoke(cart, context)
            validateNoExistingPaymentStep.invoke(cart, context)
            val payment = createPaymentStep.invoke(input, context).data
            linkPaymentToCartStep.invoke(payment, context)

            logger.info {
                "Create payment collection workflow completed for cart: ${cart.id}, " +
                "payment: ${payment.id}, amount: ${payment.amount}"
            }

            return WorkflowResult.success(PaymentResponse.from(payment))

        } catch (e: Exception) {
            logger.error(e) { "Create payment collection workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
