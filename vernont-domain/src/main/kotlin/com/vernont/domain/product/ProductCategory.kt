package com.vernont.domain.product

import com.fasterxml.jackson.annotation.JsonIgnore
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "product_category",
    indexes = [
        Index(name = "idx_category_handle", columnList = "handle", unique = true),
        Index(name = "idx_category_parent_id", columnList = "parent_category_id"),
        Index(name = "idx_category_active", columnList = "is_active"),
        Index(name = "idx_category_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ProductCategory.full",
    attributeNodes = [
        NamedAttributeNode("products"),
        NamedAttributeNode("parent"),
        NamedAttributeNode("subCategories")
    ]
)
@NamedEntityGraph(
    name = "ProductCategory.withProducts",
    attributeNodes = [
        NamedAttributeNode("products")
    ]
)
@NamedEntityGraph(
    name = "ProductCategory.hierarchy",
    attributeNodes = [
        NamedAttributeNode("parent"),
        NamedAttributeNode("subCategories")
    ]
)
class ProductCategory : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @NotBlank
    @Column(nullable = false, unique = true)
    var handle: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column
    var image: String? = null

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column(nullable = false)
    var isInternal: Boolean = false

    @Column(nullable = false)
    var position: Int = 0

    @Column(name = "external_id", unique = true)
    var externalId: String? = null

    @Column(name = "source")
    var source: String? = null  // e.g., "FLEXOFFERS", "AWIN", "RAKUTEN"

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    var parent: ProductCategory? = null

    // Legacy field for backward compatibility
    @get:JsonIgnore
    var parentCategory: ProductCategory?
        get() = parent
        set(value) { parent = value }

    @JsonIgnore
    @OneToMany(mappedBy = "parent", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var subCategories: MutableSet<ProductCategory> = mutableSetOf()

    @JsonIgnore
    @ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
    var products: MutableSet<Product> = mutableSetOf()

    fun addSubCategory(category: ProductCategory) {
        subCategories.add(category)
        category.parent = this
    }

    fun removeSubCategory(category: ProductCategory) {
        subCategories.remove(category)
        category.parent = null
    }

    fun addProduct(product: Product) {
        products.add(product)
        product.categories.add(this)
    }

    fun removeProduct(product: Product) {
        products.remove(product)
        product.categories.remove(this)
    }

    fun activate() {
        this.isActive = true
    }

    fun deactivate() {
        this.isActive = false
    }

    fun isTopLevel(): Boolean = parentCategory == null

    fun hasSubCategories(): Boolean = subCategories.isNotEmpty()

    fun getProductCount(): Int = products.size

    fun getSubCategoryCount(): Int = subCategories.size
}
