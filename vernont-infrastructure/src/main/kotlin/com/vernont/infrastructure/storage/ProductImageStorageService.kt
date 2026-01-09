package com.vernont.infrastructure.storage

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.net.URL
import java.util.Base64
import java.util.UUID

/**
 * Result of an image upload operation.
 */
data class ImageUploadResult(
    val uploadedUrls: List<String>,
    val failedSources: List<FailedImageUpload>
) {
    val hasFailures: Boolean get() = failedSources.isNotEmpty()
    val allSucceeded: Boolean get() = failedSources.isEmpty()
}

data class FailedImageUpload(
    val source: String,
    val error: String
)

/**
 * Callback for tracking upload progress.
 * @param current Current image number (1-indexed)
 * @param total Total number of images to upload
 * @param message Human-readable status message
 */
typealias UploadProgressCallback = suspend (current: Int, total: Int, message: String) -> Unit

@Service
class ProductImageStorageService(
    private val storageService: StorageService,
    // Optional folder prefix inside the bucket (do NOT set this to the bucket name)
    private val imagePrefix: String = ""
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Upload and resolve URLs for product images.
     * Returns only successfully uploaded URLs. Failed uploads are logged but don't fail the entire operation.
     */
    suspend fun uploadAndResolveUrls(imageSources: List<String>, productId: String): List<String> {
        val result = uploadAndResolveUrlsWithResult(imageSources, productId)
        if (result.hasFailures) {
            logger.warn { "Some images failed to upload for product $productId: ${result.failedSources.map { "${it.source}: ${it.error}" }}" }
        }
        return result.uploadedUrls
    }

    /**
     * Upload and resolve URLs with detailed result including failures.
     * Use this when you need to know which images failed and why.
     *
     * @param imageSources List of image URLs or base64 data to upload
     * @param productId The product ID for organizing uploads
     * @param onProgress Optional callback invoked after each image is processed (current, total, message)
     */
    suspend fun uploadAndResolveUrlsWithResult(
        imageSources: List<String>,
        productId: String,
        onProgress: UploadProgressCallback? = null
    ): ImageUploadResult {
        if (imageSources.isEmpty()) return ImageUploadResult(emptyList(), emptyList())

        val uploadedUrls = mutableListOf<String>()
        val failedSources = mutableListOf<FailedImageUpload>()
        val total = imageSources.size

        imageSources.forEachIndexed { index, raw ->
            val source = raw.trim()
            val current = index + 1

            if (source.isBlank()) {
                onProgress?.invoke(current, total, "Skipping blank image $current/$total")
                return@forEachIndexed
            }

            try {
                if (isAlreadyStored(source)) {
                    logger.debug { "Skipping upload for already-stored image: $source" }
                    uploadedUrls += source
                    onProgress?.invoke(current, total, "Image $current/$total already stored")
                    return@forEachIndexed
                }

                onProgress?.invoke(current, total, "Uploading image $current/$total...")

                val payload = loadImage(source)
                val key = buildObjectKey(productId, payload.extension)

                val url = storageService.uploadFileWithSize(
                    key,
                    payload.bytes.inputStream(),
                    payload.contentType,
                    payload.bytes.size.toLong(),
                    metadata = mapOf("productId" to productId)
                )
                logger.info { "Uploaded product image for $productId to key $key" }
                uploadedUrls += url
                onProgress?.invoke(current, total, "Uploaded image $current/$total")
            } catch (e: StorageException) {
                val errorMsg = "S3 upload failed: ${e.message}"
                logger.error(e) { "Failed to upload image for product $productId: $errorMsg" }
                failedSources += FailedImageUpload(source.take(100), errorMsg)
                onProgress?.invoke(current, total, "Failed image $current/$total: S3 error")
            } catch (e: IllegalArgumentException) {
                val errorMsg = "Invalid image data: ${e.message}"
                logger.error(e) { "Failed to process image for product $productId: $errorMsg" }
                failedSources += FailedImageUpload(source.take(100), errorMsg)
                onProgress?.invoke(current, total, "Failed image $current/$total: Invalid data")
            } catch (e: Exception) {
                val errorMsg = "Unexpected error: ${e.message}"
                logger.error(e) { "Failed to upload image for product $productId: $errorMsg" }
                failedSources += FailedImageUpload(source.take(100), errorMsg)
                onProgress?.invoke(current, total, "Failed image $current/$total: Error")
            }
        }

        return ImageUploadResult(uploadedUrls, failedSources)
    }

    private fun isAlreadyStored(url: String): Boolean {
        return url.contains(imagePrefix.trim('/'), ignoreCase = true)
    }

    private fun buildObjectKey(productId: String, extension: String?): String {
        val normalizedPrefix = imagePrefix.trim('/').takeIf { it.isNotBlank() }?.let { "$it/" } ?: ""
        val ext = extension?.takeIf { it.isNotBlank() } ?: "bin"
        return "$normalizedPrefix$productId/${UUID.randomUUID()}.$ext"
    }

    private suspend fun loadImage(source: String): ImagePayload {
        return when {
            source.startsWith("data:", ignoreCase = true) -> parseDataUrl(source)
            source.startsWith("http", ignoreCase = true) -> fetchRemote(source)
            else -> parseBase64(source)
        }
    }

    private fun parseDataUrl(dataUrl: String): ImagePayload {
        val prefix = dataUrl.substringBefore(",")
        val base64Data = dataUrl.substringAfter(",")
        val contentType = prefix.substringAfter("data:").substringBefore(";").ifBlank { "application/octet-stream" }
        val extension = guessExtensionFromContentType(contentType)
        val bytes = Base64.getDecoder().decode(base64Data)
        return ImagePayload(bytes, contentType, extension)
    }

    private fun parseBase64(data: String): ImagePayload {
        val bytes = Base64.getDecoder().decode(data)
        return ImagePayload(bytes, "application/octet-stream", "bin")
    }

    private suspend fun fetchRemote(url: String): ImagePayload = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection()
        connection.getInputStream().use { stream ->
            val bytes = stream.readAllBytes()
            val contentType = connection.contentType ?: "application/octet-stream"
            val extension = guessExtensionFromContentType(contentType) ?: guessExtensionFromUrl(url)
            ImagePayload(bytes, contentType, extension)
        }
    }

    private fun guessExtensionFromContentType(contentType: String?): String? {
        if (contentType == null) return null
        return contentType.substringAfter("/").substringBefore(";").takeIf { it.isNotBlank() }
    }

    private fun guessExtensionFromUrl(url: String): String? {
        val path = url.substringBefore("?", url)
        val candidate = path.substringAfterLast(".", "")
        return candidate.takeIf { it.isNotBlank() && it.length <= 5 }
    }

    private data class ImagePayload(
        val bytes: ByteArray,
        val contentType: String,
        val extension: String?
    )
}
