package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "product_variant_option",
    indexes = [
        Index(name = "idx_variant_option_variant_id", columnList = "variant_id"),
        Index(name = "idx_variant_option_option_id", columnList = "option_id"),
        Index(name = "idx_variant_option_deleted_at", columnList = "deleted_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_variant_option",
            columnNames = ["variant_id", "option_id"]
        )
    ]
)
@NamedEntityGraph(
    name = "ProductVariantOption.full",
    attributeNodes = [
        NamedAttributeNode("variant"),
        NamedAttributeNode("option")
    ]
)
class ProductVariantOption : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    var variant: ProductVariant? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    var option: ProductOption? = null

    @NotBlank
    @Column(nullable = false)
    var value: String = ""

    fun matches(optionTitle: String, optionValue: String): Boolean {
        return option?.title == optionTitle && value == optionValue
    }
}
