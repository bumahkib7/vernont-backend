package com.vernont.domain.inventory

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "inventory_item",
    indexes = [
        Index(name = "idx_inventory_item_sku", columnList = "sku", unique = true),
        Index(name = "idx_inventory_item_origin_country", columnList = "origin_country"),
        Index(name = "idx_inventory_item_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "InventoryItem.full",
    attributeNodes = [
        NamedAttributeNode("inventoryLevels")
    ]
)
@NamedEntityGraph(
    name = "InventoryItem.withLevels",
    attributeNodes = [
        NamedAttributeNode(value = "inventoryLevels", subgraph = "levelsSubgraph")
    ],
    subgraphs = [
        NamedSubgraph(
            name = "levelsSubgraph",
            attributeNodes = [
                NamedAttributeNode("location")
            ]
        )
    ]
)
@NamedEntityGraph(
    name = "InventoryItem.summary",
    attributeNodes = []
)
class InventoryItem : BaseEntity() {

    @Column(unique = true)
    var sku: String? = null

    @Column(name = "hs_code")
    var hsCode: String? = null

    @Column(name = "origin_country")
    var originCountry: String? = null

    @Column(name = "mid_code")
    var midCode: String? = null

    @Column
    var material: String? = null

    @Column
    var weight: Int? = null

    @Column
    var length: Int? = null

    @Column
    var height: Int? = null

    @Column
    var width: Int? = null

    @Column(name = "requires_shipping", nullable = false)
    var requiresShipping: Boolean = true

    @OneToMany(mappedBy = "inventoryItem", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var inventoryLevels: MutableSet<InventoryLevel> = mutableSetOf()

    fun addInventoryLevel(level: InventoryLevel) {
        inventoryLevels.add(level)
        level.inventoryItem = this
    }

    fun removeInventoryLevel(level: InventoryLevel) {
        inventoryLevels.remove(level)
        level.inventoryItem = null
    }

    fun getTotalStockQuantity(): Int {
        return inventoryLevels.sumOf { it.stockedQuantity }
    }

    fun getTotalAvailableQuantity(): Int {
        return inventoryLevels.sumOf { it.availableQuantity }
    }

    fun getStockAtLocation(locationId: String): Int? {
        return inventoryLevels.find { it.location?.id == locationId }?.stockedQuantity
    }

    fun hasStock(): Boolean {
        return getTotalStockQuantity() > 0
    }

    fun isAvailable(quantity: Int = 1): Boolean {
        return getTotalAvailableQuantity() >= quantity
    }

    fun updateSku(newSku: String) {
        require(newSku.isNotBlank()) { "SKU cannot be blank" }
        this.sku = newSku
    }

    fun getDimensions(): Map<String, Int?> {
        return mapOf(
            "weight" to weight,
            "length" to length,
            "height" to height,
            "width" to width
        )
    }
}
