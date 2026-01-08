package com.vernont.api.admin

import com.vernont.api.dto.admin.*
import com.vernont.repository.product.ProductCollectionRepository
import com.vernont.repository.product.ProductRepository
import org.springframework.transaction.annotation.Transactional
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.flows.collection.CreateCollectionInput
import com.vernont.workflow.flows.collection.CreateCollectionOutput
import com.vernont.workflow.flows.collection.EditCollectionInput
import com.vernont.workflow.flows.collection.EditCollectionOutput
import com.vernont.workflow.flows.collection.PublishCollectionInput
import com.vernont.workflow.flows.collection.PublishCollectionOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/collections")
class AdminCollectionController(
    private val workflowEngine: WorkflowEngine,
    private val collectionRepository: ProductCollectionRepository,
    private val productRepository: ProductRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun listCollections(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<AdminCollectionsResponse> {
        logger.info { "Listing collections with offset=$offset, limit=$limit" }

        val pageRequest = PageRequest.of(offset / limit, limit)
        val page = collectionRepository.findAll(pageRequest)

        val collections = page.content.map { AdminCollection.from(it) }

        return ResponseEntity.ok(
            AdminCollectionsResponse(
                collections = collections,
                count = page.totalElements.toInt(),
                offset = offset,
                limit = limit
            )
        )
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    fun getCollection(@PathVariable id: String): ResponseEntity<AdminCollectionResponse> {
        logger.info { "Getting collection: $id" }

        val collection = collectionRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Collection not found: $id") }

        return ResponseEntity.ok(
            AdminCollectionResponse(
                collection = AdminCollection.from(collection)
            )
        )
    }

    @PostMapping
    suspend fun createCollection(
        @RequestBody request: CreateCollectionRequest
    ): ResponseEntity<AdminCollectionResponse> {
        logger.info { "Creating collection: ${request.title}" }

        val input = CreateCollectionInput(
            title = request.title,
            handle = request.handle,
            metadata = request.metadata
        )

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CreateCollection.CREATE_COLLECTION,
            input = input,
            inputType = CreateCollectionInput::class,
            outputType = CreateCollectionOutput::class
        )

        return when (result) {
            is com.vernont.workflow.engine.WorkflowResult.Success -> {
                // Reload collection to get full entity for AdminCollection DTO
                val collection = collectionRepository.findById(result.data.collection.id).get()
                ResponseEntity.ok(
                    AdminCollectionResponse(
                        collection = AdminCollection.from(collection)
                    )
                )
            }
            is com.vernont.workflow.engine.WorkflowResult.Failure -> {
                throw result.error
            }
        }
    }

    @PutMapping("/{id}")
    suspend fun updateCollection(
        @PathVariable id: String,
        @RequestBody request: UpdateCollectionRequest
    ): ResponseEntity<AdminCollectionResponse> {
        logger.info { "Updating collection: $id" }

        val input = EditCollectionInput(
            id = id,
            title = request.title,
            handle = request.handle,
            metadata = request.metadata
        )

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.EditCollection.EDIT_COLLECTION,
            input = input,
            inputType = EditCollectionInput::class,
            outputType = EditCollectionOutput::class
        )

        return when (result) {
            is com.vernont.workflow.engine.WorkflowResult.Success -> {
                // Reload collection to get full entity for AdminCollection DTO
                val collection = collectionRepository.findById(result.data.collection.id).get()
                ResponseEntity.ok(
                    AdminCollectionResponse(
                        collection = AdminCollection.from(collection)
                    )
                )
            }
            is com.vernont.workflow.engine.WorkflowResult.Failure -> {
                throw result.error
            }
        }
    }

    @PostMapping("/{id}/publish")
    suspend fun publishCollection(
        @PathVariable id: String
    ): ResponseEntity<AdminCollectionResponse> {
        logger.info { "Publishing collection: $id" }

        val input = PublishCollectionInput(id = id)

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CollectionLifecycle.PUBLISH_COLLECTION,
            input = input,
            inputType = PublishCollectionInput::class,
            outputType = PublishCollectionOutput::class
        )

        return when (result) {
            is com.vernont.workflow.engine.WorkflowResult.Success -> {
                // Reload collection to get full entity for AdminCollection DTO
                val collection = collectionRepository.findById(result.data.collection.id).get()
                ResponseEntity.ok(
                    AdminCollectionResponse(
                        collection = AdminCollection.from(collection)
                    )
                )
            }
            is com.vernont.workflow.engine.WorkflowResult.Failure -> {
                throw result.error
            }
        }
    }

    @DeleteMapping("/{id}")
    fun deleteCollection(@PathVariable id: String): ResponseEntity<DeleteCollectionResponse> {
        logger.info { "Deleting collection: $id" }

        if (!collectionRepository.existsById(id)) {
            throw IllegalArgumentException("Collection not found: $id")
        }

        collectionRepository.deleteById(id)

        return ResponseEntity.ok(
            DeleteCollectionResponse(
                id = id,
                deleted = true
            )
        )
    }

    @PostMapping("/{id}/products")
    @Transactional
    fun manageCollectionProducts(
        @PathVariable id: String,
        @RequestBody request: ManageProductsRequest
    ): ResponseEntity<AdminCollectionResponse> {
        logger.info { "Managing products for collection: $id, add=${request.add?.size ?: 0}, remove=${request.remove?.size ?: 0}" }

        val collection = collectionRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Collection not found: $id") }

        // Add products to collection
        request.add?.forEach { productId ->
            val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            if (product != null) {
                product.collection = collection
                productRepository.save(product)
                logger.debug { "Added product $productId to collection $id" }
            } else {
                logger.warn { "Product $productId not found, skipping" }
            }
        }

        // Remove products from collection
        request.remove?.forEach { productId ->
            val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            if (product != null && product.collection?.id == id) {
                product.collection = null
                productRepository.save(product)
                logger.debug { "Removed product $productId from collection $id" }
            }
        }

        // Reload collection with updated products
        val updatedCollection = collectionRepository.findById(id).get()

        return ResponseEntity.ok(
            AdminCollectionResponse(
                collection = AdminCollection.from(updatedCollection)
            )
        )
    }

    @GetMapping("/{id}/products")
    fun getCollectionProducts(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<CollectionProductsResponse> {
        logger.info { "Getting products for collection: $id" }

        val collection = collectionRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Collection not found: $id") }

        val products = collection.products
            .filter { it.deletedAt == null }
            .drop(offset)
            .take(limit)
            .map { product ->
                CollectionProduct(
                    id = product.id,
                    title = product.title,
                    handle = product.handle,
                    thumbnail = product.thumbnail,
                    status = product.status.name
                )
            }

        return ResponseEntity.ok(
            CollectionProductsResponse(
                products = products,
                count = collection.products.count { it.deletedAt == null },
                offset = offset,
                limit = limit
            )
        )
    }
}
