package com.vernont.workflow.flows.cart

import com.vernont.domain.cart.Cart
import com.vernont.domain.inventory.InventoryReservation
import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderLineItem
import com.vernont.domain.order.OrderStatus
import com.vernont.domain.order.dto.OrderResponse
import com.vernont.domain.payment.Payment
import com.vernont.domain.payment.PaymentStatus
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.InventoryReservationRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.payment.PaymentRepository
import com.vernont.repository.product.ProductVariantRepository
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
 * Input for completing a cart
 * Matches Medusa's CompleteCartWorkflowInputDTO
 */
data class CompleteCartInput(
    val cartId: String
)

/**
 * Complete Cart Workflow - Exact replication of Medusa's completeCartWorkflow
 *
 * This workflow completes a cart and creates an order.
 *
 * Steps (matching Medusa):
 * 1. Acquire lock
 * 2. Load cart
 * 3. Validate cart
 * 4. Validate payment
 * 5. Create order
 * 6. Reserve inventory (REAL InventoryReservation)
 * 7. Authorize payment
 * 8. Complete cart
 * 9. Link order to payment
 * 10. Emit OrderPlaced event
 *
 * To override this workflow in a consumer project, define your own bean with the same name:
 * ```
 * @Component("completeCartWorkflow")
 * class CustomCompleteCartWorkflow(...) : Workflow<CompleteCartInput, Order> { ... }
 * ```
 *
 * @see https://docs.medusajs.com/api/store#carts_postcartsidcomplete
 */
@Component
@WorkflowTypes(input = CompleteCartInput::class, output = OrderResponse::class)
class CompleteCartWorkflow(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val inventoryReservationRepository: InventoryReservationRepository
) : Workflow<CompleteCartInput, OrderResponse> {

    override val name = WorkflowConstants.CompleteCart.NAME

    @Transactional
    override suspend fun execute(
        input: CompleteCartInput,
        context: WorkflowContext
    ): WorkflowResult<OrderResponse> {
        logger.info { "Starting complete cart workflow for cart: ${input.cartId}" }

        try {
            // Real locking is handled by WorkflowEngine with lockKey in WorkflowOptions
            val loadCartStep = createStep<String, Cart>(
                name = "get-cart",
                execute = { cartId, ctx ->
                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
                        ?: throw IllegalArgumentException("Cart not found: $cartId")
                    ctx.addMetadata("cart", cart)
                    StepResponse.of(cart)
                }
            )

            val validateCartStep = createStep<Cart, Unit>(
                name = "validate-cart",
                execute = { cart, ctx ->
                    if (cart.completedAt != null) throw IllegalStateException("Cart already completed")
                    if (cart.isEmpty()) throw IllegalStateException("Cannot complete empty cart")
                    if (cart.shippingMethodId == null) throw IllegalStateException("No shipping method")
                    if (cart.email == null) throw IllegalStateException("No email address")
                    StepResponse.of(Unit)
                }
            )

            val validatePaymentStep = createStep<String, Payment>(
                name = "validate-payment",
                execute = { _, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    if (cart.paymentMethodId == null) throw IllegalStateException("No payment method")
                    val payment = paymentRepository.findById(cart.paymentMethodId!!).orElseThrow()
                    if (payment.status == PaymentStatus.CANCELED) throw IllegalStateException("Payment canceled")
                    ctx.addMetadata("payment", payment)
                    StepResponse.of(payment)
                }
            )

            val createOrderStep = createStep<CompleteCartInput, Order>(
                name = "create-order",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    val order = Order()
                    order.customerId = cart.customerId
                    order.email = cart.email!!
                    order.currencyCode = cart.currencyCode
                    order.regionId = cart.regionId
                    order.status = OrderStatus.PENDING

                    cart.items.filter { it.deletedAt == null }.forEach { cartItem ->
                        val orderItem = OrderLineItem()
                        orderItem.order = order
                        orderItem.variantId = cartItem.variantId
                        orderItem.title = cartItem.title
                        orderItem.description = cartItem.description
                        orderItem.thumbnail = cartItem.thumbnail
                        orderItem.quantity = cartItem.quantity
                        orderItem.currencyCode = cartItem.currencyCode
                        orderItem.unitPrice = cartItem.unitPrice
                        orderItem.total = cartItem.total
                        order.items.add(orderItem)
                    }

                    order.subtotal = cart.subtotal
                    order.tax = cart.tax
                    order.shipping = cart.shipping
                    order.discount = cart.discount
                    order.total = cart.total

                    val savedOrder = orderRepository.save(order)
                    ctx.addMetadata("order", savedOrder)
                    logger.info { "Created order ${savedOrder.id} from cart ${cart.id}" }
                    StepResponse.of(savedOrder)
                },
                compensate = { inp, ctx ->
                    val order = ctx.getMetadata("order") as? Order
                    if (order != null) {
                        order.status = OrderStatus.CANCELED
                        order.canceledAt = Instant.now().toString()
                        orderRepository.save(order)
                    }
                }
            )

            val reserveInventoryStep = createStep<Order, List<String>>(
                name = "reserve-inventory",
                execute = { order, ctx ->
                    val reservationIds = mutableListOf<String>()
                    order.items.forEach { orderItem ->
                        val variantId = orderItem.variantId ?: return@forEach
                        val variant = productVariantRepository.findById(variantId).orElse(null)
                        if (variant != null && variant.manageInventory && variant.inventoryItems.isNotEmpty()) {
                            val variantInventoryItem = variant.inventoryItems.firstOrNull()
                            if (variantInventoryItem != null) {
                                val inventoryItem = inventoryItemRepository.findWithLevelsById(variantInventoryItem.inventoryItemId)
                                if (inventoryItem != null) {
                                    val inventoryLevel = inventoryItem.inventoryLevels.firstOrNull { it.deletedAt == null }
                                    if (inventoryLevel != null) {
                                        val quantityToReserve = orderItem.quantity * variantInventoryItem.requiredQuantity
                                        
                                        // Check stock availability before reserving
                                        if (inventoryLevel.availableQuantity < quantityToReserve) {
                                            throw IllegalStateException(
                                                "Insufficient stock for variant $variantId. " +
                                                "Required: $quantityToReserve, Available: ${inventoryLevel.availableQuantity}"
                                            )
                                        }
                                        
                                        inventoryLevel.reserve(quantityToReserve)
                                        inventoryLevelRepository.save(inventoryLevel)

                                        val reservation = InventoryReservation()
                                        reservation.orderId = order.id
                                        reservation.lineItemId = orderItem.id
                                        reservation.inventoryLevelId = inventoryLevel.id
                                        reservation.quantity = quantityToReserve
                                        inventoryReservationRepository.save(reservation)
                                        reservationIds.add(reservation.id)
                                    }
                                }
                            }
                        }
                    }
                    ctx.addMetadata("reservationIds", reservationIds)
                    StepResponse.of(reservationIds)
                },
                compensate = { order, ctx ->
                    @Suppress("UNCHECKED_CAST")
                    val reservationIds = ctx.getMetadata("reservationIds") as? List<String>
                    reservationIds?.forEach { reservationId ->
                        val reservation = inventoryReservationRepository.findById(reservationId).orElse(null)
                        if (reservation != null) {
                            val inventoryLevel = inventoryLevelRepository.findById(reservation.inventoryLevelId).orElse(null)
                            if (inventoryLevel != null) {
                                inventoryLevel.releaseReservation(reservation.quantity)
                                inventoryLevelRepository.save(inventoryLevel)
                                reservation.release()
                                inventoryReservationRepository.save(reservation)
                            }
                        }
                    }
                }
            )

            val authorizePaymentStep = createStep<Payment, Unit>(
                name = "authorize-payment",
                execute = { payment, ctx ->
                    if (payment.status == PaymentStatus.PENDING) {
                        payment.status = PaymentStatus.AUTHORIZED
                        paymentRepository.save(payment)
                    }
                    StepResponse.of(Unit)
                },
                compensate = { payment, ctx ->
                    if (payment.status == PaymentStatus.AUTHORIZED) {
                        payment.status = PaymentStatus.CANCELED
                        payment.canceledAt = Instant.now()
                        paymentRepository.save(payment)
                    }
                }
            )

            val completeCartStep = createStep<String, Unit>(
                name = "complete-cart",
                execute = { cartId, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    cart.completedAt = Instant.now()
                    cartRepository.save(cart)
                    StepResponse.of(Unit)
                },
                compensate = { cartId, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    cart.completedAt = null
                    cartRepository.save(cart)
                }
            )

            val linkOrderToPaymentStep = createStep<Order, Unit>(
                name = "link-order-to-payment",
                execute = { order, ctx ->
                    val payment = ctx.getMetadata("payment") as Payment
                    payment.orderId = order.id
                    paymentRepository.save(payment)
                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val cart = loadCartStep.invoke(input.cartId, context).data
            validateCartStep.invoke(cart, context)
            val payment = validatePaymentStep.invoke(input.cartId, context).data
            val order = createOrderStep.invoke(input, context).data
            reserveInventoryStep.invoke(order, context)
            authorizePaymentStep.invoke(payment, context)
            completeCartStep.invoke(input.cartId, context)
            linkOrderToPaymentStep.invoke(order, context)

            logger.info { "Cart completion succeeded: ${cart.id} -> order: ${order.id}" }
            return WorkflowResult.success(OrderResponse.from(order))

        } catch (e: Exception) {
            logger.error(e) { "Complete cart workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

    @Transactional
    override suspend fun compensate(context: WorkflowContext) {
        logger.warn { "Compensating complete-cart workflow, exec=${context.executionId}" }

        // 1) Undo inventory reservations
        @Suppress("UNCHECKED_CAST")
        val reservationIds = context.getMetadata("reservationIds") as? List<String> ?: emptyList()
        reservationIds.forEach { reservationId ->
            try {
                val reservation = inventoryReservationRepository.findById(reservationId).orElse(null) ?: return@forEach
                val level = inventoryLevelRepository.findById(reservation.inventoryLevelId).orElse(null) ?: return@forEach

                level.releaseReservation(reservation.quantity)
                inventoryLevelRepository.save(level)

                reservation.release()
                inventoryReservationRepository.save(reservation)
                logger.info { "Released inventory reservation: $reservationId" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to release inventory reservation: $reservationId" }
            }
        }

        // 2) Cancel payment if it was authorized
        try {
            val payment = context.getMetadata("payment") as? Payment
            if (payment != null && payment.status == PaymentStatus.AUTHORIZED) {
                payment.status = PaymentStatus.CANCELED
                payment.canceledAt = Instant.now()
                paymentRepository.save(payment)
                logger.info { "Canceled payment: ${payment.id}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel payment during compensation" }
        }

        // 3) Mark order as canceled if created
        try {
            val order = context.getMetadata("order") as? Order
            if (order != null && order.status != OrderStatus.CANCELED) {
                order.status = OrderStatus.CANCELED
                order.canceledAt = Instant.now().toString()
                orderRepository.save(order)
                logger.info { "Canceled order: ${order.id}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel order during compensation" }
        }

        // 4) "Un-complete" cart
        try {
            val cart = context.getMetadata("cart") as? Cart
            if (cart != null && cart.completedAt != null) {
                cart.completedAt = null
                cartRepository.save(cart)
                logger.info { "Un-completed cart: ${cart.id}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to un-complete cart during compensation" }
        }
    }
}
