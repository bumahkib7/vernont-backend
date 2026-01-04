package com.vernont.infrastructure.storage

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.net.URL
import java.util.Base64
import java.util.UUID

@Service
class ProductImageStorageService(
    private val storageService: StorageService,
    // Optional folder prefix inside the bucket (do NOT set this to the bucket name)
    private val imagePrefix: String = ""
) {
    private val logger = KotlinLogging.logger {}

    suspend fun uploadAndResolveUrls(imageSources: List<String>, productId: String): List<String> {
        if (imageSources.isEmpty()) return emptyList()

        val uploadedUrls = mutableListOf<String>()

        imageSources.forEach { raw ->
            val source = raw.trim()
            if (source.isBlank()) return@forEach

            if (isAlreadyStored(source)) {
                logger.debug { "Skipping upload for already-stored image: $source" }
                uploadedUrls += source
                return@forEach
            }

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
        }

        return uploadedUrls
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
