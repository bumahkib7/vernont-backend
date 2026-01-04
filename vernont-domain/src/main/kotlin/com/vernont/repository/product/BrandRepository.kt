package com.vernont.repository.product

import com.vernont.domain.product.Brand
import com.vernont.domain.product.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@Repository
interface BrandRepository : JpaRepository<Brand, String> {
    fun findBySlugIgnoreCase(slug: String): Brand?
    fun findByNameIgnoreCase(name: String): Brand?
    fun findAllByActiveTrue(): List<Brand>

    @Query(
        """
        SELECT b FROM Brand b
        WHERE b.active = true
          AND b.deletedAt IS NULL
          AND LOWER(b.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        """
    )
    fun searchActiveByName(
        @Param("searchTerm") searchTerm: String,
        pageable: Pageable
    ): Page<Brand>

    fun findByIdAndDeletedAtIsNull(id: String): Brand?

    fun existsBySlug(slug: String): Boolean

    fun existsBySlugAndIdNot(slug: String, id: String): Boolean

    fun findAllByDeletedAtIsNull(): List<Brand>

    @Modifying
    @Query("DELETE FROM Brand b WHERE b.id IN :ids")
    fun deleteByIdIn(@Param("ids") ids: List<String>)
}
