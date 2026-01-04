package com.vernont.api.controller.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductCollection
import com.vernont.repository.product.ProductCollectionRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/store/collections")
@CrossOrigin(origins = ["http://localhost:8000", "http://localhost:9000", "http://localhost:3000"])
class CollectionController(
    private val collectionRepository: ProductCollectionRepository
) {

    @GetMapping
    fun listCollections(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) handle: String?
    ): ResponseEntity<StoreCollectionListResponse> {
        val allCollections = if (handle != null) {
            listOfNotNull(collectionRepository.findByHandleAndDeletedAtIsNull(handle))
        } else {
            collectionRepository.findByDeletedAtIsNull()
        }

        val paginatedCollections = allCollections.drop(offset).take(limit)

        return ResponseEntity.ok(StoreCollectionListResponse(
            collections = paginatedCollections.map { StoreCollectionDto.from(it) },
            count = allCollections.size,
            offset = offset,
            limit = limit
        ))
    }

    @GetMapping("/{id}")
    fun getCollection(@PathVariable id: String): ResponseEntity<StoreCollectionResponse> {
        val collection = collectionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(StoreCollectionResponse(collection = StoreCollectionDto.from(collection)))
    }
}

data class StoreCollectionDto(
    val id: String,
    val title: String,
    val handle: String,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant
) {
    companion object {
        fun from(collection: ProductCollection) = StoreCollectionDto(
            id = collection.id ?: "",
            title = collection.title,
            handle = collection.handle,
            createdAt = collection.createdAt,
            updatedAt = collection.updatedAt
        )
    }
}

data class StoreCollectionListResponse(
    val collections: List<StoreCollectionDto>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class StoreCollectionResponse(val collection: StoreCollectionDto)
