package com.vernont.domain.region

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "tax_rate",
    indexes = [
        Index(name = "idx_tax_rate_region_id", columnList = "region_id"),
        Index(name = "idx_tax_rate_code", columnList = "code"),
        Index(name = "idx_tax_rate_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "TaxRate.full",
    attributeNodes = [
        NamedAttributeNode("region")
    ]
)
@NamedEntityGraph(
    name = "TaxRate.withRegion",
    attributeNodes = [
        NamedAttributeNode("region")
    ]
)
class TaxRate : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column
    var code: String? = null

    @Column(nullable = false)
    var rate: Double = 0.0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    var region: Region? = null

    @Column(name = "product_types", columnDefinition = "TEXT")
    var productTypes: String? = null

    @Column(name = "product_categories", columnDefinition = "TEXT")
    var productCategories: String? = null

    @Column(name = "shipping_option_id")
    var shippingOptionId: String? = null

    fun updateRate(newRate: Double) {
        require(newRate >= 0.0) { "Tax rate must be non-negative" }
        this.rate = newRate
    }

    fun calculateTax(amount: Double): Double {
        return amount * (rate / 100.0)
    }

    fun appliesTo(productTypeId: String?, productCategoryId: String?): Boolean {
        if (productTypes == null && productCategories == null) {
            return true
        }

        productTypeId?.let {
            if (productTypes?.contains(it) == true) return true
        }

        productCategoryId?.let {
            if (productCategories?.contains(it) == true) return true
        }

        return false
    }
}
