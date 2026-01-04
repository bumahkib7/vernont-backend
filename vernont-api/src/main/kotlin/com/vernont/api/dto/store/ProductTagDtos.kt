package com.vernont.api.dto.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductTag
import java.time.Instant

/**
 * Product Tag response matching Medusa Store API specification
 */
data class ProductTagResponse(
    val id: String,
    val value: String,
    @JsonProperty("created_at")
    val createdAt: Instant,
    @JsonProperty("updated_at")
    val updatedAt: Instant,
    @JsonProperty("deleted_at")
    val deletedAt: Instant? = null,
    val metadata: Map<String, Any>? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(tag: ProductTag): ProductTagResponse {
            return ProductTagResponse(
                id = tag.id,
                value = tag.value,
                createdAt = tag.createdAt,
                updatedAt = tag.updatedAt,
                deletedAt = tag.deletedAt,
                metadata = tag.metadata as Map<String, Any>?
            )
        }
    }
}

/**
 * Paginated list response for product tags
 */
data class ProductTagListResponse(
    val limit: Int,
    val offset: Int,
    val count: Long,
    @JsonProperty("product_tags")
    val productTags: List<ProductTagResponse>
)
