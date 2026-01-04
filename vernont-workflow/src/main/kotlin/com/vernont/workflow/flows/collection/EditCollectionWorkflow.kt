package com.vernont.workflow.flows.collection

import com.vernont.domain.product.ProductCollection
import com.vernont.repository.product.ProductCollectionRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

data class EditCollectionInput(
    val id: String,
    val title: String? = null,
    val handle: String? = null,
    val metadata: Map<String, Any>? = null
)

data class EditCollectionOutput(
    val collection: ProductCollection
)

@Component
@WorkflowTypes(
    input = EditCollectionInput::class,
    output = EditCollectionOutput::class
)
class EditCollectionWorkflow(
    private val collectionRepository: ProductCollectionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : Workflow<EditCollectionInput, EditCollectionOutput> {

    override val name = WorkflowConstants.EditCollection.EDIT_COLLECTION

    companion object {
        private const val STEP_LOAD_COLLECTION = "load-collection"
        private const val STEP_VALIDATE_INPUT = "validate-edit-input"
        private const val STEP_UPDATE_COLLECTION = "update-collection"
        private const val STEP_PUBLISH_EVENT = "publish-edit-event"
    }

    @Transactional
    override suspend fun execute(
        input: EditCollectionInput,
        context: WorkflowContext
    ): WorkflowResult<EditCollectionOutput> {
        logger.info { "Starting collection edit workflow for: ${input.id}" }

        try {
            val existingCollection = loadCollection(input.id, context)
            val validatedInput = validateInput(input, existingCollection, context)
            val updatedCollection = updateCollection(validatedInput, existingCollection, context)
            publishEvent(updatedCollection, context)

            logger.info { "Collection updated successfully: ${updatedCollection.id}" }
            return WorkflowResult.success(EditCollectionOutput(updatedCollection))
        } catch (e: Exception) {
            logger.error(e) { "Collection edit workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

    private suspend fun loadCollection(
        collectionId: String,
        context: WorkflowContext
    ): ProductCollection {
        val step = createStep<String, ProductCollection>(
            name = STEP_LOAD_COLLECTION,
            execute = { id, ctx ->
                logger.debug { "Loading collection: $id" }
                
                val collection = collectionRepository.findById(id)
                    .orElseThrow { IllegalArgumentException("Collection not found: $id") }
                
                // Store original state for potential rollback
                ctx.addMetadata("originalTitle", collection.title)
                ctx.addMetadata("originalHandle", collection.handle)
                
                StepResponse.of(collection)
            },
            compensate = { _, _ -> /* No compensation needed for read operation */ }
        )
        return step.invoke(collectionId, context).data
    }

    private suspend fun validateInput(
        input: EditCollectionInput,
        existingCollection: ProductCollection,
        context: WorkflowContext
    ): EditCollectionInput {
        val step = createStep<EditCollectionInput, EditCollectionInput>(
            name = STEP_VALIDATE_INPUT,
            execute = { editInput, _ ->
                logger.debug { "Validating edit input for collection: ${editInput.id}" }

                // If handle is being updated, check for duplicates
                val newHandle = editInput.handle
                if (newHandle != null && newHandle != existingCollection.handle) {
                    if (collectionRepository.existsByHandleAndIdNot(newHandle, editInput.id)) {
                        throw IllegalArgumentException("Collection with handle '$newHandle' already exists")
                    }
                }

                StepResponse.of(editInput)
            },
            compensate = { _, _ -> /* No compensation needed for validation */ }
        )
        return step.invoke(input, context).data
    }

    private suspend fun updateCollection(
        input: EditCollectionInput,
        existingCollection: ProductCollection,
        context: WorkflowContext
    ): ProductCollection {
        val step = createStep<Pair<EditCollectionInput, ProductCollection>, ProductCollection>(
            name = STEP_UPDATE_COLLECTION,
            execute = { (editInput, collection), ctx ->
                logger.debug { "Updating collection entity: ${collection.id}" }

                // Apply updates only for non-null fields
                editInput.title?.let { collection.title = it }
                editInput.handle?.let { collection.handle = it }

                val saved = collectionRepository.save(collection)
                ctx.addMetadata("collectionId", saved.id!!) // Fixed Argument type mismatch
                StepResponse.of(saved, saved.id)
            },
            compensate = { _, ctx ->
                // Rollback to original values
                val collectionId = ctx.getMetadata("collectionId") as? String
                val originalTitle = ctx.getMetadata("originalTitle") as? String
                val originalHandle = ctx.getMetadata("originalHandle") as? String
                
                if (collectionId != null) {
                    try {
                        collectionRepository.findById(collectionId).ifPresent { collection ->
                            originalTitle?.let { collection.title = it }
                            originalHandle?.let { collection.handle = it }
                            collectionRepository.save(collection)
                            logger.info { "Compensated: Restored collection $collectionId to original state" }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate collection update for $collectionId" }
                    }
                }
            }
        )

        return step.invoke(Pair(input, existingCollection), context).data
    }

    private suspend fun publishEvent(
        collection: ProductCollection,
        context: WorkflowContext
    ) {
        val step = createStep<ProductCollection, ProductCollection>(
            name = STEP_PUBLISH_EVENT,
            execute = { col, _ ->
                logger.debug { "Publishing collection-updated event for: ${col.id}" }

                eventPublisher.publishEvent(
                    mapOf(
                        "eventType" to "product-collection.updated",
                        "id" to col.id
                    )
                )

                StepResponse.of(col)
            },
            compensate = { _, _ -> /* Events are fire-and-forget */ }
        )
        step.invoke(collection, context)
    }
}