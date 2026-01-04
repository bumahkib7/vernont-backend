package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "product_type",
    indexes = [
        Index(name = "idx_type_value", columnList = "value", unique = true),
        Index(name = "idx_type_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ProductType.withProducts",
    attributeNodes = [
        NamedAttributeNode("products")
    ]
)
class ProductType : BaseEntity() {

    @NotBlank
    @Column(nullable = false, unique = true)
    var value: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @OneToMany(mappedBy = "type", fetch = FetchType.LAZY)
    var products: MutableSet<Product> = mutableSetOf()

    fun getProductCount(): Int = products.size

    fun addProduct(product: Product) {
        products.add(product)
        product.type = this
    }

    fun removeProduct(product: Product) {
        products.remove(product)
        product.type = null
    }
}
