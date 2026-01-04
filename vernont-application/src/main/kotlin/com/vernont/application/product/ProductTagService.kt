package com.vernont.application.product

import com.vernont.domain.product.ProductTag
import com.vernont.repository.product.ProductTagRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class ProductTagService(
    private val productTagRepository: ProductTagRepository
) {

    fun getTagById(id: String, fields: String? = null): ProductTag {
        return productTagRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ProductTagNotFoundException("Product tag with ID $id not found")
    }

    fun listTags(
        pageable: Pageable,
        q: String? = null,
        id: String? = null,
        ids: List<String>? = null,
        value: String? = null,
        values: List<String>? = null,
        createdAtGte: Instant? = null,
        createdAtLte: Instant? = null,
        updatedAtGte: Instant? = null,
        updatedAtLte: Instant? = null,
        withDeleted: Boolean = false,
        fields: String? = null
    ): Page<ProductTag> {
        val spec = Specification<ProductTag> { root, query, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()

            // Deleted filter
            if (!withDeleted) {
                predicates.add(criteriaBuilder.isNull(root.get<Instant>("deletedAt")))
            }

            // Search query
            q?.let {
                predicates.add(
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("value")),
                        "%${it.lowercase()}%"
                    )
                )
            }

            // ID filters
            id?.let {
                predicates.add(criteriaBuilder.equal(root.get<String>("id"), it))
            }
            ids?.takeIf { it.isNotEmpty() }?.let {
                predicates.add(root.get<String>("id").`in`(it))
            }

            // Value filters
            value?.let {
                predicates.add(criteriaBuilder.equal(root.get<String>("value"), it))
            }
            values?.takeIf { it.isNotEmpty() }?.let {
                predicates.add(root.get<String>("value").`in`(it))
            }

            // Created at filters
            createdAtGte?.let {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), it))
            }
            createdAtLte?.let {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), it))
            }

            // Updated at filters
            updatedAtGte?.let {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("updatedAt"), it))
            }
            updatedAtLte?.let {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("updatedAt"), it))
            }

            criteriaBuilder.and(*predicates.toTypedArray())
        }

        return productTagRepository.findAll(spec, pageable)
    }
}

class ProductTagNotFoundException(message: String) : RuntimeException(message)
