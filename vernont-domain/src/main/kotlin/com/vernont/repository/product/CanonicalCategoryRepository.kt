package com.vernont.repository.product

import com.vernont.domain.product.CanonicalCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CanonicalCategoryRepository : JpaRepository<CanonicalCategory, String> {
    fun findBySlugAndDeletedAtIsNull(slug: String): CanonicalCategory?
    fun findBySlug(slug: String): Optional<CanonicalCategory>

    @Query(
        """
        SELECT c FROM CanonicalCategory c
        WHERE c.deletedAt IS NULL
          AND LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        """
    )
    fun searchByName(@Param("searchTerm") searchTerm: String): List<CanonicalCategory>
}

@Repository
interface CanonicalCategoryMappingRepository : JpaRepository<com.vernont.domain.product.CanonicalCategoryMapping, String> {
    fun findByExternalSourceAndExternalKeyAndDeletedAtIsNull(externalSource: String, externalKey: String): com.vernont.domain.product.CanonicalCategoryMapping?
}
