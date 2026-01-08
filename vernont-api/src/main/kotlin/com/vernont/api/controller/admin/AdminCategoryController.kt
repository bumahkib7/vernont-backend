package com.vernont.api.controller.admin

import com.vernont.api.dto.admin.*
import com.vernont.domain.product.ProductCategory
import com.vernont.repository.product.ProductCategoryRepository
import com.vernont.repository.product.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/categories")
@Tag(name = "Admin Categories", description = "Category management endpoints")
class AdminCategoryController(
    private val categoryRepository: ProductCategoryRepository,
    private val productRepository: ProductRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List categories", description = "Get paginated list of categories")
    fun listCategories(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(name = "parent_category_id", required = false) parentCategoryId: String?,
        @RequestParam(name = "is_active", required = false) isActive: Boolean?,
        @RequestParam(name = "include_internal", defaultValue = "true") includeInternal: Boolean
    ): ResponseEntity<AdminCategoriesResponse> {
        logger.info { "Listing categories with offset=$offset, limit=$limit, q=$q" }

        var categories = when {
            !q.isNullOrBlank() -> categoryRepository.searchByName(q)
            parentCategoryId != null -> categoryRepository.findByParentCategoryIdAndDeletedAtIsNull(parentCategoryId)
            else -> categoryRepository.findByDeletedAtIsNull()
        }

        // Filter by isActive if specified
        if (isActive != null) {
            categories = categories.filter { it.isActive == isActive }
        }

        // Filter internal categories if not including them
        if (!includeInternal) {
            categories = categories.filter { !it.isInternal }
        }

        val count = categories.size
        val paginatedCategories = categories
            .sortedBy { it.position }
            .drop(offset)
            .take(limit.coerceAtMost(100))
            .map { AdminCategory.from(it) }

        return ResponseEntity.ok(
            AdminCategoriesResponse(
                categories = paginatedCategories,
                count = count,
                offset = offset,
                limit = limit
            )
        )
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get category", description = "Get category by ID")
    fun getCategory(@PathVariable id: String): ResponseEntity<AdminCategoryResponse> {
        logger.info { "Getting category: $id" }

        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            AdminCategoryResponse(
                category = AdminCategory.from(category)
            )
        )
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create category", description = "Create a new category")
    fun createCategory(
        @RequestBody request: CreateCategoryRequest
    ): ResponseEntity<AdminCategoryResponse> {
        logger.info { "Creating category: ${request.name}" }

        // Generate handle if not provided
        val handle = request.handle ?: request.name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

        // Check if handle already exists
        if (categoryRepository.existsByHandle(handle)) {
            return ResponseEntity.badRequest().build()
        }

        val category = ProductCategory().apply {
            this.name = request.name
            this.handle = handle
            this.description = request.description
            this.image = request.image
            this.isActive = request.isActive
            this.isInternal = request.isInternal
            this.position = request.position
        }

        // Set parent category if provided
        request.parentCategoryId?.let { parentId ->
            val parent = categoryRepository.findByIdAndDeletedAtIsNull(parentId)
            if (parent != null) {
                category.parent = parent
            }
        }

        val savedCategory = categoryRepository.save(category)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            AdminCategoryResponse(
                category = AdminCategory.from(savedCategory)
            )
        )
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Update category", description = "Update an existing category")
    fun updateCategory(
        @PathVariable id: String,
        @RequestBody request: UpdateCategoryRequest
    ): ResponseEntity<AdminCategoryResponse> {
        logger.info { "Updating category: $id" }

        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Update fields if provided
        request.name?.let { category.name = it }
        request.handle?.let { handle ->
            // Check if new handle already exists for different category
            if (categoryRepository.existsByHandleAndIdNot(handle, id)) {
                return ResponseEntity.badRequest().build()
            }
            category.handle = handle
        }
        request.description?.let { category.description = it }
        request.image?.let { category.image = it }
        request.isActive?.let { category.isActive = it }
        request.isInternal?.let { category.isInternal = it }
        request.position?.let { category.position = it }

        // Update parent category if provided
        if (request.parentCategoryId != null) {
            if (request.parentCategoryId == id) {
                return ResponseEntity.badRequest().build() // Can't be own parent
            }
            val parent = categoryRepository.findByIdAndDeletedAtIsNull(request.parentCategoryId)
            category.parent = parent
        }

        val savedCategory = categoryRepository.save(category)

        return ResponseEntity.ok(
            AdminCategoryResponse(
                category = AdminCategory.from(savedCategory)
            )
        )
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Delete category", description = "Soft delete a category")
    fun deleteCategory(@PathVariable id: String): ResponseEntity<DeleteCategoryResponse> {
        logger.info { "Deleting category: $id" }

        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Soft delete
        category.deletedAt = Instant.now()
        categoryRepository.save(category)

        return ResponseEntity.ok(
            DeleteCategoryResponse(
                id = id,
                deleted = true
            )
        )
    }

    @PostMapping("/{id}/activate")
    @Transactional
    @Operation(summary = "Activate category", description = "Set category as active")
    fun activateCategory(@PathVariable id: String): ResponseEntity<AdminCategoryResponse> {
        logger.info { "Activating category: $id" }

        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        category.activate()
        val savedCategory = categoryRepository.save(category)

        return ResponseEntity.ok(
            AdminCategoryResponse(
                category = AdminCategory.from(savedCategory)
            )
        )
    }

    @PostMapping("/{id}/deactivate")
    @Transactional
    @Operation(summary = "Deactivate category", description = "Set category as inactive")
    fun deactivateCategory(@PathVariable id: String): ResponseEntity<AdminCategoryResponse> {
        logger.info { "Deactivating category: $id" }

        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        category.deactivate()
        val savedCategory = categoryRepository.save(category)

        return ResponseEntity.ok(
            AdminCategoryResponse(
                category = AdminCategory.from(savedCategory)
            )
        )
    }

    @GetMapping("/{id}/products")
    @Transactional(readOnly = true)
    @Operation(summary = "Get category products", description = "Get products in a category")
    fun getCategoryProducts(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<CategoryProductsResponse> {
        logger.info { "Getting products for category: $id" }

        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val products = category.products
            .filter { it.deletedAt == null }
            .sortedBy { it.title }
            .drop(offset)
            .take(limit)
            .map { CategoryProductItem(
                id = it.id,
                title = it.title,
                handle = it.handle,
                thumbnail = it.thumbnail,
                status = it.status.name
            ) }

        return ResponseEntity.ok(
            CategoryProductsResponse(
                products = products,
                count = category.products.count { it.deletedAt == null },
                offset = offset,
                limit = limit
            )
        )
    }

    @PostMapping("/{id}/products")
    @Transactional
    @Operation(summary = "Manage category products", description = "Add or remove products from a category")
    fun manageCategoryProducts(
        @PathVariable id: String,
        @RequestBody request: ManageCategoryProductsRequest
    ): ResponseEntity<AdminCategoryResponse> {
        logger.info { "Managing products for category: $id, add=${request.add?.size ?: 0}, remove=${request.remove?.size ?: 0}" }

        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Add products to category
        request.add?.forEach { productId ->
            val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            if (product != null && !category.products.contains(product)) {
                category.addProduct(product)
                logger.debug { "Added product $productId to category $id" }
            }
        }

        // Remove products from category
        request.remove?.forEach { productId ->
            val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            if (product != null && category.products.contains(product)) {
                category.removeProduct(product)
                logger.debug { "Removed product $productId from category $id" }
            }
        }

        val savedCategory = categoryRepository.save(category)

        return ResponseEntity.ok(
            AdminCategoryResponse(
                category = AdminCategory.from(savedCategory)
            )
        )
    }

    @PostMapping("/{id}/products/{productId}/move")
    @Transactional
    @Operation(summary = "Move product to category", description = "Move a product from one category to another")
    fun moveProductToCategory(
        @PathVariable id: String,
        @PathVariable productId: String,
        @RequestParam(name = "from_category_id", required = false) fromCategoryId: String?
    ): ResponseEntity<AdminCategoryResponse> {
        logger.info { "Moving product $productId to category $id from $fromCategoryId" }

        val targetCategory = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: return ResponseEntity.badRequest().build()

        // Remove from source category if specified
        if (fromCategoryId != null) {
            val sourceCategory = categoryRepository.findByIdAndDeletedAtIsNull(fromCategoryId)
            if (sourceCategory != null && sourceCategory.products.contains(product)) {
                sourceCategory.removeProduct(product)
                categoryRepository.save(sourceCategory)
            }
        }

        // Add to target category if not already there
        if (!targetCategory.products.contains(product)) {
            targetCategory.addProduct(product)
        }

        val savedCategory = categoryRepository.save(targetCategory)

        return ResponseEntity.ok(
            AdminCategoryResponse(
                category = AdminCategory.from(savedCategory)
            )
        )
    }
}
