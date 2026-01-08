package com.vernont.application.inventory

import com.vernont.events.FulfillmentCreated
import com.vernont.events.FulfillmentCancelled
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Event listener that handles inventory adjustments when fulfillments are created/cancelled.
 *
 * This decouples the fulfillment module from the inventory module using event-driven architecture.
 */
@Component
class FulfillmentInventoryListener(
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryLevelRepository: InventoryLevelRepository
) {

    /**
     * When a fulfillment is created, decrement inventory for each item.
     */
    @Async
    @EventListener
    @Transactional
    fun onFulfillmentCreated(event: FulfillmentCreated) {
        logger.info { "Handling FulfillmentCreated event: ${event.aggregateId} with ${event.items.size} items" }

        if (event.items.isEmpty()) {
            logger.warn { "FulfillmentCreated event has no items, skipping inventory decrement" }
            return
        }

        val locationId = event.locationId

        event.items.forEach { item ->
            try {
                decrementInventory(item.sku, locationId, item.quantity)
            } catch (e: Exception) {
                logger.error(e) { "Failed to decrement inventory for ${item.sku}: ${e.message}" }
                // Continue with other items - don't fail the whole operation
            }
        }

        logger.info { "Inventory decremented for fulfillment: ${event.aggregateId}" }
    }

    /**
     * When a fulfillment is cancelled, restore inventory for each item.
     */
    @Async
    @EventListener
    @Transactional
    fun onFulfillmentCancelled(event: FulfillmentCancelled) {
        logger.info { "Handling FulfillmentCancelled event: ${event.aggregateId}" }

        // Note: To restore inventory on cancellation, we'd need items in the cancelled event
        // or look them up from the fulfillment. For now, log a warning.
        logger.warn { "Fulfillment cancelled: ${event.aggregateId}. Manual inventory restore may be needed." }
    }

    private fun decrementInventory(sku: String, locationId: String, quantity: Int) {
        // Find inventory item by SKU
        val inventoryItem = inventoryItemRepository.findBySkuAndDeletedAtIsNull(sku)
        if (inventoryItem == null) {
            logger.warn { "Inventory item not found for SKU: $sku" }
            return
        }

        // Find inventory level at this location
        val level = inventoryLevelRepository.findByInventoryItemIdAndLocationId(
            inventoryItem.id,
            locationId
        )

        if (level == null) {
            logger.warn { "No inventory level found for $sku at location $locationId" }
            return
        }

        // Check if there's a reservation to fulfill, otherwise just decrement stock
        if (level.reservedQuantity >= quantity) {
            // Fulfill reservation (reduces both reserved and stocked)
            level.fulfillReservation(quantity)
            logger.info { "Fulfilled reservation: $quantity units of $sku" }
        } else {
            // Direct stock decrement (no reservation)
            level.adjustStockQuantity(-quantity)
            logger.info { "Decremented stock: $quantity units of $sku" }
        }

        inventoryLevelRepository.save(level)
    }
}
