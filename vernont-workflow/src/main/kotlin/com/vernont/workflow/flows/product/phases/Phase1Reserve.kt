package com.vernont.workflow.flows.product.phases

import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.domain.inventory.InventoryItem
import com.vernont.domain.inventory.InventoryLevel
import com.vernont.domain.outbox.OutboxEvent
import com.vernont.domain.product.*
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.StockLocationRepository
import com.vernont.repository.outbox.OutboxEventRepository
import com.vernont.repository.product.*
import com.vernont.workflow.domain.WorkflowExecution
import com.vernont.workflow.domain.WorkflowExecutionStatus
import com.vernont.workflow.flows.product.CreateProductInput
import com.vernont.workflow.flows.product.rules.*
import com.vernont.workflow.repository.WorkflowExecutionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

private val logger = KotlinLogging.logger {}

/**
 * Result of Phase 1: Reserve
 */
data class ReserveResult(
    val executionId: String,
    val productId: String,
    val pendingUploadIds: List<String>,
    val variantCount: Int
)

/**
 * Phase 1: Reserve - Single short DB transaction
 *
 * Creates the product, variants, options, prices, and inventory in a single transaction.
 * Also creates pending image upload records and queues an outbox event.
 *
 * This phase:
 * - Validates idempotency (first-writer-wins)
 * - Validates all business rules
 * - Creates all DB entities
 * - Queues ProductCreationStarted event
 *
 * Transaction boundary: ~50-100ms
 */
@Component
class Phase1Reserve(
    private val productRepository: ProductRepository,
    private val productOptionRepository: ProductOptionRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val pendingImageUploadRepository: PendingImageUploadRepository,
    private val workflowExecutionRepository: WorkflowExecutionRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        const val WORKFLOW_NAME = "create-product"
        const val MAX_SKU_GENERATION_ATTEMPTS = 3
    }

    /**
     * Execute Phase 1 in a single transaction
     */
    fun execute(input: CreateProductInput, idempotencyKey: String, correlationId: String? = null): ReserveResult {
        logger.info { "Phase1Reserve: Starting for handle=${input.handle}, idempotencyKey=$idempotencyKey" }

        return transactionTemplate.execute { status ->
            try {
                executeInternal(input, idempotencyKey, correlationId)
            } catch (e: Exception) {
                logger.error(e) { "Phase1Reserve: Failed for handle=${input.handle}" }
                status.setRollbackOnly()
                throw e
            }
        } ?: throw IllegalStateException("Transaction returned null")
    }

    private fun executeInternal(
        input: CreateProductInput,
        idempotencyKey: String,
        correlationId: String?
    ): ReserveResult {

        // ====================================================================
        // 1. IDEMPOTENCY CHECK (with pessimistic lock)
        // ====================================================================
        val existingExecution = workflowExecutionRepository.findByIdempotencyKeyForUpdate(
            idempotencyKey, WORKFLOW_NAME
        )

        if (existingExecution != null) {
            when (existingExecution.status) {
                WorkflowExecutionStatus.COMPLETED -> {
                    logger.info { "Idempotent hit: returning cached result for $idempotencyKey" }
                    throw IdempotentCompletedException(existingExecution.resultPayload ?: emptyMap())
                }
                WorkflowExecutionStatus.FAILED, WorkflowExecutionStatus.CLEANED_UP -> {
                    // Allow retry after failure/cleanup
                    logger.info { "Retrying after previous failure for $idempotencyKey" }
                    existingExecution.status = WorkflowExecutionStatus.RUNNING
                    existingExecution.retryCount++
                    existingExecution.errorMessage = null
                    workflowExecutionRepository.save(existingExecution)
                }
                WorkflowExecutionStatus.RUNNING -> {
                    logger.warn { "Workflow already in progress: ${existingExecution.id}" }
                    throw IdempotentInProgressException(existingExecution.id)
                }
                else -> {
                    throw IdempotentInProgressException(existingExecution.id)
                }
            }
        }

        // Create new execution record
        val execution = existingExecution ?: WorkflowExecution.createIdempotent(
            workflowName = WORKFLOW_NAME,
            idempotencyKey = idempotencyKey,
            inputData = objectMapper.writeValueAsString(input),
            correlationId = correlationId
        )

        try {
            workflowExecutionRepository.saveAndFlush(execution)
        } catch (e: DataIntegrityViolationException) {
            // Lost race on insert - another request won
            logger.warn { "Lost idempotency race for $idempotencyKey" }
            throw IdempotentConflictException(idempotencyKey)
        }

        // ====================================================================
        // 2. VALIDATE BUSINESS RULES
        // ====================================================================
        ProductCreationRules.validateInput(input).onFailure { throw it }

        // ====================================================================
        // 3. CHECK FOR HANDLE CONFLICT
        // ====================================================================
        val existingProduct = productRepository.findByHandle(input.handle)
        if (existingProduct != null) {
            throw ProductHandleConflictException(input.handle)
        }

        // ====================================================================
        // 4. CHECK FOR SKU CONFLICTS
        // ====================================================================
        val inputSkus = input.variants.mapNotNull { it.sku }.filter { it.isNotBlank() }
        for (sku in inputSkus) {
            if (inventoryItemRepository.findBySkuAndDeletedAtIsNull(sku) != null) {
                throw SkuConflictException(sku)
            }
        }

        // ====================================================================
        // 5. GET DEFAULT STOCK LOCATION (if needed)
        // ====================================================================
        val needsInventory = input.variants.any { it.manageInventory }
        val defaultLocation = if (needsInventory) {
            stockLocationRepository.findByDeletedAtIsNull()
                .minByOrNull { it.priority }
                ?: throw NoStockLocationException(
                    "At least one variant has manageInventory=true but no StockLocation exists. Create a stock location first."
                )
        } else null

        // ====================================================================
        // 6. CREATE PRODUCT
        // ====================================================================
        val product = Product().apply {
            title = input.title
            description = input.description
            handle = input.handle
            status = ProductStatus.PENDING_ASSETS
            shippingProfileId = input.shippingProfileId
        }

        val savedProduct = try {
            productRepository.saveAndFlush(product)
        } catch (e: DataIntegrityViolationException) {
            if (e.message?.contains("handle") == true || e.message?.contains("uq_product_handle") == true) {
                throw ProductHandleConflictException(input.handle)
            }
            throw e
        }

        logger.debug { "Created product ${savedProduct.id} with handle ${savedProduct.handle}" }

        // ====================================================================
        // 7. CREATE OPTIONS
        // ====================================================================
        val savedOptions = input.options.map { optionInput ->
            ProductOption().apply {
                title = optionInput.title
                this.product = savedProduct
                values = optionInput.values.toMutableList()
            }.let { productOptionRepository.save(it) }
        }

        // ====================================================================
        // 8. CREATE VARIANTS WITH PRICES AND OPTIONS
        // ====================================================================
        for (variantInput in input.variants) {
            val sku = if (variantInput.sku.isNullOrBlank()) {
                generateUniqueSku(input.categoryIds.firstOrNull())
            } else {
                variantInput.sku
            }

            val ean = if (variantInput.ean.isNullOrBlank()) {
                generateEan13()
            } else {
                variantInput.ean
            }

            val variant = ProductVariant().apply {
                title = variantInput.title
                this.sku = sku
                this.ean = ean
                this.barcode = variantInput.barcode ?: ean
                this.manageInventory = variantInput.manageInventory
                this.allowBackorder = variantInput.allowBackorder
                this.product = savedProduct
            }

            // Create variant options
            variant.options = variantInput.options.map { (optionTitle, value) ->
                val option = savedOptions.find { it.title == optionTitle }
                    ?: throw InvalidVariantException("Option '$optionTitle' not found")
                ProductVariantOption().apply {
                    this.option = option
                    this.value = value
                    this.variant = variant
                }
            }.toMutableSet()

            // Create variant prices
            variant.prices = variantInput.prices.map { priceInput ->
                ProductVariantPrice().apply {
                    currencyCode = priceInput.currencyCode
                    amount = priceInput.amount
                    compareAtPrice = priceInput.compareAtPrice
                    regionId = priceInput.regionId
                    this.variant = variant
                }
            }.toMutableSet()

            productVariantRepository.save(variant)

            // Create inventory if managed
            if (variant.manageInventory && defaultLocation != null) {
                val inventoryItem = InventoryItem().apply {
                    this.sku = variant.sku ?: "VAR-${variant.id.take(8)}"
                    this.requiresShipping = true
                }
                inventoryItemRepository.save(inventoryItem)

                val inventoryLevel = InventoryLevel().apply {
                    this.inventoryItem = inventoryItem
                    this.location = defaultLocation
                    this.stockedQuantity = variantInput.inventoryQuantity
                    this.availableQuantity = variantInput.inventoryQuantity
                    this.reservedQuantity = 0
                    this.incomingQuantity = 0
                }
                inventoryLevelRepository.save(inventoryLevel)

                variant.inventoryItems.add(ProductVariantInventoryItem().apply {
                    this.variant = variant
                    this.inventoryItemId = inventoryItem.id
                    this.requiredQuantity = 1
                })
                productVariantRepository.save(variant)

                logger.debug { "Created inventory for variant ${variant.id}: sku=${variant.sku}" }
            }
        }

        // ====================================================================
        // 9. LINK CATEGORIES
        // ====================================================================
        for (categoryId in input.categoryIds) {
            val category = productCategoryRepository.findById(categoryId)
                .orElseThrow { CategoryNotFoundException(categoryId) }
            savedProduct.categories.add(category)
        }
        productRepository.save(savedProduct)

        // ====================================================================
        // 10. CREATE PENDING IMAGE UPLOADS
        // ====================================================================
        val pendingUploads = input.images.mapIndexed { index, sourceUrl ->
            PendingImageUpload.create(
                productId = savedProduct.id,
                sourceUrl = sourceUrl,
                position = index
            ).let { pendingImageUploadRepository.save(it) }
        }

        logger.info { "Created ${pendingUploads.size} pending image uploads for product ${savedProduct.id}" }

        // ====================================================================
        // 11. QUEUE OUTBOX EVENT
        // ====================================================================
        val outboxEvent = OutboxEvent.create(
            aggregateType = "Product",
            aggregateId = savedProduct.id,
            eventType = "ProductCreationStarted",
            payload = mapOf(
                "productId" to savedProduct.id,
                "handle" to savedProduct.handle,
                "title" to savedProduct.title,
                "pendingImages" to pendingUploads.size,
                "variantCount" to input.variants.size
            ),
            correlationId = correlationId
        )
        outboxEventRepository.save(outboxEvent)

        // ====================================================================
        // 12. UPDATE EXECUTION WITH RESULT
        // ====================================================================
        execution.resultId = savedProduct.id
        workflowExecutionRepository.save(execution)

        logger.info {
            "Phase1Reserve: Completed for product ${savedProduct.id}, " +
            "${input.variants.size} variants, ${pendingUploads.size} pending images"
        }

        return ReserveResult(
            executionId = execution.id,
            productId = savedProduct.id,
            pendingUploadIds = pendingUploads.map { it.id },
            variantCount = input.variants.size
        )
    }

    private fun generateUniqueSku(categoryId: String?): String {
        val categoryPrefix = categoryId?.let { getCategoryPrefix(it) }

        repeat(MAX_SKU_GENERATION_ATTEMPTS) {
            val sku = SkuRules.generate(categoryPrefix)
            if (inventoryItemRepository.findBySkuAndDeletedAtIsNull(sku) == null) {
                return sku
            }
            logger.debug { "SKU collision: $sku, retrying..." }
        }

        throw SkuGenerationExhaustedException(
            "Failed to generate unique SKU after $MAX_SKU_GENERATION_ATTEMPTS attempts"
        )
    }

    private fun getCategoryPrefix(categoryId: String): String? {
        return productCategoryRepository.findById(categoryId)
            .map { it.handle?.take(4)?.uppercase() }
            .orElse(null)
    }

    private fun generateEan13(): String {
        // Generate 12 random digits, calculate check digit
        val digits = (1..12).map { (0..9).random() }
        val oddSum = digits.filterIndexed { i, _ -> i % 2 == 0 }.sum()
        val evenSum = digits.filterIndexed { i, _ -> i % 2 == 1 }.sum()
        val checkDigit = (10 - ((oddSum + evenSum * 3) % 10)) % 10
        return digits.joinToString("") + checkDigit
    }
}
