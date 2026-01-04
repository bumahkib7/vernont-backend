package com.vernont.infrastructure.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream
import java.time.Duration

/**
 * AWS S3 implementation of the StorageService interface.
 * Handles file storage operations using Amazon S3.
 */
@Service
class S3StorageService(
    @Value("\${aws.s3.bucket-name:product-images}")
    private val bucketName: String,
    @Value("\${aws.s3.region:us-east-1}")
    private val region: String,
    @Value("\${aws.s3.url-expiration-hours:24}")
    private val urlExpirationHours: Long,
    private val s3Client: S3Client
) : StorageService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun uploadFile(
        key: String,
        inputStream: InputStream,
        contentType: String,
        metadata: Map<String, String>?
    ): String {
        val bytes = inputStream.readAllBytes()
        return uploadFileWithSize(key, bytes.inputStream(), contentType, bytes.size.toLong(), metadata)
    }

    override suspend fun uploadFileWithSize(
        key: String,
        inputStream: InputStream,
        contentType: String,
        contentLength: Long,
        metadata: Map<String, String>?
    ): String {
        try {
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .apply {
                    metadata?.let {
                        metadata(it)
                    }
                }
                .build()

            s3Client.putObject(
                putObjectRequest,
                RequestBody.fromInputStream(inputStream, contentLength)
            )

            logger.info("File uploaded successfully to S3: s3://$bucketName/$key")
            return getFileUrl(key)

        } catch (e: Exception) {
            logger.error("Failed to upload file to S3 with key: $key", e)
            throw StorageException("Failed to upload file: $key", e)
        }
    }

    override suspend fun downloadFile(key: String): InputStream {
        try {
            val getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            val response = s3Client.getObject(getObjectRequest)
            logger.info("File downloaded successfully from S3: s3://$bucketName/$key")
            return response

        } catch (e: NoSuchKeyException) {
            logger.warn("File not found in S3: s3://$bucketName/$key")
            throw StorageException("File not found: $key", e)
        } catch (e: Exception) {
            logger.error("Failed to download file from S3 with key: $key", e)
            throw StorageException("Failed to download file: $key", e)
        }
    }

    override suspend fun deleteFile(key: String) {
        try {
            val deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            s3Client.deleteObject(deleteObjectRequest)
            logger.info("File deleted successfully from S3: s3://$bucketName/$key")

        } catch (e: Exception) {
            logger.error("Failed to delete file from S3 with key: $key", e)
            throw StorageException("Failed to delete file: $key", e)
        }
    }

    override suspend fun fileExists(key: String): Boolean {
        return try {
            val headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            s3Client.headObject(headObjectRequest)
            logger.debug("File exists in S3: s3://$bucketName/$key")
            true

        } catch (e: NoSuchKeyException) {
            logger.debug("File does not exist in S3: s3://$bucketName/$key")
            false
        } catch (e: Exception) {
            logger.error("Failed to check file existence in S3 with key: $key", e)
            throw StorageException("Failed to check file existence: $key", e)
        }
    }

    override suspend fun getFileUrl(key: String, expirationSeconds: Long?): String {
        return try {
            val expirationDuration = if (expirationSeconds != null) {
                Duration.ofSeconds(expirationSeconds)
            } else {
                Duration.ofHours(urlExpirationHours)
            }

            val url = s3Client.utilities().getUrl { builder ->
                builder
                    .bucket(bucketName)
                    .key(key)
            }

            logger.debug("Generated S3 URL for key: $key")
            url.toString()

        } catch (e: Exception) {
            logger.error("Failed to generate S3 URL for key: $key", e)
            throw StorageException("Failed to generate file URL: $key", e)
        }
    }

    override suspend fun getFileMetadata(key: String): Map<String, Any> {
        return try {
            val headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            val response = s3Client.headObject(headObjectRequest)

            val metadata = mutableMapOf<String, Any>(
                "size" to (response.contentLength() ?: 0L),
                "contentType" to (response.contentType() ?: "application/octet-stream"),
                "lastModified" to (response.lastModified()?.toString() ?: "unknown"),
                "etag" to (response.eTag() ?: "unknown")
            )

            response.metadata()?.let {
                metadata.putAll(it)
            }

            logger.debug("Retrieved metadata for S3 file: s3://$bucketName/$key")
            metadata

        } catch (e: NoSuchKeyException) {
            logger.warn("File not found in S3 when retrieving metadata: s3://$bucketName/$key")
            throw StorageException("File not found: $key", e)
        } catch (e: Exception) {
            logger.error("Failed to retrieve metadata for S3 file with key: $key", e)
            throw StorageException("Failed to retrieve file metadata: $key", e)
        }
    }

    override suspend fun copyFile(sourceKey: String, destinationKey: String) {
        try {
            val copyObjectRequest = CopyObjectRequest.builder()
                .copySource("$bucketName/$sourceKey")
                .bucket(bucketName)
                .key(destinationKey)
                .build()

            s3Client.copyObject(copyObjectRequest)
            logger.info("File copied in S3 from s3://$bucketName/$sourceKey to s3://$bucketName/$destinationKey")

        } catch (e: NoSuchKeyException) {
            logger.warn("Source file not found in S3 when copying: s3://$bucketName/$sourceKey")
            throw StorageException("Source file not found: $sourceKey", e)
        } catch (e: Exception) {
            logger.error("Failed to copy file in S3 from $sourceKey to $destinationKey", e)
            throw StorageException("Failed to copy file from $sourceKey to $destinationKey", e)
        }
    }

    override suspend fun moveFile(sourceKey: String, destinationKey: String) {
        try {
            // Copy the file to the new location
            copyFile(sourceKey, destinationKey)
            // Delete the original file
            deleteFile(sourceKey)
            logger.info("File moved in S3 from s3://$bucketName/$sourceKey to s3://$bucketName/$destinationKey")

        } catch (e: StorageException) {
            logger.error("Failed to move file in S3 from $sourceKey to $destinationKey", e)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error while moving file in S3 from $sourceKey to $destinationKey", e)
            throw StorageException("Failed to move file from $sourceKey to $destinationKey", e)
        }
    }

    override suspend fun listFiles(prefix: String): List<String> {
        return try {
            val listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build()

            val response = s3Client.listObjectsV2(listObjectsRequest)
            val fileKeys = mutableListOf<String>()

            response.contents()?.forEach { s3Object ->
                if (!s3Object.key().endsWith("/")) {
                    fileKeys.add(s3Object.key())
                }
            }

            logger.debug("Listed ${fileKeys.size} files with prefix: $prefix")
            fileKeys

        } catch (e: Exception) {
            logger.error("Failed to list files with prefix: $prefix", e)
            throw StorageException("Failed to list files with prefix: $prefix", e)
        }
    }
}
