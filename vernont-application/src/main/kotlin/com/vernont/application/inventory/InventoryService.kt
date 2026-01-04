package com.vernont.application.inventory

import com.vernont.domain.inventory.dto.*
import com.vernont.domain.inventory.InventoryItem
import com.vernont.domain.inventory.InventoryLevel
import com.vernont.events.*
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.StockLocationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class InventoryService(
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val eventPublisher: EventPublisher
) {

    /**
     * Create a new inventory item
     */
    fun createInventoryItem(request: CreateInventoryItemRequest): InventoryItemResponse {
        logger.info { "Creating inventory item with SKU: ${request.sku}" }

        // Validate unique SKU if provided
        request.sku?.let { sku ->
            if (inventoryItemRepository.findBySku(sku) != null) {
                throw DuplicateSkuException("Inventory item with SKU $sku already exists")
            }
        }

        val item = InventoryItem().apply {
            sku = request.sku
            hsCode = request.hsCode
            originCountry = request.originCountry
            midCode = request.midCode
            material = request.material
            weight = request.weight
            length = request.length
            height = request.height
            width = request.width
            requiresShipping = request.requiresShipping
        }

        val saved = inventoryItemRepository.save(item)

        eventPublisher.publish(
            InventoryItemCreated(
                aggregateId = saved.id,
                inventoryItemId = saved.id,
                sku = saved.sku
            )
        )

        logger.info { "Inventory item created: ${saved.id}" }
        return InventoryItemResponse.from(saved)
    }

    /**
     * Update inventory item
     */
    fun updateInventoryItem(itemId: String, request: UpdateInventoryItemRequest): InventoryItemResponse {
        logger.info { "Updating inventory item: $itemId" }

        val item = inventoryItemRepository.findByIdAndDeletedAtIsNull(itemId)
            ?: throw InventoryItemNotFoundException("Inventory item not found: $itemId")

        // Validate unique SKU if changing
        request.sku?.let { newSku ->
            if (newSku != item.sku) {
                inventoryItemRepository.findBySku(newSku)?.let {
                    throw DuplicateSkuException("Inventory item with SKU $newSku already exists")
                }
                item.updateSku(newSku)
            }
        }

        item.apply {
            request.hsCode?.let { hsCode = it }
            request.originCountry?.let { originCountry = it }
            request.midCode?.let { midCode = it }
            request.material?.let { material = it }
            request.weight?.let { weight = it }
            request.length?.let { length = it }
            request.height?.let { height = it }
            request.width?.let { width = it }
            request.requiresShipping?.let { requiresShipping = it }
        }

        val updated = inventoryItemRepository.save(item)

        logger.info { "Inventory item updated: $itemId" }
        return InventoryItemResponse.from(updated)
    }

    /**
     * Reserve inventory at a specific location
     */
    fun reserveInventory(request: ReserveInventoryRequest): InventoryLevelResponse {
        logger.info { "Reserving ${request.quantity} units of item ${request.inventoryItemId} at location ${request.locationId}" }

        val level = getOrCreateInventoryLevel(request.inventoryItemId, request.locationId)

        level.reserve(request.quantity)
        val updated = inventoryLevelRepository.save(level)

        eventPublisher.publish(
            InventoryReserved(
                aggregateId = level.id,
                inventoryItemId = request.inventoryItemId,
                locationId = request.locationId,
                quantity = request.quantity,
                reservationId = java.util.UUID.randomUUID().toString()
            )
        )

        logger.info { "Inventory reserved: ${request.quantity} units" }
        return InventoryLevelResponse.from(updated)
    }

    /**
     * Release inventory reservation
     */
    fun releaseReservation(request: ReleaseInventoryRequest): InventoryLevelResponse {
        logger.info { "Releasing ${request.quantity} units of item ${request.inventoryItemId} at location ${request.locationId}" }

        val level = inventoryLevelRepository.findByInventoryItemIdAndLocationId(
            request.inventoryItemId,
            request.locationId
        ) ?: throw InventoryLevelNotFoundException(
            "Inventory level not found for item ${request.inventoryItemId} at location ${request.locationId}"
        )

        level.releaseReservation(request.quantity)
        val updated = inventoryLevelRepository.save(level)

        eventPublisher.publish(
            InventoryReleased(
                aggregateId = level.id,
                inventoryItemId = request.inventoryItemId,
                locationId = request.locationId,
                quantity = request.quantity,
                reservationId = java.util.UUID.randomUUID().toString()
            )
        )

        logger.info { "Inventory reservation released: ${request.quantity} units" }
        return InventoryLevelResponse.from(updated)
    }

    /**
     * Adjust inventory stock levels
     */
    fun adjustStock(request: AdjustInventoryStockRequest): InventoryLevelResponse {
        logger.info { "Adjusting stock for item ${request.inventoryItemId} at location ${request.locationId} by ${request.adjustment}" }

        val level = getOrCreateInventoryLevel(request.inventoryItemId, request.locationId)

        level.adjustStockQuantity(request.adjustment)
        val updated = inventoryLevelRepository.save(level)

        eventPublisher.publish(
            InventoryAdjusted(
                aggregateId = level.id,
                inventoryItemId = request.inventoryItemId,
                locationId = request.locationId,
                adjustment = request.adjustment,
                reason = request.reason
            )
        )

        logger.info { "Inventory stock adjusted: ${request.adjustment} units" }
        return InventoryLevelResponse.from(updated)
    }

    /**
     * Fulfill a reservation (reduces both reserved and stocked quantities)
     */
    fun fulfillReservation(inventoryItemId: String, locationId: String, quantity: Int): InventoryLevelResponse {
        logger.info { "Fulfilling $quantity units of item $inventoryItemId at location $locationId" }

        val level = inventoryLevelRepository.findByInventoryItemIdAndLocationId(inventoryItemId, locationId)
            ?: throw InventoryLevelNotFoundException(
                "Inventory level not found for item $inventoryItemId at location $locationId"
            )

        level.fulfillReservation(quantity)
        val updated = inventoryLevelRepository.save(level)

        eventPublisher.publish(
            InventoryFulfilled(
                aggregateId = level.id,
                inventoryItemId = inventoryItemId,
                locationId = locationId,
                quantity = quantity
            )
        )

        logger.info { "Reservation fulfilled: $quantity units" }
        return InventoryLevelResponse.from(updated)
    }

    /**
     * Get inventory item by ID
     */
    @Transactional(readOnly = true)
    fun getInventoryItem(itemId: String): InventoryItemResponse {
        val item = inventoryItemRepository.findWithLevelsById(itemId)
            ?: throw InventoryItemNotFoundException("Inventory item not found: $itemId")

        return InventoryItemResponse.from(item)
    }

    /**
     * Get inventory item by SKU
     */
    @Transactional(readOnly = true)
    fun getInventoryItemBySku(sku: String): InventoryItemResponse {
        val item = inventoryItemRepository.findWithLevelsBySku(sku)
            ?: throw InventoryItemNotFoundException("Inventory item not found with SKU: $sku")

        return InventoryItemResponse.from(item)
    }

    /**
     * Check if inventory is available for a quantity
     */
    @Transactional(readOnly = true)
    fun checkAvailability(itemId: String, quantity: Int): Boolean {
        val item = inventoryItemRepository.findWithLevelsById(itemId)
            ?: throw InventoryItemNotFoundException("Inventory item not found: $itemId")

        return item.isAvailable(quantity)
    }

    /**
     * Get inventory levels for an item
     */
    @Transactional(readOnly = true)
    fun getInventoryLevels(itemId: String): List<InventoryLevelResponse> {
        val levels = inventoryLevelRepository.findByInventoryItemIdAndDeletedAtIsNull(itemId)
        return levels.map { InventoryLevelResponse.from(it) }
    }

    /**
     * Get low stock items (available quantity below threshold)
     */
    @Transactional(readOnly = true)
    fun getLowStockItems(threshold: Int = 10): List<InventoryItemSummaryResponse> {
        val items = inventoryItemRepository.findLowStockItems(threshold)
        return items.map { InventoryItemSummaryResponse.from(it) }
    }

    /**
     * List inventory items with pagination
     */
    @Transactional(readOnly = true)
    fun listInventoryItems(pageable: Pageable): Page<InventoryItemSummaryResponse> {
        val page = inventoryItemRepository.findAll(pageable)
        return page.map { InventoryItemSummaryResponse.from(it) }
    }

    /**
     * Delete inventory item
     */
    fun deleteInventoryItem(itemId: String) {
        logger.info { "Deleting inventory item: $itemId" }

        val item = inventoryItemRepository.findById(itemId)
            .orElseThrow { InventoryItemNotFoundException("Inventory item not found: $itemId") }

        item.softDelete()
        inventoryItemRepository.save(item)

        logger.info { "Inventory item deleted: $itemId" }
    }

    /**
     * Helper to get or create inventory level
     */
    private fun getOrCreateInventoryLevel(inventoryItemId: String, locationId: String): InventoryLevel {
        return inventoryLevelRepository.findByInventoryItemIdAndLocationId(inventoryItemId, locationId)
            ?: run {
                val item = inventoryItemRepository.findByIdAndDeletedAtIsNull(inventoryItemId)
                    ?: throw InventoryItemNotFoundException("Inventory item not found: $inventoryItemId")

                val location = stockLocationRepository.findByIdAndDeletedAtIsNull(locationId)
                    ?: throw StockLocationNotFoundException("Stock location not found: $locationId")

                val level = InventoryLevel().apply {
                    inventoryItem = item
                    this.location = location
                }

                item.addInventoryLevel(level)
                inventoryLevelRepository.save(level)
            }
    }
}

// Custom exceptions
class InventoryItemNotFoundException(message: String) : RuntimeException(message)
class InventoryLevelNotFoundException(message: String) : RuntimeException(message)
class StockLocationNotFoundException(message: String) : RuntimeException(message)
class DuplicateSkuException(message: String) : RuntimeException(message)
class InsufficientInventoryException(message: String) : RuntimeException(message)
