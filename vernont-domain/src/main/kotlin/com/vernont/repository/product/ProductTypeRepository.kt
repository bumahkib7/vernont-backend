package com.vernont.repository.product

import com.vernont.domain.product.ProductType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductTypeRepository : JpaRepository<ProductType, String> {

    fun findByValue(value: String): ProductType?

    fun findByValueAndDeletedAtIsNull(value: String): ProductType?

    fun findByIdAndDeletedAtIsNull(id: String): ProductType?

    fun findByDeletedAtIsNull(): List<ProductType>

    fun existsByValue(value: String): Boolean

    fun existsByValueAndIdNot(value: String, id: String): Boolean

    @Query("SELECT pt FROM ProductType pt WHERE LOWER(pt.value) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND pt.deletedAt IS NULL")
    fun searchByValue(@Param("searchTerm") searchTerm: String): List<ProductType>

    @Query("SELECT COUNT(pt) FROM ProductType pt WHERE pt.deletedAt IS NULL")
    fun countActiveTypes(): Long
}
