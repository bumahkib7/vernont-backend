package com.vernont.api.controller.admin

import com.vernont.application.product.ProductService
import com.vernont.domain.auth.UserContext
import com.vernont.domain.product.Product
import com.vernont.domain.product.dto.CreateProductImageRequest
import com.vernont.domain.product.dto.CreateProductOptionRequest
import com.vernont.domain.product.dto.ProductResponse
import com.vernont.domain.product.dto.ProductSummaryResponse
import com.vernont.domain.product.dto.ReorderImagesRequest
import com.vernont.domain.product.dto.SetThumbnailRequest
import com.vernont.domain.product.dto.UpdateProductImageRequest
import com.vernont.domain.product.dto.UpdateProductOptionRequest
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowOptions
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.product.CreateProductInput
import com.vernont.workflow.flows.product.UpdateProductsWorkflowInput
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.runBlocking
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
    fun createProduct(
        @RequestBody request: CreateProductInput,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> = runBlocking {
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

        return@runBlocking when (result) {
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
    fun updateProduct(
        @PathVariable id: String,
        @RequestBody request: com.vernont.workflow.flows.product.UpdateProductInput,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> = runBlocking {
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

        return@runBlocking when (result) {
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
    fun batchUpdateProducts(
        @RequestBody request: UpdateProductsWorkflowInput,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> = runBlocking {
        val correlationId = requestId ?: UUID.randomUUID().toString()
        logger.info { "Received batch update request for products, correlationId: $correlationId" }

        val workflowInput = request.copy(correlationId = correlationId)

        val context = WorkflowContext()
        context.addMetadata("correlationId", correlationId)

        val result = workflowEngine.execute(workflowName = WorkflowConstants.UpdateProduct.NAME, input = workflowInput, inputType = UpdateProductsWorkflowInput::class, outputType = List::class, context = context, options = WorkflowOptions(
                correlationId = correlationId
            ))

        return@runBlocking when (result) {
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
    ): ResponseEntity<Page<ProductSummaryResponse>> {
        val size = end - start
        val page = if (size > 0) start / size else 0
        val pageable: Pageable = PageRequest.of(page, size)
        val products = productService.listProductSummaries(pageable)
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

    // Option endpoints
    @PostMapping("/{id}/options")
    @Operation(summary = "Add option", description = "Add option to product")
    fun addOption(
        @PathVariable id: String,
        @RequestBody request: CreateProductOptionRequest
    ): ResponseEntity<ProductResponse> {
        val product = productService.addOption(id, request)
        return ResponseEntity.ok(product)
    }

    @PutMapping("/{id}/options/{optionId}")
    @Operation(summary = "Update option", description = "Update product option")
    fun updateOption(
        @PathVariable id: String,
        @PathVariable optionId: String,
        @RequestBody request: UpdateProductOptionRequest
    ): ResponseEntity<ProductResponse> {
        val product = productService.updateOption(id, optionId, request)
        return ResponseEntity.ok(product)
    }

    @DeleteMapping("/{id}/options/{optionId}")
    @Operation(summary = "Delete option", description = "Delete product option")
    fun deleteOption(
        @PathVariable id: String,
        @PathVariable optionId: String
    ): ResponseEntity<ProductResponse> {
        val product = productService.deleteOption(id, optionId)
        return ResponseEntity.ok(product)
    }

    // Image endpoints
    @PostMapping("/{id}/images")
    @Operation(summary = "Add image", description = "Add image to product")
    fun addImage(
        @PathVariable id: String,
        @RequestBody request: CreateProductImageRequest
    ): ResponseEntity<ProductResponse> {
        val product = productService.addImage(id, request)
        return ResponseEntity.ok(product)
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @Operation(summary = "Delete image", description = "Delete product image")
    fun deleteImage(
        @PathVariable id: String,
        @PathVariable imageId: String
    ): ResponseEntity<ProductResponse> {
        val product = productService.deleteImage(id, imageId)
        return ResponseEntity.ok(product)
    }

    @PutMapping("/{id}/images/{imageId}")
    @Operation(summary = "Update image", description = "Update image properties (position, alt text)")
    fun updateImage(
        @PathVariable id: String,
        @PathVariable imageId: String,
        @RequestBody request: UpdateProductImageRequest
    ): ResponseEntity<ProductResponse> {
        val product = productService.updateImage(id, imageId, request)
        return ResponseEntity.ok(product)
    }

    @PostMapping("/{id}/images/reorder")
    @Operation(summary = "Reorder images", description = "Reorder product images. First image becomes thumbnail.")
    fun reorderImages(
        @PathVariable id: String,
        @RequestBody request: ReorderImagesRequest
    ): ResponseEntity<ProductResponse> {
        val product = productService.reorderImages(id, request)
        return ResponseEntity.ok(product)
    }

    @PutMapping("/{id}/thumbnail")
    @Operation(summary = "Set thumbnail", description = "Set product thumbnail by image ID or URL")
    fun setThumbnail(
        @PathVariable id: String,
        @RequestBody request: SetThumbnailRequest
    ): ResponseEntity<ProductResponse> {
        val product = productService.setThumbnail(id, request)
        return ResponseEntity.ok(product)
    }
}
