package com.vernont.workflow.flows.product.phases

import com.vernont.domain.product.PendingImageUpload
import com.vernont.domain.product.PendingUploadStatus
import com.vernont.infrastructure.storage.ProductImageStorageService
import com.vernont.repository.product.PendingImageUploadRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

private val logger = KotlinLogging.logger {}

/**
 * Progress callback for Phase 2 upload tracking
 * @param current Current upload number (1-indexed)
 * @param total Total uploads to process
 * @param message Status message
 * @param percentComplete Overall completion percentage (0-100)
 */
typealias Phase2ProgressCallback = (current: Int, total: Int, message: String, percentComplete: Int) -> Unit

/**
 * Result of Phase 2: Upload
 */
data class UploadResult(
    val productId: String,
    val successCount: Int,
    val failureCount: Int,
    val permanentFailureCount: Int,
    val completedUrls: List<CompletedUpload>,
    val failedUploads: List<FailedUpload>
) {
    val allSucceeded: Boolean get() = failureCount == 0
    val hasAnySuccess: Boolean get() = successCount > 0
    val hasPermanentFailures: Boolean get() = permanentFailureCount > 0
}

data class CompletedUpload(
    val uploadId: String,
    val sourceUrl: String,
    val resultUrl: String,
    val position: Int
)

data class FailedUpload(
    val uploadId: String,
    val sourceUrl: String,
    val position: Int,
    val errorMessage: String,
    val isPermanent: Boolean
)

/**
 * Phase 2: Upload - External I/O (no transaction boundary)
 *
 * Processes pending image uploads outside of any database transaction.
 * Each upload status update uses its own short transaction.
 *
 * This phase:
 * - Fetches pending uploads for a product
 * - Uploads each image to S3
 * - Updates upload records individually
 * - Reports progress
 * - Returns summary of results
 *
 * Duration: 5-30+ seconds (network I/O)
 * Transaction boundary: Individual micro-transactions for status updates
 */
@Component
class Phase2Upload(
    private val pendingImageUploadRepository: PendingImageUploadRepository,
    private val productImageStorageService: ProductImageStorageService,
    private val transactionTemplate: TransactionTemplate
) {

    /**
     * Execute Phase 2 for a product
     *
     * @param productId The product ID to process uploads for
     * @param onProgress Optional callback for progress updates
     * @return Upload result summary
     */
    fun execute(productId: String, onProgress: Phase2ProgressCallback? = null): UploadResult {
        logger.info { "Phase2Upload: Starting for productId=$productId" }

        // Fetch pending uploads (read-only, no transaction needed)
        val pendingUploads = pendingImageUploadRepository.findPendingForProduct(productId)

        if (pendingUploads.isEmpty()) {
            logger.info { "Phase2Upload: No pending uploads for productId=$productId" }
            // Check if there are any completed uploads from previous run
            val completed = pendingImageUploadRepository.findCompletedForProduct(productId)
            return UploadResult(
                productId = productId,
                successCount = completed.size,
                failureCount = 0,
                permanentFailureCount = 0,
                completedUrls = completed.map {
                    CompletedUpload(it.id, it.sourceUrl, it.resultUrl ?: "", it.position)
                },
                failedUploads = emptyList()
            )
        }

        val total = pendingUploads.size
        val completedUrls = mutableListOf<CompletedUpload>()
        val failedUploads = mutableListOf<FailedUpload>()
        var successCount = 0
        var permanentFailureCount = 0

        logger.info { "Phase2Upload: Processing $total pending uploads for productId=$productId" }

        pendingUploads.forEachIndexed { index, upload ->
            val current = index + 1
            val percentComplete = ((current - 1) * 100) / total

            onProgress?.invoke(current, total, "Uploading image $current of $total...", percentComplete)

            try {
                processUpload(upload, productId)

                if (upload.status == PendingUploadStatus.COMPLETED) {
                    successCount++
                    completedUrls.add(
                        CompletedUpload(
                            uploadId = upload.id,
                            sourceUrl = upload.sourceUrl,
                            resultUrl = upload.resultUrl ?: "",
                            position = upload.position
                        )
                    )
                    onProgress?.invoke(current, total, "Uploaded image $current of $total", (current * 100) / total)
                } else {
                    // Failed but might be retryable
                    val isPermanent = upload.status == PendingUploadStatus.FAILED
                    if (isPermanent) permanentFailureCount++

                    failedUploads.add(
                        FailedUpload(
                            uploadId = upload.id,
                            sourceUrl = upload.sourceUrl.take(100),
                            position = upload.position,
                            errorMessage = upload.errorMessage ?: "Unknown error",
                            isPermanent = isPermanent
                        )
                    )
                    onProgress?.invoke(
                        current, total,
                        "Failed image $current of $total${if (isPermanent) " (permanent)" else " (will retry)"}",
                        (current * 100) / total
                    )
                }
            } catch (e: Exception) {
                // Unexpected error - mark as failed
                logger.error(e) { "Phase2Upload: Unexpected error processing upload ${upload.id}" }
                markUploadFailed(upload.id, "Unexpected error: ${e.message}")

                failedUploads.add(
                    FailedUpload(
                        uploadId = upload.id,
                        sourceUrl = upload.sourceUrl.take(100),
                        position = upload.position,
                        errorMessage = e.message ?: "Unexpected error",
                        isPermanent = false
                    )
                )
            }
        }

        // Also include any previously completed uploads in the result
        val previouslyCompleted = pendingImageUploadRepository.findCompletedForProduct(productId)
            .filter { completed -> completedUrls.none { it.uploadId == completed.id } }
            .map { CompletedUpload(it.id, it.sourceUrl, it.resultUrl ?: "", it.position) }

        val allCompleted = (completedUrls + previouslyCompleted).sortedBy { it.position }
        val totalSuccess = successCount + previouslyCompleted.size

        logger.info {
            "Phase2Upload: Completed for productId=$productId - " +
            "success=$totalSuccess, failures=${failedUploads.size}, permanent=$permanentFailureCount"
        }

        onProgress?.invoke(total, total, "Upload phase complete", 100)

        return UploadResult(
            productId = productId,
            successCount = totalSuccess,
            failureCount = failedUploads.size,
            permanentFailureCount = permanentFailureCount,
            completedUrls = allCompleted,
            failedUploads = failedUploads
        )
    }

    /**
     * Process a single upload with its own transaction boundaries
     */
    private fun processUpload(upload: PendingImageUpload, productId: String) {
        // Mark in progress (micro-transaction)
        markUploadInProgress(upload.id)

        try {
            // Perform actual upload (outside transaction)
            val resultUrl = runBlocking {
                uploadSingleImage(upload.sourceUrl, productId)
            }

            // Mark completed (micro-transaction)
            markUploadCompleted(upload.id, resultUrl)
            upload.status = PendingUploadStatus.COMPLETED
            upload.resultUrl = resultUrl

        } catch (e: Exception) {
            logger.warn(e) { "Phase2Upload: Upload failed for ${upload.id}: ${e.message}" }

            // Mark failed (micro-transaction) - may revert to PENDING if retries available
            val updatedUpload = markUploadFailed(upload.id, e.message ?: "Upload failed")
            upload.status = updatedUpload.status
            upload.errorMessage = updatedUpload.errorMessage
        }
    }

    /**
     * Upload a single image to S3
     */
    private suspend fun uploadSingleImage(sourceUrl: String, productId: String): String {
        val result = productImageStorageService.uploadAndResolveUrlsWithResult(
            listOf(sourceUrl),
            productId
        )

        if (result.uploadedUrls.isEmpty()) {
            val error = result.failedSources.firstOrNull()?.error ?: "Upload returned no URL"
            throw ImageUploadException(error)
        }

        return result.uploadedUrls.first()
    }

    /**
     * Mark upload as in progress (micro-transaction)
     */
    private fun markUploadInProgress(uploadId: String) {
        transactionTemplate.execute {
            val upload = pendingImageUploadRepository.findById(uploadId).orElse(null)
            if (upload != null) {
                upload.markInProgress()
                pendingImageUploadRepository.save(upload)
            }
        }
    }

    /**
     * Mark upload as completed (micro-transaction)
     */
    private fun markUploadCompleted(uploadId: String, resultUrl: String) {
        transactionTemplate.execute {
            val upload = pendingImageUploadRepository.findById(uploadId).orElse(null)
            if (upload != null) {
                upload.markCompleted(resultUrl)
                pendingImageUploadRepository.save(upload)
            }
        }
    }

    /**
     * Mark upload as failed (micro-transaction)
     * Returns the updated upload entity to check final status
     */
    private fun markUploadFailed(uploadId: String, error: String): PendingImageUpload {
        return transactionTemplate.execute {
            val upload = pendingImageUploadRepository.findById(uploadId).orElseThrow()
            upload.markFailed(error)
            pendingImageUploadRepository.save(upload)
        } ?: throw IllegalStateException("Transaction returned null")
    }
}

/**
 * Exception for image upload failures
 */
class ImageUploadException(message: String) : RuntimeException(message)
