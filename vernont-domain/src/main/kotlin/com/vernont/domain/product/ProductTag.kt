package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "product_tag",
    indexes = [
        Index(name = "idx_tag_value", columnList = "value", unique = true),
        Index(name = "idx_tag_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ProductTag.withProducts",
    attributeNodes = [
        NamedAttributeNode("products")
    ]
)
class ProductTag : BaseEntity() {

    @NotBlank
    @Column(nullable = false, unique = true)
    var value: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    var products: MutableSet<Product> = mutableSetOf()

    fun getProductCount(): Int = products.size

    fun addToProduct(product: Product) {
        products.add(product)
        product.tags.add(this)
    }

    fun removeFromProduct(product: Product) {
        products.remove(product)
        product.tags.remove(this)
    }
}
