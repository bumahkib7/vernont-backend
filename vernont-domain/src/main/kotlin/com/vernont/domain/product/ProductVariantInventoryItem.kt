package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "product_variant_inventory_item",
    indexes = [
        Index(name = "idx_variant_inventory_variant_id", columnList = "variant_id"),
        Index(name = "idx_variant_inventory_inventory_item_id", columnList = "inventory_item_id"),
        Index(name = "idx_variant_inventory_deleted_at", columnList = "deleted_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_variant_inventory_item",
            columnNames = ["variant_id", "inventory_item_id"]
        )
    ]
)
@NamedEntityGraph(
    name = "ProductVariantInventoryItem.full",
    attributeNodes = [
        NamedAttributeNode("variant")
    ]
)
class ProductVariantInventoryItem : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    var variant: ProductVariant? = null

    @Column(name = "inventory_item_id", nullable = false)
    var inventoryItemId: String = ""

    @Column(nullable = false)
    var requiredQuantity: Int = 1

    fun updateRequiredQuantity(quantity: Int) {
        require(quantity > 0) { "Required quantity must be greater than 0" }
        this.requiredQuantity = quantity
    }
}
