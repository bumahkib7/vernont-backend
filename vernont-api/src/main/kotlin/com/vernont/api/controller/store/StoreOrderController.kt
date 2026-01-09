package com.vernont.api.controller.store

import com.vernont.domain.auth.UserContext
import com.vernont.domain.auth.getCurrentUserContext
import com.vernont.api.dto.store.*
import com.vernont.domain.order.Order
import com.vernont.domain.order.dto.OrderResponse as DomainOrderResponse
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.fulfillment.FulfillmentRepository
import com.vernont.application.order.OrderEventService
import com.vernont.domain.order.OrderEventType
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.order.CancelOrderInput
import com.vernont.workflow.flows.order.CreateOrderInput
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@RestController
@RequestMapping("/store/orders")
class StoreOrderController(
    private val workflowEngine: WorkflowEngine,
    private val orderRepository: OrderRepository,
    private val fulfillmentRepository: FulfillmentRepository,
    private val orderService: com.vernont.application.order.OrderService,
    private val orderEventService: OrderEventService
) {

    data class CreateOrderRequest(
        val cartId: String,
        val customerId: String?,
        val email: String
    )

    data class OrderResponse(
        val id: String,
        val status: String,
        val email: String,
        val customerId: String?,
        val currencyCode: String,
        val subtotal: String,
        val tax: String,
        val shipping: String,
        val discount: String,
        val total: String
    ) {
        companion object {
            fun from(order: Order) = OrderResponse(
                id = order.id,
                status = order.status.name,
                email = order.email,
                customerId = order.customerId,
                currencyCode = order.currencyCode,
                subtotal = order.subtotal.toPlainString(),
                tax = order.tax.toPlainString(),
                shipping = order.shipping.toPlainString(),
                discount = order.discount.toPlainString(),
                total = order.total.toPlainString()
            )
        }
    }

    @PostMapping("/from-cart")
    suspend fun createOrderFromCart(
        @RequestBody req: CreateOrderRequest,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {

        val input = CreateOrderInput(
            cartId = req.cartId,
            customerId = req.customerId,
            email = req.email
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        // SECURITY: Use cart-specific lock to prevent race conditions
        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CreateOrder.NAME,
            input = input,
            inputType = CreateOrderInput::class,
            outputType = DomainOrderResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(
                correlationId = correlationId,
                lockKey = "workflow:order:cart:${req.cartId}"
            )
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val domainResponse = result.data
                ResponseEntity.status(201).body(
                    OrderResponse(
                        id = domainResponse.id,
                        status = domainResponse.status.name,
                        email = domainResponse.email,
                        customerId = domainResponse.customerId,
                        currencyCode = domainResponse.currencyCode,
                        subtotal = domainResponse.subtotal.toString(),
                        tax = domainResponse.tax.toString(),
                        shipping = domainResponse.shipping.toString(),
                        discount = domainResponse.discount.toString(),
                        total = domainResponse.total.toString()
                    )
                )
            }

            is WorkflowResult.Failure ->
                ResponseEntity.badRequest().body(
                    mapOf(
                        "message" to "Failed to create order",
                        "error" to (result.error.message ?: "Unknown error")
                    )
                )
        }
    }


    data class CancelOrderRequest(
        val reason: String? = null,
        val token: String? = null   // guest cancel token
    )

    @PostMapping("/{id}/cancel")
    suspend fun cancelOrderAsCustomerOrGuest(
        @PathVariable id: String,
        @RequestBody(required = false) body: CancelOrderRequest?,
        auth: Authentication?, // null for guests
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {

        val order = orderRepository.findWithItemsById(id)
            ?: return ResponseEntity.notFound().build()

        // 1) If authenticated, treat as CUSTOMER
        if (auth != null && auth.isAuthenticated) {
            val customerId = auth.name // This will be the user ID from JWT

            // Basic ownership check
            if (order.customerId == null || order.customerId != customerId) {
                return ResponseEntity.status(403).body(
                    mapOf("message" to "You are not allowed to cancel this order")
                )
            }

            val input = CancelOrderInput(
                orderId = id,
                canceledBy = customerId,
                reason = body?.reason
                // initiatedByRole = "CUSTOMER"
            )

            val correlationId = requestId ?: UUID.randomUUID().toString()

            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.CancelOrder.NAME,
                input = input,
                inputType = CancelOrderInput::class,
                outputType = Unit::class,
                context = WorkflowContext(),
                options = WorkflowOptions(correlationId = correlationId)
            )

            return when (result) {
                is WorkflowResult.Success ->
                    ResponseEntity.ok(mapOf("message" to "Order canceled successfully"))

                is WorkflowResult.Failure ->
                    ResponseEntity.badRequest().body(
                        mapOf(
                            "message" to "Failed to cancel order",
                            "error" to (result.error.message ?: "Unknown error")
                        )
                    )
            }
        }

        // 2) Guest cancellation via token
        val token = body?.token
            ?: return ResponseEntity.status(401).body(
                mapOf("message" to "Guest cancellation requires token")
            )

        // Validate the cancellation token
        // Token is a simple hash of orderId + email + creation date for now
        // In production, use a more secure token mechanism with expiration
        val expectedToken = generateCancellationToken(order)
        if (token != expectedToken) {
            return ResponseEntity.status(403).body(
                mapOf("message" to "Invalid or expired cancellation token")
            )
        }

        val input = CancelOrderInput(
            orderId = id,
            canceledBy = order.email, // guest email
            reason = body.reason
            // initiatedByRole = "GUEST"
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CancelOrder.NAME,
            input = input,
            inputType = CancelOrderInput::class,
            outputType = Unit::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        return when (result) {
            is WorkflowResult.Success ->
                ResponseEntity.ok(mapOf("message" to "Order canceled successfully"))

            is WorkflowResult.Failure ->
                ResponseEntity.badRequest().body(
                    mapOf(
                        "message" to "Failed to cancel order",
                        "error" to (result.error.message ?: "Unknown error")
                    )
                )
        }
    }

    @GetMapping
    fun listOrders(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<StoreOrderListResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        if (context.customerId == null) {
            return ResponseEntity.status(403).build()
        }

        val pageable = PageRequest.of(offset / limit, limit)
        // Use the new method that queries by customerId OR email
        // This includes guest orders placed before registration
        val orders = orderService.listCustomerOrdersByIdOrEmail(
            customerId = context.customerId!!,
            email = context.email,
            pageable = pageable
        )

        val storeOrders = orders.content.map { order ->
            StoreOrder(
                id = order.id,
                displayId = order.displayId,
                status = order.status.name.lowercase(),
                fulfillmentStatus = order.fulfillmentStatus.name.lowercase().replace("_", " "),
                paymentStatus = order.paymentStatus.name.lowercase().replace("_", " "),
                email = order.email,
                customerId = order.customerId,
                subtotal = 0, // Not available in summary
                tax = 0, // Not available in summary
                shipping = 0, // Not available in summary
                discount = 0, // Not available in summary
                total = order.total.multiply(java.math.BigDecimal(100)).toInt(),
                currencyCode = order.currencyCode,
                itemCount = order.itemCount,
                createdAt = order.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = order.updatedAt.atOffset(ZoneOffset.UTC),
                completedAt = null
            )
        }

        return ResponseEntity.ok(
            StoreOrderListResponse(
                orders = storeOrders,
                count = orders.totalElements.toInt(),
                offset = offset,
                limit = limit
            )
        )
    }

    @GetMapping("/{id}")
    fun getOrder(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable id: String
    ): ResponseEntity<StoreOrderResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        if (context.customerId == null) {
            return ResponseEntity.status(403).build()
        }

        val order = orderService.getOrder(id)

        // Check authorization - customer can see orders that:
        // 1. Match their customerId, OR
        // 2. Match their email (guest orders placed before registration)
        val isOwner = order.customerId == context.customerId
        val isEmailMatch = order.email.equals(context.email, ignoreCase = true)
        if (!isOwner && !isEmailMatch) {
            return ResponseEntity.status(403).build()
        }

        val storeOrder = StoreOrder(
            id = order.id,
            displayId = order.displayId,
            status = order.status.name.lowercase(),
            fulfillmentStatus = order.fulfillmentStatus.name.lowercase().replace("_", " "),
            paymentStatus = order.paymentStatus.name.lowercase().replace("_", " "),
            email = order.email,
            customerId = order.customerId,
            subtotal = order.subtotal.multiply(java.math.BigDecimal(100)).toInt(),
            tax = order.tax.multiply(java.math.BigDecimal(100)).toInt(),
            shipping = order.shipping.multiply(java.math.BigDecimal(100)).toInt(),
            discount = order.discount.multiply(java.math.BigDecimal(100)).toInt(),
            total = order.total.multiply(java.math.BigDecimal(100)).toInt(),
            currencyCode = order.currencyCode,
            itemCount = order.items.size,
            items = order.items.map { item ->
                StoreOrderLineItem(
                    id = item.id,
                    title = item.title,
                    description = item.description,
                    thumbnail = item.thumbnail,
                    variantId = item.variantId,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice.multiply(java.math.BigDecimal(100)).toInt(),
                    total = item.total.multiply(java.math.BigDecimal(100)).toInt(),
                    currencyCode = item.currencyCode
                )
            },
            createdAt = order.createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = order.updatedAt.atOffset(ZoneOffset.UTC),
            completedAt = null
        )

        return ResponseEntity.ok(StoreOrderResponse(storeOrder))
    }

    // =========================================================================
    // Order Tracking (Public - no auth required)
    // =========================================================================

    data class TrackOrderRequest(
        val orderNumber: String,  // Can be displayId (e.g., "1234") or full ID
        val email: String
    )

    data class TrackingStep(
        val status: String,
        val date: String,
        val location: String,
        val completed: Boolean,
        val current: Boolean
    )

    data class TrackOrderResponse(
        val orderId: String,
        val displayId: Int,
        val email: String,
        val status: String,
        val paymentStatus: String,
        val fulfillmentStatus: String,
        val total: Int,
        val currencyCode: String,
        val itemCount: Int,
        val trackingNumber: String?,
        val carrier: String?,
        val trackingUrl: String?,
        val steps: List<TrackingStep>,
        val createdAt: String,
        val updatedAt: String
    )

    @PostMapping("/track")
    fun trackOrder(@RequestBody request: TrackOrderRequest): ResponseEntity<Any> {
        val email = request.email.lowercase().trim()

        // Try to parse as displayId first, then as UUID
        val order = try {
            val displayId = request.orderNumber.replace("#", "").trim().toInt()
            orderRepository.findByDisplayIdAndEmailAndDeletedAtIsNull(displayId, email)
        } catch (e: NumberFormatException) {
            // Try as UUID
            orderRepository.findByIdAndDeletedAtIsNull(request.orderNumber)?.takeIf {
                it.email.equals(email, ignoreCase = true)
            }
        }

        if (order == null) {
            return ResponseEntity.status(404).body(
                mapOf(
                    "error" to "Order not found",
                    "message" to "No order found with that order number and email combination"
                )
            )
        }

        // Get fulfillment info for tracking
        val fulfillments = fulfillmentRepository.findByOrderIdAndDeletedAtIsNull(order.id)
        val latestFulfillment = fulfillments.maxByOrNull { it.createdAt }

        val trackingNumber = latestFulfillment?.trackingNumbers?.split(",")?.firstOrNull()
        val carrier = latestFulfillment?.data?.get("carrier") as? String
        val trackingUrl = latestFulfillment?.trackingUrls?.split(",")?.firstOrNull()

        val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy - h:mm a")

        // Build tracking steps based on order status
        val steps = buildTrackingSteps(order, latestFulfillment, dateFormatter)

        val response = TrackOrderResponse(
            orderId = order.id,
            displayId = order.displayId,
            email = order.email,
            status = order.status.name.lowercase(),
            paymentStatus = order.paymentStatus.name.lowercase().replace("_", " "),
            fulfillmentStatus = order.fulfillmentStatus.name.lowercase().replace("_", " "),
            total = order.total.multiply(java.math.BigDecimal(100)).toInt(),
            currencyCode = order.currencyCode,
            itemCount = order.items.size,
            trackingNumber = trackingNumber,
            carrier = carrier,
            trackingUrl = trackingUrl,
            steps = steps,
            createdAt = order.createdAt.atOffset(ZoneOffset.UTC).toString(),
            updatedAt = order.updatedAt.atOffset(ZoneOffset.UTC).toString()
        )

        return ResponseEntity.ok(response)
    }

    private fun buildTrackingSteps(
        order: Order,
        fulfillment: com.vernont.domain.fulfillment.Fulfillment?,
        dateFormatter: DateTimeFormatter
    ): List<TrackingStep> {
        val steps = mutableListOf<TrackingStep>()

        // Get all order events
        val events = orderEventService.getOrderEvents(order.id)
        val eventMap = events.associateBy { it.eventType }

        // Helper to get event date or fallback
        fun getEventDate(eventType: OrderEventType): String {
            val event = eventMap[eventType]
            return event?.createdAt?.atOffset(ZoneOffset.UTC)?.format(dateFormatter) ?: "-"
        }

        fun hasEvent(eventType: OrderEventType): Boolean {
            return eventMap.containsKey(eventType)
        }

        // Step 1: Order Placed - always completed if order exists
        val orderPlacedEvent = eventMap[OrderEventType.ORDER_PLACED]
        steps.add(TrackingStep(
            status = "Order Placed",
            date = orderPlacedEvent?.createdAt?.atOffset(ZoneOffset.UTC)?.format(dateFormatter)
                ?: order.createdAt.atOffset(ZoneOffset.UTC).format(dateFormatter),
            location = orderPlacedEvent?.location ?: "Online",
            completed = true,
            current = !hasEvent(OrderEventType.PAYMENT_CAPTURED) && !hasEvent(OrderEventType.PAYMENT_AUTHORIZED)
        ))

        // Step 2: Payment Confirmed
        val paymentEvent = eventMap[OrderEventType.PAYMENT_CAPTURED] ?: eventMap[OrderEventType.PAYMENT_AUTHORIZED]
        val paymentConfirmed = paymentEvent != null
        steps.add(TrackingStep(
            status = "Payment Confirmed",
            date = paymentEvent?.createdAt?.atOffset(ZoneOffset.UTC)?.format(dateFormatter) ?: "-",
            location = paymentEvent?.location ?: "Vernont",
            completed = paymentConfirmed,
            current = paymentConfirmed && !hasEvent(OrderEventType.FULFILLMENT_CREATED)
        ))

        // Step 3: Order Fulfilled
        val fulfillmentEvent = eventMap[OrderEventType.FULFILLMENT_CREATED]
        val isFulfilled = fulfillmentEvent != null
        steps.add(TrackingStep(
            status = "Order Fulfilled",
            date = fulfillmentEvent?.createdAt?.atOffset(ZoneOffset.UTC)?.format(dateFormatter) ?: "-",
            location = fulfillmentEvent?.location ?: "Vernont Warehouse",
            completed = isFulfilled,
            current = isFulfilled && !hasEvent(OrderEventType.SHIPPED)
        ))

        // Step 4: Shipped
        val shippedEvent = eventMap[OrderEventType.SHIPPED]
        val isShipped = shippedEvent != null
        val carrier = shippedEvent?.eventData?.get("carrier") as? String
            ?: fulfillment?.data?.get("carrier") as? String
        steps.add(TrackingStep(
            status = "Shipped",
            date = shippedEvent?.createdAt?.atOffset(ZoneOffset.UTC)?.format(dateFormatter) ?: "-",
            location = carrier ?: "Courier",
            completed = isShipped,
            current = isShipped && !hasEvent(OrderEventType.DELIVERED) && !hasEvent(OrderEventType.ORDER_COMPLETED)
        ))

        // Step 5: Delivered/Completed
        val deliveredEvent = eventMap[OrderEventType.DELIVERED] ?: eventMap[OrderEventType.ORDER_COMPLETED]
        val isDelivered = deliveredEvent != null
        steps.add(TrackingStep(
            status = "Delivered",
            date = deliveredEvent?.createdAt?.atOffset(ZoneOffset.UTC)?.format(dateFormatter) ?: "-",
            location = deliveredEvent?.location ?: if (isDelivered) "Delivered" else "-",
            completed = isDelivered,
            current = isDelivered
        ))

        return steps
    }

    /**
     * Generate a cancellation token for guest order cancellation.
     * This is a simple hash-based token for demo purposes.
     * In production, use JWT or database-stored tokens with expiration.
     */
    private fun generateCancellationToken(order: Order): String {
        val secret = System.getenv("ORDER_CANCEL_SECRET") ?: "vernont-cancel-secret-key"
        val data = "${order.id}:${order.email}:${order.createdAt.epochSecond}:$secret"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }
}
