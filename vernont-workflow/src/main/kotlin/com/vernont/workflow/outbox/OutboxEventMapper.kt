package com.vernont.workflow.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.vernont.domain.outbox.OutboxEvent
import com.vernont.events.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Maps outbox events to domain events for publishing.
 *
 * This mapper converts the generic JSON payload stored in the outbox
 * back into typed domain event objects.
 */
@Component
class OutboxEventMapper(
    private val objectMapper: ObjectMapper
) {

    /**
     * Convert an outbox event to a domain event for publishing
     */
    fun toDomainEvent(outboxEvent: OutboxEvent): DomainEvent? {
        return try {
            when (outboxEvent.eventType) {
                // Shipment events
                "ShipmentCreated" -> objectMapper.convertValue<ShipmentCreated>(outboxEvent.payload)
                "ShipmentLabelPurchased" -> objectMapper.convertValue<ShipmentLabelPurchased>(outboxEvent.payload)
                "ShipmentLabelVoided" -> objectMapper.convertValue<ShipmentLabelVoided>(outboxEvent.payload)
                "ShipmentLabelVoidFailed" -> objectMapper.convertValue<ShipmentLabelVoidFailed>(outboxEvent.payload)

                // Fulfillment events
                "FulfillmentCreated" -> objectMapper.convertValue<FulfillmentCreated>(outboxEvent.payload)

                // Order events
                "OrderUpdated" -> objectMapper.convertValue<OrderUpdated>(outboxEvent.payload)

                else -> {
                    logger.warn { "Unknown outbox event type: ${outboxEvent.eventType}" }
                    // Try generic deserialization as fallback
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to deserialize outbox event: ${outboxEvent.eventType} (id=${outboxEvent.id})" }
            throw e
        }
    }
}
