package com.vernont.workflow.outbox

import com.vernont.domain.outbox.OutboxEvent
import com.vernont.domain.outbox.OutboxEventStatus
import com.vernont.events.DomainEvent
import com.vernont.events.EventPublisher
import com.vernont.repository.outbox.OutboxEventRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Background scheduler that publishes outbox events.
 *
 * Polls the outbox table for pending events and publishes them to the event bus.
 * Handles retries with exponential backoff and marks failed events after max attempts.
 */
@Component
class OutboxPublisherScheduler(
    private val outboxEventRepository: OutboxEventRepository,
    private val eventPublisher: EventPublisher,
    private val outboxEventMapper: OutboxEventMapper,
    @Value("\${app.outbox.batch-size:100}")
    private val batchSize: Int = 100,
    @Value("\${app.outbox.enabled:true}")
    private val enabled: Boolean = true
) {

    /**
     * Poll and publish pending outbox events every 2 seconds
     */
    @Scheduled(fixedRate = 2000)
    fun publishPendingEvents() {
        if (!enabled) return

        try {
            val pendingEvents = outboxEventRepository.findPendingEventsBatch(Instant.now(), batchSize)

            if (pendingEvents.isEmpty()) return

            logger.debug { "Found ${pendingEvents.size} pending outbox events to publish" }

            pendingEvents.forEach { event ->
                publishEvent(event)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error polling outbox events" }
        }
    }

    /**
     * Publish a single event, handling success and failure
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun publishEvent(event: OutboxEvent) {
        try {
            // Convert to domain event
            val domainEvent = outboxEventMapper.toDomainEvent(event)

            if (domainEvent != null) {
                // Publish to Spring event bus (or Kafka later)
                eventPublisher.publish(domainEvent)
            }

            // Mark as published
            event.markPublished()
            outboxEventRepository.saveAndFlush(event)

            logger.debug { "Published outbox event: ${event.eventType} (id=${event.id}, aggregate=${event.aggregateId})" }

        } catch (e: ObjectOptimisticLockingFailureException) {
            // Another instance published this event - that's fine
            logger.info { "Outbox event already processed (optimistic lock): ${event.id}" }

        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish outbox event: ${event.eventType} (id=${event.id})" }

            try {
                // Re-fetch to avoid stale state
                val freshEvent = outboxEventRepository.findById(event.id).orElse(null)
                if (freshEvent != null && freshEvent.status == OutboxEventStatus.PENDING) {
                    freshEvent.markFailed(e.message ?: "Unknown error")
                    outboxEventRepository.saveAndFlush(freshEvent)

                    if (freshEvent.status == OutboxEventStatus.FAILED) {
                        logger.error { "Outbox event permanently failed after ${freshEvent.attempts} attempts: ${freshEvent.eventType} (id=${freshEvent.id})" }
                    }
                }
            } catch (saveError: Exception) {
                logger.error(saveError) { "Failed to update outbox event failure status: ${event.id}" }
            }
        }
    }

    /**
     * Alert on failed events (run hourly)
     */
    @Scheduled(fixedRate = 3600000)
    fun alertOnFailedEvents() {
        if (!enabled) return

        try {
            val failedCount = outboxEventRepository.countByStatus(OutboxEventStatus.FAILED)
            if (failedCount > 0) {
                logger.warn { "ALERT: $failedCount outbox events in FAILED status require attention" }

                // Log details of failed events
                val failedEvents = outboxEventRepository.findByStatus(OutboxEventStatus.FAILED)
                failedEvents.take(10).forEach { event ->
                    logger.warn {
                        "Failed outbox event: type=${event.eventType}, aggregate=${event.aggregateId}, " +
                            "attempts=${event.attempts}, lastError=${event.lastError}"
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error checking for failed outbox events" }
        }
    }

    /**
     * Cleanup old published events (run daily at 3 AM)
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    fun cleanupOldEvents() {
        if (!enabled) return

        try {
            // Keep published events for 7 days
            val cutoff = Instant.now().minus(java.time.Duration.ofDays(7))
            val deleted = outboxEventRepository.deletePublishedBefore(cutoff)
            if (deleted > 0) {
                logger.info { "Cleaned up $deleted old published outbox events" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error cleaning up old outbox events" }
        }
    }
}
