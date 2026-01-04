package com.vernont.application.product

import com.vernont.domain.product.ProductCollection
import com.vernont.repository.product.ProductCollectionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class ProductCollectionService(
    private val collectionRepository: ProductCollectionRepository
) {

    @Transactional(readOnly = true)
    fun getCollection(id: String): ProductCollection {
        return collectionRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw CollectionNotFoundException("Collection not found: $id")
    }

    @Transactional(readOnly = true)
    fun getCollectionByHandle(handle: String): ProductCollection {
        return collectionRepository.findByHandleAndDeletedAtIsNull(handle)
            ?: throw CollectionNotFoundException("Collection not found with handle: $handle")
    }

    @Transactional(readOnly = true)
    fun listCollections(
        handle: String? = null,
        title: String? = null,
        searchTerm: String? = null,
        pageable: Pageable
    ): Page<ProductCollection> {
        val spec = buildSpecification(handle, title, searchTerm)
        return collectionRepository.findAll(spec, pageable)
    }

    private fun buildSpecification(
        handle: String?,
        title: String?,
        searchTerm: String?
    ): Specification<ProductCollection> {
        return Specification { root, query, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            // Not deleted
            predicates.add(cb.isNull(root.get<Any>("deletedAt")))

            // Filter by handle
            handle?.let {
                predicates.add(cb.equal(root.get<String>("handle"), it))
            }

            // Filter by title (exact match)
            title?.let {
                predicates.add(cb.equal(root.get<String>("title"), it))
            }

            // Search in title (partial match)
            searchTerm?.let {
                predicates.add(
                    cb.like(
                        cb.lower(root.get("title")),
                        "%${it.lowercase()}%"
                    )
                )
            }

            cb.and(*predicates.toTypedArray())
        }
    }

    fun updateCollection(collection: ProductCollection): ProductCollection {
        // TODO: Implement actual update logic
        throw NotImplementedError("Update Collection not yet implemented")
    }

    fun deleteCollection(id: String) {
        // TODO: Implement actual delete logic
        throw NotImplementedError("Delete Collection not yet implemented")
    }

    fun manageProducts(id: String, addProductIds: List<String>, removeProductIds: List<String>): ProductCollection {
        // TODO: Implement actual manage products logic
        throw NotImplementedError("Manage Products not yet implemented")
    }
}

class CollectionNotFoundException(message: String) : RuntimeException(message)
