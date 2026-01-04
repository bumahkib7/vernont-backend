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

@Component
@WorkflowTypes(
    input = CreateCollectionInput::class,
    output = CreateCollectionOutput::class
)
class CreateCollectionWorkflow(
    private val collectionRepository: ProductCollectionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : Workflow<CreateCollectionInput, CreateCollectionOutput> {

    override val name = WorkflowConstants.CreateCollection.CREATE_COLLECTION

    companion object {
        private const val STEP_VALIDATE_INPUT = "validate-collection-input"
        private const val STEP_CREATE_COLLECTION = "create-collection"
        private const val STEP_PUBLISH_EVENT = "publish-event"
    }

    @Transactional
    override suspend fun execute(
        input: CreateCollectionInput,
        context: WorkflowContext
    ): WorkflowResult<CreateCollectionOutput> {
        logger.info { "Starting collection creation workflow for: ${input.title}" }

        val validatedInput = validateInput(input, context)
        val collection = createCollection(validatedInput, context)
        publishEvent(collection, context)

        logger.info { "Collection created successfully: ${collection.id}" }
        return WorkflowResult.success(CreateCollectionOutput(collection))
    }

    private suspend fun validateInput(
        input: CreateCollectionInput,
        context: WorkflowContext
    ): CreateCollectionInput {
        val step = createStep<CreateCollectionInput, CreateCollectionInput>(
            name = STEP_VALIDATE_INPUT,
            execute = { collectionInput, _ ->
                logger.debug { "Validating input for collection: ${collectionInput.title}" }

                val handle = collectionInput.handle ?: collectionInput.title.lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')

                if (collectionRepository.existsByHandle(handle)) {
                    throw IllegalArgumentException("Collection with handle '$handle' already exists")
                }

                StepResponse.of(collectionInput.copy(handle = handle))
            },
            compensate = { _, _ -> /* No compensation needed */ }
        )
        return step.invoke(input, context).data
    }

    private suspend fun createCollection(
        input: CreateCollectionInput,
        context: WorkflowContext
    ): ProductCollection {
        val step = createStep<CreateCollectionInput, ProductCollection>(
            name = STEP_CREATE_COLLECTION,
            execute = { collectionInput, ctx ->
                logger.debug { "Creating collection entity: ${collectionInput.title}" }

                val collection = ProductCollection().apply {
                    title = collectionInput.title
                    handle = collectionInput.handle!!
                    published = false
                }

                val saved = collectionRepository.save(collection)
                ctx.addMetadata("collectionId", saved.id!!) // Fixed Argument type mismatch
                StepResponse.of(saved, saved.id)
            },
            compensate = { _, ctx ->
                val collectionId = ctx.getCompensationData<String>(STEP_CREATE_COLLECTION)
                if (collectionId != null) {
                    try {
                        collectionRepository.deleteById(collectionId)
                        logger.info { "Compensated: Deleted collection $collectionId" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate deleting collection $collectionId" }
                    }
                }
            }
        )

        return step.invoke(input, context).data
    }

    private suspend fun publishEvent(
        collection: ProductCollection,
        context: WorkflowContext
    ) {
        val step = createStep<ProductCollection, ProductCollection>(
            name = STEP_PUBLISH_EVENT,
            execute = { col, _ ->
                logger.debug { "Publishing collection-created event for: ${col.id}" }

                eventPublisher.publishEvent(
                    mapOf(
                        "eventType" to "product-collection.created",
                        "id" to col.id
                    )
                )

                StepResponse.of(col)
            },
            compensate = { _, _ -> /* Events are fire-and-forget */ }
        )
        step.invoke(collection, context)
    }

    private inline fun <reified T> WorkflowContext.getCompensationData(stepName: String): T? {
        val compensationKey = "compensationData:$stepName"
        return this.getMetadata(compensationKey) as? T
    }
}