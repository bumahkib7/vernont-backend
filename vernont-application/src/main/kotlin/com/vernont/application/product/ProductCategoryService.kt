package com.vernont.application.product

import com.vernont.domain.product.ProductCategory
import com.vernont.repository.product.ProductCategoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.criteria.JoinType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class ProductCategoryService(
    private val categoryRepository: ProductCategoryRepository
) {

    @Transactional(readOnly = true)
    fun getCategory(
        id: String,
        includeAncestorsTree: Boolean = false,
        includeDescendantsTree: Boolean = false
    ): ProductCategory {
        val category = categoryRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ProductCategoryNotFoundException("Product category not found: $id")
        
        // Eagerly load relationships if requested
        if (includeAncestorsTree) {
            loadAncestors(category)
        }
        if (includeDescendantsTree) {
            loadDescendants(category)
        }
        
        return category
    }

    @Transactional(readOnly = true)
    fun listCategories(
        ids: List<String>? = null,
        handle: List<String>? = null,
        name: List<String>? = null,
        description: List<String>? = null,
        parentCategoryId: List<String>? = null,
        searchTerm: String? = null,
        includeAncestorsTree: Boolean = false,
        includeDescendantsTree: Boolean = false,
        pageable: Pageable
    ): Page<ProductCategory> {
        val spec = buildSpecification(
            ids = ids,
            handle = handle,
            name = name,
            description = description,
            parentCategoryId = parentCategoryId,
            searchTerm = searchTerm,
            includeAncestorsTree = includeAncestorsTree,
            includeDescendantsTree = includeDescendantsTree
        )
        
        return categoryRepository.findAll(spec, pageable)
    }

    private fun buildSpecification(
        ids: List<String>?,
        handle: List<String>?,
        name: List<String>?,
        description: List<String>?,
        parentCategoryId: List<String>?,
        searchTerm: String?,
        includeAncestorsTree: Boolean,
        includeDescendantsTree: Boolean
    ): Specification<ProductCategory> {
        return Specification { root, query, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            // Not deleted
            predicates.add(cb.isNull(root.get<Any>("deletedAt")))

            // Filter by IDs
            ids?.let {
                if (it.isNotEmpty()) {
                    predicates.add(root.get<String>("id").`in`(it))
                }
            }

            // Filter by handle
            handle?.let {
                if (it.isNotEmpty()) {
                    predicates.add(root.get<String>("handle").`in`(it))
                }
            }

            // Filter by name
            name?.let {
                if (it.isNotEmpty()) {
                    predicates.add(root.get<String>("name").`in`(it))
                }
            }

            // Filter by description
            description?.let {
                if (it.isNotEmpty()) {
                    val descPredicates = it.map { desc ->
                        cb.like(cb.lower(root.get("description")), "%${desc.lowercase()}%")
                    }
                    predicates.add(cb.or(*descPredicates.toTypedArray()))
                }
            }

            // Filter by parent category ID
            parentCategoryId?.let {
                if (it.isNotEmpty()) {
                    val parentJoin = root.join<ProductCategory, ProductCategory>("parentCategory", JoinType.LEFT)
                    predicates.add(parentJoin.get<String>("id").`in`(it))
                }
            }

            // Search in name and description
            searchTerm?.let {
                val searchPattern = "%${it.lowercase()}%"
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("name")), searchPattern),
                        cb.like(cb.lower(root.get("description")), searchPattern)
                    )
                )
            }

            // Fetch relationships if needed
            if (includeAncestorsTree && query != null) {
                root.fetch<ProductCategory, ProductCategory>("parentCategory", JoinType.LEFT)
            }
            if (includeDescendantsTree && query != null) {
                root.fetch<ProductCategory, ProductCategory>("subCategories", JoinType.LEFT)
            }

            cb.and(*predicates.toTypedArray())
        }
    }

    /**
     * Recursively load all parent categories (ancestors)
     */
    private fun loadAncestors(category: ProductCategory) {
        var current = category.parentCategory
        while (current != null) {
            // Force initialization
            current.name
            current = current.parentCategory
        }
    }

    /**
     * Recursively load all child categories (descendants)
     */
    private fun loadDescendants(category: ProductCategory) {
        category.subCategories.forEach { child ->
            child.name // Force initialization
            loadDescendants(child)
        }
    }
}
