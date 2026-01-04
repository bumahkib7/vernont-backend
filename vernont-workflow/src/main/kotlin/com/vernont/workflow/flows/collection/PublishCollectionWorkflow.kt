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

data class PublishCollectionInput(
    val id: String
)

data class PublishCollectionOutput(
    val collection: ProductCollection
)

@Component
@WorkflowTypes(
    input = PublishCollectionInput::class,
    output = PublishCollectionOutput::class
)
class PublishCollectionWorkflow(
    private val collectionRepository: ProductCollectionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : Workflow<PublishCollectionInput, PublishCollectionOutput> {

    override val name = WorkflowConstants.CollectionLifecycle.PUBLISH_COLLECTION

    companion object {
        private const val STEP_LOAD_COLLECTION = "load-collection-to-publish"
        private const val STEP_VALIDATE = "validate-collection-before-publish"
        private const val STEP_PUBLISH = "set-collection-published"
        private const val STEP_PUBLISH_EVENT = "publish-collection-published-event"
    }

    @Transactional
    override suspend fun execute(
        input: PublishCollectionInput,
        context: WorkflowContext
    ): WorkflowResult<PublishCollectionOutput> {
        logger.info { "Starting publish collection workflow for: ${input.id}" }

        return try {
            val collection = loadCollection(input.id, context)
            val validated = validateCollection(collection, context)
            val published = publishCollection(validated, context)
            publishEvent(published, context)

            logger.info { "Collection published successfully: ${published.id}" }
            WorkflowResult.success(PublishCollectionOutput(published))
        } catch (e: Exception) {
            logger.error(e) { "Publish collection workflow failed: ${e.message}" }
            WorkflowResult.failure(e)
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
                ctx.addMetadata("originalPublished", collection.published)

                StepResponse.of(collection)
            },
            compensate = { _, _ -> /* No compensation needed for read operation */ }
        )
        return step.invoke(collectionId, context).data
    }

    private suspend fun validateCollection(
        collection: ProductCollection,
        context: WorkflowContext
    ): ProductCollection {
        val step = createStep<ProductCollection, ProductCollection>(
            name = STEP_VALIDATE,
            execute = { col, _ ->
                logger.debug { "Validating collection before publish: ${col.id}" }

                if (col.published) {
                    throw IllegalStateException("Collection ${col.id} is already published")
                }

                // Add any additional validation rules here
                // For example, check if collection has products, has valid handle, etc.

                StepResponse.of(col)
            },
            compensate = { _, _ -> /* No compensation needed for validation */ }
        )
        return step.invoke(collection, context).data
    }

    private suspend fun publishCollection(
        collection: ProductCollection,
        context: WorkflowContext
    ): ProductCollection {
        val step = createStep<ProductCollection, ProductCollection>(
            name = STEP_PUBLISH,
            execute = { col, ctx ->
                logger.debug { "Setting collection ${col.id} to published" }

                col.published = true
                val saved = collectionRepository.save(col)
                ctx.addMetadata("collectionId", saved.id!!) // Fixed Argument type mismatch

                StepResponse.of(saved, saved.id)
            },
            compensate = { _, ctx ->
                // Rollback to unpublished state
                val collectionId = ctx.getMetadata("collectionId") as? String
                val originalPublished = ctx.getMetadata("originalPublished") as? Boolean ?: false

                if (collectionId != null) {
                    try {
                        collectionRepository.findById(collectionId).ifPresent { col ->
                            col.published = originalPublished
                            collectionRepository.save(col)
                            logger.info { "Compensated: Restored collection $collectionId published state to $originalPublished" }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate collection publish for $collectionId" }
                    }
                }
            }
        )
        return step.invoke(collection, context).data
    }

    private suspend fun publishEvent(
        collection: ProductCollection,
        context: WorkflowContext
    ) {
        val step = createStep<ProductCollection, ProductCollection>(
            name = STEP_PUBLISH_EVENT,
            execute = { col, _ ->
                logger.debug { "Publishing collection-published event for: ${col.id}" }

                eventPublisher.publishEvent(
                    mapOf(
                        "eventType" to "product-collection.published",
                        "id" to col.id,
                        "handle" to col.handle,
                        "title" to col.title
                    )
                )

                StepResponse.of(col)
            },
            compensate = { _, _ -> /* Events are fire-and-forget */ }
        )
        step.invoke(collection, context)
    }
}