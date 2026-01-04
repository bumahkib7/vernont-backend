package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "product_option",
    indexes = [
        Index(name = "idx_product_option_product_id", columnList = "product_id"),
        Index(name = "idx_product_option_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ProductOption.withProduct",
    attributeNodes = [
        NamedAttributeNode("product")
    ]
)
class ProductOption : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product? = null

    @NotBlank
    @Column(nullable = false)
    var title: String = ""

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "product_option_value",
        joinColumns = [JoinColumn(name = "option_id")]
    )
    @Column(name = "value", nullable = false)
    @OrderColumn(name = "position")
    var values: MutableList<String> = mutableListOf()

    @Column(nullable = false)
    var position: Int = 0

    fun addValue(value: String) {
        if (!values.contains(value)) {
            values.add(value)
        }
    }

    fun removeValue(value: String) {
        values.remove(value)
    }

    fun hasValue(value: String): Boolean = values.contains(value)

    fun getValueCount(): Int = values.size
}
