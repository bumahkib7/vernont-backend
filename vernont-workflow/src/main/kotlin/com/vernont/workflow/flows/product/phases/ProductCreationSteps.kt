package com.vernont.workflow.flows.product.phases

import com.vernont.infrastructure.storage.StorageService
import com.vernont.repository.product.PendingImageUploadRepository
import com.vernont.repository.product.ProductImageRepository
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.flows.product.CreateProductInput
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.WorkflowStep
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Step 1: Reserve - Creates product records in a short DB transaction
 *
 * Compensation: Not needed for DB - transaction will rollback on failure.
 * If step succeeds, product is created but in PENDING_ASSETS state.
 * Cleanup job handles orphaned products.
 */
@Component
class ReserveProductStep(
    private val phase1Reserve: Phase1Reserve
) : WorkflowStep<ReserveStepInput, ReserveResult> {

    override val name = "reserve-product"

    override suspend fun invoke(
        input: ReserveStepInput,
        context: WorkflowContext
    ): StepResponse<ReserveResult> {
        logger.info { "Step [$name]: Starting for handle=${input.productInput.handle}" }

        val result = phase1Reserve.execute(
            input = input.productInput,
            idempotencyKey = input.idempotencyKey,
            correlationId = input.correlationId
        )

        // Store product ID for potential compensation in later steps
        context.addMetadata("productId", result.productId)
        context.addMetadata("executionId", result.executionId)

        return StepResponse.of(result, result.productId)
    }

    // No compensation - DB transaction handles rollback
    // Failed products are cleaned up by FailedProductCleanupJob
}

/**
 * Input for the reserve step
 */
data class ReserveStepInput(
    val productInput: CreateProductInput,
    val idempotencyKey: String,
    val correlationId: String?
)

/**
 * Step 2: Upload - Uploads images to S3 outside any transaction
 *
 * Compensation: Deletes uploaded S3 images on failure.
 * This is the only step that requires compensation since it has external side effects.
 * Compensation is registered via context.pushCompensation() for saga-pattern rollback.
 */
@Component
class UploadImagesStep(
    private val phase2Upload: Phase2Upload,
    private val storageService: StorageService,
    private val productImageRepository: ProductImageRepository,
    private val pendingImageUploadRepository: PendingImageUploadRepository
) : WorkflowStep<UploadStepInput, UploadResult> {

    override val name = "upload-images"

    override suspend fun invoke(
        input: UploadStepInput,
        context: WorkflowContext
    ): StepResponse<UploadResult> {
        logger.info { "Step [$name]: Starting for productId=${input.productId}" }

        val result = phase2Upload.execute(input.productId) { current, total, message, percent ->
            // Publish progress via context
            context.publishStepProgress(
                stepName = name,
                stepIndex = context.getCurrentStepCount() - 1,
                progressCurrent = current,
                progressTotal = total,
                progressMessage = message,
                totalSteps = 3
            )
        }

        // Store uploaded URLs for potential compensation
        val uploadedUrls = result.completedUrls.map { it.resultUrl }

        // Register compensation for S3 cleanup (saga pattern)
        // This runs automatically via context.runCompensations() on failure
        if (uploadedUrls.isNotEmpty()) {
            context.pushCompensation(name) {
                deleteUploadedImages(input.productId)
            }
        }

        return StepResponse.of(result, S3CompensationData(input.productId, uploadedUrls))
    }

    /**
     * Delete uploaded S3 images - called during compensation
     */
    private suspend fun deleteUploadedImages(productId: String) {
        logger.warn { "Step [$name]: Compensating - deleting uploaded images for $productId" }

        try {
            // Get all images that were uploaded (from pending uploads with resultUrl)
            val pendingUploads = pendingImageUploadRepository
                .findByProductIdAndDeletedAtIsNullOrderByPosition(productId)
                .filter { it.resultUrl != null }

            // Also check existing product images
            val existingImages = productImageRepository.findByProductId(productId)

            val allUrls = (pendingUploads.mapNotNull { it.resultUrl } + existingImages.map { it.url }).distinct()

            for (url in allUrls) {
                try {
                    val key = extractS3Key(url)
                    if (key != null) {
                        storageService.deleteFile(key)
                        logger.debug { "Compensated: Deleted S3 object $key" }
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to delete S3 object during compensation: $url" }
                    // Continue with other deletions
                }
            }

            logger.info { "Step [$name]: Compensation completed - deleted ${allUrls.size} S3 objects" }
        } catch (e: Exception) {
            logger.error(e) { "Step [$name]: Compensation failed for $productId" }
            // Don't throw - compensation is best-effort
        }
    }

    private fun extractS3Key(url: String): String? {
        return try {
            val path = java.net.URI(url).path
            path.removePrefix("/")
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Compensation data for S3 uploads
 */
data class S3CompensationData(
    val productId: String,
    val uploadedUrls: List<String>
)

/**
 * Input for the upload step
 */
data class UploadStepInput(
    val productId: String,
    val pendingUploadIds: List<String>
)

/**
 * Step 3: Finalize - Applies upload results in a short DB transaction
 *
 * Compensation: Not needed for DB - transaction will rollback on failure.
 * At this point, S3 images exist and product is in PENDING_ASSETS state.
 * If this step fails, cleanup job will handle the product and S3 compensation
 * from the upload step will delete the images.
 */
@Component
class FinalizeProductStep(
    private val phase3Finalize: Phase3Finalize
) : WorkflowStep<FinalizeStepInput, FinalizeResult> {

    override val name = "finalize-product"

    override suspend fun invoke(
        input: FinalizeStepInput,
        context: WorkflowContext
    ): StepResponse<FinalizeResult> {
        logger.info { "Step [$name]: Starting for productId=${input.productId}" }

        val result = phase3Finalize.execute(
            executionId = input.executionId,
            productId = input.productId,
            uploadResult = input.uploadResult,
            correlationId = input.correlationId
        )

        return StepResponse.of(result)
    }

    // No compensation - DB transaction handles rollback
    // Product status is set to FAILED if finalization determines failure
}

/**
 * Input for the finalize step
 */
data class FinalizeStepInput(
    val executionId: String,
    val productId: String,
    val uploadResult: UploadResult,
    val correlationId: String?
)
