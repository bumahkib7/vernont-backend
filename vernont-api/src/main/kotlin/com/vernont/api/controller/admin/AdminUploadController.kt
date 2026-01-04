package com.vernont.api.controller.admin

import com.vernont.infrastructure.storage.StorageService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

private val uploadLogger = KotlinLogging.logger {}

data class UploadResponse(val url: String, val key: String)

@RestController
@RequestMapping("/admin/uploads")
class AdminUploadController(
    private val storageService: StorageService
) {

    @PostMapping("/product-image")
    suspend fun uploadProductImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("productId", required = false) productId: String?
    ): ResponseEntity<UploadResponse> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().build()
        }

        val contentType = file.contentType ?: "application/octet-stream"
        val extension = guessExtension(file.originalFilename, contentType)
        val key = buildKey(productId, extension)

        val url = storageService.uploadFileWithSize(
            key,
            file.inputStream,
            contentType,
            file.size,
            metadata = mapOf("productId" to (productId ?: "unknown"))
        )

        uploadLogger.info { "Uploaded product image to key $key" }
        return ResponseEntity.ok(UploadResponse(url = url, key = key))
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
}
