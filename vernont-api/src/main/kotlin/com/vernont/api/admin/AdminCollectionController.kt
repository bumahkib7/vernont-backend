package com.vernont.api.admin

import com.vernont.api.dto.admin.*
import com.vernont.repository.product.ProductCollectionRepository
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
    private val collectionRepository: ProductCollectionRepository
) {

    @GetMapping
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
                ResponseEntity.ok(
                    AdminCollectionResponse(
                        collection = AdminCollection.from(result.data.collection)
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
                ResponseEntity.ok(
                    AdminCollectionResponse(
                        collection = AdminCollection.from(result.data.collection)
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
                ResponseEntity.ok(
                    AdminCollectionResponse(
                        collection = AdminCollection.from(result.data.collection)
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
}
