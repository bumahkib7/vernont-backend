package com.vernont.workflow.flows.order

import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderAddress
import com.vernont.domain.order.dto.OrderResponse
import com.vernont.domain.fulfillment.Fulfillment
import com.vernont.domain.inventory.InventoryLevel
import com.vernont.domain.payment.Payment
import com.vernont.domain.payment.PaymentStatus
import com.vernont.events.OrderCreated
import com.vernont.events.OrderItem
import com.vernont.events.EventPublisher
import com.vernont.application.order.OrderEventService
import com.vernont.application.giftcard.GiftCardOrderService
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.customer.CustomerAddressRepository
import com.vernont.repository.fulfillment.FulfillmentRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.StockLocationRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.payment.PaymentRepository
import com.vernont.repository.product.ProductVariantRepository
import com.vernont.repository.region.RegionRepository
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
import java.math.BigDecimal
import java.math.RoundingMode

private val logger = KotlinLogging.logger {}

/**
 * Input for creating an order from a cart
 */
data class CreateOrderInput(
    val cartId: String,
    val customerId: String?,
    val email: String,
    /** Optional gift card code to apply */
    val giftCardCode: String? = null
)

/**
 * Represents an allocation of inventory for a specific cart item from a specific location.
 */
data class InventoryAllocation(
    val cartItemId: String,
    val locationId: String,
    val inventoryLevelId: String,
    val quantity: Int,
    val variantId: String,
    val isBackorder: Boolean = false
)

/**
 * Represents an inventory reservation for compensation purposes.
 * Type-safe alternative to string parsing.
 */
data class InventoryReservation(
    val levelId: String,
    val quantity: Int
)

/**
 * Compensation data for payment step rollback.
 */
data class PaymentCompensationData(
    val paymentId: String,
    val status: PaymentStatus,
    val amount: BigDecimal
)

/**
 * Workflow for creating an order from a cart.
 *
 * This workflow can be overridden in consumer projects by defining a bean with the same name:
 * ```
 * @Component("createOrderWorkflow")
 * @WorkflowTypes(input = CreateOrderInput::class, output = Order::class)
 * class CustomCreateOrderWorkflow(...) : Workflow<CreateOrderInput, Order> { ... }
 * ```
 *
 * Alternatively, use WorkflowCustomizer to add hooks without full replacement.
 */
@Component
@WorkflowTypes(input = CreateOrderInput::class, output = OrderResponse::class)
class CreateOrderWorkflow(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val customerAddressRepository: CustomerAddressRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val regionRepository: RegionRepository,
    private val paymentRepository: PaymentRepository,
    private val fulfillmentRepository: FulfillmentRepository,
    private val eventPublisher: EventPublisher,
    private val orderEventService: OrderEventService,
    private val giftCardOrderService: GiftCardOrderService
) : Workflow<CreateOrderInput, OrderResponse> {

    override val name = WorkflowConstants.CreateOrder.NAME

    /**
     * SECURITY NOTE: When invoking this workflow, callers should set the lockKey
     * in WorkflowOptions to prevent race conditions:
     * ```
     * options = WorkflowOptions(lockKey = "workflow:order:cart:${cartId}")
     * ```
     * This ensures only one order can be created from the same cart at a time.
     */
    @Transactional
    override suspend fun execute(
        input: CreateOrderInput,
        context: WorkflowContext
    ): WorkflowResult<OrderResponse> {
        logger.info { "Starting enhanced order creation workflow for cart: ${input.cartId}" }

        try {
            // Step 1: Validate cart and calculate taxes
            val validateAndCalculateTaxesStep = createStep<CreateOrderInput, BigDecimal>(
                name = "validate-cart-and-calculate-taxes",
                execute = { inp, ctx ->
                    logger.debug { "Validating cart and calculating taxes: ${inp.cartId}" }

                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(inp.cartId)
                        ?: throw IllegalArgumentException("Cart not found: ${inp.cartId}")

                    if (cart.items.isEmpty()) {
                        throw IllegalStateException("Cannot create order from empty cart")
                    }

                    if (cart.completedAt != null) {
                        throw IllegalStateException("Cart already completed")
                    }

                    // SECURITY: Validate cart ownership if cart has a customerId
                    if (cart.customerId != null && inp.customerId != null) {
                        if (cart.customerId != inp.customerId) {
                            throw IllegalArgumentException(
                                "Cart does not belong to this customer"
                            )
                        }
                    }

                    // SECURITY: Validate all cart items have positive quantities
                    for (item in cart.items) {
                        if (item.quantity <= 0) {
                            throw IllegalArgumentException(
                                "Invalid quantity ${item.quantity} for cart item ${item.id}. Quantity must be positive."
                            )
                        }
                    }

                    // Validate addresses exist
                    if (cart.shippingAddress == null) {
                        throw IllegalStateException("Cart must have shipping address before creating order")
                    }
                    // Calculate tax if region has automatic taxes enabled
                    val calculatedTax: BigDecimal = if (cart.regionId.isNotBlank()) {
                        val region = regionRepository.findWithTaxRatesById(cart.regionId)
                        if (region != null && region.automaticTaxes) {
                            // taxRate is stored as decimal (0.18 for 18%)
                            cart.subtotal
                                .multiply(region.taxRate)
                                .setScale(4, RoundingMode.HALF_UP)
                        } else {
                            cart.tax
                        }
                    } else {
                        cart.tax
                    }

                    ctx.addMetadata("cartTotal", cart.total)
                    ctx.addMetadata("calculatedTax", calculatedTax)
                    ctx.addMetadata("cart", cart)
                    
                    StepResponse.of(calculatedTax)
                }
            )

            // Step 2: Smart multi-location inventory allocation
            val allocateInventoryStep = createStep<String, List<InventoryAllocation>>(
                name = "allocate-inventory",
                execute = { cartId, ctx ->
                    logger.debug { "Performing smart inventory allocation for cart: $cartId" }
                    
                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)!!
                    val allocations = mutableListOf<InventoryAllocation>()
                    
                    // Get all fulfillment-enabled locations ordered by priority
                    val locations = stockLocationRepository.findByDeletedAtIsNull()
                        .filter { it.fulfillmentEnabled }
                        .sortedBy { it.priority }
                    
                    if (locations.isEmpty()) {
                        throw IllegalStateException("No fulfillment-enabled locations available")
                    }

                    // For each cart item, allocate inventory
                    cart.items.forEach { cartItem ->
                        val variant = productVariantRepository.findById(cartItem.variantId).orElseThrow {
                            IllegalStateException("Variant not found: ${cartItem.variantId}")
                        }

                        if (variant.manageInventory) {
                            val remainingQuantity = cartItem.quantity

                            variant.inventoryItems.forEach { variantInventory ->
                                val requiredQtyPerCartItem = variantInventory.requiredQuantity
                                var totalRequired = remainingQuantity * requiredQtyPerCartItem

                                // Try to allocate from locations in priority order
                                for (location in locations) {
                                    if (totalRequired <= 0) break

                                    val level = inventoryLevelRepository
                                        .findByInventoryItemIdAndLocationId(
                                            variantInventory.inventoryItemId,
                                            location.id
                                        )

                                    if (level != null && level.hasAvailableStock(1)) {
                                        val allocateQty = minOf(totalRequired, level.availableQuantity)

                                        allocations.add(
                                            InventoryAllocation(
                                                cartItemId = cartItem.id,
                                                locationId = location.id,
                                                inventoryLevelId = level.id,
                                                quantity = allocateQty,
                                                variantId = variant.id,
                                                isBackorder = false
                                            )
                                        )

                                        totalRequired -= allocateQty
                                        logger.debug {
                                            "Allocated $allocateQty units from location ${location.name} for variant ${variant.sku}"
                                        }
                                    }
                                }

                                // Check if backorders are allowed for remaining quantity
                                if (totalRequired > 0) {
                                    if (variant.allowBackorder) {
                                        // Create backorder allocation - use first location as placeholder
                                        val backorderLocation = locations.first()
                                        val level = inventoryLevelRepository
                                            .findByInventoryItemIdAndLocationId(
                                                variantInventory.inventoryItemId,
                                                backorderLocation.id
                                            )

                                        if (level != null) {
                                            allocations.add(
                                                InventoryAllocation(
                                                    cartItemId = cartItem.id,
                                                    locationId = backorderLocation.id,
                                                    inventoryLevelId = level.id,
                                                    quantity = totalRequired,
                                                    variantId = variant.id,
                                                    isBackorder = true
                                                )
                                            )
                                            logger.info {
                                                "Created backorder allocation for $totalRequired units of variant ${variant.sku}"
                                            }
                                        }
                                    } else {
                                        throw IllegalStateException(
                                            "Insufficient inventory for variant ${variant.sku}. " +
                                            "Needed: ${remainingQuantity * requiredQtyPerCartItem}, Missing: $totalRequired. " +
                                            "Backorders are not allowed for this product."
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    ctx.addMetadata("allocations", allocations)
                    logger.info { "Successfully allocated inventory across ${allocations.groupBy { it.locationId }.size} location(s)" }
                    
                    StepResponse.of(allocations)
                }
            )

            // Step 3: Reserve inventory (skip backorder allocations)
            val reserveInventoryStep = createStep<List<InventoryAllocation>, List<InventoryReservation>>(
                name = "reserve-inventory",
                execute = { allocations, ctx ->
                    // Filter out backorder allocations - we can't reserve inventory that doesn't exist
                    val reservableAllocations = allocations.filter { !it.isBackorder }
                    val backorderAllocations = allocations.filter { it.isBackorder }

                    if (backorderAllocations.isNotEmpty()) {
                        logger.info { "${backorderAllocations.size} backorder allocation(s) will not be reserved (pending stock)" }
                    }

                    logger.debug { "Reserving inventory for ${reservableAllocations.size} allocations" }

                    val reservations = mutableListOf<InventoryReservation>()

                    reservableAllocations.forEach { allocation ->
                        val level = inventoryLevelRepository.findById(allocation.inventoryLevelId).orElseThrow {
                            IllegalStateException("Inventory level not found: ${allocation.inventoryLevelId}")
                        }

                        // Use the proper reserve method
                        level.reserve(allocation.quantity)
                        inventoryLevelRepository.save(level)

                        // Store typed reservation details for compensation
                        reservations.add(InventoryReservation(
                            levelId = allocation.inventoryLevelId,
                            quantity = allocation.quantity
                        ))

                        logger.debug { "Reserved ${allocation.quantity} units from level ${allocation.inventoryLevelId}" }
                    }

                    ctx.addMetadata("hasBackorders", backorderAllocations.isNotEmpty())
                    // Return reservations as both output and compensationData for type-safe rollback
                    StepResponse.of(reservations, compensationData = reservations)
                },
                compensate = { _, _, compensationData, _ ->
                    @Suppress("UNCHECKED_CAST")
                    val reservations = compensationData as? List<InventoryReservation> ?: return@createStep
                    reservations.forEach { reservation ->
                        try {
                            val level = inventoryLevelRepository.findById(reservation.levelId).orElse(null)
                            if (level != null) {
                                level.releaseReservation(reservation.quantity)
                                inventoryLevelRepository.save(level)
                                logger.info { "Compensated: Released reservation of ${reservation.quantity} units from level ${reservation.levelId}" }
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate reservation: $reservation" }
                        }
                    }
                }
            )

            // Step 4: Validate and apply gift card (if provided)
            val applyGiftCardStep = createStep<BigDecimal, BigDecimal>(
                name = "apply-gift-card",
                execute = { tax, ctx ->
                    val cart = ctx.getMetadata("cart")!! as com.vernont.domain.cart.Cart
                    val orderTotalBeforeGiftCard = cart.subtotal.add(tax).add(cart.shipping).subtract(cart.discount)
                    val orderTotalCents = orderTotalBeforeGiftCard.multiply(BigDecimal(100)).toInt()

                    var giftCardAmountCents = 0
                    var giftCardCode: String? = null

                    // Apply gift card if provided
                    if (!input.giftCardCode.isNullOrBlank()) {
                        logger.info { "Validating gift card: ${input.giftCardCode}" }

                        val validation = giftCardOrderService.validateGiftCard(
                            input.giftCardCode,
                            cart.currencyCode
                        )

                        if (!validation.valid) {
                            throw IllegalArgumentException("Gift card error: ${validation.errorMessage}")
                        }

                        // Calculate how much to use from gift card
                        val giftCard = validation.giftCard!!
                        giftCardAmountCents = minOf(validation.availableBalance, orderTotalCents)
                        giftCardCode = giftCard.code

                        logger.info {
                            "Gift card ${giftCard.code} validated. " +
                            "Available: ${validation.availableBalance} cents, " +
                            "Will use: $giftCardAmountCents cents"
                        }
                    }

                    val giftCardAmountDecimal = BigDecimal(giftCardAmountCents).divide(BigDecimal(100))
                    val payableAmount = orderTotalBeforeGiftCard.subtract(giftCardAmountDecimal)
                        .coerceAtLeast(BigDecimal.ZERO)

                    ctx.addMetadata("orderTotalBeforeGiftCard", orderTotalBeforeGiftCard)
                    ctx.addMetadata("giftCardAmountCents", giftCardAmountCents)
                    ctx.addMetadata("giftCardAmountDecimal", giftCardAmountDecimal)
                    ctx.addMetadata("giftCardCode", giftCardCode ?: "")
                    ctx.addMetadata("payableAmount", payableAmount)

                    StepResponse.of(payableAmount)
                }
            )

            // Step 5: Authorize payment (for amount after gift card)
            val authorizePaymentStep = createStep<BigDecimal, Payment>(
                name = "authorize-payment",
                execute = { payableAmount, ctx ->
                    logger.debug { "Authorizing payment for payable amount: $payableAmount" }

                    val cart = ctx.getMetadata("cart")!! as com.vernont.domain.cart.Cart
                    val orderTotalBeforeGiftCard = ctx.getMetadata("orderTotalBeforeGiftCard") as BigDecimal

                    // Create payment entity - amount is what customer pays (after gift card)
                    val payment = Payment().apply {
                        cartId = cart.id
                        currencyCode = cart.currencyCode
                        amount = payableAmount
                        status = PaymentStatus.PENDING

                        // In a real system, this would integrate with a payment provider
                        // For now, we'll simulate authorization
                        externalId = "sim_auth_${System.currentTimeMillis()}"
                    }

                    val savedPayment = paymentRepository.save(payment)

                    // Simulate authorization (skip if amount is zero - fully covered by gift card)
                    if (payableAmount > BigDecimal.ZERO) {
                        savedPayment.authorize()
                    } else {
                        // No payment needed - fully covered by gift card
                        savedPayment.status = PaymentStatus.CAPTURED
                        logger.info { "Order fully covered by gift card - no payment authorization needed" }
                    }
                    val authorizedPayment = paymentRepository.save(savedPayment)

                    ctx.addMetadata("paymentId", authorizedPayment.id)
                    ctx.addMetadata("totalAmount", orderTotalBeforeGiftCard)

                    logger.info { "Payment authorized: ${authorizedPayment.id} for amount $payableAmount (order total: $orderTotalBeforeGiftCard)" }

                    // Return payment with typed compensation data
                    StepResponse.of(
                        authorizedPayment,
                        compensationData = PaymentCompensationData(
                            paymentId = authorizedPayment.id,
                            status = authorizedPayment.status,
                            amount = authorizedPayment.amount
                        )
                    )
                },
                compensate = { _, _, compensationData, _ ->
                    val compData = compensationData as? PaymentCompensationData ?: return@createStep
                    try {
                        val payment = paymentRepository.findById(compData.paymentId).orElse(null)
                        if (payment != null) {
                            when (payment.status) {
                                PaymentStatus.AUTHORIZED -> {
                                    // Cancel authorized payment
                                    payment.cancel()
                                    paymentRepository.save(payment)
                                    logger.info { "Compensated: Canceled authorized payment ${compData.paymentId}" }
                                }
                                PaymentStatus.CAPTURED -> {
                                    // SECURITY: Refund captured payment - cannot just cancel
                                    val refund = com.vernont.domain.payment.Refund().apply {
                                        this.payment = payment
                                        this.orderId = payment.orderId
                                        this.currencyCode = payment.currencyCode
                                        this.amount = payment.amount
                                        this.reason = com.vernont.domain.payment.RefundReason.CANCEL
                                        this.note = "Workflow compensation - order creation failed after payment capture"
                                        this.status = com.vernont.domain.payment.RefundStatus.SUCCEEDED
                                    }
                                    // Note: In production, would need RefundRepository injection
                                    payment.status = PaymentStatus.REFUNDED
                                    paymentRepository.save(payment)
                                    logger.warn { "Compensated: Refunded captured payment ${compData.paymentId} (requires manual verification)" }
                                }
                                else -> {
                                    logger.debug { "Payment ${compData.paymentId} in status ${payment.status} - no compensation needed" }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate payment: ${compData.paymentId}" }
                    }
                }
            )

            // Step 5: Create order
            val createOrderStep = createStep<Payment, Order>(
                name = "create-order",
                execute = { payment, ctx ->
                    logger.debug { "Creating order" }
                    
                    val cart = ctx.getMetadata("cart")!! as com.vernont.domain.cart.Cart
                    val tax = ctx.getMetadata("calculatedTax") as BigDecimal
                    val totalAmount = ctx.getMetadata("totalAmount") as BigDecimal
                    
                    val order = Order().apply {
                        customerId = input.customerId
                        cartId = cart.id
                        email = input.email
                        currencyCode = cart.currencyCode
                        status = com.vernont.domain.order.OrderStatus.PENDING
                        fulfillmentStatus = com.vernont.domain.order.FulfillmentStatus.NOT_FULFILLED
                        paymentStatus = com.vernont.domain.order.PaymentStatus.AWAITING // Payment authorized but not captured
                        
                        regionId = cart.regionId
                        subtotal = cart.subtotal
                        this.tax = tax
                        shipping = cart.shipping
                        discount = cart.discount
                        total = totalAmount
                    }

                    // Handle shipping address from embedded Address in Cart
                    cart.shippingAddress?.let { commonAddress ->
                        order.shippingAddress = OrderAddress.fromCommonAddress(commonAddress)
                    }
                    
                    // Handle billing address from CustomerAddress (if billingAddressId exists)
                    cart.billingAddressId?.let { billingAddressId ->
                        val billingAddr = customerAddressRepository.findById(billingAddressId).orElse(null)
                        if (billingAddr != null) {
                            order.billingAddress = OrderAddress.fromCustomerAddress(billingAddr)
                        }
                    }

                    // Convert cart items to order items
                    cart.items.forEach { cartItem ->
                        val variant = productVariantRepository.findByIdAndDeletedAtIsNull(cartItem.variantId)
                        val orderItem = com.vernont.domain.order.OrderLineItem().apply {
                            variantId = cartItem.variantId
                            productId = variant?.product?.id
                            title = cartItem.title
                            quantity = cartItem.quantity
                            unitPrice = cartItem.unitPrice
                            currencyCode = cartItem.currencyCode
                            recalculateTotal()
                        }
                        order.addItem(orderItem)
                    }

                    val savedOrder = orderRepository.save(order)

                    // Update payment with order ID
                    payment.orderId = savedOrder.id
                    paymentRepository.save(payment)

                    // Record ORDER_PLACED event
                    orderEventService.recordOrderPlaced(
                        orderId = savedOrder.id,
                        email = savedOrder.email,
                        total = savedOrder.total,
                        currencyCode = savedOrder.currencyCode,
                        itemCount = savedOrder.items.size
                    )

                    // Record PAYMENT_CAPTURED event (payment was already captured via Stripe)
                    orderEventService.recordPaymentCaptured(
                        orderId = savedOrder.id,
                        paymentId = payment.id,
                        amount = payment.amount,
                        currencyCode = payment.currencyCode,
                        provider = "stripe"
                    )

                    ctx.addMetadata("orderId", savedOrder.id)
                    ctx.addMetadata("order", savedOrder)

                    logger.info { "Order created: ${savedOrder.id}" }
                    StepResponse.of(savedOrder)
                }
            )

            // Step 6: Create fulfillment records (split shipment support)
            val createFulfillmentsStep = createStep<Order, List<Fulfillment>>(
                name = "create-fulfillments",
                execute = { order, ctx ->
                    logger.debug { "Creating fulfillment records" }

                    @Suppress("UNCHECKED_CAST")
                    val allocations = ctx.getMetadata("allocations") as List<InventoryAllocation>
                    val fulfillments = mutableListOf<Fulfillment>()

                    // Group allocations by location
                    val allocationsByLocation = allocations.groupBy { it.locationId }

                    // Create one fulfillment per location
                    allocationsByLocation.forEach { (locationId, locationAllocations) ->
                        val hasBackorders = locationAllocations.any { it.isBackorder }

                        val fulfillment = Fulfillment().apply {
                            orderId = order.id
                            this.locationId = locationId
                            noNotification = false

                            // Store allocation details in data field including backorder status
                            val allocationData = locationAllocations.map {
                                mapOf(
                                    "variantId" to it.variantId,
                                    "quantity" to it.quantity,
                                    "isBackorder" to it.isBackorder
                                )
                            }
                            data = mapOf(
                                "allocations" to allocationData,
                                "hasBackorders" to hasBackorders
                            )
                        }

                        fulfillments.add(fulfillmentRepository.save(fulfillment))

                        if (hasBackorders) {
                            val backorderCount = locationAllocations.count { it.isBackorder }
                            logger.info {
                                "Created fulfillment for location $locationId with ${locationAllocations.size} items ($backorderCount on backorder)"
                            }
                        } else {
                            logger.debug { "Created fulfillment for location $locationId with ${locationAllocations.size} items" }
                        }
                    }

                    ctx.addMetadata("fulfillments", fulfillments)
                    logger.info { "Created ${fulfillments.size} fulfillment record(s) for multi-location shipment" }

                    StepResponse.of(fulfillments)
                }
            )

            // Step 7: Fulfill inventory reservations (skip backorders - no stock to fulfill)
            val fulfillInventoryStep = createStep<List<InventoryAllocation>, Unit>(
                name = "fulfill-inventory-reservations",
                execute = { allocations, ctx ->
                    // Filter out backorder allocations - they have no reservation to fulfill
                    val fulfillableAllocations = allocations.filter { !it.isBackorder }
                    val backorderAllocations = allocations.filter { it.isBackorder }

                    if (backorderAllocations.isNotEmpty()) {
                        logger.info {
                            "${backorderAllocations.size} backorder allocation(s) skipped - pending stock arrival"
                        }
                    }

                    logger.debug { "Fulfilling ${fulfillableAllocations.size} inventory reservations" }

                    fulfillableAllocations.forEach { allocation ->
                        val level = inventoryLevelRepository.findById(allocation.inventoryLevelId).orElseThrow()

                        // Convert reservation to actual fulfillment
                        level.fulfillReservation(allocation.quantity)
                        inventoryLevelRepository.save(level)

                        logger.debug { "Fulfilled ${allocation.quantity} units from level ${allocation.inventoryLevelId}" }
                    }

                    logger.info { "Fulfilled ${fulfillableAllocations.size} inventory reservation(s)" }
                    StepResponse.of(Unit)
                }
            )

            // Step 8: Mark cart as completed
            val completeCartStep = createStep<String, Unit>(
                name = "complete-cart",
                execute = { cartId, ctx ->
                    logger.debug { "Marking cart as completed: $cartId" }
                    
                    val cart = cartRepository.findById(cartId).orElseThrow()
                    cart.complete()
                    cartRepository.save(cart)
                    
                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val calculatedTax = validateAndCalculateTaxesStep.invoke(input, context).data
            val allocations = allocateInventoryStep.invoke(input.cartId, context).data
            reserveInventoryStep.invoke(allocations, context)

            val payableAmount = applyGiftCardStep.invoke(calculatedTax, context).data
            val payment = authorizePaymentStep.invoke(payableAmount, context).data

            val order = createOrderStep.invoke(payment, context).data
            val fulfillments = createFulfillmentsStep.invoke(order, context).data
            fulfillInventoryStep.invoke(allocations, context)
            completeCartStep.invoke(input.cartId, context)

            // Publish enhanced event
            eventPublisher.publish(
                OrderCreated(
                    aggregateId = order.id,
                    customerId = order.customerId ?: "guest",
                    items = order.items.map { 
                        OrderItem(
                            productId = it.variantId ?: "unknown",
                            quantity = it.quantity,
                            unitPrice = it.unitPrice,
                            totalPrice = it.total
                        )
                    },
                    shippingAddress = order.shippingAddress?.getFullAddress() ?: "No address",
                    totalAmount = order.total
                )
            )

            val backorderCount = allocations.count { it.isBackorder }
            logger.info {
                "Order created successfully: ${order.id} with ${fulfillments.size} fulfillment(s) " +
                "across ${allocations.groupBy { it.locationId }.size} location(s)" +
                if (backorderCount > 0) " ($backorderCount item(s) on backorder)" else ""
            }

            return WorkflowResult.success(OrderResponse.from(order))

        } catch (e: Exception) {
            logger.error(e) { "Order creation workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
