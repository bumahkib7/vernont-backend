package com.vernont.api.dto.admin

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductCategory
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AdminCategoryResponse(
    val category: AdminCategory
)

data class AdminCategoriesResponse(
    val categories: List<AdminCategory>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class AdminCategory(
    val id: String,
    val name: String,
    val handle: String,
    val description: String?,
    val image: String?,
    @JsonProperty("is_active")
    val isActive: Boolean,
    @JsonProperty("is_internal")
    val isInternal: Boolean,
    val position: Int,
    @JsonProperty("parent_category_id")
    val parentCategoryId: String?,
    @JsonProperty("external_id")
    val externalId: String?,
    val source: String?,
    @JsonProperty("product_count")
    val productCount: Int,
    @JsonProperty("subcategory_count")
    val subcategoryCount: Int,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @JsonProperty("updated_at")
    val updatedAt: OffsetDateTime,
    @JsonProperty("deleted_at")
    val deletedAt: OffsetDateTime?
) {
    companion object {
        fun from(category: ProductCategory): AdminCategory {
            return AdminCategory(
                id = category.id,
                name = category.name,
                handle = category.handle,
                description = category.description,
                image = category.image,
                isActive = category.isActive,
                isInternal = category.isInternal,
                position = category.position,
                parentCategoryId = category.parent?.id,
                externalId = category.externalId,
                source = category.source,
                productCount = category.getProductCount(),
                subcategoryCount = category.getSubCategoryCount(),
                createdAt = category.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = category.updatedAt.atOffset(ZoneOffset.UTC),
                deletedAt = category.deletedAt?.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

data class CreateCategoryRequest(
    val name: String,
    val handle: String? = null,
    val description: String? = null,
    val image: String? = null,
    @JsonProperty("is_active")
    val isActive: Boolean = true,
    @JsonProperty("is_internal")
    val isInternal: Boolean = false,
    val position: Int = 0,
    @JsonProperty("parent_category_id")
    val parentCategoryId: String? = null
)

data class UpdateCategoryRequest(
    val name: String? = null,
    val handle: String? = null,
    val description: String? = null,
    val image: String? = null,
    @JsonProperty("is_active")
    val isActive: Boolean? = null,
    @JsonProperty("is_internal")
    val isInternal: Boolean? = null,
    val position: Int? = null,
    @JsonProperty("parent_category_id")
    val parentCategoryId: String? = null
)

data class DeleteCategoryResponse(
    val id: String,
    val `object`: String = "category",
    val deleted: Boolean = true
)

data class CategoryProductItem(
    val id: String,
    val title: String,
    val handle: String,
    val thumbnail: String?,
    val status: String
)

data class CategoryProductsResponse(
    val products: List<CategoryProductItem>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class ManageCategoryProductsRequest(
    val add: List<String>? = null,
    val remove: List<String>? = null
)
