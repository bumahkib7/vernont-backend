package com.vernont.workflow.flows.product

import com.vernont.domain.inventory.InventoryItem
import com.vernont.domain.inventory.InventoryLevel
import com.vernont.domain.product.Product
import com.vernont.domain.product.ProductImage
import com.vernont.domain.product.ProductOption
import com.vernont.domain.product.ProductVariant
import com.vernont.domain.product.ProductVariantInventoryItem
import com.vernont.domain.product.ProductVariantOption
import com.vernont.domain.product.ProductVariantPrice
import com.vernont.events.EventPublisher
import com.vernont.events.ProductCreated
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.StockLocationRepository
import com.vernont.repository.product.*
import com.vernont.infrastructure.storage.ProductImageStorageService
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.repository.store.SalesChannelRepository


private val logger = KotlinLogging.logger {}

// --- Input and Output Data Classes for the Workflow ---

// Input data classes are now imported from com.vernont.api.model
// data class CreateProductInput(...)
// data class ProductOptionInput(...)
// data class ProductVariantInput(...)
// data class ProductVariantPriceInput(...)

// --- Helper Data Classes for Step Outputs ---

data class ValidatedProductData(
    val productInput: CreateProductInput,
    // Add any other validated/transformed data needed by subsequent steps
)

// You might not need distinct data classes for each step's output if you're directly
// modifying and passing the Product entity, but for clarity, they can be useful.

/**
 * The main workflow for creating a product.
 * This class orchestrates a series of steps to ensure a robust and transactional product creation process.
 */
@Component
@WorkflowTypes(input = CreateProductInput::class, output = Product::class)
class CreateProductWorkflow(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productOptionRepository: ProductOptionRepository,
    private val productImageRepository: ProductImageRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val salesChannelRepository: SalesChannelRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val eventPublisher: EventPublisher,
    private val productImageStorageService: ProductImageStorageService
) : Workflow<CreateProductInput, Product> {

    override val name = WorkflowConstants.CreateProduct.NAME

    @Transactional
    override suspend fun execute(
        input: CreateProductInput,
        context: WorkflowContext
    ): WorkflowResult<Product> {
        logger.info { "Starting product creation workflow for product: ${input.title}" }

        try {
            // Step 1: Validate Product Input
            val validateInputStep = createStep<CreateProductInput, ValidatedProductData>(
                name = "validate-product-input",
                execute = { productInput, ctx ->
                    logger.debug { "Validating input for product: ${productInput.title}" }
                    // Perform comprehensive validation here:
                    // - Check if handle is unique
                    // - Validate image URLs
                    // - Validate variant structure (e.g., options match product options)
                    // - Validate pricing
                    // - Check if shippingProfileId, categoryIds, salesChannelIds exist
                    
                    if (productInput.handle.isBlank()) {
                        throw IllegalArgumentException("Product handle cannot be empty.")
                    }
                    val existingProduct = productRepository.findByHandle(productInput.handle)
                    if (existingProduct != null) {
                        throw IllegalArgumentException("Product with handle '${productInput.handle}' already exists.")
                    }

                    // SECURITY: Validate SKU uniqueness across all variants
                    val allSkus = productInput.variants.mapNotNull { it.sku }.filter { it.isNotBlank() }
                    val duplicateSkus = allSkus.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                    if (duplicateSkus.isNotEmpty()) {
                        throw IllegalArgumentException("Duplicate SKUs found in variants: ${duplicateSkus.joinToString()}")
                    }

                    // Check against existing SKUs in database
                    for (sku in allSkus) {
                        if (inventoryItemRepository.findBySkuAndDeletedAtIsNull(sku) != null) {
                            throw IllegalArgumentException("SKU '$sku' already exists in the system")
                        }
                    }

                    // SECURITY: Validate prices are positive
                    for (variant in productInput.variants) {
                        for (price in variant.prices) {
                            if (price.amount < java.math.BigDecimal.ZERO) {
                                throw IllegalArgumentException(
                                    "Price cannot be negative for variant '${variant.title}': ${price.amount}"
                                )
                            }
                        }
                    }

                    // SECURITY: Validate quantities are positive
                    for (variant in productInput.variants) {
                        if (variant.inventoryQuantity != null && variant.inventoryQuantity < 0) {
                            throw IllegalArgumentException(
                                "Inventory quantity cannot be negative for variant '${variant.title}'"
                            )
                        }
                    }

                    ctx.addMetadata("validatedProductInput", productInput)
                    StepResponse.of(ValidatedProductData(productInput))
                },
                compensate = { _, ctx ->
                    // No compensation needed for validation, as nothing is persisted yet
                }
            )

            // Step 2: Create Product Entity
            val createProductEntityStep = createStep<ValidatedProductData, Product>(
                name = "create-product-entity",
                execute = { validatedData, ctx ->
                    logger.debug { "Creating product entity for: ${validatedData.productInput.title}" }
                    val productInput = validatedData.productInput

                    val product = Product().apply {
                        title = productInput.title
                        description = productInput.description
                        handle = productInput.handle
                        status = productInput.status
                        this.shippingProfileId = productInput.shippingProfileId
                        // Set other base product properties
                    }
                    val savedProduct = productRepository.save(product)
                    
                    ctx.addMetadata("productId", savedProduct.id)
                    ctx.addMetadata("product", savedProduct)
                    StepResponse.of(savedProduct, savedProduct.id) // Compensation data: product ID to delete
                },
                compensate = { _, ctx ->
                    val productId = ctx.getCompensationData<String>("create-product-entity")
                    if (productId != null) {
                        try {
                            productRepository.deleteById(productId)
                            logger.info { "Compensated: Deleted product entity $productId" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate deleting product entity $productId" }
                        }
                    }
                }
            )

            // Step 3: Create Product Options
            val createProductOptionsStep = createStep<Product, Product>(
                name = "create-product-options",
                execute = { product, ctx ->
                    logger.debug { "Creating options for product: ${product.handle}" }
                    val productInput = ctx.getMetadata("validatedProductInput") as CreateProductInput

                    productInput.options.forEach { optionInput ->
                        val productOption = ProductOption().apply {
                            this.title = optionInput.title
                            this.product = product
                            this.values = optionInput.values.toMutableList()
                        }
                        productOptionRepository.save(productOption)
                    }
                    // Re-fetch product if options are not eagerly loaded or use a richer product entity
                    StepResponse.of(product, product.id) // Compensation: product ID to clean options
                },
                compensate = { _, ctx ->
                    val productId = ctx.getCompensationData<String>("create-product-options")
                    if (productId != null) {
                        try {
                            // Find and delete options associated with this product
                            val options = productOptionRepository.findByProductId(productId)
                            productOptionRepository.deleteAll(options)
                            logger.info { "Compensated: Deleted options for product $productId" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate deleting options for product $productId" }
                        }
                    }
                }
            )

            // Step 4: Create Product Variants
            val createProductVariantsStep = createStep<Product, Product>(
                name = "create-product-variants",
                execute = { product, ctx ->
                    logger.debug { "Creating variants for product: ${product.handle}" }
                    val productInput = ctx.getMetadata("validatedProductInput") as CreateProductInput
                    val existingOptions = productOptionRepository.findByProductId(product.id)

                    productInput.variants.forEach { variantInput ->
                        val productVariant = ProductVariant() // Declare it here

                        productVariant.apply { // Apply properties to it
                            this.title = variantInput.title
                            this.sku = variantInput.sku
                            this.ean = variantInput.ean
                            this.barcode = variantInput.barcode
                            // this.inventoryQuantity = variantInput.inventoryQuantity // Inventory is managed via InventoryItems
                            this.manageInventory = variantInput.manageInventory
                            this.allowBackorder = variantInput.allowBackorder
                            this.product = product // Set the Product object directly
                            
                            // Map variant options to ProductVariantOption entities
                            this.options = variantInput.options.map { (optionTitle, optionValue) ->
                                val correspondingProductOption = existingOptions.find { it.title == optionTitle }
                                    ?: throw IllegalStateException("Option '$optionTitle' not found for product ${product.id}")
                                
                                // Create ProductVariantOption entity
                                ProductVariantOption().apply {
                                    this.option = correspondingProductOption
                                    this.value = optionValue
                                    this.variant = productVariant // Now productVariant is definitely in scope
                                }
                            }.toMutableSet() // ProductVariant.options is a MutableSet<ProductVariantOption>
                            
                            // Set prices
                            this.prices = variantInput.prices.map { priceInput ->
                                ProductVariantPrice().apply {
                                    this.currencyCode = priceInput.currencyCode
                                    this.amount = priceInput.amount
                                    this.regionId = priceInput.regionId
                                    this.variant = productVariant // Now productVariant is definitely in scope
                                }
                            }.toMutableSet() // ProductVariant.prices is a MutableSet<ProductVariantPrice>
                        }
                        productVariantRepository.save(productVariant)
                    }
                    StepResponse.of(product, product.id) // Compensation: product ID to clean variants
                },
                compensate = { _, ctx ->
                    val productId = ctx.getCompensationData<String>("create-product-variants")
                    if (productId != null) {
                        try {
                            val variants = productVariantRepository.findByProductId(productId)
                            productVariantRepository.deleteAll(variants)
                            logger.info { "Compensated: Deleted variants for product $productId" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate deleting variants for product $productId" }
                        }
                    }
                }
            )

            // Step 5: Create Inventory Items for Variants
            val createInventoryForVariantsStep = createStep<Product, Product>(
                name = "create-inventory-for-variants",
                execute = { product, ctx ->
                    logger.debug { "Creating inventory items for product variants: ${product.handle}" }

                    val variants = productVariantRepository.findByProductId(product.id)

                    // Get or create a default stock location
                    val defaultLocation = stockLocationRepository.findByDeletedAtIsNull()
                        .minByOrNull { it.priority }

                    if (defaultLocation == null) {
                        logger.warn { "No stock location found. Skipping inventory creation for product ${product.id}" }
                        return@createStep StepResponse.of(product, product.id)
                    }

                    val createdInventoryIds = mutableListOf<String>()

                    for (variant in variants) {
                        // Only create inventory for variants that manage inventory
                        if (!variant.manageInventory) {
                            logger.debug { "Variant ${variant.id} does not manage inventory, skipping" }
                            continue
                        }

                        // Check if variant already has inventory items
                        if (variant.inventoryItems.isNotEmpty()) {
                            logger.debug { "Variant ${variant.id} already has inventory items, skipping" }
                            continue
                        }

                        // Create inventory item
                        val inventoryItem = InventoryItem().apply {
                            sku = variant.sku ?: "VAR-${variant.id.take(8)}"
                            requiresShipping = true
                        }
                        val savedInventoryItem = inventoryItemRepository.save(inventoryItem)
                        createdInventoryIds.add(savedInventoryItem.id)

                        // Create inventory level at default location with 0 stock
                        val inventoryLevel = InventoryLevel().apply {
                            this.inventoryItem = savedInventoryItem
                            this.location = defaultLocation
                            this.stockedQuantity = 0
                            this.reservedQuantity = 0
                            this.incomingQuantity = 0
                            this.availableQuantity = 0
                        }
                        inventoryLevelRepository.save(inventoryLevel)

                        // Link variant to inventory item
                        val variantInventoryLink = ProductVariantInventoryItem().apply {
                            this.variant = variant
                            this.inventoryItemId = savedInventoryItem.id
                            this.requiredQuantity = 1
                        }
                        variant.inventoryItems.add(variantInventoryLink)
                        productVariantRepository.save(variant)

                        logger.info { "Created inventory item ${savedInventoryItem.id} for variant ${variant.id}" }
                    }

                    ctx.addMetadata("createdInventoryIds", createdInventoryIds)
                    StepResponse.of(product, product.id)
                },
                compensate = { _, ctx ->
                    @Suppress("UNCHECKED_CAST")
                    val inventoryIds = ctx.getMetadata("createdInventoryIds") as? List<String> ?: emptyList()
                    try {
                        for (inventoryId in inventoryIds) {
                            // Delete inventory levels first
                            val levels = inventoryLevelRepository.findByInventoryItemIdAndDeletedAtIsNull(inventoryId)
                            inventoryLevelRepository.deleteAll(levels)
                            // Delete inventory item
                            inventoryItemRepository.deleteById(inventoryId)
                        }
                        logger.info { "Compensated: Deleted ${inventoryIds.size} inventory items" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate deleting inventory items" }
                    }
                }
            )

            // Step 6: Assign Product Images
            val assignProductImagesStep = createStep<Product, Product>(
                name = "assign-product-images",
                execute = { product, ctx ->
                    logger.debug { "Assigning images to product: ${product.handle}" }
                    val productInput = ctx.getMetadata("validatedProductInput") as CreateProductInput

                    val sources = mutableListOf<String>().apply {
                        addAll(productInput.images)
                        productInput.thumbnail?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.distinct()

                    if (sources.isEmpty()) {
                        logger.info { "No images provided for product ${product.handle}; skipping upload" }
                    }

                    val uploadedUrls = productImageStorageService.uploadAndResolveUrls(sources, product.id)

                    uploadedUrls.forEach { imageUrl ->
                        val productImage = ProductImage().apply {
                            this.url = imageUrl
                            this.product = product // Set the Product object directly
                        }
                        productImageRepository.save(productImage)
                    }

                    if (uploadedUrls.isNotEmpty()) {
                        // Always store a URL (never a raw data URL)
                        product.thumbnail = uploadedUrls.first()
                        productRepository.save(product)
                    } else if (productInput.thumbnail?.isNotBlank() == true && product.thumbnail.isNullOrBlank()) {
                        // Fallback: if upload skipped but a thumbnail string exists, clear it to avoid persisting raw data
                        product.thumbnail = null
                        productRepository.save(product)
                    }
                    StepResponse.of(product, product.id) // Compensation: product ID to clean images
                },
                compensate = { _, ctx ->
                    val productId = ctx.getCompensationData<String>("assign-product-images")
                    if (productId != null) {
                        try {
                            val images = productImageRepository.findByProductId(productId)
                            productImageRepository.deleteAll(images)
                            logger.info { "Compensated: Deleted images for product $productId" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate deleting images for product $productId" }
                        }
                    }
                }
            )

            // Step 6: Link Product to Categories
            val linkProductToCategoriesStep = createStep<Product, Product>(
                name = "link-product-to-categories",
                execute = { product, ctx ->
                    logger.debug { "Linking product ${product.handle} to categories" }
                    val productInput = ctx.getMetadata("validatedProductInput") as CreateProductInput

                    productInput.categoryIds.forEach { categoryId ->
                        val category = productCategoryRepository.findById(categoryId)
                            .orElseThrow { IllegalArgumentException("Product Category $categoryId not found.") }
                        // Assuming a many-to-many relationship where you add the product to the category's product list
                        // Or, if using an explicit join table, create an entry here
                        product.categories.add(category) // Example: assuming product has a mutable 'categories' set
                    }
                    productRepository.save(product) // Persist the relationships
                    StepResponse.of(product, product.id) // Compensation: product ID to unlink categories
                },
                compensate = { _, ctx ->
                    val productId = ctx.getCompensationData<String>("link-product-to-categories")
                    if (productId != null) {
                        try {
                            val product = productRepository.findById(productId).orElse(null)
                            if (product != null) {
                                product.categories.clear()
                                productRepository.save(product)
                                logger.info { "Compensated: Unlinked categories for product $productId" }
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate unlinking categories for product $productId" }
                        }
                    }
                }
            )

            // Step 7: Link Product to Sales Channels
            val linkProductToSalesChannelsStep = createStep<Product, Product>(
                name = "link-product-to-sales-channels",
                execute = { product, ctx ->
                    logger.debug { "Linking product ${product.handle} to sales channels (SKIPPED - 'salesChannels' not directly in Product entity)" }
                    val productInput = ctx.getMetadata("validatedProductInput") as CreateProductInput

                    // productInput.salesChannelIds.forEach { salesChannelId ->
                    //     val salesChannel = salesChannelRepository.findById(salesChannelId)
                    //         .orElseThrow { IllegalArgumentException("Sales Channel $salesChannelId not found.") }
                    //     // Similar to categories, establish the relationship
                    //     product.salesChannels.add(salesChannel) // Example: assuming product has a mutable 'salesChannels' set
                    // }
                    // productRepository.save(product) // Persist the relationships
                    StepResponse.of(product, product.id) // Compensation: product ID to unlink sales channels
                },
                compensate = { _, ctx ->
                    val productId = ctx.getCompensationData<String>("link-product-to-sales-channels")
                    if (productId != null) {
                        try {
                            // val product = productRepository.findById(productId).orElse(null)
                            // if (product != null) {
                            //     product.salesChannels.clear()
                            //     productRepository.save(product)
                            //     logger.info { "Compensated: Unlinked sales channels for product $productId" }
                            // }
                            logger.info { "Compensated: Sales channels unlinking for product $productId (SKIPPED - 'salesChannels' not directly in Product entity)" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate unlinking sales channels for product $productId" }
                        }
                    }
                }
            )

            // Step 8: Publish Product Created Event
            val publishProductCreatedEventStep = createStep<Product, Unit>(
                name = "publish-product-created-event",
                execute = { product, _ ->
                    logger.debug { "Publishing ProductCreated event for product: ${product.id}" }
                    eventPublisher.publish(
                        ProductCreated(
                            aggregateId = product.id,
                            title = product.title,
                            handle = product.handle,
                            status = product.status.name, // Assuming ProductStatus is an enum
                            // Add other relevant product data to the event
                        )
                    )
                    StepResponse.of(Unit)
                },
                compensate = { _, _ ->
                    // Event publishing is often fire-and-forget or handled by a separate idempotent system.
                    // If compensation is critical, you might need a mechanism to 'un-publish' or mark as invalid.
                }
            )


            // --- Execute Workflow Steps ---
            val validatedData = validateInputStep.invoke(input, context).data
            val productEntity = createProductEntityStep.invoke(validatedData, context).data
            val productWithOptions = createProductOptionsStep.invoke(productEntity, context).data
            val productWithVariants = createProductVariantsStep.invoke(productWithOptions, context).data
            val productWithInventory = createInventoryForVariantsStep.invoke(productWithVariants, context).data
            val productWithImages = assignProductImagesStep.invoke(productWithInventory, context).data
            val productWithCategories = linkProductToCategoriesStep.invoke(productWithImages, context).data
            val finalProduct = linkProductToSalesChannelsStep.invoke(productWithCategories, context).data
            publishProductCreatedEventStep.invoke(finalProduct, context)

            logger.info { "Product created successfully: ${finalProduct.id}" }
            
            return WorkflowResult.success(finalProduct)

        } catch (e: Exception) {
            logger.error(e) { "Product creation workflow failed: ${e.message}" }
            // The @Transactional annotation will handle rolling back database changes.
            // The compensation logic in steps will handle external side effects if implemented.
            return WorkflowResult.failure(e)
        }
    }
}

// Extension function to easily retrieve compensation data by step name
// This assumes compensationData is stored in context metadata
private inline fun <reified T> WorkflowContext.getCompensationData(stepName: String): T? {
    // Assuming you store compensation data with a key like "compensationData:<stepName>"
    val compensationKey = "compensationData:$stepName"
    // Using context.getMetadata(compensationKey) and casting
    return this.getMetadata(compensationKey) as? T
}
