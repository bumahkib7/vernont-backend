package com.vernont.api.controller.admin

import com.vernont.application.product.ProductService
import com.vernont.domain.auth.UserContext
import com.vernont.domain.product.Product
import com.vernont.domain.product.dto.ProductResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowOptions
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.product.CreateProductInput
import com.vernont.workflow.flows.product.UpdateProductsWorkflowInput
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/products")
class AdminProductController(
    private val workflowEngine: WorkflowEngine,
    private val productService: ProductService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createProduct(
        @RequestBody request: CreateProductInput,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: UUID.randomUUID().toString()
        logger.info { "Received request to create product: ${request.title}, handle: ${request.handle}, correlationId: $correlationId" }

        val context = WorkflowContext()
        context.addMetadata("correlationId", correlationId)
        context.addMetadata("productTitle", request.title)

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CreateProduct.NAME,
            input = request,
            inputType = CreateProductInput::class,
            outputType = Product::class,
            context = context,
            options = WorkflowOptions(
                correlationId = correlationId,
                lockKey = request.handle.let { "product:create:$it" }
            )
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val product = result.data
                logger.info { "Product creation workflow succeeded for product ID: ${product.id}, correlationId: $correlationId" }
                ResponseEntity.status(HttpStatus.CREATED).body(product)
            }
            is WorkflowResult.Failure -> {
                logger.error(result.error) { "Product creation workflow failed for product: ${request.title}, handle: ${request.handle}, correlationId: $correlationId: ${result.error.message}" }
                throw result.error
            }
        }
    }

    @PutMapping("/{id}")
    suspend fun updateProduct(
        @PathVariable id: String,
        @RequestBody request: com.vernont.workflow.flows.product.UpdateProductInput,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: UUID.randomUUID().toString()
        logger.info { "Received request to update product: $id, correlationId: $correlationId" }

        val inputWithId = request.copy(id = id)

        val workflowInput = UpdateProductsWorkflowInput(
            products = listOf(inputWithId),
            correlationId = correlationId
        )

        val context = WorkflowContext()
        context.addMetadata("correlationId", correlationId)
        context.addMetadata("productId", id)

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.UpdateProduct.NAME,
            input = workflowInput,
            inputType = UpdateProductsWorkflowInput::class,
            outputType = List::class,
            context = context,
            options = WorkflowOptions(
                correlationId = correlationId,
                lockKey = "product:update:$id"
            )
        )

        return when (result) {
            is WorkflowResult.Success -> {
                @Suppress("UNCHECKED_CAST")
                val products = result.data as List<ProductResponse>
                val product = products.firstOrNull()

                if (product == null) {
                    logger.warn { "Product update returned no results for ID: $id" }
                    ResponseEntity.notFound().build()
                } else {
                    logger.info { "Product update workflow succeeded for product ID: ${product.id}, correlationId: $correlationId" }
                    ResponseEntity.ok(product)
                }
            }
            is WorkflowResult.Failure -> {
                logger.error(result.error) { "Product update workflow failed for product: $id, correlationId: $correlationId: ${result.error.message}" }
                throw result.error
            }
        }
    }

    @PutMapping
    suspend fun batchUpdateProducts(
        @RequestBody request: UpdateProductsWorkflowInput,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: UUID.randomUUID().toString()
        logger.info { "Received batch update request for products, correlationId: $correlationId" }

        val workflowInput = request.copy(correlationId = correlationId)

        val context = WorkflowContext()
        context.addMetadata("correlationId", correlationId)

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.UpdateProduct.NAME,
            input = workflowInput,
            inputType = UpdateProductsWorkflowInput::class,
            outputType = List::class,
            context = context,
            options = WorkflowOptions(
                correlationId = correlationId
            )
        )

        return when (result) {
            is WorkflowResult.Success -> {
                @Suppress("UNCHECKED_CAST")
                val products = result.data as List<ProductResponse>
                logger.info { "Batch product update workflow succeeded, updated ${products.size} products, correlationId: $correlationId" }
                ResponseEntity.ok(products)
            }
            is WorkflowResult.Failure -> {
                logger.error(result.error) { "Batch product update workflow failed, correlationId: $correlationId: ${result.error.message}" }
                throw result.error
            }
        }
    }

    @GetMapping
    @Operation(summary = "List products", description = "Get paginated list of products")
    fun listProducts(
        @RequestParam(name = "_start", defaultValue = "0") start: Int,
        @RequestParam(name = "_end", defaultValue = "10") end: Int
    ): ResponseEntity<Page<ProductResponse>> {
        val size = end - start
        val page = if (size > 0) start / size else 0
        val pageable: Pageable = PageRequest.of(page, size)
        val products = productService.listProducts(pageable)
        return ResponseEntity.ok(products)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product", description = "Get product by ID")
    fun getProduct(@PathVariable id: String): ResponseEntity<ProductResponse> {
        val product = productService.getProduct(id)
        return ResponseEntity.ok(product)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", description = "Soft delete a product")
    fun deleteProduct(@PathVariable id: String): ResponseEntity<Void> {
        productService.deleteProduct(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/draft")
    @Operation(summary = "Draft product", description = "Set product to draft status")
    fun unpublishProduct(@PathVariable id: String): ResponseEntity<ProductResponse> {
        val product = productService.unpublishProduct(id)
        return ResponseEntity.ok(product)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userContext: UserContext): ResponseEntity<UserContext> {
        return ResponseEntity.ok(userContext)
    }
}
