package com.vernont.api.controller.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.ProductCollection
import com.vernont.domain.product.dto.StoreProductDto
import com.vernont.repository.product.ProductCollectionRepository
import com.vernont.repository.product.ProductRepository
import com.vernont.api.service.StoreProductService
import com.vernont.infrastructure.storage.PresignedUrlService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/store/collections")
@CrossOrigin(origins = ["http://localhost:8000", "http://localhost:9000", "http://localhost:3000"])
class CollectionController(
    private val collectionRepository: ProductCollectionRepository,
    private val productRepository: ProductRepository,
    private val storeProductService: StoreProductService,
    private val presignedUrlService: PresignedUrlService
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

    @GetMapping("/{idOrHandle}")
    fun getCollection(@PathVariable idOrHandle: String): ResponseEntity<StoreCollectionResponse> {
        // Try by ID first, then by handle
        val collection = collectionRepository.findByIdAndDeletedAtIsNull(idOrHandle)
            ?: collectionRepository.findByHandleAndDeletedAtIsNull(idOrHandle)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(StoreCollectionResponse(collection = StoreCollectionDto.from(collection)))
    }

    @GetMapping("/{handle}/products")
    fun getCollectionProducts(
        @PathVariable handle: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(name = "currency_code", required = false, defaultValue = "usd") currencyCode: String,
        @RequestParam(name = "region_id", required = false) regionId: String?
    ): ResponseEntity<StoreCollectionProductsResponse> {
        // Find collection by handle
        val collection = collectionRepository.findByHandleAndDeletedAtIsNull(handle)
            ?: return ResponseEntity.notFound().build()

        // Find products in collection
        val allProducts = productRepository.findByCollectionIdAndDeletedAtIsNull(collection.id!!)
        val paginatedProducts = allProducts.drop(offset).take(limit)

        // Get inventory and prices
        val variantIds = paginatedProducts.flatMap { product ->
            product.variants.filter { it.deletedAt == null }.map { it.id }
        }
        val inventoryMap = storeProductService.getInventoryForVariants(variantIds)
        val priceMap = storeProductService.getPricesForVariants(variantIds, currencyCode, regionId)

        // Transform to DTOs with signed URLs
        val productDtos = paginatedProducts.map { product ->
            signMedia(StoreProductDto.from(product, inventoryMap, priceMap))
        }

        return ResponseEntity.ok(StoreCollectionProductsResponse(
            items = productDtos,
            total = allProducts.size,
            page = offset / limit,
            size = limit
        ))
    }

    private fun signMedia(dto: StoreProductDto): StoreProductDto {
        val signedImages = dto.images.map { img ->
            img.copy(url = presignedUrlService.signIfNeeded(img.url) ?: img.url)
        }
        val signedThumbnail = presignedUrlService.signIfNeeded(dto.thumbnail) ?: dto.thumbnail
        val signedVariants = dto.variants.map { variant ->
            variant.copy(
                images = variant.images.map { img ->
                    img.copy(url = presignedUrlService.signIfNeeded(img.url) ?: img.url)
                }
            )
        }
        return dto.copy(
            images = signedImages,
            thumbnail = signedThumbnail,
            variants = signedVariants
        )
    }
}

data class StoreCollectionDto(
    val id: String,
    val title: String,
    val handle: String,
    val description: String?,
    val thumbnail: String?,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant
) {
    companion object {
        fun from(collection: ProductCollection) = StoreCollectionDto(
            id = collection.id ?: "",
            title = collection.title,
            handle = collection.handle,
            description = collection.description,
            thumbnail = collection.imageUrl,
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

data class StoreCollectionProductsResponse(
    val items: List<StoreProductDto>,
    val total: Int,
    val page: Int,
    val size: Int,
    val filters: Any? = null
)
