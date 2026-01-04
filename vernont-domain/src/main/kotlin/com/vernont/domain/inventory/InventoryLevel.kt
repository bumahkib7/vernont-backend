package com.vernont.domain.inventory

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "inventory_level",
    indexes = [
        Index(name = "idx_inventory_level_inventory_item_id", columnList = "inventory_item_id"),
        Index(name = "idx_inventory_level_location_id", columnList = "location_id"),
        Index(name = "idx_inventory_level_item_location", columnList = "inventory_item_id,location_id", unique = true),
        Index(name = "idx_inventory_level_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "InventoryLevel.full",
    attributeNodes = [
        NamedAttributeNode("inventoryItem"),
        NamedAttributeNode("location")
    ]
)
@NamedEntityGraph(
    name = "InventoryLevel.withItem",
    attributeNodes = [
        NamedAttributeNode("inventoryItem")
    ]
)
@NamedEntityGraph(
    name = "InventoryLevel.withLocation",
    attributeNodes = [
        NamedAttributeNode("location")
    ]
)
class InventoryLevel : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    var inventoryItem: InventoryItem? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    var location: StockLocation? = null

    @Column(name = "stocked_quantity", nullable = false)
    var stockedQuantity: Int = 0

    @Column(name = "reserved_quantity", nullable = false)
    var reservedQuantity: Int = 0

    @Column(name = "incoming_quantity", nullable = false)
    var incomingQuantity: Int = 0

    @Column(name = "available_quantity", nullable = false)
    var availableQuantity: Int = 0

    fun adjustStockQuantity(adjustment: Int) {
        val newQuantity = this.stockedQuantity + adjustment
        require(newQuantity >= 0) {
            "Stock quantity cannot go negative. Current: $stockedQuantity, Adjustment: $adjustment"
        }
        this.stockedQuantity = newQuantity
        recalculateAvailableQuantity()
    }

    fun reserve(quantity: Int) {
        require(quantity > 0) { "Reservation quantity must be positive" }
        require(availableQuantity >= quantity) { "Insufficient available quantity for reservation" }
        this.reservedQuantity += quantity
        recalculateAvailableQuantity()
    }

    fun releaseReservation(quantity: Int) {
        require(quantity > 0) { "Release quantity must be positive" }
        require(reservedQuantity >= quantity) { "Cannot release more than reserved quantity" }
        this.reservedQuantity -= quantity
        recalculateAvailableQuantity()
    }

    fun addIncoming(quantity: Int) {
        require(quantity > 0) { "Incoming quantity must be positive" }
        this.incomingQuantity += quantity
    }

    fun receiveIncoming(quantity: Int) {
        require(quantity > 0) { "Receive quantity must be positive" }
        require(incomingQuantity >= quantity) { "Cannot receive more than incoming quantity" }
        this.incomingQuantity -= quantity
        this.stockedQuantity += quantity
        recalculateAvailableQuantity()
    }

    fun fulfillReservation(quantity: Int) {
        require(quantity > 0) { "Fulfill quantity must be positive" }
        require(reservedQuantity >= quantity) { "Cannot fulfill more than reserved quantity" }
        require(stockedQuantity >= quantity) { "Insufficient stock to fulfill reservation" }
        this.reservedQuantity -= quantity
        this.stockedQuantity -= quantity
        recalculateAvailableQuantity()
    }

    fun recalculateAvailableQuantity() {
        this.availableQuantity = stockedQuantity - reservedQuantity
    }

    fun hasStock(): Boolean {
        return stockedQuantity > 0
    }

    fun hasAvailableStock(quantity: Int = 1): Boolean {
        return availableQuantity >= quantity
    }

    fun isOutOfStock(): Boolean {
        return availableQuantity <= 0
    }
}
