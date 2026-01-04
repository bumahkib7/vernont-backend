package com.vernont.domain.pricing

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

/**
 * Price Set entity - represents a collection of prices for different contexts
 * Equivalent to Medusa's PriceSet functionality
 */
@Entity
@Table(
    name = "price_set",
    indexes = [
        Index(name = "idx_price_set_type", columnList = "type"),
        Index(name = "idx_price_set_deleted_at", columnList = "deleted_at")
    ]
)
class PriceSet : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var type: String = "default" // product, variant, shipping, etc.

    @OneToMany(mappedBy = "priceSet", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var prices: MutableSet<Price> = mutableSetOf()


    fun addPrice(price: Price) {
        prices.add(price)
        price.priceSet = this
    }

    fun removePrice(price: Price) {
        prices.remove(price)
        price.priceSet = null
    }

    fun getPriceForRegion(regionId: String, currencyCode: String): Price? {
        return prices.find { 
            it.regionId == regionId && it.currencyCode == currencyCode && it.deletedAt == null
        }
    }

    fun getPriceForCurrency(currencyCode: String): Price? {
        return prices.find { 
            it.currencyCode == currencyCode && it.deletedAt == null
        }
    }

    fun getDefaultPrice(): Price? {
        return prices.find { it.isDefault && it.deletedAt == null }
    }
}

/**
 * Price entity - individual price within a price set
 */
@Entity
@Table(
    name = "price",
    indexes = [
        Index(name = "idx_price_currency_code", columnList = "currency_code"),
        Index(name = "idx_price_region_id", columnList = "region_id"),
        Index(name = "idx_price_price_set_id", columnList = "price_set_id"),
        Index(name = "idx_price_deleted_at", columnList = "deleted_at")
    ]
)
class Price : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_set_id", nullable = false)
    var priceSet: PriceSet? = null

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(precision = 19, scale = 4, nullable = false)
    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "region_id")
    var regionId: String? = null

    @Column(name = "min_quantity")
    var minQuantity: Int? = null

    @Column(name = "max_quantity")
    var maxQuantity: Int? = null

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false


    fun isApplicableForQuantity(quantity: Int): Boolean {
        val minOk = minQuantity?.let { quantity >= it } ?: true
        val maxOk = maxQuantity?.let { quantity <= it } ?: true
        return minOk && maxOk
    }

    fun isApplicableForRegion(regionId: String): Boolean {
        return this.regionId == null || this.regionId == regionId
    }
}