package com.vernont.api.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vernont.domain.inventory.InventoryMovement
import com.vernont.events.*
import com.vernont.infrastructure.messaging.MessagingTopics
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.InventoryMovementRepository
import com.vernont.repository.inventory.StockLocationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Consumes inventory events from Kafka/Redpanda and persists them as InventoryMovement records.
 * Also listens to Spring ApplicationEvents for backward compatibility.
 */
@Component
class InventoryEventConsumer(
    private val inventoryMovementRepository: InventoryMovementRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * Kafka consumer for inventory events (when using Redpanda/Kafka)
     */
    @KafkaListener(
        topics = [MessagingTopics.INVENTORY_EVENTS],
        groupId = "\${messaging.kafka.consumer-group-id:vernont-inventory-consumer}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @ConditionalOnProperty(name = ["messaging.provider"], havingValue = "kafka", matchIfMissing = true)
    fun consumeFromKafka(message: String, ack: Acknowledgment) {
        try {
            logger.debug { "Received inventory event from Kafka: ${message.take(200)}..." }

            // Parse the event type from the message
            val eventMap = objectMapper.readValue<Map<String, Any>>(message)
            val eventType = eventMap["eventType"] as? String
                ?: eventMap["type"] as? String
                ?: eventMap.keys.firstOrNull { it.contains("Type") }?.let { eventMap[it] as? String }

            when {
                message.contains("InventoryAdjusted") || eventType == "InventoryAdjusted" -> {
                    val event = objectMapper.readValue<InventoryAdjusted>(message)
                    handleInventoryAdjusted(event)
                }
                message.contains("InventoryReserved") || eventType == "InventoryReserved" -> {
                    val event = objectMapper.readValue<InventoryReserved>(message)
                    handleInventoryReserved(event)
                }
                message.contains("InventoryReleased") || eventType == "InventoryReleased" -> {
                    val event = objectMapper.readValue<InventoryReleased>(message)
                    handleInventoryReleased(event)
                }
                message.contains("InventoryFulfilled") || eventType == "InventoryFulfilled" -> {
                    val event = objectMapper.readValue<InventoryFulfilled>(message)
                    handleInventoryFulfilled(event)
                }
                else -> {
                    logger.warn { "Unknown inventory event type: $eventType" }
                }
            }

            ack.acknowledge()
        } catch (e: Exception) {
            logger.error(e) { "Failed to process inventory event from Kafka: ${e.message}" }
            // Don't acknowledge - let Kafka retry
        }
    }

    /**
     * Spring ApplicationEvent listener for InventoryAdjusted events
     * (used when events are published via Spring's ApplicationEventPublisher)
     */
    @EventListener
    @Async
    @Transactional
    fun onInventoryAdjusted(event: InventoryAdjusted) {
        logger.info { "Received InventoryAdjusted event via Spring: ${event.eventId}" }
        handleInventoryAdjusted(event)
    }

    @EventListener
    @Async
    @Transactional
    fun onInventoryReserved(event: InventoryReserved) {
        logger.info { "Received InventoryReserved event via Spring: ${event.eventId}" }
        handleInventoryReserved(event)
    }

    @EventListener
    @Async
    @Transactional
    fun onInventoryReleased(event: InventoryReleased) {
        logger.info { "Received InventoryReleased event via Spring: ${event.eventId}" }
        handleInventoryReleased(event)
    }

    @EventListener
    @Async
    @Transactional
    fun onInventoryFulfilled(event: InventoryFulfilled) {
        logger.info { "Received InventoryFulfilled event via Spring: ${event.eventId}" }
        handleInventoryFulfilled(event)
    }

    // =========================================================================
    // Event Handlers
    // =========================================================================

    @Transactional
    fun handleInventoryAdjusted(event: InventoryAdjusted) {
        try {
            // Check if we've already processed this event (idempotency)
            if (inventoryMovementRepository.existsByEventId(event.eventId)) {
                logger.debug { "Event ${event.eventId} already processed, skipping" }
                return
            }

            // Enrich with additional data
            val inventoryLevel = inventoryLevelRepository.findByIdAndDeletedAtIsNull(event.aggregateId)
            val location = inventoryLevel?.location
                ?: stockLocationRepository.findByIdAndDeletedAtIsNull(event.locationId)
            val inventoryItem = inventoryLevel?.inventoryItem
                ?: inventoryItemRepository.findByIdAndDeletedAtIsNull(event.inventoryItemId)

            val movement = InventoryMovement.fromAdjustment(
                eventId = event.eventId,
                inventoryItemId = event.inventoryItemId,
                inventoryLevelId = event.aggregateId,
                locationId = event.locationId,
                locationName = location?.name,
                sku = inventoryItem?.sku,
                productTitle = null, // Could be enriched from variant
                adjustment = event.adjustment,
                previousQuantity = null, // Not available in event
                newQuantity = inventoryLevel?.stockedQuantity,
                reason = event.reason,
                note = null,
                performedBy = null,
                occurredAt = event.occurredAt
            )

            inventoryMovementRepository.save(movement)
            logger.info { "Persisted inventory adjustment movement: ${movement.id}, adjustment=${event.adjustment}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to persist InventoryAdjusted event: ${e.message}" }
            throw e
        }
    }

    @Transactional
    fun handleInventoryReserved(event: InventoryReserved) {
        try {
            if (inventoryMovementRepository.existsByEventId(event.eventId)) {
                logger.debug { "Event ${event.eventId} already processed, skipping" }
                return
            }

            val location = stockLocationRepository.findByIdAndDeletedAtIsNull(event.locationId)
            val inventoryItem = inventoryItemRepository.findByIdAndDeletedAtIsNull(event.inventoryItemId)

            val movement = InventoryMovement.fromReservation(
                eventId = event.eventId,
                inventoryItemId = event.inventoryItemId,
                inventoryLevelId = event.aggregateId,
                locationId = event.locationId,
                quantity = event.quantity,
                reservationId = event.reservationId,
                lineItemId = event.lineItemId,
                occurredAt = event.occurredAt
            ).apply {
                this.locationName = location?.name
                this.sku = inventoryItem?.sku
            }

            inventoryMovementRepository.save(movement)
            logger.info { "Persisted inventory reservation movement: ${movement.id}, quantity=${event.quantity}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to persist InventoryReserved event: ${e.message}" }
            throw e
        }
    }

    @Transactional
    fun handleInventoryReleased(event: InventoryReleased) {
        try {
            if (inventoryMovementRepository.existsByEventId(event.eventId)) {
                logger.debug { "Event ${event.eventId} already processed, skipping" }
                return
            }

            val location = stockLocationRepository.findByIdAndDeletedAtIsNull(event.locationId)
            val inventoryItem = inventoryItemRepository.findByIdAndDeletedAtIsNull(event.inventoryItemId)

            val movement = InventoryMovement.fromRelease(
                eventId = event.eventId,
                inventoryItemId = event.inventoryItemId,
                inventoryLevelId = event.aggregateId,
                locationId = event.locationId,
                quantity = event.quantity,
                reservationId = event.reservationId,
                occurredAt = event.occurredAt
            ).apply {
                this.locationName = location?.name
                this.sku = inventoryItem?.sku
            }

            inventoryMovementRepository.save(movement)
            logger.info { "Persisted inventory release movement: ${movement.id}, quantity=${event.quantity}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to persist InventoryReleased event: ${e.message}" }
            throw e
        }
    }

    @Transactional
    fun handleInventoryFulfilled(event: InventoryFulfilled) {
        try {
            if (inventoryMovementRepository.existsByEventId(event.eventId)) {
                logger.debug { "Event ${event.eventId} already processed, skipping" }
                return
            }

            val location = stockLocationRepository.findByIdAndDeletedAtIsNull(event.locationId)
            val inventoryItem = inventoryItemRepository.findByIdAndDeletedAtIsNull(event.inventoryItemId)

            val movement = InventoryMovement.fromFulfillment(
                eventId = event.eventId,
                inventoryItemId = event.inventoryItemId,
                inventoryLevelId = event.aggregateId,
                locationId = event.locationId,
                quantity = event.quantity,
                orderId = null, // Not available in event
                occurredAt = event.occurredAt
            ).apply {
                this.locationName = location?.name
                this.sku = inventoryItem?.sku
            }

            inventoryMovementRepository.save(movement)
            logger.info { "Persisted inventory fulfillment movement: ${movement.id}, quantity=${event.quantity}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to persist InventoryFulfilled event: ${e.message}" }
            throw e
        }
    }
}
