package com.vernont.domain.order

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Tracks order lifecycle events with accurate timestamps.
 * Used for order tracking timeline and audit purposes.
 */
@Entity
@Table(
    name = "order_event",
    indexes = [
        Index(name = "idx_order_event_order_id", columnList = "order_id"),
        Index(name = "idx_order_event_type", columnList = "event_type"),
        Index(name = "idx_order_event_created_at", columnList = "created_at"),
        Index(name = "idx_order_event_deleted_at", columnList = "deleted_at")
    ]
)
class OrderEvent : BaseEntity() {

    @Column(name = "order_id", nullable = false)
    var orderId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    var eventType: OrderEventType = OrderEventType.ORDER_PLACED

    @Column(nullable = false)
    var title: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column
    var location: String? = null

    /**
     * Who or what triggered this event.
     * Could be: customer email, admin user ID, "system", "stripe", etc.
     */
    @Column(name = "triggered_by")
    var triggeredBy: String? = null

    /**
     * Role of who triggered: CUSTOMER, ADMIN, SYSTEM, PAYMENT_PROVIDER, etc.
     */
    @Column(name = "triggered_by_role")
    var triggeredByRole: String? = null

    /**
     * Additional event-specific data stored as JSONB.
     * Examples:
     * - Payment: { "payment_id": "...", "amount": 1000, "provider": "stripe" }
     * - Shipping: { "tracking_number": "...", "carrier": "royal_mail" }
     * - Refund: { "refund_id": "...", "amount": 500, "reason": "..." }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb")
    var eventData: Map<String, Any>? = null

    /**
     * Reference to related entity (e.g., fulfillment_id, payment_id, refund_id)
     */
    @Column(name = "reference_id")
    var referenceId: String? = null

    /**
     * Type of the referenced entity
     */
    @Column(name = "reference_type")
    var referenceType: String? = null

    companion object {
        fun create(
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
            return OrderEvent().apply {
                this.orderId = orderId
                this.eventType = eventType
                this.title = title
                this.description = description
                this.location = location
                this.triggeredBy = triggeredBy
                this.triggeredByRole = triggeredByRole
                this.eventData = eventData
                this.referenceId = referenceId
                this.referenceType = referenceType
            }
        }
    }
}

/**
 * All possible order event types for the order lifecycle.
 */
enum class OrderEventType {
    // Order lifecycle
    ORDER_PLACED,
    ORDER_CONFIRMED,
    ORDER_UPDATED,
    ORDER_CANCELED,
    ORDER_COMPLETED,
    ORDER_ARCHIVED,

    // Payment events
    PAYMENT_PENDING,
    PAYMENT_AUTHORIZED,
    PAYMENT_CAPTURED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,
    PAYMENT_PARTIALLY_REFUNDED,

    // Fulfillment events
    FULFILLMENT_CREATED,
    FULFILLMENT_CANCELED,

    // Shipping events
    SHIPMENT_CREATED,
    SHIPPED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_ATTEMPTED,
    DELIVERY_FAILED,

    // Return events
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURN_RECEIVED,
    RETURN_REJECTED,

    // Exchange events
    EXCHANGE_REQUESTED,
    EXCHANGE_APPROVED,
    EXCHANGE_COMPLETED,

    // Customer actions
    NOTE_ADDED,
    CUSTOMER_CONTACTED,

    // Admin actions
    ADMIN_NOTE_ADDED,
    STATUS_CHANGED,

    // Other
    CUSTOM
}
