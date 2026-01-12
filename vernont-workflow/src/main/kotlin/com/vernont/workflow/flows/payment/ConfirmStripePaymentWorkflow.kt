package com.vernont.workflow.flows.payment

import com.vernont.application.order.OrderEventService
import com.vernont.application.payment.StripeService
import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderAddress
import com.vernont.domain.order.OrderLineItem
import com.vernont.domain.order.OrderStatus
import com.vernont.domain.order.PaymentStatus
import com.vernont.domain.payment.Payment
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.payment.PaymentRepository
import com.vernont.repository.payment.PaymentProviderRepository
import com.vernont.repository.product.ProductVariantRepository
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
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Simple DTO for payment verification result (avoids Stripe SDK dependency in workflow)
 */
data class PaymentVerificationResult(
    val paymentIntentId: String,
    val status: String,
    val isSuccessful: Boolean
)

/**
 * Input for confirming a Stripe payment and creating an order
 */
data class ConfirmStripePaymentInput(
    val cartId: String,
    val paymentIntentId: String
)

/**
 * Response after confirming payment and creating order
 */
data class StripePaymentConfirmationResponse(
    val orderId: String,
    val orderDisplayId: Int?,
    val status: String,
    val email: String,
    val total: java.math.BigDecimal,
    val currencyCode: String,
    val paymentStatus: String
)

/**
 * Confirm Stripe Payment Workflow
 *
 * This workflow verifies that a Stripe payment was successful and creates an order.
 *
 * Steps:
 * 1. Validate cart exists and matches PaymentIntent
 * 2. Verify Stripe PaymentIntent status is 'succeeded'
 * 3. Create order from cart
 * 4. Create payment record
 * 5. Mark cart as completed
 * 6. Return order details
 */
@Component
@WorkflowTypes(input = ConfirmStripePaymentInput::class, output = StripePaymentConfirmationResponse::class)
class ConfirmStripePaymentWorkflow(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentProviderRepository: PaymentProviderRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val stripeService: StripeService,
    private val orderEventService: OrderEventService
) : Workflow<ConfirmStripePaymentInput, StripePaymentConfirmationResponse> {

    override val name = WorkflowConstants.ConfirmStripePayment.NAME

    @Transactional
    override suspend fun execute(
        input: ConfirmStripePaymentInput,
        context: WorkflowContext
    ): WorkflowResult<StripePaymentConfirmationResponse> {
        logger.info { "Starting confirm Stripe payment workflow for cart: ${input.cartId}" }

        try {
            // Step 1: Load and validate cart
            val loadCartStep = createStep<ConfirmStripePaymentInput, com.vernont.domain.cart.Cart>(
                name = "load-and-validate-cart",
                execute = { inp, ctx ->
                    logger.debug { "Loading cart: ${inp.cartId}" }

                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(inp.cartId)
                        ?: throw IllegalArgumentException("Cart not found: ${inp.cartId}")

                    if (cart.completedAt != null) {
                        throw IllegalStateException("Cart already completed: ${inp.cartId}")
                    }

                    if (cart.isEmpty()) {
                        throw IllegalStateException("Cart is empty: ${inp.cartId}")
                    }

                    if (cart.email.isNullOrBlank()) {
                        throw IllegalStateException("Cart has no email: ${inp.cartId}")
                    }

                    // Verify the PaymentIntent ID matches what's stored in cart
                    val storedPaymentIntentId = cart.metadata?.get("stripe_payment_intent_id")
                    if (storedPaymentIntentId != inp.paymentIntentId) {
                        throw IllegalStateException(
                            "PaymentIntent mismatch: expected $storedPaymentIntentId, got ${inp.paymentIntentId}"
                        )
                    }

                    ctx.addMetadata("cart", cart)
                    logger.info { "Cart validated: ${cart.id}" }
                    StepResponse.of(cart)
                }
            )

            // Step 2: Verify Stripe PaymentIntent status
            val verifyPaymentStep = createStep<String, PaymentVerificationResult>(
                name = "verify-stripe-payment",
                execute = { paymentIntentId, ctx ->
                    logger.debug { "Verifying PaymentIntent: $paymentIntentId" }

                    val isSuccessful = stripeService.isPaymentSuccessful(paymentIntentId)

                    if (!isSuccessful) {
                        throw IllegalStateException(
                            "Payment not successful. Please complete payment before confirming."
                        )
                    }

                    val result = PaymentVerificationResult(
                        paymentIntentId = paymentIntentId,
                        status = "succeeded",
                        isSuccessful = true
                    )

                    ctx.addMetadata("paymentVerification", result)
                    logger.info { "Payment verified: $paymentIntentId" }
                    StepResponse.of(result)
                }
            )

            // Step 3: Create order from cart
            val createOrderStep = createStep<com.vernont.domain.cart.Cart, Order>(
                name = "create-order",
                execute = { cart, ctx ->
                    logger.debug { "Creating order from cart: ${cart.id}" }

                    val order = Order()
                    order.displayId = orderRepository.getNextDisplayId()
                    order.customerId = cart.customerId
                    order.email = cart.email!!
                    order.currencyCode = cart.currencyCode
                    order.regionId = cart.regionId
                    order.status = OrderStatus.PENDING
                    order.paymentStatus = PaymentStatus.CAPTURED

                    // Copy shipping address from cart (embedded Address -> OrderAddress entity)
                    cart.shippingAddress?.let { addr ->
                        order.shippingAddress = OrderAddress().apply {
                            firstName = addr.firstName
                            lastName = addr.lastName
                            phone = addr.phone
                            address1 = addr.address1
                            address2 = addr.address2
                            city = addr.city
                            province = addr.province
                            postalCode = addr.postalCode
                            countryCode = addr.countryCode
                        }
                        logger.debug { "Copied shipping address to order" }
                    }

                    // Copy shipping method ID
                    order.shippingMethodId = cart.shippingMethodId
                    logger.debug { "Copied shipping method ID: ${cart.shippingMethodId}" }

                    // Copy line items
                    cart.items.filter { it.deletedAt == null }.forEach { cartItem ->
                        val orderItem = OrderLineItem()
                        orderItem.order = order
                        orderItem.variantId = cartItem.variantId
                        // Look up product ID from variant for verified purchase checks
                        val variant = productVariantRepository.findByIdAndDeletedAtIsNull(cartItem.variantId)
                        orderItem.productId = variant?.product?.id
                        orderItem.title = cartItem.title
                        orderItem.description = cartItem.description
                        orderItem.thumbnail = cartItem.thumbnail
                        orderItem.quantity = cartItem.quantity
                        orderItem.currencyCode = cartItem.currencyCode
                        orderItem.unitPrice = cartItem.unitPrice
                        orderItem.total = cartItem.total
                        order.items.add(orderItem)
                    }

                    // Copy totals
                    order.subtotal = cart.subtotal
                    order.tax = cart.tax
                    order.shipping = cart.shipping
                    order.discount = cart.discount
                    order.total = cart.total

                    // Store Stripe PaymentIntent ID in metadata
                    order.metadata = mutableMapOf(
                        "stripe_payment_intent_id" to input.paymentIntentId,
                        "payment_method" to "stripe"
                    )

                    val savedOrder = orderRepository.save(order)
                    ctx.addMetadata("order", savedOrder)

                    logger.info { "Order created: ${savedOrder.id}, displayId: ${savedOrder.displayId}" }
                    StepResponse.of(savedOrder)
                },
                compensate = { cart, ctx ->
                    val order = ctx.getMetadata("order") as? Order
                    if (order != null) {
                        order.status = OrderStatus.CANCELED
                        order.canceledAt = Instant.now().toString()
                        orderRepository.save(order)
                        logger.info { "Compensated: Canceled order ${order.id}" }
                    }
                }
            )

            // Step 4: Create payment record
            val createPaymentRecordStep = createStep<Order, Payment>(
                name = "create-payment-record",
                execute = { order, ctx ->
                    logger.debug { "Creating payment record for order: ${order.id}" }

                    val paymentVerification = ctx.getMetadata("paymentVerification") as PaymentVerificationResult

                    // Get or create Stripe payment provider
                    var stripeProvider = paymentProviderRepository.findByNameAndDeletedAtIsNull("stripe")
                    if (stripeProvider == null) {
                        stripeProvider = com.vernont.domain.payment.PaymentProvider()
                        stripeProvider.name = "stripe"
                        stripeProvider.isActive = true
                        stripeProvider = paymentProviderRepository.save(stripeProvider)
                        logger.info { "Created Stripe payment provider: ${stripeProvider.id}" }
                    }

                    val payment = Payment()
                    payment.orderId = order.id
                    payment.provider = stripeProvider
                    payment.currencyCode = order.currencyCode
                    payment.amount = order.total
                    payment.status = com.vernont.domain.payment.PaymentStatus.CAPTURED
                    payment.externalId = paymentVerification.paymentIntentId
                    payment.capturedAt = Instant.now()
                    payment.capturedAmount = order.total
                    payment.data = mapOf("stripe_payment_intent_id" to paymentVerification.paymentIntentId)

                    val savedPayment = paymentRepository.save(payment)
                    ctx.addMetadata("payment", savedPayment)

                    logger.info { "Payment record created: ${savedPayment.id}" }
                    StepResponse.of(savedPayment)
                }
            )

            // Step 5: Complete cart
            val completeCartStep = createStep<ConfirmStripePaymentInput, Unit>(
                name = "complete-cart",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as com.vernont.domain.cart.Cart
                    val order = ctx.getMetadata("order") as Order

                    cart.completedAt = Instant.now()

                    // Add order ID to cart metadata
                    val metadata = cart.metadata?.toMutableMap() ?: mutableMapOf()
                    metadata["order_id"] = order.id
                    cart.metadata = metadata

                    cartRepository.save(cart)
                    logger.info { "Cart completed: ${cart.id}" }
                    StepResponse.of(Unit)
                },
                compensate = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as? com.vernont.domain.cart.Cart
                    if (cart != null) {
                        cart.completedAt = null
                        cartRepository.save(cart)
                        logger.info { "Compensated: Uncompleted cart ${cart.id}" }
                    }
                }
            )

            // Execute workflow steps
            val cart = loadCartStep.invoke(input, context).data
            verifyPaymentStep.invoke(input.paymentIntentId, context)
            val order = createOrderStep.invoke(cart, context).data
            val payment = createPaymentRecordStep.invoke(order, context).data
            completeCartStep.invoke(input, context)

            // Record order events
            orderEventService.recordOrderPlaced(
                orderId = order.id,
                email = order.email,
                total = order.total,
                currencyCode = order.currencyCode,
                itemCount = order.items.size
            )

            orderEventService.recordPaymentCaptured(
                orderId = order.id,
                paymentId = payment.id,
                amount = order.total,
                currencyCode = order.currencyCode,
                provider = "stripe"
            )

            // Build response
            val response = StripePaymentConfirmationResponse(
                orderId = order.id,
                orderDisplayId = order.displayId,
                status = order.status.name.lowercase(),
                email = order.email,
                total = order.total,
                currencyCode = order.currencyCode,
                paymentStatus = "captured"
            )

            logger.info { "Confirm Stripe payment workflow completed. Order: ${order.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Confirm Stripe payment workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
