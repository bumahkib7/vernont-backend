package com.vernont.workflow.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.vernont.domain.outbox.OutboxEvent
import com.vernont.events.DomainEvent
import com.vernont.repository.outbox.OutboxEventRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Service for enqueueing events to the outbox.
 *
 * Workflows call this service to enqueue events within the same transaction
 * that writes business state. This ensures atomic state + event semantics.
 *
 * Usage:
 * ```
 * @Transactional
 * fun applyShipment(fulfillment: Fulfillment) {
 *     fulfillmentRepository.save(fulfillment)
 *     outboxService.enqueue(ShipmentCreated(...))
 * }
 * ```
 */
@Service
class OutboxService(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * Enqueue a domain event to the outbox.
     *
     * MUST be called within an existing transaction (same tx as business state write).
     * The event will be persisted in the same transaction and published by the scheduler.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun enqueue(event: DomainEvent, correlationId: String? = null) {
        val payload = objectMapper.convertValue<Map<String, Any?>>(event)

        val outboxEvent = OutboxEvent.create(
            aggregateType = extractAggregateType(event),
            aggregateId = event.aggregateId,
            eventType = event::class.simpleName ?: "Unknown",
            payload = payload,
            correlationId = correlationId
        )

        outboxEventRepository.save(outboxEvent)

        logger.debug {
            "Enqueued outbox event: ${outboxEvent.eventType} (aggregate=${outboxEvent.aggregateId})"
        }
    }

    /**
     * Enqueue multiple events in the same transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun enqueueAll(events: List<DomainEvent>, correlationId: String? = null) {
        events.forEach { event ->
            enqueue(event, correlationId)
        }
    }

    /**
     * Extract aggregate type from event class name
     */
    private fun extractAggregateType(event: DomainEvent): String {
        val className = event::class.simpleName ?: "Unknown"
        return when {
            className.contains("Shipment") -> "fulfillment"
            className.contains("Fulfillment") -> "fulfillment"
            className.contains("Order") -> "order"
            className.contains("Payment") -> "payment"
            className.contains("Inventory") -> "inventory"
            else -> "unknown"
        }
    }
}
