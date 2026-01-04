package com.vernont.application.order

import com.vernont.domain.order.OrderEvent
import com.vernont.domain.order.OrderEventType
import com.vernont.repository.order.OrderEventRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@Service
class OrderEventService(
    private val orderEventRepository: OrderEventRepository
) {

    // =========================================================================
    // Order Lifecycle Events
    // =========================================================================

    @Transactional
    fun recordOrderPlaced(
        orderId: String,
        email: String,
        total: BigDecimal,
        currencyCode: String,
        itemCount: Int
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.ORDER_PLACED,
            title = "Order placed",
            description = "Order placed with $itemCount item(s)",
            location = "Online",
            triggeredBy = email,
            triggeredByRole = "CUSTOMER",
            eventData = mapOf(
                "total" to total.toDouble(),
                "currency_code" to currencyCode,
                "item_count" to itemCount
            )
        )
    }

    @Transactional
    fun recordOrderConfirmed(
        orderId: String,
        triggeredBy: String? = null
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.ORDER_CONFIRMED,
            title = "Order confirmed",
            description = "Order has been confirmed and is being processed",
            location = "Vernont",
            triggeredBy = triggeredBy ?: "system",
            triggeredByRole = "SYSTEM"
        )
    }

    @Transactional
    fun recordOrderCanceled(
        orderId: String,
        reason: String?,
        canceledBy: String?,
        canceledByRole: String = "CUSTOMER"
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.ORDER_CANCELED,
            title = "Order canceled",
            description = reason ?: "Order has been canceled",
            triggeredBy = canceledBy,
            triggeredByRole = canceledByRole,
            eventData = reason?.let { mapOf("reason" to it) }
        )
    }

    @Transactional
    fun recordOrderCompleted(
        orderId: String,
        completedBy: String? = null
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.ORDER_COMPLETED,
            title = "Order completed",
            description = "Order has been completed successfully",
            triggeredBy = completedBy ?: "system",
            triggeredByRole = if (completedBy != null) "ADMIN" else "SYSTEM"
        )
    }

    // =========================================================================
    // Payment Events
    // =========================================================================

    @Transactional
    fun recordPaymentAuthorized(
        orderId: String,
        paymentId: String,
        amount: BigDecimal,
        currencyCode: String,
        provider: String = "stripe"
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.PAYMENT_AUTHORIZED,
            title = "Payment authorized",
            description = "Payment of ${currencyCode.uppercase()} ${amount.toPlainString()} authorized",
            triggeredBy = provider,
            triggeredByRole = "PAYMENT_PROVIDER",
            eventData = mapOf(
                "payment_id" to paymentId,
                "amount" to amount.toDouble(),
                "currency_code" to currencyCode,
                "provider" to provider
            ),
            referenceId = paymentId,
            referenceType = "payment"
        )
    }

    @Transactional
    fun recordPaymentCaptured(
        orderId: String,
        paymentId: String,
        amount: BigDecimal,
        currencyCode: String,
        provider: String = "stripe"
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.PAYMENT_CAPTURED,
            title = "Payment captured",
            description = "Payment of ${currencyCode.uppercase()} ${amount.toPlainString()} successfully captured",
            location = "Vernont",
            triggeredBy = provider,
            triggeredByRole = "PAYMENT_PROVIDER",
            eventData = mapOf(
                "payment_id" to paymentId,
                "amount" to amount.toDouble(),
                "currency_code" to currencyCode,
                "provider" to provider
            ),
            referenceId = paymentId,
            referenceType = "payment"
        )
    }

    @Transactional
    fun recordPaymentFailed(
        orderId: String,
        paymentId: String?,
        reason: String?,
        provider: String = "stripe"
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.PAYMENT_FAILED,
            title = "Payment failed",
            description = reason ?: "Payment processing failed",
            triggeredBy = provider,
            triggeredByRole = "PAYMENT_PROVIDER",
            eventData = buildMap {
                paymentId?.let { put("payment_id", it) }
                reason?.let { put("failure_reason", it) }
                put("provider", provider)
            },
            referenceId = paymentId,
            referenceType = "payment"
        )
    }

    @Transactional
    fun recordPaymentRefunded(
        orderId: String,
        refundId: String,
        amount: BigDecimal,
        currencyCode: String,
        reason: String?,
        refundedBy: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.PAYMENT_REFUNDED,
            title = "Payment refunded",
            description = "Refund of ${currencyCode.uppercase()} ${amount.toPlainString()} processed",
            triggeredBy = refundedBy ?: "system",
            triggeredByRole = if (refundedBy != null) "ADMIN" else "SYSTEM",
            eventData = buildMap {
                put("refund_id", refundId)
                put("amount", amount.toDouble())
                put("currency_code", currencyCode)
                reason?.let { put("reason", it) }
            },
            referenceId = refundId,
            referenceType = "refund"
        )
    }

    // =========================================================================
    // Fulfillment Events
    // =========================================================================

    @Transactional
    fun recordFulfillmentCreated(
        orderId: String,
        fulfillmentId: String,
        itemCount: Int,
        fulfilledBy: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.FULFILLMENT_CREATED,
            title = "Order fulfilled",
            description = "$itemCount item(s) prepared for shipment",
            location = "Vernont Warehouse",
            triggeredBy = fulfilledBy ?: "system",
            triggeredByRole = if (fulfilledBy != null) "ADMIN" else "SYSTEM",
            eventData = mapOf(
                "fulfillment_id" to fulfillmentId,
                "item_count" to itemCount
            ),
            referenceId = fulfillmentId,
            referenceType = "fulfillment"
        )
    }

    @Transactional
    fun recordFulfillmentCanceled(
        orderId: String,
        fulfillmentId: String,
        reason: String?,
        canceledBy: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.FULFILLMENT_CANCELED,
            title = "Fulfillment canceled",
            description = reason ?: "Fulfillment has been canceled",
            triggeredBy = canceledBy ?: "system",
            triggeredByRole = if (canceledBy != null) "ADMIN" else "SYSTEM",
            eventData = buildMap {
                put("fulfillment_id", fulfillmentId)
                reason?.let { put("reason", it) }
            },
            referenceId = fulfillmentId,
            referenceType = "fulfillment"
        )
    }

    // =========================================================================
    // Shipping Events
    // =========================================================================

    @Transactional
    fun recordShipped(
        orderId: String,
        fulfillmentId: String,
        trackingNumber: String?,
        carrier: String?,
        trackingUrl: String?,
        shippedBy: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.SHIPPED,
            title = "Order shipped",
            description = if (carrier != null) "Shipped via $carrier" else "Order has been shipped",
            location = carrier ?: "Courier",
            triggeredBy = shippedBy ?: "system",
            triggeredByRole = if (shippedBy != null) "ADMIN" else "SYSTEM",
            eventData = buildMap {
                put("fulfillment_id", fulfillmentId)
                trackingNumber?.let { put("tracking_number", it) }
                carrier?.let { put("carrier", it) }
                trackingUrl?.let { put("tracking_url", it) }
            },
            referenceId = fulfillmentId,
            referenceType = "fulfillment"
        )
    }

    @Transactional
    fun recordInTransit(
        orderId: String,
        fulfillmentId: String,
        location: String?,
        carrier: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.IN_TRANSIT,
            title = "In transit",
            description = "Package is on its way",
            location = location ?: "In Transit",
            triggeredBy = carrier ?: "carrier",
            triggeredByRole = "CARRIER",
            eventData = mapOf("fulfillment_id" to fulfillmentId),
            referenceId = fulfillmentId,
            referenceType = "fulfillment"
        )
    }

    @Transactional
    fun recordOutForDelivery(
        orderId: String,
        fulfillmentId: String,
        location: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.OUT_FOR_DELIVERY,
            title = "Out for delivery",
            description = "Package is out for delivery",
            location = location ?: "Local Area",
            triggeredBy = "carrier",
            triggeredByRole = "CARRIER",
            eventData = mapOf("fulfillment_id" to fulfillmentId),
            referenceId = fulfillmentId,
            referenceType = "fulfillment"
        )
    }

    @Transactional
    fun recordDelivered(
        orderId: String,
        fulfillmentId: String,
        deliveredTo: String?,
        signature: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.DELIVERED,
            title = "Delivered",
            description = if (deliveredTo != null) "Delivered to $deliveredTo" else "Package delivered",
            location = "Delivered",
            triggeredBy = "carrier",
            triggeredByRole = "CARRIER",
            eventData = buildMap {
                put("fulfillment_id", fulfillmentId)
                deliveredTo?.let { put("delivered_to", it) }
                signature?.let { put("signature", it) }
            },
            referenceId = fulfillmentId,
            referenceType = "fulfillment"
        )
    }

    // =========================================================================
    // Return Events
    // =========================================================================

    @Transactional
    fun recordReturnRequested(
        orderId: String,
        returnId: String,
        itemCount: Int,
        refundAmount: BigDecimal,
        currencyCode: String,
        requestedBy: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.RETURN_REQUESTED,
            title = "Return requested",
            description = "Return request submitted for $itemCount item(s)",
            triggeredBy = requestedBy,
            triggeredByRole = "CUSTOMER",
            eventData = mapOf(
                "return_id" to returnId,
                "item_count" to itemCount,
                "refund_amount" to refundAmount.toDouble(),
                "currency_code" to currencyCode
            ),
            referenceId = returnId,
            referenceType = "return"
        )
    }

    @Transactional
    fun recordReturnApproved(
        orderId: String,
        returnId: String,
        approvedBy: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.RETURN_APPROVED,
            title = "Return approved",
            description = "Return request has been approved",
            triggeredBy = approvedBy ?: "system",
            triggeredByRole = if (approvedBy != null) "ADMIN" else "SYSTEM",
            eventData = mapOf("return_id" to returnId),
            referenceId = returnId,
            referenceType = "return"
        )
    }

    @Transactional
    fun recordReturnReceived(
        orderId: String,
        returnId: String,
        receivedBy: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.RETURN_RECEIVED,
            title = "Return received",
            description = "Returned items have been received at warehouse",
            location = "Vernont Warehouse",
            triggeredBy = receivedBy ?: "system",
            triggeredByRole = if (receivedBy != null) "ADMIN" else "SYSTEM",
            eventData = mapOf("return_id" to returnId),
            referenceId = returnId,
            referenceType = "return"
        )
    }

    @Transactional
    fun recordReturnRejected(
        orderId: String,
        returnId: String,
        reason: String?,
        rejectedBy: String?
    ): OrderEvent {
        return record(
            orderId = orderId,
            eventType = OrderEventType.RETURN_REJECTED,
            title = "Return rejected",
            description = reason ?: "Return request has been rejected",
            triggeredBy = rejectedBy ?: "system",
            triggeredByRole = if (rejectedBy != null) "ADMIN" else "SYSTEM",
            eventData = buildMap {
                put("return_id", returnId)
                reason?.let { put("rejection_reason", it) }
            },
            referenceId = returnId,
            referenceType = "return"
        )
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    fun getOrderEvents(orderId: String): List<OrderEvent> {
        return orderEventRepository.findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAsc(orderId)
    }

    fun getOrderEventsDescending(orderId: String): List<OrderEvent> {
        return orderEventRepository.findByOrderIdAndDeletedAtIsNullOrderByCreatedAtDesc(orderId)
    }

    fun getLatestEvent(orderId: String, eventType: OrderEventType): OrderEvent? {
        return orderEventRepository.findFirstByOrderIdAndEventTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
            orderId, eventType
        )
    }

    fun hasEvent(orderId: String, eventType: OrderEventType): Boolean {
        return orderEventRepository.existsByOrderIdAndEventTypeAndDeletedAtIsNull(orderId, eventType)
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private fun record(
        orderId: String,
        eventType: OrderEventType,
        title: String,
        description: String? = null,
        location: String? = null,
        triggeredBy: String? = null,
        triggeredByRole: String? = null,
        eventData: Map<String, Any>? = null,
        referenceId: String? = null,
        referenceType: String? = null
    ): OrderEvent {
        val event = OrderEvent.create(
            orderId = orderId,
            eventType = eventType,
            title = title,
            description = description,
            location = location,
            triggeredBy = triggeredBy,
            triggeredByRole = triggeredByRole,
            eventData = eventData,
            referenceId = referenceId,
            referenceType = referenceType
        )

        val savedEvent = orderEventRepository.save(event)
        logger.info { "Recorded order event: ${eventType.name} for order $orderId" }
        return savedEvent
    }
}
