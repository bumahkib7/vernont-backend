package com.vernont.api.dto.admin

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductCollection
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AdminCollectionResponse(
    val collection: AdminCollection
)

data class AdminCollectionsResponse(
    val collections: List<AdminCollection>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class CollectionProduct(
    val id: String,
    val title: String,
    val handle: String,
    val thumbnail: String?,
    val status: String
)

data class AdminCollection(
    val id: String,
    val title: String,
    val handle: String,
    @JsonProperty("image_url")
    val imageUrl: String?,
    @JsonProperty("product_count")
    val productCount: Int,
    val products: List<CollectionProduct>,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @JsonProperty("updated_at")
    val updatedAt: OffsetDateTime,
    @JsonProperty("deleted_at")
    val deletedAt: OffsetDateTime?,
    val metadata: Map<String, Any>? = null
) {
    companion object {
        fun from(collection: ProductCollection): AdminCollection {
            val activeProducts = collection.products.filter { it.deletedAt == null }
            return AdminCollection(
                id = collection.id ?: "",
                title = collection.title,
                handle = collection.handle,
                imageUrl = collection.imageUrl,
                productCount = activeProducts.size,
                products = activeProducts.map { product ->
                    CollectionProduct(
                        id = product.id,
                        title = product.title,
                        handle = product.handle,
                        thumbnail = product.thumbnail,
                        status = product.status.name
                    )
                },
                createdAt = collection.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = collection.updatedAt.atOffset(ZoneOffset.UTC),
                deletedAt = collection.deletedAt?.atOffset(ZoneOffset.UTC),
                metadata = null
            )
        }
    }
}

data class CreateCollectionRequest(
    val title: String,
    val handle: String? = null,
    val metadata: Map<String, Any>? = null,
    @JsonProperty("additional_data")
    val additionalData: Map<String, Any>? = null
)

data class UpdateCollectionRequest(
    val title: String? = null,
    val handle: String? = null,
    val metadata: Map<String, Any>? = null,
    @JsonProperty("additional_data")
    val additionalData: Map<String, Any>? = null
)

data class ManageProductsRequest(
    val add: List<String>? = null,
    val remove: List<String>? = null
)

data class CollectionProductsResponse(
    val products: List<CollectionProduct>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class DeleteCollectionResponse(
    val id: String,
    val `object`: String = "collection",
    val deleted: Boolean = true
)
