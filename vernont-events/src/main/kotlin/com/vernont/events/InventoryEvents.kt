package com.vernont.events

import java.time.Instant

/**
 * Inventory-related domain events.
 */

/**
 * Fired when inventory is reserved for an order.
 *
 * @property inventoryItemId ID of the inventory item
 * @property locationId ID of the stock location
 * @property quantity Quantity reserved
 * @property reservationId Unique reservation identifier
 * @property lineItemId Associated order line item ID (optional)
 */
data class InventoryReserved(
    override val aggregateId: String,
    val inventoryItemId: String,
    val locationId: String,
    val quantity: Int,
    val reservationId: String,
    val lineItemId: String? = null,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when inventory reservation is released.
 *
 * @property inventoryItemId ID of the inventory item
 * @property locationId ID of the stock location
 * @property quantity Quantity released
 * @property reservationId Unique reservation identifier
 * @property lineItemId Associated order line item ID (optional)
 */
data class InventoryReleased(
    override val aggregateId: String,
    val inventoryItemId: String,
    val locationId: String,
    val quantity: Int,
    val reservationId: String,
    val lineItemId: String? = null,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when stock level is updated.
 *
 * @property inventoryItemId ID of the inventory item
 * @property locationId ID of the stock location
 * @property previousAvailable Previous available quantity
 * @property newAvailable New available quantity
 * @property stockedQuantity Current stocked quantity
 * @property reservedQuantity Current reserved quantity
 */
data class StockLevelUpdated(
    override val aggregateId: String,
    val inventoryItemId: String,
    val locationId: String,
    val previousAvailable: Int,
    val newAvailable: Int,
    val stockedQuantity: Int,
    val reservedQuantity: Int,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when stock level falls below threshold.
 *
 * @property inventoryItemId ID of the inventory item
 * @property locationId ID of the stock location
 * @property availableQuantity Current available quantity
 * @property threshold Low stock threshold that was breached
 */
data class LowStockAlert(
    override val aggregateId: String,
    val inventoryItemId: String,
    val locationId: String,
    val availableQuantity: Int,
    val threshold: Int,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when an inventory item is created.
 *
 * @property inventoryItemId ID of the inventory item
 * @property sku SKU of the inventory item (optional)
 */
data class InventoryItemCreated(
    override val aggregateId: String,
    val inventoryItemId: String,
    val sku: String?,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when inventory stock is adjusted.
 *
 * @property inventoryItemId ID of the inventory item
 * @property locationId ID of the stock location
 * @property adjustment Amount adjusted (positive or negative)
 * @property reason Reason for the adjustment
 */
data class InventoryAdjusted(
    override val aggregateId: String,
    val inventoryItemId: String,
    val locationId: String,
    val adjustment: Int,
    val reason: String?,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a reservation is fulfilled.
 *
 * @property inventoryItemId ID of the inventory item
 * @property locationId ID of the stock location
 * @property quantity Quantity fulfilled
 */
data class InventoryFulfilled(
    override val aggregateId: String,
    val inventoryItemId: String,
    val locationId: String,
    val quantity: Int,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)