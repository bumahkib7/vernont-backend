package com.vernont.workflow.jobs

import com.vernont.domain.intervention.HumanInterventionItem
import com.vernont.domain.intervention.InterventionSeverity
import com.vernont.domain.product.ProductStatus
import com.vernont.infrastructure.storage.StorageService
import com.vernont.repository.intervention.HumanInterventionRepository
import com.vernont.repository.product.PendingImageUploadRepository
import com.vernont.repository.product.ProductImageRepository
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.domain.WorkflowExecutionStatus
import com.vernont.workflow.repository.WorkflowExecutionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Scheduled job that cleans up failed product creations.
 *
 * This job:
 * - Finds products in FAILED status older than a threshold
 * - Deletes associated S3 images (compensating transaction for external side effects)
 * - Soft-deletes the product and related entities
 * - Updates workflow execution status to CLEANED_UP
 * - Creates human intervention items for cleanup failures
 *
 * Run frequency: Every 15 minutes (configurable)
 * Default age threshold: 1 hour (gives time for manual recovery attempts)
 */
@Component
class FailedProductCleanupJob(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val pendingImageUploadRepository: PendingImageUploadRepository,
    private val workflowExecutionRepository: WorkflowExecutionRepository,
    private val humanInterventionRepository: HumanInterventionRepository,
    private val storageService: StorageService,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${app.cleanup.failed-product-age-hours:1}")
    private val failedProductAgeHours: Long = 1,
    @Value("\${app.cleanup.batch-size:50}")
    private val batchSize: Int = 50,
    @Value("\${app.cleanup.enabled:true}")
    private val enabled: Boolean = true
) {
    companion object {
        const val INTERVENTION_TYPE_CLEANUP_FAILED = "FAILED_PRODUCT_CLEANUP"
    }

    /**
     * Run cleanup job every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    fun cleanupFailedProducts() {
        if (!enabled) {
            logger.debug { "Failed product cleanup job is disabled" }
            return
        }

        logger.info { "Starting failed product cleanup job" }

        try {
            val cutoffTime = Instant.now().minus(Duration.ofHours(failedProductAgeHours))

            // Find products in FAILED status older than threshold
            val failedProducts = productRepository.findByStatusAndCreatedAtBefore(
                ProductStatus.FAILED,
                cutoffTime
            ).take(batchSize)

            if (failedProducts.isEmpty()) {
                logger.debug { "No failed products found for cleanup" }
                return
            }

            logger.info { "Found ${failedProducts.size} failed products for cleanup" }

            var successCount = 0
            var failCount = 0

            for (product in failedProducts) {
                try {
                    cleanupProduct(product.id, product.handle)
                    successCount++
                } catch (e: Exception) {
                    logger.error(e) { "Failed to cleanup product ${product.id}: ${e.message}" }
                    createInterventionForCleanupFailure(product.id, product.handle, e.message ?: "Unknown error")
                    failCount++
                }
            }

            logger.info { "Cleanup job completed: $successCount succeeded, $failCount failed" }

        } catch (e: Exception) {
            logger.error(e) { "Failed product cleanup job encountered an error" }
        }
    }

    /**
     * Cleanup a single failed product
     */
    fun cleanupProduct(productId: String, handle: String) {
        logger.info { "Starting cleanup for product $productId ($handle)" }

        // Step 1: Delete S3 images (compensation for external side effects)
        val s3DeletionErrors = deleteS3Images(productId)

        // Step 2: Database cleanup in a transaction
        transactionTemplate.execute {
            // Soft-delete pending uploads
            pendingImageUploadRepository.deleteAllForProduct(productId)

            // Get product with images
            val product = productRepository.findById(productId).orElse(null)
            if (product == null) {
                logger.warn { "Product $productId not found during cleanup" }
                return@execute
            }

            // Soft-delete images
            product.images.forEach { image ->
                image.deletedAt = Instant.now()
            }

            // Soft-delete product
            product.deletedAt = Instant.now()
            product.status = ProductStatus.ARCHIVED
            productRepository.save(product)

            // Update workflow execution
            val execution = workflowExecutionRepository.findByResultIdAndDeletedAtIsNull(productId)
            if (execution != null) {
                execution.markAsCleanedUp()
                workflowExecutionRepository.save(execution)
            }

            logger.info { "Database cleanup completed for product $productId" }
        }

        // Log any S3 deletion errors (but don't fail the cleanup)
        if (s3DeletionErrors.isNotEmpty()) {
            logger.warn {
                "S3 deletion had ${s3DeletionErrors.size} errors for product $productId: " +
                "${s3DeletionErrors.take(5).map { it.key }}"
            }
            // Create low-severity intervention for orphaned S3 objects
            createInterventionForOrphanedImages(productId, handle, s3DeletionErrors)
        }
    }

    /**
     * Delete S3 images for a product
     * Returns list of deletion errors (empty if all succeeded)
     */
    private fun deleteS3Images(productId: String): List<S3DeletionError> {
        val errors = mutableListOf<S3DeletionError>()

        try {
            // Get existing images
            val images = productImageRepository.findByProductId(productId)

            // Get pending uploads that may have succeeded
            val pendingUploads = pendingImageUploadRepository.findByProductIdAndDeletedAtIsNullOrderByPosition(productId)
                .filter { it.resultUrl != null }

            val allUrls = images.map { it.url } + pendingUploads.mapNotNull { it.resultUrl }

            if (allUrls.isEmpty()) {
                logger.debug { "No S3 images to delete for product $productId" }
                return emptyList()
            }

            logger.info { "Deleting ${allUrls.size} S3 images for product $productId" }

            for (url in allUrls) {
                try {
                    val key = extractS3Key(url)
                    if (key != null) {
                        runBlocking {
                            storageService.deleteFile(key)
                        }
                        logger.debug { "Deleted S3 object: $key" }
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to delete S3 object for url: $url" }
                    errors.add(S3DeletionError(extractS3Key(url) ?: url, e.message ?: "Unknown error"))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Error during S3 cleanup for product $productId" }
            errors.add(S3DeletionError("unknown", "Batch operation failed: ${e.message}"))
        }

        return errors
    }

    /**
     * Extract S3 key from full URL
     */
    private fun extractS3Key(url: String): String? {
        return try {
            // URL format: https://bucket.s3.region.amazonaws.com/key/path
            // or https://s3.region.amazonaws.com/bucket/key/path
            val path = java.net.URI(url).path
            path.removePrefix("/")
        } catch (e: Exception) {
            logger.warn { "Could not extract S3 key from URL: $url" }
            null
        }
    }

    /**
     * Create human intervention item for cleanup failure
     */
    private fun createInterventionForCleanupFailure(productId: String, handle: String, error: String) {
        try {
            transactionTemplate.execute {
                // Check if intervention already exists
                val exists = humanInterventionRepository.existsPendingForEntity(
                    entityType = "Product",
                    entityId = productId,
                    interventionType = INTERVENTION_TYPE_CLEANUP_FAILED
                )

                if (!exists) {
                    val intervention = HumanInterventionItem.create(
                        interventionType = INTERVENTION_TYPE_CLEANUP_FAILED,
                        entityType = "Product",
                        entityId = productId,
                        title = "Failed cleanup: $handle",
                        description = "Automatic cleanup of failed product creation failed.\n\nProduct: $handle ($productId)\nError: $error\n\nManual cleanup may be required.",
                        errorMessage = error,
                        severity = InterventionSeverity.MEDIUM,
                        contextData = mapOf(
                            "productId" to productId,
                            "handle" to handle,
                            "cleanupError" to error
                        ),
                        maxAutoRetries = 3
                    )
                    intervention.scheduleAutoRetry()
                    humanInterventionRepository.save(intervention)

                    logger.info { "Created intervention for failed cleanup: $productId" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create intervention for cleanup failure: $productId" }
        }
    }

    /**
     * Create low-severity intervention for orphaned S3 images
     */
    private fun createInterventionForOrphanedImages(
        productId: String,
        handle: String,
        errors: List<S3DeletionError>
    ) {
        try {
            transactionTemplate.execute {
                val intervention = HumanInterventionItem.create(
                    interventionType = "ORPHANED_S3_IMAGES",
                    entityType = "Product",
                    entityId = productId,
                    title = "Orphaned images: $handle",
                    description = buildString {
                        append("Some S3 images could not be deleted during product cleanup.\n\n")
                        append("Product: $handle ($productId)\n")
                        append("Failed deletions: ${errors.size}\n\n")
                        errors.take(10).forEach {
                            append("- ${it.key}: ${it.error}\n")
                        }
                        if (errors.size > 10) {
                            append("... and ${errors.size - 10} more\n")
                        }
                    },
                    severity = InterventionSeverity.LOW,
                    contextData = mapOf(
                        "productId" to productId,
                        "handle" to handle,
                        "orphanedKeys" to errors.map { it.key }
                    ),
                    maxAutoRetries = 0 // Don't auto-retry orphaned image cleanup
                )
                humanInterventionRepository.save(intervention)

                logger.info { "Created intervention for orphaned images: $productId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create intervention for orphaned images: $productId" }
        }
    }

    /**
     * Manually trigger cleanup for a specific product
     */
    fun triggerCleanup(productId: String): Boolean {
        return try {
            val product = productRepository.findById(productId).orElse(null)
            if (product == null) {
                logger.warn { "Product $productId not found for manual cleanup" }
                return false
            }

            if (product.status != ProductStatus.FAILED) {
                logger.warn { "Product $productId is not in FAILED status (current: ${product.status})" }
                return false
            }

            cleanupProduct(productId, product.handle)
            true
        } catch (e: Exception) {
            logger.error(e) { "Manual cleanup failed for product $productId" }
            false
        }
    }

    private data class S3DeletionError(val key: String, val error: String)
}
