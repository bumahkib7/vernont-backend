package com.vernont.domain.inventory

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "stock_location",
    indexes = [
        Index(name = "idx_stock_location_name", columnList = "name"),
        Index(name = "idx_stock_location_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "StockLocation.full",
    attributeNodes = [
        NamedAttributeNode("inventoryLevels")
    ]
)
@NamedEntityGraph(
    name = "StockLocation.withLevels",
    attributeNodes = [
        NamedAttributeNode(value = "inventoryLevels", subgraph = "levelsSubgraph")
    ],
    subgraphs = [
        NamedSubgraph(
            name = "levelsSubgraph",
            attributeNodes = [
                NamedAttributeNode("inventoryItem")
            ]
        )
    ]
)
@NamedEntityGraph(
    name = "StockLocation.summary",
    attributeNodes = []
)
class StockLocation : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var address: String? = null

    @Column(name = "address_1")
    var address1: String? = null

    @Column(name = "address_2")
    var address2: String? = null

    @Column
    var city: String? = null

    @Column(name = "country_code", length = 2)
    var countryCode: String? = null

    @Column
    var province: String? = null

    @Column(name = "postal_code")
    var postalCode: String? = null

    @Column
    var phone: String? = null

    @Column(nullable = false)
    var priority: Int = 0

    @Column(name = "fulfillment_enabled", nullable = false)
    var fulfillmentEnabled: Boolean = true

    @OneToMany(mappedBy = "location", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var inventoryLevels: MutableSet<InventoryLevel> = mutableSetOf()

    fun addInventoryLevel(level: InventoryLevel) {
        inventoryLevels.add(level)
        level.location = this
    }

    fun removeInventoryLevel(level: InventoryLevel) {
        inventoryLevels.remove(level)
        level.location = null
    }

    fun enableFulfillment() {
        this.fulfillmentEnabled = true
    }

    fun disableFulfillment() {
        this.fulfillmentEnabled = false
    }

    fun updatePriority(newPriority: Int) {
        require(newPriority >= 0) { "Priority must be non-negative" }
        this.priority = newPriority
    }

    fun getFullAddress(): String {
        val parts = listOfNotNull(
            address1,
            address2,
            city,
            province,
            postalCode,
            countryCode
        )
        return parts.joinToString(", ")
    }

    fun getTotalItemsInStock(): Int {
        return inventoryLevels.count { it.hasStock() }
    }

    fun canFulfillOrders(): Boolean {
        return fulfillmentEnabled && inventoryLevels.any { it.hasStock() }
    }
}
