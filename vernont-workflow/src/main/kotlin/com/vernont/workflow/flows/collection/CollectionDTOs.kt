package com.vernont.workflow.flows.collection

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductCollection
import java.time.Instant

/**
 * DTO for collection workflow outputs.
 * This prevents circular reference issues when serializing ProductCollection entities.
 */
data class CollectionDto(
    val id: String,
    val title: String,
    val handle: String,
    val description: String?,
    @JsonProperty("image_url")
    val imageUrl: String?,
    val published: Boolean,
    @JsonProperty("created_at")
    val createdAt: Instant,
    @JsonProperty("updated_at")
    val updatedAt: Instant,
    val metadata: Map<String, Any?>?
) {
    companion object {
        fun from(collection: ProductCollection) = CollectionDto(
            id = collection.id ?: "",
            title = collection.title,
            handle = collection.handle,
            description = collection.description,
            imageUrl = collection.imageUrl,
            published = collection.published,
            createdAt = collection.createdAt,
            updatedAt = collection.updatedAt,
            metadata = collection.metadata
        )
    }
}

// Input/Output DTOs for Create workflow
data class CreateCollectionInput(
    val title: String,
    val handle: String? = null,
    val metadata: Map<String, Any>? = null
)

data class CreateCollectionOutput(
    val collection: CollectionDto
)

// Input/Output DTOs for Edit workflow
data class EditCollectionInput(
    val id: String,
    val title: String? = null,
    val handle: String? = null,
    val metadata: Map<String, Any>? = null
)

data class EditCollectionOutput(
    val collection: CollectionDto
)

// Input/Output DTOs for Publish workflow
data class PublishCollectionInput(
    val id: String
)

data class PublishCollectionOutput(
    val collection: CollectionDto
)
