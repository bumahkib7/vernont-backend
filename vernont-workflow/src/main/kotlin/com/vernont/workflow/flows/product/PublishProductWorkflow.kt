// com.vernont.workflow.flows.product.PublishProductWorkflow.kt

package com.vernont.workflow.flows.product

import com.vernont.domain.product.Product
import com.vernont.domain.product.ProductStatus
import com.vernont.events.EventPublisher
import com.vernont.events.ProductPublished
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

data class PublishProductInput(
    val productId: String? = null,
    val handle: String? = null
)


data class PublishProductOutput(
    val id: String,
    val status: String,
    val handle: String?,
    val title: String?
)

@Component
@WorkflowTypes(input = PublishProductInput::class, output = PublishProductOutput::class)
class PublishProductWorkflow(
    private val productRepository: ProductRepository,
    private val eventPublisher: EventPublisher
) : Workflow<PublishProductInput, Product> {

    override val name: String = WorkflowConstants.ProductLifecycle.PUBLISH_PRODUCT

    override suspend fun execute(
        input: PublishProductInput,
        context: WorkflowContext
    ): WorkflowResult<Product> {
        logger.info { "Publishing product: id=${input.productId}, handle=${input.handle}" }

        return try {
            // Step 1: load product
            val loadStep = createStep<PublishProductInput, Product>(
                name = "load-product-to-publish",
                execute = { inp, _ ->
                    val product = when {
                        !inp.productId.isNullOrBlank() ->
                            productRepository.findById(inp.productId).orElse(null)
                        !inp.handle.isNullOrBlank() ->
                            productRepository.findByHandle(inp.handle)
                        else -> null
                    } ?: throw IllegalArgumentException("Product not found for id=${inp.productId}, handle=${inp.handle}")

                    StepResponse.of(product)
                }
            )

            // Step 2: validate business rules
            val validateStep = createStep<Product, Product>(
                name = "validate-product-before-publish",
                execute = { product, _ ->
                    if (product.status == ProductStatus.PUBLISHED) {
                        throw IllegalStateException("Product ${product.id} is already PUBLISHED")
                    }

                    // example validations – tweak as you like:
                    if (product.images.isEmpty()) {
                        throw IllegalStateException("Product ${product.id} cannot be published without at least one image")
                    }
                    if (product.variants.isEmpty()) {
                        throw IllegalStateException("Product ${product.id} cannot be published without variants")
                    }
                    val hasPrice = product.variants
                        .flatMap { it.prices }
                        .any()
                    if (!hasPrice) {
                        throw IllegalStateException("Product ${product.id} cannot be published without prices")
                    }

                    StepResponse.of(product)
                }
            )

            // Step 3: change status + persist
            val publishStep = createStep<Product, Product>(
                name = "set-product-status-published",
                execute = { product, _ ->
                    logger.info { "Setting product ${product.id} status to PUBLISHED" }
                    product.status = ProductStatus.PUBLISHED
                    val updated = productRepository.save(product)
                    StepResponse.of(updated)
                }
            )

            // Step 4: emit event
            val publishEventStep = createStep<Product, Unit>(
                name = "publish-product-published-event",
                execute = { product, _ ->
                    logger.debug { "Publishing ProductPublished event for product: ${product.id}" }
                    eventPublisher.publish(
                        ProductPublished(
                            aggregateId = product.id,
                            title = product.title,
                            handle = product.handle,
                            status = product.status.name
                        )
                    )

                    StepResponse.of(Unit)
                }
            )

            val loaded = loadStep.invoke(input, context).data
            val validated = validateStep.invoke(loaded, context).data
            val published = publishStep.invoke(validated, context).data
            publishEventStep.invoke(published, context)

            WorkflowResult.success(published)

        } catch (e: Exception) {
            logger.error(e) { "PublishProductWorkflow failed: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }
}
