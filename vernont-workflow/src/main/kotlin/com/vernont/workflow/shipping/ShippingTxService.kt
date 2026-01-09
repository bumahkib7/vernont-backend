package com.vernont.workflow.shipping

import com.vernont.domain.fulfillment.Fulfillment
import com.vernont.domain.fulfillment.LabelStatus
import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderLineItem
import com.vernont.domain.order.OrderLineItemStatus
import com.vernont.domain.order.OrderStatus
import com.vernont.events.*
import com.vernont.repository.fulfillment.FulfillmentRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.workflow.flows.fulfillment.CreateShipmentInput
import com.vernont.workflow.flows.fulfillment.ShipmentItemInput
import com.vernont.workflow.outbox.OutboxService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Prepared shipment context returned from T1 (prepare step)
 */
data class PreparedShipment(
    val fulfillmentId: String,
    val orderId: String,
    val provider: String,
    val idempotencyKey: String,
    val labelAlreadyPurchased: Boolean,
    val existingLabelId: String?,
    val existingTrackingNumber: String?,
    val shipToAddress: ShippingAddress,
    val items: List<ValidatedShipmentItem>
)

data class ValidatedShipmentItem(
    val orderLineItemId: String,
    val fulfillmentItemId: String,
    val quantityToShip: Int,
    val currentShippedQuantity: Int,
    val newShippedQuantity: Int,
    val title: String,
    val sku: String
)

/**
 * Result of applying shipment to DB (T2)
 */
data class ApplyShipmentResult(
    val fulfillmentId: String,
    val orderId: String,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val labelId: String?,
    val shippedAt: Instant
)

/**
 * Transactional service for shipping operations.
 *
 * All DB operations are encapsulated here with proper transaction boundaries.
 * External API calls (label purchase/void) happen OUTSIDE this service.
 */
@Service
class ShippingTxService(
    private val orderRepository: OrderRepository,
    private val fulfillmentRepository: FulfillmentRepository,
    private val outboxService: OutboxService
) {

    /**
     * T1: Prepare shipment - load, validate, and prepare for label purchase
     *
     * @return PreparedShipment with all data needed for external label purchase
     */
    @Transactional(readOnly = true)
    fun prepareShipment(input: CreateShipmentInput): PreparedShipment {
        logger.info { "T1: Preparing shipment for order=${input.orderId}, fulfillment=${input.fulfillmentId}" }

        // Load order with items
        val order = orderRepository.findWithItemsById(input.orderId)
            ?: throw IllegalArgumentException("Order not found: ${input.orderId}")

        // Guard: order not canceled
        if (order.status == OrderStatus.CANCELED) {
            throw IllegalStateException("Cannot ship canceled order: ${input.orderId}")
        }

        // Guard: order not pending
        if (order.status == OrderStatus.PENDING) {
            throw IllegalStateException("Cannot ship pending order: ${input.orderId}")
        }

        // Load fulfillment with items
        val fulfillment = fulfillmentRepository.findByIdWithItems(input.fulfillmentId).orElse(null)
            ?: throw IllegalArgumentException("Fulfillment not found: ${input.fulfillmentId}")

        // Guard: fulfillment belongs to order
        if (fulfillment.orderId != order.id) {
            throw IllegalArgumentException("Fulfillment ${input.fulfillmentId} does not belong to order ${input.orderId}")
        }

        // Guard: fulfillment not canceled
        if (fulfillment.isCanceled()) {
            throw IllegalStateException("Cannot ship canceled fulfillment: ${input.fulfillmentId}")
        }

        // Check if already shipped (idempotent)
        if (fulfillment.isShipped() && fulfillment.hasLabelPurchased()) {
            logger.info { "Fulfillment already shipped with label - returning existing data" }
            return createPreparedShipmentFromExisting(order, fulfillment, input)
        }

        // Check if label already purchased (can skip external call)
        val labelAlreadyPurchased = fulfillment.hasLabelPurchased()
        if (labelAlreadyPurchased) {
            logger.info { "Label already purchased for fulfillment ${fulfillment.id} - will reuse" }
        }

        // Validate shipment items
        val validatedItems = validateShipmentItems(order, fulfillment, input.items)

        // Build ship-to address from fulfillment data
        val shipToAddress = extractShipToAddress(fulfillment)

        // Generate idempotency key
        val idempotencyKey = fulfillment.labelIdempotencyKey
            ?: fulfillment.generateLabelIdempotencyKey()

        return PreparedShipment(
            fulfillmentId = fulfillment.id,
            orderId = order.id,
            provider = fulfillment.provider?.providerId ?: "manual",
            idempotencyKey = idempotencyKey,
            labelAlreadyPurchased = labelAlreadyPurchased,
            existingLabelId = fulfillment.labelId,
            existingTrackingNumber = fulfillment.getTrackingNumbersList().firstOrNull(),
            shipToAddress = shipToAddress,
            items = validatedItems
        )
    }

    /**
     * T1b: Mark label as pending purchase (before external API call)
     *
     * This is a small write tx to set the idempotency key before the external call.
     */
    @Transactional
    fun markLabelPendingPurchase(fulfillmentId: String, idempotencyKey: String) {
        val fulfillment = fulfillmentRepository.findById(fulfillmentId).orElseThrow {
            IllegalArgumentException("Fulfillment not found: $fulfillmentId")
        }

        if (fulfillment.canPurchaseLabel()) {
            fulfillment.markLabelPendingPurchase(idempotencyKey)
            fulfillmentRepository.saveAndFlush(fulfillment)
            logger.debug { "Marked fulfillment $fulfillmentId as PENDING_PURCHASE" }
        }
    }

    /**
     * T2: Apply label result and mark as shipped
     *
     * @param prepared The prepared shipment from T1
     * @param labelResult The result from external label purchase (or null if manual)
     * @param correlationId Optional correlation ID for tracing
     * @return ApplyShipmentResult
     */
    @Transactional
    fun applyLabelResult(
        prepared: PreparedShipment,
        labelResult: LabelResult?,
        correlationId: String? = null
    ): ApplyShipmentResult {
        logger.info { "T2: Applying label result for fulfillment=${prepared.fulfillmentId}" }

        // Re-load with optimistic lock
        val fulfillment = fulfillmentRepository.findById(prepared.fulfillmentId).orElseThrow {
            IllegalArgumentException("Fulfillment not found: ${prepared.fulfillmentId}")
        }

        val order = orderRepository.findWithItemsById(prepared.orderId)
            ?: throw IllegalArgumentException("Order not found: ${prepared.orderId}")

        // Guard again (state might have changed)
        if (order.status == OrderStatus.CANCELED) {
            throw IllegalStateException("Order was canceled during shipping: ${prepared.orderId}")
        }

        if (fulfillment.isCanceled()) {
            throw IllegalStateException("Fulfillment was canceled during shipping: ${prepared.fulfillmentId}")
        }

        // Idempotent: if already shipped, just return existing data
        if (fulfillment.isShipped()) {
            logger.info { "Fulfillment already shipped (idempotent return)" }
            return ApplyShipmentResult(
                fulfillmentId = fulfillment.id,
                orderId = order.id,
                trackingNumber = fulfillment.getTrackingNumbersList().firstOrNull(),
                trackingUrl = fulfillment.getTrackingUrlsList().firstOrNull(),
                labelId = fulfillment.labelId,
                shippedAt = fulfillment.shippedAt!!
            )
        }

        // Apply label purchase result
        if (labelResult != null) {
            fulfillment.applyLabelPurchase(
                labelId = labelResult.labelId,
                trackingNumber = labelResult.trackingNumber,
                labelUrl = labelResult.labelUrl,
                carrier = labelResult.carrier,
                service = labelResult.service,
                costCents = labelResult.cost?.multiply(BigDecimal(100))?.toLong()
            )

            labelResult.trackingUrl?.let { fulfillment.addTrackingUrl(it) }
        }

        // Mark as shipped
        fulfillment.ship()

        // Update order line items with shipped quantities
        updateOrderLineItems(order, prepared.items)

        // Update order fulfillment status
        updateOrderFulfillmentStatus(order)

        // Save both
        try {
            fulfillmentRepository.saveAndFlush(fulfillment)
            orderRepository.saveAndFlush(order)
        } catch (e: ObjectOptimisticLockingFailureException) {
            logger.warn { "Optimistic lock conflict on shipment apply - concurrent update detected" }
            throw e
        }

        // Enqueue outbox events (same transaction)
        enqueueShipmentEvents(order, fulfillment, labelResult, prepared.items, correlationId)

        logger.info { "T2: Shipment applied successfully for fulfillment=${fulfillment.id}" }

        return ApplyShipmentResult(
            fulfillmentId = fulfillment.id,
            orderId = order.id,
            trackingNumber = fulfillment.getTrackingNumbersList().firstOrNull(),
            trackingUrl = fulfillment.getTrackingUrlsList().firstOrNull(),
            labelId = fulfillment.labelId,
            shippedAt = fulfillment.shippedAt!!
        )
    }

    /**
     * T3: Mark void outcome (after external void attempt)
     */
    @Transactional
    fun markVoidOutcome(
        fulfillmentId: String,
        success: Boolean,
        error: String?,
        correlationId: String? = null
    ) {
        logger.info { "T3: Marking void outcome for fulfillment=$fulfillmentId, success=$success" }

        val fulfillment = fulfillmentRepository.findById(fulfillmentId).orElseThrow {
            IllegalArgumentException("Fulfillment not found: $fulfillmentId")
        }

        if (success) {
            fulfillment.markLabelVoided()

            outboxService.enqueue(
                ShipmentLabelVoided(
                    fulfillmentId = fulfillmentId,
                    orderId = fulfillment.orderId ?: "",
                    labelId = fulfillment.labelId ?: "",
                    provider = fulfillment.provider?.providerId ?: "unknown",
                    refundAmount = fulfillment.labelCost?.let { BigDecimal(it).divide(BigDecimal(100)) }
                ),
                correlationId
            )
        } else {
            fulfillment.markLabelVoidFailed(error ?: "Unknown error")

            outboxService.enqueue(
                ShipmentLabelVoidFailed(
                    fulfillmentId = fulfillmentId,
                    orderId = fulfillment.orderId ?: "",
                    labelId = fulfillment.labelId ?: "",
                    provider = fulfillment.provider?.providerId ?: "unknown",
                    error = error ?: "Unknown error",
                    requiresManualIntervention = true
                ),
                correlationId
            )
        }

        fulfillmentRepository.saveAndFlush(fulfillment)
    }

    // ========== Private Helpers ==========

    private fun validateShipmentItems(
        order: Order,
        fulfillment: Fulfillment,
        items: List<ShipmentItemInput>
    ): List<ValidatedShipmentItem> {
        return items.map { item ->
            val orderLineItem = order.items.find { it.id == item.orderLineItemId }
                ?: throw IllegalArgumentException("Order line item not found: ${item.orderLineItemId}")

            val fulfillmentItem = fulfillment.items.find { it.lineItemId == item.orderLineItemId }
                ?: throw IllegalArgumentException("Line item ${item.orderLineItemId} not in fulfillment ${fulfillment.id}")

            // Validate quantity
            require(item.quantity > 0) { "Shipment quantity must be positive" }
            require(item.quantity <= fulfillmentItem.quantity) {
                "Cannot ship ${item.quantity} items. Fulfillment only has ${fulfillmentItem.quantity}"
            }

            val availableToShip = orderLineItem.quantity - orderLineItem.shippedQuantity
            require(item.quantity <= availableToShip) {
                "Cannot ship ${item.quantity} items. Only $availableToShip available"
            }

            ValidatedShipmentItem(
                orderLineItemId = item.orderLineItemId,
                fulfillmentItemId = fulfillmentItem.id,
                quantityToShip = item.quantity,
                currentShippedQuantity = orderLineItem.shippedQuantity,
                newShippedQuantity = orderLineItem.shippedQuantity + item.quantity,
                title = fulfillmentItem.title ?: orderLineItem.title,
                sku = fulfillmentItem.sku ?: orderLineItem.variantId ?: ""
            )
        }
    }

    private fun updateOrderLineItems(order: Order, items: List<ValidatedShipmentItem>) {
        items.forEach { item ->
            val orderLineItem = order.items.find { it.id == item.orderLineItemId }
            if (orderLineItem != null) {
                orderLineItem.shippedQuantity = item.newShippedQuantity

                orderLineItem.status = if (orderLineItem.isFullyShipped()) {
                    OrderLineItemStatus.SHIPPED
                } else {
                    OrderLineItemStatus.PARTIALLY_SHIPPED
                }
            }
        }
    }

    private fun updateOrderFulfillmentStatus(order: Order) {
        val allItems = order.items
        val fullyShipped = allItems.all { it.status == OrderLineItemStatus.SHIPPED }
        val anyShipped = allItems.any {
            it.status == OrderLineItemStatus.SHIPPED || it.status == OrderLineItemStatus.PARTIALLY_SHIPPED
        }

        order.fulfillmentStatus = when {
            fullyShipped -> com.vernont.domain.order.FulfillmentStatus.SHIPPED
            anyShipped -> com.vernont.domain.order.FulfillmentStatus.PARTIALLY_SHIPPED
            else -> order.fulfillmentStatus
        }
    }

    private fun enqueueShipmentEvents(
        order: Order,
        fulfillment: Fulfillment,
        labelResult: LabelResult?,
        items: List<ValidatedShipmentItem>,
        correlationId: String?
    ) {
        // ShipmentCreated event
        outboxService.enqueue(
            ShipmentCreated(
                fulfillmentId = fulfillment.id,
                orderId = order.id,
                trackingNumber = fulfillment.getTrackingNumbersList().firstOrNull(),
                carrierCode = fulfillment.carrierCode,
                items = items.map {
                    ShipmentItemData(
                        lineItemId = it.orderLineItemId,
                        variantId = "",
                        quantity = it.quantityToShip,
                        title = it.title
                    )
                }
            ),
            correlationId
        )

        // ShipmentLabelPurchased event (if label was purchased)
        if (labelResult != null && fulfillment.labelId != null) {
            outboxService.enqueue(
                ShipmentLabelPurchased(
                    fulfillmentId = fulfillment.id,
                    orderId = order.id,
                    labelId = fulfillment.labelId!!,
                    trackingNumber = fulfillment.getTrackingNumbersList().firstOrNull(),
                    trackingUrl = fulfillment.getTrackingUrlsList().firstOrNull(),
                    carrier = fulfillment.carrierCode,
                    service = fulfillment.serviceCode,
                    cost = fulfillment.labelCost?.let { BigDecimal(it).divide(BigDecimal(100)) },
                    labelUrl = fulfillment.labelUrl,
                    provider = fulfillment.provider?.providerId ?: "unknown",
                    idempotencyKey = fulfillment.labelIdempotencyKey ?: ""
                ),
                correlationId
            )
        }
    }

    private fun extractShipToAddress(fulfillment: Fulfillment): ShippingAddress {
        @Suppress("UNCHECKED_CAST")
        val deliveryAddress = fulfillment.data?.get("delivery_address") as? Map<String, Any?>

        return ShippingAddress(
            name = listOfNotNull(
                deliveryAddress?.get("first_name") as? String,
                deliveryAddress?.get("last_name") as? String
            ).joinToString(" ").takeIf { it.isNotBlank() },
            street1 = deliveryAddress?.get("address_1") as? String ?: "",
            street2 = deliveryAddress?.get("address_2") as? String,
            city = deliveryAddress?.get("city") as? String ?: "",
            state = deliveryAddress?.get("province") as? String,
            postalCode = deliveryAddress?.get("postal_code") as? String ?: "",
            country = deliveryAddress?.get("country_code") as? String ?: "US",
            phone = deliveryAddress?.get("phone") as? String
        )
    }

    private fun createPreparedShipmentFromExisting(
        order: Order,
        fulfillment: Fulfillment,
        input: CreateShipmentInput
    ): PreparedShipment {
        val validatedItems = validateShipmentItems(order, fulfillment, input.items)
        val shipToAddress = extractShipToAddress(fulfillment)

        return PreparedShipment(
            fulfillmentId = fulfillment.id,
            orderId = order.id,
            provider = fulfillment.provider?.providerId ?: "manual",
            idempotencyKey = fulfillment.labelIdempotencyKey ?: fulfillment.generateLabelIdempotencyKey(),
            labelAlreadyPurchased = true,
            existingLabelId = fulfillment.labelId,
            existingTrackingNumber = fulfillment.getTrackingNumbersList().firstOrNull(),
            shipToAddress = shipToAddress,
            items = validatedItems
        )
    }
}
