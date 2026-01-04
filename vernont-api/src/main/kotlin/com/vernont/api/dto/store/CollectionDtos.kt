package com.vernont.api.dto.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductCollection
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class StoreCollectionResponse(
    val collection: StoreCollection
)

data class StoreCollectionsResponse(
    val collections: List<StoreCollection>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class StoreCollection(
    val id: String,
    val title: String,
    val handle: String,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @JsonProperty("updated_at")
    val updatedAt: OffsetDateTime,
    @JsonProperty("deleted_at")
    val deletedAt: OffsetDateTime?,
    val metadata: Map<String, Any>? = null
) {
    companion object {
        fun from(collection: ProductCollection): StoreCollection {
            return StoreCollection(
                id = collection.id ?: "",
                title = collection.title,
                handle = collection.handle,
                createdAt = collection.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = collection.updatedAt.atOffset(ZoneOffset.UTC),
                deletedAt = collection.deletedAt?.atOffset(ZoneOffset.UTC),
                metadata = null
            )
        }
    }
}
