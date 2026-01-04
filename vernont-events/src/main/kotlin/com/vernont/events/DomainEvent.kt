package com.vernont.events

import org.springframework.context.ApplicationEvent
import java.time.Instant
import java.util.UUID

/**
 * Base sealed class for all domain events in the NexusCommerce system.
 *
 * This serves as the root event type for the event-driven architecture,
 * extending Spring's ApplicationEvent for seamless integration with
 * Spring's event publishing and listening mechanisms.
 *
 * All domain events should extend this class to ensure consistency
 * and type safety across the application.
 *
 * @property eventId Unique identifier for this event
 * @property aggregateId The ID of the aggregate that triggered this event
 * @property occurredAt Timestamp when the event occurred
 * @property version Version of the event schema
 */
sealed class DomainEvent(
    open val eventId: String = UUID.randomUUID().toString(),
    open val aggregateId: String,
    open val occurredAt: Instant = Instant.now(),
    open val version: Int = 1
) : ApplicationEvent(System.currentTimeMillis()) {

    override fun toString(): String {
        return "${this::class.simpleName}(" +
                "eventId='$eventId', " +
                "aggregateId='$aggregateId', " +
                "occurredAt=$occurredAt, " +
                "version=$version)"
    }
}
