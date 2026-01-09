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
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Phased CreateProductWorkflow - Production-Safe Implementation
 *
 * This workflow uses a three-phase architecture with proper WorkflowStep integration:
 *
 * Step 1 - ReserveProductStep (Phase 1): Single short DB transaction (~50-100ms)
 *   - Validates idempotency
 *   - Validates all business rules
 *   - Creates product, options, variants, prices, inventory
 *   - Creates pending image upload records
 *   - Queues ProductCreationStarted outbox event
 *   - Compensation: None (DB rollback handles failure)
 *
 * Step 2 - UploadImagesStep (Phase 2): External I/O (5-30+ seconds)
 *   - Uploads images to S3 outside any transaction
 *   - Updates pending upload records individually
 *   - Reports progress via WebSocket
 *   - Compensation: Deletes uploaded S3 objects (external side effect)
 *
 * Step 3 - FinalizeProductStep (Phase 3): Single short DB transaction (~50-100ms)
 *   - Creates ProductImage entities from completed uploads
 *   - Sets product status to READY or FAILED
 *   - Cleans up pending upload records
 *   - Queues ProductCreationCompleted/Failed event
 *   - Compensation: None (DB rollback handles failure)
 *
 * Key features:
 *   - Idempotency: Safe to retry with same idempotency key
 *   - Short transactions: No external I/O inside DB transactions
 *   - Proper compensation: Only S3 uploads have compensation (external effects)
 *   - Step events: Uses WorkflowStep for consistent event publishing
 *   - Cleanup jobs: Automatic cleanup of failed products
 *   - Human intervention: Edge cases go to review queue
 */
@Component
@WorkflowTypes(input = CreateProductInput::class, output = ProductResponse::class)
class CreateProductWorkflow(
    private val reserveStep: ReserveProductStep,
    private val uploadStep: UploadImagesStep,
    private val finalizeStep: FinalizeProductStep,
    private val phase3Finalize: Phase3Finalize,
    private val productRepository: ProductRepository
) : Workflow<CreateProductInput, ProductResponse> {

    override val name = WorkflowConstants.CreateProduct.NAME

    override suspend fun execute(
        input: CreateProductInput,
        context: WorkflowContext
    ): WorkflowResult<ProductResponse> {
        logger.info { "Starting phased product creation workflow for product: ${input.title}" }

        // Extract idempotency key from context or generate from handle
        val idempotencyKey = context.getMetadata("idempotencyKey") as? String
            ?: "create-product:${input.handle}"

        val correlationId = context.getMetadata("correlationId") as? String

        try {
            // ================================================================
            // STEP 1: RESERVE (Short DB Transaction)
            // Uses WorkflowStep for event publishing and logging
            // ================================================================
            val reserveInput = ReserveStepInput(
                productInput = input,
                idempotencyKey = idempotencyKey,
                correlationId = correlationId
            )

            val reserveResponse = try {
                reserveStep.invoke(reserveInput, context)
            } catch (e: IdempotentCompletedException) {
                // Duplicate request - return cached result
                logger.info { "Idempotent hit for ${input.handle}: returning cached result" }
                return handleIdempotentCompleted(e.cachedPayload)
            } catch (e: IdempotentInProgressException) {
                // Another request is in progress
                logger.warn { "Workflow already in progress for ${input.handle}: ${e.executionId}" }
                return WorkflowResult.failure(
                    IllegalStateException("Product creation already in progress. Execution ID: ${e.executionId}")
                )
            } catch (e: IdempotentConflictException) {
                // Lost race condition
                logger.warn { "Idempotency conflict for ${input.handle}" }
                return WorkflowResult.failure(
                    IllegalStateException("Concurrent request conflict. Please retry.")
                )
            }

            val reserveResult = reserveResponse.data
            logger.info { "Step 1 completed: productId=${reserveResult.productId}, pendingUploads=${reserveResult.pendingUploadIds.size}" }

            // ================================================================
            // STEP 2: UPLOAD (External I/O - No Transaction)
            // Uses WorkflowStep with S3 compensation
            // ================================================================
            val uploadInput = UploadStepInput(
                productId = reserveResult.productId,
                pendingUploadIds = reserveResult.pendingUploadIds
            )

            val uploadResponse = try {
                uploadStep.invoke(uploadInput, context)
            } catch (e: Exception) {
                // Phase 2 failed completely - run compensations and finalize with failure
                logger.error(e) { "Step 2 failed completely for product ${reserveResult.productId}" }

                // Run compensations (deletes S3 uploads)
                context.runCompensations()

                // Finalize with failure
                phase3Finalize.handleCompleteFailure(
                    executionId = reserveResult.executionId,
                    productId = reserveResult.productId,
                    error = "Image upload phase failed: ${e.message}",
                    correlationId = correlationId
                )

                return WorkflowResult.failure(
                    ProductCreationFailedException(
                        reserveResult.productId,
                        "Image upload failed: ${e.message}"
                    )
                )
            }

            val uploadResult = uploadResponse.data
            logger.info {
                "Step 2 completed: productId=${reserveResult.productId}, " +
                "success=${uploadResult.successCount}, failures=${uploadResult.failureCount}"
            }

            // ================================================================
            // STEP 3: FINALIZE (Short DB Transaction)
            // Uses WorkflowStep for event publishing
            // ================================================================
            val finalizeInput = FinalizeStepInput(
                executionId = reserveResult.executionId,
                productId = reserveResult.productId,
                uploadResult = uploadResult,
                correlationId = correlationId
            )

            val finalizeResponse = try {
                finalizeStep.invoke(finalizeInput, context)
            } catch (e: Exception) {
                // Finalize failed - run compensations (S3 cleanup)
                logger.error(e) { "Step 3 failed for product ${reserveResult.productId}" }
                context.runCompensations()
                throw e
            }

            val finalizeResult = finalizeResponse.data
            logger.info {
                "Step 3 completed: productId=${reserveResult.productId}, " +
                "status=${finalizeResult.productStatus}, images=${finalizeResult.imageCount}"
            }

            // ================================================================
            // RETURN RESULT
            // ================================================================
            if (finalizeResult.productStatus == ProductStatus.FAILED) {
                logger.warn { "Product creation completed with FAILED status: ${reserveResult.productId}" }
                // Don't run compensations - the product exists but needs manual attention
                return WorkflowResult.failure(
                    ProductCreationFailedException(
                        reserveResult.productId,
                        finalizeResult.interventionReason ?: "Product creation failed"
                    )
                )
            }

            // Fetch the final product for response
            val product = productRepository.findById(reserveResult.productId)
                .orElseThrow { ProductNotFoundException(reserveResult.productId) }

            logger.info { "Product created successfully: ${product.id} (${product.handle})" }
            return WorkflowResult.success(ProductResponse.from(product))

        } catch (e: ProductWorkflowException) {
            // Known business rule violation - run compensations
            logger.warn { "Product creation failed (business rule): ${e.message}" }
            context.runCompensations()
            return WorkflowResult.failure(e)

        } catch (e: Exception) {
            // Unexpected error - run compensations
            logger.error(e) { "Product creation workflow failed unexpectedly: ${e.message}" }
            context.runCompensations()
            return WorkflowResult.failure(e)
        }
    }

    /**
     * Handle idempotent completion - return cached result
     */
    private fun handleIdempotentCompleted(cachedPayload: Map<String, Any?>): WorkflowResult<ProductResponse> {
        val productId = cachedPayload["productId"] as? String
            ?: return WorkflowResult.failure(IllegalStateException("Cached result missing productId"))

        val product = productRepository.findById(productId).orElse(null)
            ?: return WorkflowResult.failure(ProductNotFoundException(productId))

        return WorkflowResult.success(ProductResponse.from(product))
    }
}
