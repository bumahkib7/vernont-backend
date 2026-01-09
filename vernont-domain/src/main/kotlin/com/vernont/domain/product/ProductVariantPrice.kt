package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.springframework.data.jpa.repository.EntityGraph
import java.math.BigDecimal

@Entity
@Table(
    name = "product_variant_price",
    indexes = [
        Index(name = "idx_price_variant_id", columnList = "variant_id"),
        Index(name = "idx_price_currency_region", columnList = "currency_code,region_id")
    ]
)

@NamedEntityGraph(
    name = "ProductVariantPrice.full",
    attributeNodes = [
        NamedAttributeNode("variant"),
    ]
)
class ProductVariantPrice : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    var variant: ProductVariant? = null

    @Column(nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "compareatprice", precision = 19, scale = 4)
    var compareAtPrice: BigDecimal? = null

    @Column
    var regionId: String? = null

    @Column
    var minQuantity: Int? = null

    @Column
    var maxQuantity: Int? = null

    fun hasDiscount(): Boolean {
        return compareAtPrice != null && compareAtPrice!! > amount
    }

    fun getDiscountPercentage(): BigDecimal? {
        if (!hasDiscount()) return null
        val discount = compareAtPrice!! - amount
        return (discount / compareAtPrice!!) * BigDecimal(100)
    }
}
