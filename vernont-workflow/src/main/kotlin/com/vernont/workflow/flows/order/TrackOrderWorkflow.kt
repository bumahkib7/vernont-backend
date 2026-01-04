package com.vernont.workflow.flows.order

import com.vernont.domain.fulfillment.Fulfillment
import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderEvent
import com.vernont.domain.order.OrderEventType
import com.vernont.domain.order.OrderStatus
import com.vernont.domain.order.FulfillmentStatus
import com.vernont.repository.fulfillment.FulfillmentRepository
import com.vernont.repository.order.OrderEventRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Input for tracking an order
 */
data class TrackOrderInput(
    val orderId: String,
    val customerId: String? = null,  // For ownership validation
    val email: String? = null         // Alternative for guest orders
)

/**
 * Tracking event in the order timeline
 */
data class TrackingEvent(
    val id: String,
    val eventType: String,
    val title: String,
    val description: String?,
    val location: String?,
    val occurredAt: Instant,
    val isCompleted: Boolean
)

/**
 * Shipment tracking information
 */
data class ShipmentTracking(
    val fulfillmentId: String,
    val trackingNumbers: List<String>,
    val trackingUrls: List<String>,
    val carrier: String?,
    val shippedAt: Instant?,
    val estimatedDelivery: Instant?,
    val currentStatus: String
)

/**
 * Response for order tracking
 */
data class TrackOrderResponse(
    val orderId: String,
    val orderDisplayId: Int?,
    val orderStatus: OrderStatus,
    val fulfillmentStatus: FulfillmentStatus,
    val placedAt: Instant,
    val estimatedDelivery: Instant?,
    val shipments: List<ShipmentTracking>,
    val timeline: List<TrackingEvent>,
    val currentStep: Int,         // 1-5 for progress indicator (Placed, Confirmed, Shipped, Out for Delivery, Delivered)
    val totalSteps: Int
)

/**
 * Track Order Workflow (Customer)
 *
 * This workflow provides order tracking information for customers,
 * including shipping details, tracking numbers, and event timeline.
 *
 * Steps:
 * 1. Load order and validate ownership
 * 2. Load fulfillments with tracking info
 * 3. Load order events for timeline
 * 4. Calculate current progress step
 * 5. Return tracking details
 */
@Component
@WorkflowTypes(TrackOrderInput::class, TrackOrderResponse::class)
class TrackOrderWorkflow(
    private val orderRepository: OrderRepository,
    private val fulfillmentRepository: FulfillmentRepository,
    private val orderEventRepository: OrderEventRepository
) : Workflow<TrackOrderInput, TrackOrderResponse> {

    override val name = "order.track"

    companion object {
        // Events to show in customer-facing timeline
        private val CUSTOMER_VISIBLE_EVENTS = setOf(
            OrderEventType.ORDER_PLACED,
            OrderEventType.ORDER_CONFIRMED,
            OrderEventType.PAYMENT_CAPTURED,
            OrderEventType.FULFILLMENT_CREATED,
            OrderEventType.SHIPMENT_CREATED,
            OrderEventType.SHIPPED,
            OrderEventType.IN_TRANSIT,
            OrderEventType.OUT_FOR_DELIVERY,
            OrderEventType.DELIVERED,
            OrderEventType.DELIVERY_ATTEMPTED,
            OrderEventType.ORDER_COMPLETED,
            OrderEventType.RETURN_REQUESTED,
            OrderEventType.RETURN_APPROVED,
            OrderEventType.EXCHANGE_REQUESTED,
            OrderEventType.EXCHANGE_APPROVED
        )

        private const val TOTAL_TRACKING_STEPS = 5
    }

    override suspend fun execute(
        input: TrackOrderInput,
        context: WorkflowContext
    ): WorkflowResult<TrackOrderResponse> {
        logger.info { "Starting track order workflow for order: ${input.orderId}" }

        try {
            // Step 1: Load and validate order ownership
            val loadOrderStep = createStep<String, Order>(
                name = "load-order-for-tracking",
                execute = { orderId, ctx ->
                    logger.debug { "Loading order: $orderId" }

                    val order = orderRepository.findWithItemsById(orderId)
                        ?: throw IllegalArgumentException("Order not found: $orderId")

                    // Validate ownership - customer must own the order or provide matching email
                    if (input.customerId != null) {
                        if (order.customerId != input.customerId) {
                            throw IllegalArgumentException("Order does not belong to this customer")
                        }
                    } else if (input.email != null) {
                        // Guest order validation by email
                        if (!order.email.equals(input.email, ignoreCase = true)) {
                            throw IllegalArgumentException("Email does not match order")
                        }
                    } else {
                        // No authentication provided
                        throw IllegalArgumentException("Customer ID or email required for tracking")
                    }

                    ctx.addMetadata("order", order)
                    StepResponse.of(order)
                }
            )

            // Step 2: Load fulfillments with tracking
            val loadFulfillmentsStep = createStep<Order, List<ShipmentTracking>>(
                name = "load-fulfillments-for-tracking",
                execute = { order, _ ->
                    logger.debug { "Loading fulfillments for order: ${order.id}" }

                    val fulfillments = fulfillmentRepository.findByOrderIdAndDeletedAtIsNull(order.id)

                    val shipments = fulfillments
                        .filter { it.shippedAt != null || it.trackingNumbers != null }
                        .map { fulfillment ->
                            ShipmentTracking(
                                fulfillmentId = fulfillment.id,
                                trackingNumbers = fulfillment.getTrackingNumbersList(),
                                trackingUrls = fulfillment.getTrackingUrlsList(),
                                carrier = extractCarrier(fulfillment),
                                shippedAt = fulfillment.shippedAt,
                                estimatedDelivery = calculateEstimatedDelivery(fulfillment),
                                currentStatus = getShipmentStatus(fulfillment)
                            )
                        }

                    StepResponse.of(shipments)
                }
            )

            // Step 3: Load order events for timeline
            val loadTimelineStep = createStep<Order, List<TrackingEvent>>(
                name = "load-order-timeline",
                execute = { order, _ ->
                    logger.debug { "Loading timeline events for order: ${order.id}" }

                    val events = orderEventRepository.findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAsc(order.id)

                    // Filter to customer-visible events and map to tracking events
                    val timeline = events
                        .filter { it.eventType in CUSTOMER_VISIBLE_EVENTS }
                        .mapIndexed { index, event ->
                            TrackingEvent(
                                id = event.id,
                                eventType = event.eventType.name,
                                title = event.title,
                                description = event.description,
                                location = event.location,
                                occurredAt = event.createdAt,
                                isCompleted = true  // All past events are completed
                            )
                        }

                    StepResponse.of(timeline)
                }
            )

            // Step 4: Calculate current progress step
            val calculateProgressStep = createStep<Order, Int>(
                name = "calculate-tracking-progress",
                execute = { order, ctx ->
                    logger.debug { "Calculating progress for order: ${order.id}" }

                    val currentStep = when {
                        order.status == OrderStatus.COMPLETED -> 5  // Delivered
                        order.fulfillmentStatus == FulfillmentStatus.SHIPPED -> {
                            // Check if we have delivery status
                            val hasDeliveryAttempt = orderEventRepository.existsByOrderIdAndEventTypeAndDeletedAtIsNull(
                                order.id, OrderEventType.OUT_FOR_DELIVERY
                            )
                            if (hasDeliveryAttempt) 4 else 3
                        }
                        order.fulfillmentStatus == FulfillmentStatus.PARTIALLY_SHIPPED -> 3
                        order.status == OrderStatus.PENDING -> 2  // Confirmed, awaiting shipment
                        else -> 1  // Placed
                    }

                    StepResponse.of(currentStep)
                }
            )

            // Execute workflow steps
            val order = loadOrderStep.invoke(input.orderId, context).data
            val shipments = loadFulfillmentsStep.invoke(order, context).data
            val timeline = loadTimelineStep.invoke(order, context).data
            val currentStep = calculateProgressStep.invoke(order, context).data

            // Calculate estimated delivery from shipments
            val estimatedDelivery = shipments
                .mapNotNull { it.estimatedDelivery }
                .maxOrNull()

            val response = TrackOrderResponse(
                orderId = order.id,
                orderDisplayId = order.displayId,
                orderStatus = order.status,
                fulfillmentStatus = order.fulfillmentStatus,
                placedAt = order.createdAt,
                estimatedDelivery = estimatedDelivery,
                shipments = shipments,
                timeline = timeline,
                currentStep = currentStep,
                totalSteps = TOTAL_TRACKING_STEPS
            )

            logger.info { "Track order workflow completed. Order: ${order.id}, Step: $currentStep/$TOTAL_TRACKING_STEPS" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Track order workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

    /**
     * Extract carrier name from fulfillment data
     */
    private fun extractCarrier(fulfillment: Fulfillment): String? {
        // Try to get from provider
        val providerName = fulfillment.provider?.name

        // Or from fulfillment data
        val dataCarrier = fulfillment.data?.get("carrier") as? String

        return dataCarrier ?: providerName
    }

    /**
     * Calculate estimated delivery based on shipped date and carrier
     */
    private fun calculateEstimatedDelivery(fulfillment: Fulfillment): Instant? {
        val shippedAt = fulfillment.shippedAt ?: return null

        // Try to get from fulfillment data first
        val estimatedDays = when (val estimate = fulfillment.data?.get("estimated_delivery_days")) {
            is Number -> estimate.toLong()
            is String -> estimate.toLongOrNull()
            else -> null
        }

        return if (estimatedDays != null) {
            shippedAt.plusSeconds(estimatedDays * 24 * 60 * 60)
        } else {
            // Default estimate: 5-7 business days
            shippedAt.plusSeconds(7 * 24 * 60 * 60)
        }
    }

    /**
     * Get current shipment status string
     */
    private fun getShipmentStatus(fulfillment: Fulfillment): String {
        return when {
            fulfillment.isCanceled() -> "CANCELED"
            fulfillment.isShipped() -> "SHIPPED"
            fulfillment.isPending() -> "PROCESSING"
            else -> "UNKNOWN"
        }
    }
}
