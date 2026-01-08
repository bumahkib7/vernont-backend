package com.vernont.domain.outbox

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Outbox event status
 */
enum class OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}

/**
 * Outbox event entity for reliable event delivery.
 *
 * Events are written to the outbox table in the same transaction as business state changes.
 * A background scheduler picks up pending events and publishes them, ensuring at-least-once delivery.
 */
@Entity
@Table(
    name = "outbox_event",
    indexes = [
        Index(name = "idx_outbox_event_status_next_attempt", columnList = "status, next_attempt_at"),
        Index(name = "idx_outbox_event_aggregate", columnList = "aggregate_id"),
        Index(name = "idx_outbox_event_type_created", columnList = "event_type, created_at")
    ]
)
class OutboxEvent : BaseEntity() {

    /**
     * Type of aggregate (e.g., "fulfillment", "order", "shipment")
     */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    var aggregateType: String = ""

    /**
     * ID of the aggregate this event belongs to
     */
    @Column(name = "aggregate_id", nullable = false, length = 36)
    var aggregateId: String = ""

    /**
     * Event type (e.g., "ShipmentCreated", "LabelPurchased")
     */
    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String = ""

    /**
     * JSON payload of the event
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    var payload: Map<String, Any?> = emptyMap()

    /**
     * Current status of the event
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING

    /**
     * Number of publish attempts
     */
    @Column(nullable = false)
    var attempts: Int = 0

    /**
     * When to attempt next publish (for backoff)
     */
    @Column(name = "next_attempt_at")
    var nextAttemptAt: Instant = Instant.now()

    /**
     * Last error message if publish failed
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null

    /**
     * When the event was successfully published
     */
    @Column(name = "published_at")
    var publishedAt: Instant? = null

    /**
     * Correlation ID for tracing
     */
    @Column(name = "correlation_id", length = 36)
    var correlationId: String? = null

    companion object {
        private const val MAX_ATTEMPTS = 10

        fun create(
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            payload: Map<String, Any?>,
            correlationId: String? = null
        ): OutboxEvent {
            return OutboxEvent().apply {
                this.aggregateType = aggregateType
                this.aggregateId = aggregateId
                this.eventType = eventType
                this.payload = payload
                this.correlationId = correlationId
                this.status = OutboxEventStatus.PENDING
                this.nextAttemptAt = Instant.now()
            }
        }
    }

    fun markPublished() {
        this.status = OutboxEventStatus.PUBLISHED
        this.publishedAt = Instant.now()
    }

    fun markFailed(error: String) {
        this.attempts++
        this.lastError = error

        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = OutboxEventStatus.FAILED
        } else {
            // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, 512s
            val backoffSeconds = (1L shl (attempts - 1).coerceAtMost(9))
            this.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds)
        }
    }

    fun canRetry(): Boolean = attempts < MAX_ATTEMPTS && status == OutboxEventStatus.PENDING
}
