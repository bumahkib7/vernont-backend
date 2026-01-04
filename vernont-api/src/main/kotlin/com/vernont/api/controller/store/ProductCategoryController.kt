package com.vernont.api.controller.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductCategory
import com.vernont.repository.product.ProductCategoryRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/store/product-categories")
@CrossOrigin(origins = ["http://localhost:8000", "http://localhost:9000", "http://localhost:3000"])
class ProductCategoryController(
    private val categoryRepository: ProductCategoryRepository
) {

    @GetMapping
    fun listCategories(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) handle: String?,
        @RequestParam(name = "parent_category_id", required = false) parentCategoryId: String?
    ): ResponseEntity<StoreCategoryListResponse> {
        val allCategories = when {
            handle != null -> listOfNotNull(categoryRepository.findByHandleAndDeletedAtIsNull(handle))
            parentCategoryId != null -> categoryRepository.findByParentCategoryIdAndDeletedAtIsNull(parentCategoryId)
            else -> categoryRepository.findByDeletedAtIsNull()
        }

        val paginatedCategories = allCategories.drop(offset).take(limit)

        return ResponseEntity.ok(StoreCategoryListResponse(
            product_categories = paginatedCategories.map { StoreCategoryDto.from(it) },
            count = allCategories.size,
            offset = offset,
            limit = limit
        ))
    }

    @GetMapping("/{id}")
    fun getCategory(@PathVariable id: String): ResponseEntity<StoreCategoryResponse> {
        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(StoreCategoryResponse(product_category = StoreCategoryDto.from(category)))
    }
}

data class StoreCategoryDto(
    val id: String,
    val name: String,
    val description: String?,
    val handle: String,
    @JsonProperty("is_active") val isActive: Boolean,
    @JsonProperty("parent_category_id") val parentCategoryId: String?,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant
) {
    companion object {
        fun from(category: ProductCategory) = StoreCategoryDto(
            id = category.id,
            name = category.name,
            description = category.description,
            handle = category.handle,
            isActive = category.isActive,
            parentCategoryId = category.parentCategory?.id,
            createdAt = category.createdAt,
            updatedAt = category.updatedAt
        )
    }
}

data class StoreCategoryListResponse(
    val product_categories: List<StoreCategoryDto>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class StoreCategoryResponse(val product_category: StoreCategoryDto)
