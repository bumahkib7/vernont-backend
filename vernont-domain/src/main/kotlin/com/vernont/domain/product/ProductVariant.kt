package com.vernont.domain.product

import com.fasterxml.jackson.annotation.JsonBackReference
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "product_variant",
    indexes = [
        Index(name = "idx_variant_product_id", columnList = "product_id"),
        Index(name = "idx_variant_sku", columnList = "sku", unique = true),
        Index(name = "idx_variant_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ProductVariant.full",
    attributeNodes = [
        NamedAttributeNode("product"),
        NamedAttributeNode("prices"),
        NamedAttributeNode("options"),
        NamedAttributeNode("inventoryItems")
    ]
)
class ProductVariant : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonBackReference
    var product: Product? = null

    @Column(nullable = false)
    var title: String = ""

    @Column(unique = true)
    var sku: String? = null

    @Column
    var barcode: String? = null

    @Column
    var ean: String? = null

    @Column
    var upc: String? = null

    @Column(nullable = false)
    var allowBackorder: Boolean = false

    @Column(nullable = false)
    var manageInventory: Boolean = true

    @Column
    var hsCode: String? = null

    @Column
    var originCountry: String? = null

    @Column
    var midCode: String? = null

    @Column
    var material: String? = null

    @Column
    var weight: String? = null

    @Column
    var length: String? = null

    @Column
    var height: String? = null

    @Column
    var width: String? = null

    @OneToMany(mappedBy = "variant", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var prices: MutableSet<ProductVariantPrice> = mutableSetOf()

    @OneToMany(mappedBy = "variant", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var options: MutableSet<ProductVariantOption> = mutableSetOf()

    @OneToMany(mappedBy = "variant", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var inventoryItems: MutableSet<ProductVariantInventoryItem> = mutableSetOf()

    fun addPrice(price: ProductVariantPrice) {
        prices.add(price)
        price.variant = this
    }

    fun addOption(option: ProductVariantOption) {
        options.add(option)
        option.variant = this
    }

    fun getPrice(currencyCode: String, regionId: String? = null): ProductVariantPrice? {
        return prices.find {
            it.currencyCode == currencyCode &&
            (regionId == null || it.regionId == regionId)
        }
    }
}
