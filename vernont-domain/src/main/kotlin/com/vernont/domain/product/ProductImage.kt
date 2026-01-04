package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "product_image",
    indexes = [
        Index(name = "idx_product_image_product_id", columnList = "product_id"),
        Index(name = "idx_product_image_url", columnList = "url"),
        Index(name = "idx_product_image_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ProductImage.withProduct",
    attributeNodes = [
        NamedAttributeNode("product")
    ]
)
class ProductImage : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product? = null

    @NotBlank
    @Column(nullable = false)
    var url: String = ""

    @Column
    var altText: String? = null

    @Column(nullable = false)
    var position: Int = 0

    @Column
    var width: Int? = null

    @Column
    var height: Int? = null

    @Column
    var size: Long? = null

    @Column
    var mimeType: String? = null

    fun isPortrait(): Boolean = (height ?: 0) > (width ?: 0)

    fun isLandscape(): Boolean = (width ?: 0) > (height ?: 0)

    fun isSquare(): Boolean = (width != null && height != null && width == height)
}
