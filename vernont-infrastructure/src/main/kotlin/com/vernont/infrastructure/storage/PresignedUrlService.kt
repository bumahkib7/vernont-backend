package com.vernont.infrastructure.storage

import com.vernont.infrastructure.config.AwsProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.time.Duration

@Service
class PresignedUrlService(
    private val awsProperties: AwsProperties,
    private val s3Presigner: S3Presigner
) {

    private val logger = KotlinLogging.logger {}

    /**
        * If presigning is enabled and the input looks like an S3/MinIO object URL or key,
        * return a presigned URL. Otherwise return the original string.
        */
    fun signIfNeeded(urlOrKey: String?): String? {
        if (urlOrKey.isNullOrBlank()) return urlOrKey
        if (!awsProperties.s3.presignEnabled) return urlOrKey
        if (urlOrKey.contains("X-Amz-Signature", ignoreCase = true)) return urlOrKey

        val key = extractKey(urlOrKey) ?: return urlOrKey

        return try {
            val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(awsProperties.s3.urlExpirationHours.toLong()))
                .getObjectRequest(
                    GetObjectRequest.builder()
                        .bucket(awsProperties.s3.bucketName)
                        .key(key)
                        .build()
                )
                .build()

            val presigned = s3Presigner.presignGetObject(presignRequest)
            presigned.url().toString()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to presign URL for key=$key; returning original" }
            urlOrKey
        }
    }

    private fun extractKey(urlOrKey: String): String? {
        // If it's already a URL, try to parse and strip bucket prefix; otherwise treat as key
        return if (urlOrKey.startsWith("http", ignoreCase = true)) {
            try {
                val uri = URI(urlOrKey)
                val path = uri.path.trimStart('/')
                // path-style: /bucket/key
                if (path.startsWith("${awsProperties.s3.bucketName}/")) {
                    path.removePrefix("${awsProperties.s3.bucketName}/")
                } else {
                    // virtual-hosted: host contains bucket
                    val hostHasBucket = uri.host?.contains(awsProperties.s3.bucketName, ignoreCase = true) == true
                    if (hostHasBucket) path else null
                }
            } catch (_: Exception) {
                null
            }
        } else {
            urlOrKey
        }
    }
}
