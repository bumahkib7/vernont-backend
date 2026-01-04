package com.vernont.events

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Wrapper around Spring's ApplicationEventPublisher for publishing domain events.
 *
 * This component provides a simplified and type-safe interface for publishing
 * domain events throughout the application. It handles logging and ensures
 * consistent event publishing behavior.
 *
 * Example usage:
 * ```
 * val event = ProductCreated(
 *     aggregateId = "prod-123",
 *     name = "Widget",
 *     description = "A useful widget",
 *     price = BigDecimal("9.99"),
 *     sku = "WIDGET-001",
 *     quantity = 100,
 *     categoryId = "cat-456"
 * )
 * eventPublisher.publish(event)
 * ```
 */
@Component
class EventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Publishes a domain event to all registered event listeners.
     *
     * @param event The domain event to publish
     */
    fun publish(event: DomainEvent) {
        logger.debug("Publishing event: {} (id: {}, aggregateId: {})",
            event::class.simpleName,
            event.eventId,
            event.aggregateId)

        try {
            applicationEventPublisher.publishEvent(event)
            logger.info("Event published successfully: {} (id: {})",
                event::class.simpleName,
                event.eventId)
        } catch (e: Exception) {
            logger.error("Error publishing event: {} (id: {})",
                event::class.simpleName,
                event.eventId,
                e)
            throw e
        }
    }

    /**
     * Publishes multiple domain events in sequence.
     *
     * @param events The domain events to publish
     */
    fun publishAll(events: List<DomainEvent>) {
        logger.debug("Publishing {} events", events.size)
        events.forEach { event ->
            try {
                publish(event)
            } catch (e: Exception) {
                logger.error("Error publishing event in batch: {}", event.eventId, e)
                throw e
            }
        }
        logger.info("Published {} events successfully", events.size)
    }
}
