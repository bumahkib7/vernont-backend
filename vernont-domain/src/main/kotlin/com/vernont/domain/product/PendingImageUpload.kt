package com.vernont.domain.product

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Status of a pending image upload
 */
enum class PendingUploadStatus {
    /**
     * Waiting to be processed
     */
    PENDING,

    /**
     * Currently being uploaded
     */
    IN_PROGRESS,

    /**
     * Successfully uploaded to S3
     */
    COMPLETED,

    /**
     * Failed after max retries
     */
    FAILED
}

/**
 * Tracks image uploads during product creation.
 *
 * This entity allows:
 * - Progress tracking during upload
 * - Retry on transient failures
 * - Cleanup of partial uploads
 * - Decoupling image upload from DB transaction
 */
@Entity
@Table(
    name = "pending_image_upload",
    indexes = [
        Index(name = "idx_pending_upload_product", columnList = "product_id"),
        Index(name = "idx_pending_upload_status", columnList = "status, created_at")
    ]
)
class PendingImageUpload : BaseEntity() {

    /**
     * Product this image belongs to
     */
    @Column(name = "product_id", nullable = false, length = 36)
    var productId: String = ""

    /**
     * Original image source (URL or identifier)
     */
    @Column(name = "source_url", nullable = false, length = 2000)
    var sourceUrl: String = ""

    /**
     * Position/order of this image (0-based)
     */
    @Column(nullable = false)
    var position: Int = 0

    /**
     * Current upload status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PendingUploadStatus = PendingUploadStatus.PENDING

    /**
     * S3 URL after successful upload
     */
    @Column(name = "result_url", length = 500)
    var resultUrl: String? = null

    /**
     * Error message if upload failed
     */
    @Column(name = "error_message", length = 500)
    var errorMessage: String? = null

    /**
     * Number of upload attempts
     */
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    /**
     * Maximum retry attempts before marking as FAILED
     */
    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 3

    /**
     * When the last upload attempt was made
     */
    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null

    /**
     * When the upload completed successfully
     */
    @Column(name = "completed_at")
    var completedAt: Instant? = null

    companion object {
        fun create(
            productId: String,
            sourceUrl: String,
            position: Int,
            maxAttempts: Int = 3
        ): PendingImageUpload {
            return PendingImageUpload().apply {
                this.productId = productId
                this.sourceUrl = sourceUrl
                this.position = position
                this.maxAttempts = maxAttempts
                this.status = PendingUploadStatus.PENDING
            }
        }
    }

    /**
     * Mark as in progress before attempting upload
     */
    fun markInProgress() {
        this.status = PendingUploadStatus.IN_PROGRESS
        this.attemptCount++
        this.lastAttemptAt = Instant.now()
    }

    /**
     * Mark as completed with result URL
     */
    fun markCompleted(resultUrl: String) {
        this.status = PendingUploadStatus.COMPLETED
        this.resultUrl = resultUrl
        this.completedAt = Instant.now()
        this.errorMessage = null
    }

    /**
     * Mark as failed with error message.
     * If max attempts not reached, revert to PENDING for retry.
     */
    fun markFailed(error: String) {
        this.errorMessage = error.take(500)

        if (attemptCount >= maxAttempts) {
            this.status = PendingUploadStatus.FAILED
        } else {
            // Revert to pending for retry
            this.status = PendingUploadStatus.PENDING
        }
    }

    /**
     * Check if this upload can be retried
     */
    fun canRetry(): Boolean = attemptCount < maxAttempts && status != PendingUploadStatus.COMPLETED

    /**
     * Check if upload is complete (success or permanent failure)
     */
    fun isTerminal(): Boolean = status in listOf(PendingUploadStatus.COMPLETED, PendingUploadStatus.FAILED)
}
