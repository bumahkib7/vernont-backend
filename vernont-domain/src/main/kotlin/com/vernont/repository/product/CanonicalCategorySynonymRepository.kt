package com.vernont.repository.product

import com.vernont.domain.product.CanonicalCategorySynonym
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CanonicalCategorySynonymRepository : JpaRepository<CanonicalCategorySynonym, String> {
    fun findByNameIgnoreCase(name: String): CanonicalCategorySynonym?
}
