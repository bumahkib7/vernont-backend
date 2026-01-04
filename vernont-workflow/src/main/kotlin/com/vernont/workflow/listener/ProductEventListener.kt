package com.vernont.workflow.listener

import com.vernont.events.ProductCreatedEvent
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.marketing.NewArrivalsInput
import com.vernont.workflow.flows.marketing.NewArrivalsOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class ProductEventListener(
    private val workflowEngine: WorkflowEngine
) {

    @EventListener
    fun handleProductCreated(event: ProductCreatedEvent) = runBlocking {
        logger.info { "Handling ProductCreated event for new arrivals: ${event.productId}" }

        // Only trigger new arrivals if product has a brand
        if (event.brandId == null) {
            logger.debug { "Product ${event.productId} has no brand, skipping new arrivals alert" }
            return@runBlocking
        }

        try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.Marketing.NEW_ARRIVALS_ALERT,
                input = NewArrivalsInput(productId = event.productId),
                inputType = NewArrivalsInput::class,
                outputType = NewArrivalsOutput::class,
                context = WorkflowContext()
            )

            when (result) {
                is WorkflowResult.Success -> {
                    logger.info { "New arrivals alerts completed: ${result.data}" }
                }
                is WorkflowResult.Failure -> {
                    logger.error(result.error) { "New arrivals workflow failed for product: ${event.productId}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to trigger new arrivals workflow for product: ${event.productId}" }
        }
    }
}
