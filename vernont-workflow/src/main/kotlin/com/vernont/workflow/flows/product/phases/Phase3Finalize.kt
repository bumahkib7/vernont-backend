package com.vernont.workflow.flows.product.phases

import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.domain.intervention.HumanInterventionItem
import com.vernont.domain.intervention.InterventionSeverity
import com.vernont.domain.outbox.OutboxEvent
import com.vernont.domain.product.ProductImage
import com.vernont.domain.product.ProductStatus
import com.vernont.repository.intervention.HumanInterventionRepository
import com.vernont.repository.outbox.OutboxEventRepository
import com.vernont.repository.product.PendingImageUploadRepository
import com.vernont.repository.product.ProductImageRepository
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.domain.WorkflowExecutionStatus
import com.vernont.workflow.flows.product.rules.ProductNotFoundException
import com.vernont.workflow.repository.WorkflowExecutionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

private val logger = KotlinLogging.logger {}

/**
 * Result of Phase 3: Finalize
 */
data class FinalizeResult(
    val productId: String,
    val productStatus: ProductStatus,
    val imageCount: Int,
    val thumbnailUrl: String?,
    val requiresIntervention: Boolean,
    val interventionReason: String?
)

/**
 * Phase 3: Finalize - Single short DB transaction
 *
 * Applies the results of image uploads and finalizes the product.
 *
 * This phase:
 * - Creates ProductImage entities from completed uploads
 * - Sets thumbnail (first image)
 * - Updates product status (READY, FAILED)
 * - Cleans up pending upload records
 * - Updates workflow execution with final result
 * - Queues ProductCreationCompleted or ProductCreationFailed event
 * - Creates human intervention item if needed
 *
 * Transaction boundary: ~50-100ms
 */
@Component
class Phase3Finalize(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val pendingImageUploadRepository: PendingImageUploadRepository,
    private val workflowExecutionRepository: WorkflowExecutionRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val humanInterventionRepository: HumanInterventionRepository,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        const val MIN_IMAGES_REQUIRED = 1
        const val INTERVENTION_TYPE_FAILED_PRODUCT = "FAILED_PRODUCT_CREATION"
    }

    /**
     * Execute Phase 3 to finalize product creation
     *
     * @param executionId The workflow execution ID
     * @param productId The product ID
     * @param uploadResult Result from Phase 2
     * @param correlationId Optional correlation ID for tracing
     */
    fun execute(
        executionId: String,
        productId: String,
        uploadResult: UploadResult,
        correlationId: String? = null
    ): FinalizeResult {
        logger.info { "Phase3Finalize: Starting for productId=$productId, executionId=$executionId" }

        return transactionTemplate.execute { status ->
            try {
                executeInternal(executionId, productId, uploadResult, correlationId)
            } catch (e: Exception) {
                logger.error(e) { "Phase3Finalize: Failed for productId=$productId" }
                status.setRollbackOnly()
                throw e
            }
        } ?: throw IllegalStateException("Transaction returned null")
    }

    private fun executeInternal(
        executionId: String,
        productId: String,
        uploadResult: UploadResult,
        correlationId: String?
    ): FinalizeResult {

        // ====================================================================
        // 1. FETCH PRODUCT
        // ====================================================================
        val product = productRepository.findById(productId)
            .orElseThrow { ProductNotFoundException(productId) }

        // Verify product is in expected state
        if (product.status != ProductStatus.PENDING_ASSETS) {
            logger.warn { "Product $productId is in unexpected state: ${product.status}" }
            // Allow re-finalization for idempotency, but only from valid states
            if (product.status !in setOf(ProductStatus.PENDING_ASSETS, ProductStatus.FAILED)) {
                throw IllegalStateException("Product $productId cannot be finalized: status is ${product.status}")
            }
        }

        // ====================================================================
        // 2. CREATE PRODUCT IMAGES FROM COMPLETED UPLOADS
        // ====================================================================
        val completedUploads = uploadResult.completedUrls.sortedBy { it.position }
        var imageCount = 0

        for (completedUpload in completedUploads) {
            // Skip if image already exists (idempotency)
            val existingImages = productImageRepository.findByProductId(productId)
            val alreadyExists = existingImages.any { it.url == completedUpload.resultUrl }

            if (!alreadyExists) {
                val image = ProductImage().apply {
                    this.product = product
                    this.url = completedUpload.resultUrl
                    this.position = completedUpload.position
                }
                productImageRepository.save(image)
                product.images.add(image)
                imageCount++
            } else {
                imageCount++
            }
        }

        // ====================================================================
        // 3. SET THUMBNAIL
        // ====================================================================
        val thumbnailUrl = if (product.images.isNotEmpty()) {
            val firstImage = product.images.minByOrNull { it.position }
            product.thumbnail = firstImage?.url
            firstImage?.url
        } else null

        // ====================================================================
        // 4. DETERMINE FINAL STATUS
        // ====================================================================
        val totalImages = product.images.size
        val hasMinimumImages = totalImages >= MIN_IMAGES_REQUIRED
        val hasAnyPermanentFailures = uploadResult.hasPermanentFailures

        val finalStatus = when {
            hasMinimumImages -> ProductStatus.READY
            else -> ProductStatus.FAILED
        }

        product.status = finalStatus
        productRepository.save(product)

        logger.info { "Phase3Finalize: Product $productId status set to $finalStatus with $totalImages images" }

        // ====================================================================
        // 5. CLEANUP PENDING UPLOADS
        // ====================================================================
        val deletedCount = pendingImageUploadRepository.deleteCompletedForProduct(productId)
        logger.debug { "Phase3Finalize: Deleted $deletedCount completed pending uploads for $productId" }

        // ====================================================================
        // 6. UPDATE WORKFLOW EXECUTION
        // ====================================================================
        val execution = workflowExecutionRepository.findById(executionId).orElse(null)
        if (execution != null) {
            val resultPayload = mapOf(
                "productId" to productId,
                "status" to finalStatus.name,
                "imageCount" to totalImages,
                "thumbnail" to thumbnailUrl
            )

            if (finalStatus == ProductStatus.READY) {
                execution.markAsCompletedWithResult(productId, resultPayload)
            } else {
                execution.status = WorkflowExecutionStatus.FAILED
                execution.errorMessage = "Product creation failed: insufficient images"
                execution.resultPayload = resultPayload
            }
            workflowExecutionRepository.save(execution)
        }

        // ====================================================================
        // 7. QUEUE OUTBOX EVENT
        // ====================================================================
        val eventType = if (finalStatus == ProductStatus.READY) {
            "ProductCreationCompleted"
        } else {
            "ProductCreationFailed"
        }

        val outboxEvent = OutboxEvent.create(
            aggregateType = "Product",
            aggregateId = productId,
            eventType = eventType,
            payload = mapOf(
                "productId" to productId,
                "handle" to product.handle,
                "title" to product.title,
                "status" to finalStatus.name,
                "imageCount" to totalImages,
                "thumbnail" to thumbnailUrl,
                "failedUploads" to uploadResult.failedUploads.map {
                    mapOf(
                        "position" to it.position,
                        "error" to it.errorMessage,
                        "permanent" to it.isPermanent
                    )
                }
            ),
            correlationId = correlationId
        )
        outboxEventRepository.save(outboxEvent)

        // ====================================================================
        // 8. CREATE INTERVENTION IF NEEDED
        // ====================================================================
        var requiresIntervention = false
        var interventionReason: String? = null

        if (finalStatus == ProductStatus.FAILED) {
            // Check if intervention already exists
            val existingIntervention = humanInterventionRepository.existsPendingForEntity(
                entityType = "Product",
                entityId = productId,
                interventionType = INTERVENTION_TYPE_FAILED_PRODUCT
            )

            if (!existingIntervention) {
                val intervention = HumanInterventionItem.create(
                    interventionType = INTERVENTION_TYPE_FAILED_PRODUCT,
                    entityType = "Product",
                    entityId = productId,
                    title = "Failed product creation: ${product.handle}",
                    description = buildString {
                        append("Product creation failed due to insufficient images.\n")
                        append("Product: ${product.title} (${product.handle})\n")
                        append("Required: $MIN_IMAGES_REQUIRED images, Got: $totalImages\n\n")
                        if (uploadResult.failedUploads.isNotEmpty()) {
                            append("Failed uploads:\n")
                            uploadResult.failedUploads.forEach {
                                append("- Position ${it.position}: ${it.errorMessage}")
                                if (it.isPermanent) append(" (permanent)")
                                append("\n")
                            }
                        }
                    },
                    severity = if (hasAnyPermanentFailures) InterventionSeverity.HIGH else InterventionSeverity.MEDIUM,
                    contextData = mapOf(
                        "productId" to productId,
                        "handle" to product.handle,
                        "executionId" to executionId,
                        "imageCount" to totalImages,
                        "failedCount" to uploadResult.failureCount,
                        "permanentFailureCount" to uploadResult.permanentFailureCount
                    ),
                    maxAutoRetries = 0 // Don't auto-retry failed product creation
                )
                humanInterventionRepository.save(intervention)

                requiresIntervention = true
                interventionReason = "Product creation failed: ${uploadResult.failureCount} image upload failures"
                logger.warn { "Phase3Finalize: Created intervention item for failed product $productId" }
            }
        }

        logger.info {
            "Phase3Finalize: Completed for productId=$productId - " +
            "status=$finalStatus, images=$totalImages, intervention=$requiresIntervention"
        }

        return FinalizeResult(
            productId = productId,
            productStatus = finalStatus,
            imageCount = totalImages,
            thumbnailUrl = thumbnailUrl,
            requiresIntervention = requiresIntervention,
            interventionReason = interventionReason
        )
    }

    /**
     * Handle complete failure when Phase 2 couldn't run at all
     */
    fun handleCompleteFailure(
        executionId: String,
        productId: String,
        error: String,
        correlationId: String? = null
    ): FinalizeResult {
        logger.error { "Phase3Finalize: Handling complete failure for productId=$productId: $error" }

        return transactionTemplate.execute { status ->
            try {
                // Fetch and update product status
                val product = productRepository.findById(productId).orElse(null)
                if (product != null) {
                    product.status = ProductStatus.FAILED
                    productRepository.save(product)
                }

                // Update workflow execution
                val execution = workflowExecutionRepository.findById(executionId).orElse(null)
                if (execution != null) {
                    execution.status = WorkflowExecutionStatus.FAILED
                    execution.errorMessage = error
                    workflowExecutionRepository.save(execution)
                }

                // Create intervention
                val existingIntervention = humanInterventionRepository.existsPendingForEntity(
                    entityType = "Product",
                    entityId = productId,
                    interventionType = INTERVENTION_TYPE_FAILED_PRODUCT
                )

                if (!existingIntervention) {
                    val intervention = HumanInterventionItem.create(
                        interventionType = INTERVENTION_TYPE_FAILED_PRODUCT,
                        entityType = "Product",
                        entityId = productId,
                        title = "Failed product creation: ${product?.handle ?: productId}",
                        description = "Product creation failed completely: $error",
                        errorMessage = error,
                        severity = InterventionSeverity.HIGH,
                        contextData = mapOf(
                            "productId" to productId,
                            "executionId" to executionId,
                            "error" to error
                        ),
                        maxAutoRetries = 0
                    )
                    humanInterventionRepository.save(intervention)
                }

                // Queue failure event
                val outboxEvent = OutboxEvent.create(
                    aggregateType = "Product",
                    aggregateId = productId,
                    eventType = "ProductCreationFailed",
                    payload = mapOf(
                        "productId" to productId,
                        "handle" to (product?.handle ?: "unknown"),
                        "error" to error
                    ),
                    correlationId = correlationId
                )
                outboxEventRepository.save(outboxEvent)

                FinalizeResult(
                    productId = productId,
                    productStatus = ProductStatus.FAILED,
                    imageCount = 0,
                    thumbnailUrl = null,
                    requiresIntervention = true,
                    interventionReason = error
                )
            } catch (e: Exception) {
                logger.error(e) { "Phase3Finalize: Failed to handle complete failure for $productId" }
                status.setRollbackOnly()
                throw e
            }
        } ?: throw IllegalStateException("Transaction returned null")
    }
}
