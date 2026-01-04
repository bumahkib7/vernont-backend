package com.vernont.infrastructure.storage

import java.io.InputStream

/**
 * Interface for file storage implementations.
 * Provides abstraction for storing and retrieving files from various backends.
 */
interface StorageService {

    /**
     * Upload a file to storage.
     *
     * @param key The unique key/path for the file in storage
     * @param inputStream The file content as an InputStream
     * @param contentType The MIME type of the file
     * @param metadata Optional metadata key-value pairs
     * @return The URL or path to access the uploaded file
     * @throws StorageException if the upload fails
     */
    suspend fun uploadFile(
        key: String,
        inputStream: InputStream,
        contentType: String,
        metadata: Map<String, String>? = null
    ): String

    /**
     * Upload a file to storage with specified size.
     *
     * @param key The unique key/path for the file in storage
     * @param inputStream The file content as an InputStream
     * @param contentType The MIME type of the file
     * @param contentLength The size of the file in bytes
     * @param metadata Optional metadata key-value pairs
     * @return The URL or path to access the uploaded file
     * @throws StorageException if the upload fails
     */
    suspend fun uploadFileWithSize(
        key: String,
        inputStream: InputStream,
        contentType: String,
        contentLength: Long,
        metadata: Map<String, String>? = null
    ): String

    /**
     * Download a file from storage.
     *
     * @param key The unique key/path of the file in storage
     * @return The file content as an InputStream
     * @throws StorageException if the download fails or file not found
     */
    suspend fun downloadFile(key: String): InputStream

    /**
     * Delete a file from storage.
     *
     * @param key The unique key/path of the file in storage
     * @throws StorageException if the deletion fails
     */
    suspend fun deleteFile(key: String)

    /**
     * Check if a file exists in storage.
     *
     * @param key The unique key/path of the file in storage
     * @return True if the file exists, false otherwise
     */
    suspend fun fileExists(key: String): Boolean

    /**
     * Get the URL to access a file in storage.
     *
     * @param key The unique key/path of the file in storage
     * @param expirationSeconds Optional expiration time for the URL in seconds
     * @return The URL to access the file
     * @throws StorageException if the URL generation fails
     */
    suspend fun getFileUrl(key: String, expirationSeconds: Long? = null): String

    /**
     * Get metadata about a file.
     *
     * @param key The unique key/path of the file in storage
     * @return Map containing file metadata (size, last modified, etc.)
     * @throws StorageException if the file not found or retrieval fails
     */
    suspend fun getFileMetadata(key: String): Map<String, Any>

    /**
     * Copy a file within storage.
     *
     * @param sourceKey The source file key/path
     * @param destinationKey The destination file key/path
     * @throws StorageException if the copy fails
     */
    suspend fun copyFile(sourceKey: String, destinationKey: String)

    /**
     * Move a file within storage (copy then delete).
     *
     * @param sourceKey The source file key/path
     * @param destinationKey The destination file key/path
     * @throws StorageException if the move fails
     */
    suspend fun moveFile(sourceKey: String, destinationKey: String)

    /**
     * List files in a directory/prefix.
     *
     * @param prefix The directory prefix to list
     * @return List of file keys matching the prefix
     * @throws StorageException if the listing fails
     */
    suspend fun listFiles(prefix: String): List<String>
}

/**
 * Custom exception for storage service errors.
 */
class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
