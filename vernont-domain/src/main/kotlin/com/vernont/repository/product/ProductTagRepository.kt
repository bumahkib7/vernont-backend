package com.vernont.repository.product

import com.vernont.domain.product.ProductTag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductTagRepository : JpaRepository<ProductTag, String>, 
    org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProductTag> {

    fun findByValue(value: String): ProductTag?

    fun findByValueAndDeletedAtIsNull(value: String): ProductTag?

    fun findByIdAndDeletedAtIsNull(id: String): ProductTag?

    fun findByDeletedAtIsNull(): List<ProductTag>

    fun existsByValue(value: String): Boolean

    fun existsByValueAndIdNot(value: String, id: String): Boolean

    @Query("SELECT pt FROM ProductTag pt WHERE LOWER(pt.value) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND pt.deletedAt IS NULL")
    fun searchByValue(@Param("searchTerm") searchTerm: String): List<ProductTag>

    @Query("SELECT COUNT(pt) FROM ProductTag pt WHERE pt.deletedAt IS NULL")
    fun countActiveTags(): Long

    @Query("SELECT pt FROM ProductTag pt JOIN pt.products p WHERE p.id = :productId AND pt.deletedAt IS NULL")
    fun findByProductId(@Param("productId") productId: String): List<ProductTag>
}
