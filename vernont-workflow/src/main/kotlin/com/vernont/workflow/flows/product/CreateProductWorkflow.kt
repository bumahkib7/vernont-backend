package com.vernont.workflow.flows.product

import com.vernont.domain.product.ProductStatus
import com.vernont.domain.product.dto.ProductResponse
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.flows.product.phases.*
import com.vernont.workflow.flows.product.rules.*
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * CreateProductWorkflow - Fine-grained Step Tracking
 *
 * This workflow creates products with detailed step-by-step progress tracking.
 * Each operation is a separate tracked step visible in the workflow monitor.
 *
 * Steps:
 * 1. validate-input: Validate business rules (handle, title, options, variants, images)
 * 2. reserve-product: Create product, options, variants, prices, inventory, pending uploads
 * 3. upload-image-N: Upload each image to S3 (one step per image)
 * 4. finalize-product: Create ProductImage entities, set final status, publish events
 */
@Component
@WorkflowTypes(input = CreateProductInput::class, output = ProductResponse::class)
class CreateProductWorkflow(
    private val phase1Reserve: Phase1Reserve,
    private val phase2Upload: Phase2Upload,
    private val phase3Finalize: Phase3Finalize,
    private val productRepository: ProductRepository
) : Workflow<CreateProductInput, ProductResponse> {

    override val name = WorkflowConstants.CreateProduct.NAME

    override suspend fun execute(
        input: CreateProductInput,
        context: WorkflowContext
    ): WorkflowResult<ProductResponse> {
        logger.info { "Starting product creation workflow for: ${input.title} (handle: ${input.handle})" }

        val idempotencyKey = context.getMetadata("idempotencyKey") as? String
            ?: "create-product:${input.handle}"
        val correlationId = context.correlationId

        try {
            // ================================================================
            // STEP 1: VALIDATE INPUT
            // ================================================================
            val validateStep = createStep<CreateProductInput, Unit>(
                name = "validate-input",
                execute = { inp, _ ->
                    logger.debug { "Validating input for product: ${inp.handle}" }
                    ProductCreationRules.validateInput(inp).getOrThrow()
                    logger.info { "Input validation passed for: ${inp.handle}" }
                    StepResponse.of(Unit)
                }
            )
            validateStep.invoke(input, context)

            // ================================================================
            // STEP 2: RESERVE PRODUCT (Phase 1)
            // Creates: product, options, variants, prices, inventory, pending uploads
            // ================================================================
            val reserveStep = createStep<CreateProductInput, ReserveResult>(
                name = "reserve-product",
                execute = { inp, ctx ->
                    logger.debug { "Reserving product: ${inp.handle}" }

                    val result = phase1Reserve.execute(
                        input = inp,
                        idempotencyKey = idempotencyKey,
                        correlationId = correlationId
                    )

                    ctx.addMetadata("productId", result.productId)
                    ctx.addMetadata("executionId", result.executionId)

                    logger.info { "Reserved product: ${result.productId} with ${result.pendingUploadIds.size} pending uploads" }
                    StepResponse.of(result, result.productId)
                }
            )

            val reserveResult = try {
                reserveStep.invoke(input, context).data
            } catch (e: IdempotentCompletedException) {
                logger.info { "Idempotent hit for ${input.handle}: returning cached result" }
                return handleIdempotentCompleted(e.cachedPayload)
            } catch (e: IdempotentInProgressException) {
                logger.warn { "Workflow already in progress for ${input.handle}: ${e.executionId}" }
                return WorkflowResult.failure(
                    IllegalStateException("Product creation already in progress. Execution ID: ${e.executionId}")
                )
            } catch (e: IdempotentConflictException) {
                logger.warn { "Idempotency conflict for ${input.handle}" }
                return WorkflowResult.failure(
                    IllegalStateException("Concurrent request conflict. Please retry.")
                )
            }

            // ================================================================
            // STEP 3: UPLOAD IMAGES (one step per image)
            // Uses Phase2Upload which handles S3 operations
            // ================================================================
            val uploadResult = if (reserveResult.pendingUploadIds.isNotEmpty()) {
                // Create individual steps for each image upload
                val completedUploads = mutableListOf<CompletedUpload>()
                val failedUploads = mutableListOf<FailedUpload>()

                reserveResult.pendingUploadIds.forEachIndexed { index, uploadId ->
                    val uploadImageStep = createStep<String, CompletedUpload?>(
                        name = "upload-image-${index + 1}",
                        execute = { pendingId, ctx ->
                            logger.debug { "Uploading image $pendingId (${index + 1}/${reserveResult.pendingUploadIds.size})" }

                            // Use Phase2Upload for single image
                            val result = phase2Upload.uploadSingleImage(
                                productId = reserveResult.productId,
                                pendingUploadId = pendingId
                            ) { current, total, message, percent ->
                                ctx.publishStepProgress(
                                    stepName = "upload-image-${index + 1}",
                                    stepIndex = index + 3, // After validate and reserve
                                    progressCurrent = current,
                                    progressTotal = total,
                                    progressMessage = message,
                                    totalSteps = reserveResult.pendingUploadIds.size + 4
                                )
                            }

                            if (result != null) {
                                // Register compensation for S3 cleanup
                                ctx.pushCompensation("upload-image-${index + 1}") {
                                    phase2Upload.deleteUploadedImage(result.resultUrl)
                                }
                                logger.info { "Uploaded image ${index + 1}: ${result.resultUrl}" }
                            } else {
                                logger.warn { "Failed to upload image ${index + 1}" }
                            }

                            StepResponse.of(result)
                        }
                    )

                    val uploadedImage = uploadImageStep.invoke(uploadId, context).data
                    if (uploadedImage != null) {
                        completedUploads.add(uploadedImage)
                    } else {
                        failedUploads.add(FailedUpload(
                            uploadId = uploadId,
                            sourceUrl = "",
                            position = index,
                            errorMessage = "Upload failed",
                            isPermanent = false
                        ))
                    }
                }

                UploadResult(
                    productId = reserveResult.productId,
                    successCount = completedUploads.size,
                    failureCount = failedUploads.size,
                    permanentFailureCount = failedUploads.count { it.isPermanent },
                    completedUrls = completedUploads,
                    failedUploads = failedUploads
                )
            } else {
                // No images to upload
                UploadResult(
                    productId = reserveResult.productId,
                    successCount = 0,
                    failureCount = 0,
                    permanentFailureCount = 0,
                    completedUrls = emptyList(),
                    failedUploads = emptyList()
                )
            }

            // ================================================================
            // STEP 4: FINALIZE PRODUCT
            // Creates ProductImage entities, sets status, publishes events
            // ================================================================
            val finalizeStep = createStep<FinalizeStepInput, FinalizeResult>(
                name = "finalize-product",
                execute = { finInput, _ ->
                    logger.debug { "Finalizing product: ${finInput.productId}" }

                    val result = phase3Finalize.execute(
                        executionId = finInput.executionId,
                        productId = finInput.productId,
                        uploadResult = finInput.uploadResult,
                        correlationId = finInput.correlationId
                    )

                    logger.info { "Finalized product with status: ${result.productStatus}" }
                    StepResponse.of(result)
                }
            )

            val finalizeInput = FinalizeStepInput(
                executionId = reserveResult.executionId,
                productId = reserveResult.productId,
                uploadResult = uploadResult,
                correlationId = correlationId
            )

            val finalizeResult = try {
                finalizeStep.invoke(finalizeInput, context).data
            } catch (e: Exception) {
                logger.error(e) { "Finalize failed for product ${reserveResult.productId}" }
                context.runCompensations()
                throw e
            }

            // ================================================================
            // RETURN RESULT
            // ================================================================
            val product = productRepository.findById(reserveResult.productId)
                .orElseThrow { ProductNotFoundException(reserveResult.productId) }

            return when (finalizeResult.productStatus) {
                ProductStatus.FAILED -> {
                    logger.warn { "Product creation completed with FAILED status: ${reserveResult.productId}" }
                    WorkflowResult.failure(
                        ProductCreationFailedException(
                            reserveResult.productId,
                            finalizeResult.interventionReason ?: "Product creation failed"
                        )
                    )
                }
                ProductStatus.DRAFT -> {
                    logger.info { "Product created as DRAFT (no images): ${reserveResult.productId}" }
                    WorkflowResult.success(ProductResponse.from(product))
                }
                else -> {
                    logger.info { "Product created successfully: ${product.id} (${product.handle}) - status: ${product.status}" }
                    WorkflowResult.success(ProductResponse.from(product))
                }
            }

        } catch (e: ProductWorkflowException) {
            logger.warn { "Product creation failed (business rule): ${e.message}" }
            context.runCompensations()
            return WorkflowResult.failure(e)
        } catch (e: Exception) {
            logger.error(e) { "Product creation workflow failed unexpectedly: ${e.message}" }
            context.runCompensations()
            return WorkflowResult.failure(e)
        }
    }

    private fun handleIdempotentCompleted(cachedPayload: Map<String, Any?>): WorkflowResult<ProductResponse> {
        val productId = cachedPayload["productId"] as? String
            ?: return WorkflowResult.failure(IllegalStateException("Cached result missing productId"))

        val product = productRepository.findById(productId).orElse(null)
            ?: return WorkflowResult.failure(ProductNotFoundException(productId))

        return WorkflowResult.success(ProductResponse.from(product))
    }
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
