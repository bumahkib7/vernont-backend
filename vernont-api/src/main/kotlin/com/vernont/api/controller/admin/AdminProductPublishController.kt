// com.vernont.api.controller.AdminProductPublishController.kt

package com.vernont.api.controller.admin

import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowOptions
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.product.PublishProductInput
import com.vernont.workflow.flows.product.PublishProductOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

private val publishLogger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/products")
class AdminProductPublishController(
    private val workflowEngine: WorkflowEngine
) {

    @PostMapping("/{id}/publish")
    @ResponseStatus(HttpStatus.OK)
    suspend fun publishProduct(
        @PathVariable id: String,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: UUID.randomUUID().toString()
        val context = WorkflowContext().apply {
            addMetadata("correlationId", correlationId)
            addMetadata("productId", id)
        }

        val input = PublishProductInput(productId = id)

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.ProductLifecycle.PUBLISH_PRODUCT,
            input = input,
            inputType = PublishProductInput::class,
            outputType = PublishProductOutput::class,
            context = context,
            options = WorkflowOptions(
                correlationId = correlationId,
                lockKey = "product:publish:$id"
            )
        )

        return when (result) {
            is WorkflowResult.Success -> {
                publishLogger.info { "Product published successfully id=${result.data.id}, correlationId=$correlationId" }
                ResponseEntity.ok(result.data)
            }

            is WorkflowResult.Failure -> {
                publishLogger.error(result.error) {
                    "Failed to publish product id=$id, correlationId=$correlationId: ${result.error.message}"
                }
                throw result.error
            }
        }
    }
}


class ProductAlreadyPublishedException(
    val productId: String
) : RuntimeException("Product $productId is already PUBLISHED")
