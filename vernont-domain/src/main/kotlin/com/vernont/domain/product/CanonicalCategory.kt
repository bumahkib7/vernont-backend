package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "canonical_category",
    indexes = [
        Index(name = "idx_canonical_category_slug", columnList = "slug", unique = true),
        Index(name = "idx_canonical_category_parent", columnList = "parent_id")
    ]
)
class CanonicalCategory : BaseEntity() {

    @Column(nullable = false, unique = true)
    var slug: String = ""

    @Column(nullable = false)
    var name: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: CanonicalCategory? = null

    @Column(columnDefinition = "TEXT")
    var path: String? = null

    @Column
    var depth: Int? = null

    @Column(name = "sort_order")
    var sortOrder: Int? = null

    @OneToMany(mappedBy = "category", cascade = [CascadeType.ALL], orphanRemoval = true)
    var synonyms: MutableSet<CanonicalCategorySynonym> = mutableSetOf()
}

@Entity
@Table(
    name = "canonical_category_mapping",
    indexes = [
        Index(name = "idx_cat_mapping_source_key", columnList = "external_source, external_key", unique = true),
        Index(name = "idx_cat_mapping_canonical", columnList = "canonical_category_id")
    ]
)
class CanonicalCategoryMapping : BaseEntity() {

    @Column(name = "external_source", nullable = false)
    var externalSource: String = ""

    @Column(name = "external_key", nullable = false, length = 512)
    var externalKey: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_category_id", nullable = false)
    lateinit var canonicalCategory: CanonicalCategory

    @Column
    var confidence: Double? = null

    @Column(columnDefinition = "TEXT")
    var notes: String? = null
}
