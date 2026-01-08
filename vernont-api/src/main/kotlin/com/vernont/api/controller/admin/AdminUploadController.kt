package com.vernont.api.controller.admin

import com.vernont.domain.product.ProductImage
import com.vernont.infrastructure.storage.StorageService
import com.vernont.repository.product.ProductCollectionRepository
import com.vernont.repository.product.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

private val uploadLogger = KotlinLogging.logger {}

data class UploadResponse(
    val url: String,
    val key: String,
    val productId: String? = null,
    val savedToProduct: Boolean = false
)

data class CollectionUploadResponse(
    val url: String,
    val key: String,
    val collectionId: String? = null,
    val savedToCollection: Boolean = false
)

@RestController
@RequestMapping("/admin/uploads")
class AdminUploadController(
    private val storageService: StorageService,
    private val productRepository: ProductRepository,
    private val collectionRepository: ProductCollectionRepository
) {

    @PostMapping("/product-image")
    @Transactional
    suspend fun uploadProductImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("productId", required = false) productId: String?,
        @RequestParam("setAsThumbnail", required = false, defaultValue = "false") setAsThumbnail: Boolean
    ): ResponseEntity<UploadResponse> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().build()
        }

        val contentType = file.contentType ?: "application/octet-stream"
        val extension = guessExtension(file.originalFilename, contentType)
        val key = buildKey(productId, extension)

        // Upload to S3/MinIO
        val url = storageService.uploadFileWithSize(
            key,
            file.inputStream,
            contentType,
            file.size,
            metadata = mapOf("productId" to (productId ?: "unknown"))
        )

        uploadLogger.info { "Uploaded product image to S3: $key" }

        // If productId provided, save image to the product in database
        var savedToProduct = false
        if (!productId.isNullOrBlank()) {
            val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            if (product != null) {
                // Add image to product
                val productImage = ProductImage().apply {
                    this.url = url
                    this.position = product.images.size
                }
                product.addImage(productImage)

                // Set as thumbnail if requested or if it's the first image
                if (setAsThumbnail || product.thumbnail.isNullOrBlank()) {
                    product.thumbnail = url
                }

                productRepository.save(product)
                savedToProduct = true
                uploadLogger.info { "Saved image to product $productId, thumbnail=${setAsThumbnail || product.thumbnail == url}" }
            } else {
                uploadLogger.warn { "Product $productId not found, image uploaded but not saved to product" }
            }
        }

        return ResponseEntity.ok(UploadResponse(
            url = url,
            key = key,
            productId = productId,
            savedToProduct = savedToProduct
        ))
    }

    private fun guessExtension(originalFilename: String?, contentType: String): String {
        val fromName = originalFilename?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
        if (!fromName.isNullOrBlank()) return fromName
        return contentType.substringAfter("/", "bin").substringBefore(";").ifBlank { "bin" }
    }

    private fun buildKey(productId: String?, extension: String): String {
        val folder = productId?.ifBlank { null } ?: "uploads"
        return "products/$folder/${UUID.randomUUID()}.$extension"
    }

    @PostMapping("/collection-image")
    @Transactional
    suspend fun uploadCollectionImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("collectionId", required = false) collectionId: String?
    ): ResponseEntity<CollectionUploadResponse> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().build()
        }

        val contentType = file.contentType ?: "application/octet-stream"
        val extension = guessExtension(file.originalFilename, contentType)
        val key = buildCollectionKey(collectionId, extension)

        // Upload to S3/MinIO
        val url = storageService.uploadFileWithSize(
            key,
            file.inputStream,
            contentType,
            file.size,
            metadata = mapOf("collectionId" to (collectionId ?: "unknown"))
        )

        uploadLogger.info { "Uploaded collection image to S3: $key" }

        // If collectionId provided, save image URL to the collection in database
        var savedToCollection = false
        if (!collectionId.isNullOrBlank()) {
            val collection = collectionRepository.findById(collectionId).orElse(null)
            if (collection != null) {
                collection.imageUrl = url
                collectionRepository.save(collection)
                savedToCollection = true
                uploadLogger.info { "Saved image to collection $collectionId" }
            } else {
                uploadLogger.warn { "Collection $collectionId not found, image uploaded but not saved to collection" }
            }
        }

        return ResponseEntity.ok(CollectionUploadResponse(
            url = url,
            key = key,
            collectionId = collectionId,
            savedToCollection = savedToCollection
        ))
    }

    private fun buildCollectionKey(collectionId: String?, extension: String): String {
        val folder = collectionId?.ifBlank { null } ?: "uploads"
        return "collections/$folder/${UUID.randomUUID()}.$extension"
    }
}
