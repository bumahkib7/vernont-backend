package com.vernont.domain.inventory

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Movement types for inventory tracking
 */
enum class MovementType {
    STOCK_ADDED,        // Received new inventory (restock)
    STOCK_REMOVED,      // Stock removed (damaged, lost, etc.)
    ADJUSTMENT,         // Manual correction
    RESERVED,           // Stock reserved for order
    RELEASED,           // Reservation released
    FULFILLED,          // Reserved stock shipped
    RETURNED,           // Return received
    TRANSFERRED_IN,     // Transfer from another location
    TRANSFERRED_OUT,    // Transfer to another location
    CYCLE_COUNT         // Inventory count correction
}

/**
 * Tracks all inventory movements for audit trail.
 * This entity persists events from the event stream (Redpanda/SQS)
 * for querying and historical analysis.
 */
@Entity
@Table(
    name = "inventory_movement",
    indexes = [
        Index(name = "idx_inv_movement_inventory_item_id", columnList = "inventory_item_id"),
        Index(name = "idx_inv_movement_location_id", columnList = "location_id"),
        Index(name = "idx_inv_movement_inventory_level_id", columnList = "inventory_level_id"),
        Index(name = "idx_inv_movement_type", columnList = "movement_type"),
        Index(name = "idx_inv_movement_occurred_at", columnList = "occurred_at"),
        Index(name = "idx_inv_movement_reference_type", columnList = "reference_type"),
        Index(name = "idx_inv_movement_reference_id", columnList = "reference_id")
    ]
)
class InventoryMovement : BaseEntity() {

    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: String = ""

    @Column(name = "inventory_item_id", nullable = false)
    var inventoryItemId: String = ""

    @Column(name = "inventory_level_id")
    var inventoryLevelId: String? = null

    @Column(name = "location_id", nullable = false)
    var locationId: String = ""

    @Column(name = "location_name")
    var locationName: String? = null

    @Column(name = "sku")
    var sku: String? = null

    @Column(name = "product_title")
    var productTitle: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    var movementType: MovementType = MovementType.ADJUSTMENT

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 0

    @Column(name = "previous_quantity")
    var previousQuantity: Int? = null

    @Column(name = "new_quantity")
    var newQuantity: Int? = null

    @Column(name = "reason")
    var reason: String? = null

    @Column(name = "note", length = 1000)
    var note: String? = null

    /**
     * Reference type for linked entities (ORDER, RETURN, TRANSFER, etc.)
     */
    @Column(name = "reference_type")
    var referenceType: String? = null

    /**
     * Reference ID (order ID, return ID, transfer ID, etc.)
     */
    @Column(name = "reference_id")
    var referenceId: String? = null

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now()

    @Column(name = "performed_by")
    var performedBy: String? = null

    companion object {
        fun fromAdjustment(
            eventId: String,
            inventoryItemId: String,
            inventoryLevelId: String?,
            locationId: String,
            locationName: String?,
            sku: String?,
            productTitle: String?,
            adjustment: Int,
            previousQuantity: Int?,
            newQuantity: Int?,
            reason: String?,
            note: String?,
            performedBy: String?,
            occurredAt: Instant = Instant.now()
        ): InventoryMovement {
            return InventoryMovement().apply {
                this.eventId = eventId
                this.inventoryItemId = inventoryItemId
                this.inventoryLevelId = inventoryLevelId
                this.locationId = locationId
                this.locationName = locationName
                this.sku = sku
                this.productTitle = productTitle
                this.movementType = when {
                    reason?.uppercase()?.contains("RESTOCK") == true -> MovementType.STOCK_ADDED
                    reason?.uppercase()?.contains("DAMAGED") == true -> MovementType.STOCK_REMOVED
                    reason?.uppercase()?.contains("LOST") == true -> MovementType.STOCK_REMOVED
                    reason?.uppercase()?.contains("FOUND") == true -> MovementType.STOCK_ADDED
                    reason?.uppercase()?.contains("RETURN") == true -> MovementType.RETURNED
                    reason?.uppercase()?.contains("TRANSFER_IN") == true -> MovementType.TRANSFERRED_IN
                    reason?.uppercase()?.contains("TRANSFER_OUT") == true -> MovementType.TRANSFERRED_OUT
                    reason?.uppercase()?.contains("CYCLE_COUNT") == true -> MovementType.CYCLE_COUNT
                    adjustment > 0 -> MovementType.STOCK_ADDED
                    adjustment < 0 -> MovementType.STOCK_REMOVED
                    else -> MovementType.ADJUSTMENT
                }
                this.quantity = adjustment
                this.previousQuantity = previousQuantity
                this.newQuantity = newQuantity
                this.reason = reason
                this.note = note
                this.performedBy = performedBy
                this.occurredAt = occurredAt
            }
        }

        fun fromReservation(
            eventId: String,
            inventoryItemId: String,
            inventoryLevelId: String?,
            locationId: String,
            quantity: Int,
            reservationId: String,
            lineItemId: String?,
            occurredAt: Instant = Instant.now()
        ): InventoryMovement {
            return InventoryMovement().apply {
                this.eventId = eventId
                this.inventoryItemId = inventoryItemId
                this.inventoryLevelId = inventoryLevelId
                this.locationId = locationId
                this.movementType = MovementType.RESERVED
                this.quantity = -quantity // Negative because it reduces available
                this.referenceType = "RESERVATION"
                this.referenceId = reservationId
                this.note = lineItemId?.let { "Line item: $it" }
                this.occurredAt = occurredAt
            }
        }

        fun fromRelease(
            eventId: String,
            inventoryItemId: String,
            inventoryLevelId: String?,
            locationId: String,
            quantity: Int,
            reservationId: String,
            occurredAt: Instant = Instant.now()
        ): InventoryMovement {
            return InventoryMovement().apply {
                this.eventId = eventId
                this.inventoryItemId = inventoryItemId
                this.inventoryLevelId = inventoryLevelId
                this.locationId = locationId
                this.movementType = MovementType.RELEASED
                this.quantity = quantity // Positive because it increases available
                this.referenceType = "RESERVATION"
                this.referenceId = reservationId
                this.occurredAt = occurredAt
            }
        }

        fun fromFulfillment(
            eventId: String,
            inventoryItemId: String,
            inventoryLevelId: String?,
            locationId: String,
            quantity: Int,
            orderId: String?,
            occurredAt: Instant = Instant.now()
        ): InventoryMovement {
            return InventoryMovement().apply {
                this.eventId = eventId
                this.inventoryItemId = inventoryItemId
                this.inventoryLevelId = inventoryLevelId
                this.locationId = locationId
                this.movementType = MovementType.FULFILLED
                this.quantity = -quantity // Negative because stock leaves
                this.referenceType = "ORDER"
                this.referenceId = orderId
                this.occurredAt = occurredAt
            }
        }
    }
}
