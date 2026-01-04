package com.vernont.api.dto.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductCategory
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class StoreProductCategoryResponse(
    @JsonProperty("product_category")
    val productCategory: StoreProductCategory
)

data class StoreProductCategoriesResponse(
    @JsonProperty("product_categories")
    val productCategories: List<StoreProductCategory>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class StoreProductCategory(
    val id: String,
    val name: String,
    val description: String?,
    val handle: String,
    val rank: Int,
    @JsonProperty("parent_category_id")
    val parentCategoryId: String?,
    @JsonProperty("parent_category")
    val parentCategory: StoreProductCategory?,
    @JsonProperty("category_children")
    val categoryChildren: List<StoreProductCategory>?,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @JsonProperty("updated_at")
    val updatedAt: OffsetDateTime,
    @JsonProperty("deleted_at")
    val deletedAt: OffsetDateTime?,
    val metadata: Map<String, Any>? = null
) {
    companion object {
        fun from(
            category: ProductCategory,
            includeParent: Boolean = false,
            includeChildren: Boolean = false
        ): StoreProductCategory {
            return StoreProductCategory(
                id = category.id,
                name = category.name,
                description = category.description,
                handle = category.handle,
                rank = category.position,
                parentCategoryId = category.parentCategory?.id,
                parentCategory = if (includeParent && category.parentCategory != null) {
                    from(category.parentCategory!!, includeParent = false, includeChildren = false)
                } else null,
                categoryChildren = if (includeChildren) {
                    category.subCategories.map { from(it, includeParent = false, includeChildren = false) }
                } else null,
                createdAt = category.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = category.updatedAt.atOffset(ZoneOffset.UTC),
                deletedAt = category.deletedAt?.atOffset(ZoneOffset.UTC),
                metadata = null
            )
        }
    }
}
