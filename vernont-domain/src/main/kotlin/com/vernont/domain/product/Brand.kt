package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "brand",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_brand_slug", columnNames = ["slug"])
    ],
    indexes = [
        Index(name = "idx_brand_name", columnList = "name"),
        Index(name = "idx_brand_slug_active", columnList = "slug, deleted_at")
    ]
)
class Brand : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    /**
     * URL-safe slug for routing (e.g. "hugo-boss").
     */
    @NotBlank
    @Column(nullable = false)
    var slug: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "logo_url")
    var logoUrl: String? = null

    @Column(name = "website_url")
    var websiteUrl: String? = null

    @Column(nullable = false)
    var active: Boolean = true

    /**
     * Brand tier classification for catalog prioritization.
     * Defaults to STANDARD if not classified.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 20, nullable = false)
    var tier: BrandTier = BrandTier.STANDARD
}
