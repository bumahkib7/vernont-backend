package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "canonical_category_synonym",
    indexes = [
        Index(name = "idx_canonical_synonym_name", columnList = "name", unique = true),
        Index(name = "idx_canonical_synonym_category", columnList = "category_id")
    ]
)
class CanonicalCategorySynonym : BaseEntity() {

    @Column(nullable = false, unique = true, length = 255)
    var name: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    lateinit var category: CanonicalCategory
}
