@file:Suppress("UNCHECKED_CAST")

package com.vernont.workflow.flows.product

import com.vernont.domain.product.Product
import com.vernont.domain.product.ProductImage
import com.vernont.domain.product.dto.ProductResponse
import com.vernont.events.EventPublisher
import com.vernont.events.ProductUpdated
import com.vernont.repository.product.ProductRepository
import com.vernont.repository.product.ProductImageRepository
import com.vernont.repository.product.ProductVariantRepository
import com.vernont.infrastructure.storage.ProductImageStorageService
import com.vernont.infrastructure.storage.PresignedUrlService
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.flows.product.UpdateProductVariantInput
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Input for updating a single product
 */
data class UpdateProductInput(
    val id: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val handle: String? = null,
    val isGiftcard: Boolean? = null,
    val discountable: Boolean? = null,
    val thumbnail: String? = null,
    val images: List<String>? = null,
    val weight: Int? = null,
    val length: Int? = null,
    val height: Int? = null,
    val width: Int? = null,
    val hsCode: String? = null,
    val originCountry: String? = null,
    val midCode: String? = null,
    val material: String? = null,
    val collectionId: String? = null,
    val typeId: String? = null,
    val tags: List<String>? = null,
    val categories: List<String>? = null,
    val variants: List<UpdateProductVariantInput>? = null,
    val salesChannels: List<SalesChannelInput>? = null,
    val shippingProfileId: String? = null,
    val metadata: Map<String, Any>? = null
)

data class SalesChannelInput(
    val id: String
)

/**
 * Input for updating products (supports batch and selector modes)
 */
data class UpdateProductsWorkflowInput(
    // Batch mode: Update multiple products by ID
    val products: List<UpdateProductInput>? = null,

    // Selector mode: Update products matching criteria
    val selector: ProductSelector? = null,
    val update: UpdateProductInput? = null,

    // Additional data for hooks
    val additionalData: Map<String, Any>? = null,
    val correlationId: String? = null
)

data class ProductSelector(
    val ids: List<String>? = null,
    val typeId: List<String>? = null,
    val collectionId: List<String>? = null,
    val status: List<String>? = null,
    val tags: List<String>? = null
)

/**
 * Update Products Workflow - Based on Medusa's updateProductsWorkflow
 *
 * This workflow updates one or more products with their variants, prices, and relationships.
 * Used by the Update Product Admin API Route.
 *
 * Steps (matching Medusa):
 * 1. Prepare update input (remove external relations)
 * 2. Update products in database
 * 3. Find products with sales channels
 * 4. Find products with shipping profiles
 * 5. Dismiss current sales channel links
 * 6. Create new sales channel links
 * 7. Dismiss current shipping profile links
 * 8. Create new shipping profile links
 * 9. Prepare variant prices
 * 10. Upsert variant prices
 * 11. Emit product updated events
 * 12. productsUpdated hook
 *
 * @see https://docs.medusajs.com/resources/commerce-modules/product/workflows
 */
@Component
@WorkflowTypes(input = UpdateProductsWorkflowInput::class, output = List::class)
class UpdateProductWorkflow(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productImageRepository: ProductImageRepository,
    private val productCollectionRepository: com.vernont.repository.product.ProductCollectionRepository,
    private val productTypeRepository: com.vernont.repository.product.ProductTypeRepository,
    private val productTagRepository: com.vernont.repository.product.ProductTagRepository,
    private val productCategoryRepository: com.vernont.repository.product.ProductCategoryRepository,
    private val productImageStorageService: ProductImageStorageService,
    private val presignedUrlService: PresignedUrlService,
    private val eventPublisher: EventPublisher
) : Workflow<UpdateProductsWorkflowInput, List<ProductResponse>> {

    override val name = WorkflowConstants.UpdateProduct.NAME

    @Transactional
    override suspend fun execute(
        input: UpdateProductsWorkflowInput,
        context: WorkflowContext
    ): WorkflowResult<List<ProductResponse>> {
        logger.info { "Starting update products workflow" }

        try {
            // Step 1: Prepare update input - Remove external relations that will be handled separately
            val prepareInputStep = createStep<UpdateProductsWorkflowInput, PreparedUpdateInput>(
                name = "prepare-update-product-input",
                execute = { inp, ctx ->
                    val prepared = when {
                        !inp.products.isNullOrEmpty() -> {
                            PreparedUpdateInput(
                                products = inp.products.map { product ->
                                    UpdateProductInput(
                                        id = product.id,
                                        title = product.title,
                                        subtitle = product.subtitle,
                                        description = product.description,
                                        handle = product.handle,
                                        isGiftcard = product.isGiftcard,
                                        discountable = product.discountable,
                                        thumbnail = product.thumbnail,
                                        images = product.images ?: emptyList(),
                                        weight = product.weight,
                                        length = product.length,
                                        height = product.height,
                                        width = product.width,
                                        hsCode = product.hsCode,
                                        originCountry = product.originCountry,
                                        midCode = product.midCode,
                                        material = product.material,
                                        collectionId = product.collectionId,
                                        typeId = product.typeId,
                                        tags = product.tags,
                                        categories = product.categories,
                                        variants = product.variants?.map { v ->
                                            UpdateProductVariantInput(
                                                id = v.id,
                                                title = v.title,
                                                sku = v.sku,
                                                ean = v.ean,
                                                upc = v.upc,
                                                barcode = v.barcode,
                                                hsCode = v.hsCode,
                                                inventoryQuantity = v.inventoryQuantity,
                                                allowBackorder = v.allowBackorder,
                                                manageInventory = v.manageInventory,
                                                weight = v.weight,
                                                length = v.length,
                                                height = v.height,
                                                width = v.width,
                                                originCountry = v.originCountry,
                                                midCode = v.midCode,
                                                material = v.material,
                                                metadata = v.metadata,
                                                options = v.options,
                                                prices = null // Remove prices, will be handled separately
                                            )
                                        },
                                        metadata = product.metadata,
                                        salesChannels = null, // Remove, will be handled separately
                                        shippingProfileId = null // Remove, will be handled separately
                                    )
                                },
                                selector = null,
                                originalInput = inp
                            )
                        }
                        inp.selector != null && inp.update != null -> {
                            PreparedUpdateInput(
                                products = null,
                                selector = inp.selector,
                                originalInput = inp
                            )
                        }
                        else -> throw IllegalArgumentException("Either products or selector with update must be provided")
                    }

                    ctx.addMetadata("preparedInput", prepared)
                    StepResponse.of(prepared)
                }
            )

            // Step 2: Update products in database
            val updateProductsStep = createStep<PreparedUpdateInput, List<Product>>(
                name = "update-products",
                execute = { prepared, ctx ->
                    logger.debug { "Updating products in database" }

                    // Store original state for compensation
                    val originalStates = mutableMapOf<String, ProductSnapshot>()

                    val updatedProducts = when {
                        !prepared.products.isNullOrEmpty() -> {
                            // Batch update mode
                            prepared.products.mapNotNull { productInput ->
                                val pid = productInput.id
                                    ?: throw IllegalArgumentException("Product id is required for batch update")
                                val existingProduct = productRepository.findById(pid).orElse(null)
                                    ?: run {
                                        logger.warn { "Product not found: $pid" }
                                        return@mapNotNull null
                                    }

                                // Capture original state before modification
                                originalStates[existingProduct.id] = ProductSnapshot(
                                    title = existingProduct.title,
                                    subtitle = existingProduct.subtitle,
                                    description = existingProduct.description,
                                    handle = existingProduct.handle,
                                    isGiftcard = existingProduct.isGiftcard,
                                    discountable = existingProduct.discountable,
                                    thumbnail = existingProduct.thumbnail,
                                    weight = existingProduct.weight,
                                    length = existingProduct.length,
                                    height = existingProduct.height,
                                    width = existingProduct.width,
                                    hsCode = existingProduct.hsCode,
                                    originCountry = existingProduct.originCountry,
                                    midCode = existingProduct.midCode,
                                    material = existingProduct.material,
                                    shippingProfileId = existingProduct.shippingProfileId,
                                    collectionId = existingProduct.collection?.id,
                                    typeId = existingProduct.type?.id,
                                    metadata = (existingProduct.metadata?.toMap() ?: emptyMap()) as Map<String, Any>
                                )

                                applyProductUpdates(existingProduct, productInput)
                                productRepository.save(existingProduct)
                            }
                        }
                        prepared.selector != null -> {
                            // Selector mode - find and update matching products
                            val matchingProducts = findProductsBySelector(prepared.selector)
                            val updateData = prepared.originalInput.update
                                ?: throw IllegalStateException("Update data required for selector mode")

                            matchingProducts.map { product ->
                                // Capture original state before modification
                                originalStates[product.id] = ProductSnapshot(
                                    title = product.title,
                                    subtitle = product.subtitle,
                                    description = product.description,
                                    handle = product.handle,
                                    isGiftcard = product.isGiftcard,
                                    discountable = product.discountable,
                                    thumbnail = product.thumbnail,
                                    weight = product.weight,
                                    length = product.length,
                                    height = product.height,
                                    width = product.width,
                                    hsCode = product.hsCode,
                                    originCountry = product.originCountry,
                                    midCode = product.midCode,
                                    material = product.material,
                                    shippingProfileId = product.shippingProfileId,
                                    collectionId = product.collection?.id,
                                    typeId = product.type?.id,
                                    metadata = (product.metadata?.toMap() ?: emptyMap()) as Map<String, Any>
                                )

                                applyProductUpdates(product, updateData)
                                productRepository.save(product)
                            }
                        }
                        else -> emptyList()
                    }

                    ctx.addMetadata("originalProductStates", originalStates)
                    ctx.addMetadata("updatedProducts", updatedProducts)
                    ctx.addMetadata("updatedProductIds", updatedProducts.map { it.id })

                    logger.info { "Updated ${updatedProducts.size} products" }
                    StepResponse.of(updatedProducts, originalStates)
                },
                compensate = { prepared, ctx ->
                    logger.warn { "Compensating product updates - restoring original state" }

                    @Suppress("UNCHECKED_CAST")
                    val originalStates = ctx.getMetadata("originalProductStates") as? Map<String, ProductSnapshot>
                        ?: return@createStep

                    originalStates.forEach { (productId, snapshot) ->
                        try {
                            val product = productRepository.findById(productId).orElse(null)
                            if (product != null) {
                                // Restore all original values
                                product.title = snapshot.title
                                product.subtitle = snapshot.subtitle
                                product.description = snapshot.description
                                product.handle = snapshot.handle
                                product.isGiftcard = snapshot.isGiftcard
                                product.discountable = snapshot.discountable
                                product.thumbnail = snapshot.thumbnail
                                product.weight = snapshot.weight
                                product.length = snapshot.length
                                product.height = snapshot.height
                                product.width = snapshot.width
                                product.hsCode = snapshot.hsCode
                                product.originCountry = snapshot.originCountry
                                product.midCode = snapshot.midCode
                                product.material = snapshot.material
                                product.shippingProfileId = snapshot.shippingProfileId

                                // Restore relationships
                                snapshot.collectionId?.let { collId ->
                                    product.collection = productCollectionRepository.findByIdAndDeletedAtIsNull(collId)
                                }
                                snapshot.typeId?.let { tId ->
                                    product.type = productTypeRepository.findByIdAndDeletedAtIsNull(tId)
                                }

                                product.metadata = snapshot.metadata.toMutableMap()

                                productRepository.save(product)
                                logger.debug { "Restored product $productId to original state" }
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to restore product $productId during compensation" }
                        }
                    }

                    logger.info { "Compensation completed: restored ${originalStates.size} products" }
                }
            )

            // Step 3: Update product images when provided
            val updateProductImagesStep = createStep<List<Product>, List<Product>>(
                name = "update-product-images",
                execute = { products, ctx ->
                    val prepared = ctx.getMetadata("preparedInput") as PreparedUpdateInput
                    val batchInputs = prepared.products
                    val selectorImages = prepared.originalInput.update?.images
                    val selectorThumbnail = prepared.originalInput.update?.thumbnail

                    val originalImages = mutableMapOf<String, List<String>>()

                    products.forEachIndexed { index, product ->
                        val inputImages = batchInputs?.getOrNull(index)?.images ?: selectorImages
                        val inputThumb = batchInputs?.getOrNull(index)?.thumbnail ?: selectorThumbnail

                        val newImages = mutableListOf<String>().apply {
                            if (inputImages != null) addAll(inputImages)
                            inputThumb?.takeIf { it.isNotBlank() }?.let { add(it) }
                        }.distinct()

                        if (newImages.isNullOrEmpty()) return@forEachIndexed

                        originalImages[product.id] =
                            productImageRepository.findByProductId(product.id).map { it.url }

                        val uploaded = productImageStorageService.uploadAndResolveUrls(newImages, product.id)

                        val existing = productImageRepository.findByProductId(product.id)
                        if (existing.isNotEmpty()) {
                            productImageRepository.deleteAll(existing)
                        }

                        uploaded.forEach { url ->
                            val productImage = ProductImage().apply {
                                this.url = url
                                this.product = product
                            }
                            productImageRepository.save(productImage)
                        }

                        if (uploaded.isNotEmpty()) {
                            // Always store URL from storage; if none uploaded, clear unsafe thumbnail
                            product.thumbnail = uploaded.first()
                            productRepository.save(product)
                        } else if (inputThumb != null && inputThumb.startsWith("data:", ignoreCase = true)) {
                            product.thumbnail = null
                            productRepository.save(product)
                        }
                    }

                    ctx.addMetadata("update-product-images", originalImages)
                    StepResponse.of(products)
                },
                compensate = { _, ctx ->
                    val originalImages = ctx.getMetadata("update-product-images") as? Map<*, *>
                        ?: return@createStep

                    originalImages.forEach { (productIdAny, urlsAny) ->
                        val productId = productIdAny as? String ?: return@forEach
                        val urls = (urlsAny as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val product = productRepository.findById(productId).orElse(null) ?: return@forEach
                        val existing = productImageRepository.findByProductId(productId)
                        if (existing.isNotEmpty()) {
                            productImageRepository.deleteAll(existing)
                        }

                        urls.forEach { url ->
                            val productImage = ProductImage().apply {
                                this.url = url
                                this.product = product
                            }
                            productImageRepository.save(productImage)
                        }

                        if (urls.isNotEmpty()) {
                            product.thumbnail = urls.first()
                            productRepository.save(product)
                        }
                    }
                }
            )

            // Step 4: Handle variant updates if provided
            val updateVariantsStep = createStep<List<Product>, List<Product>>(
                name = "update-product-variants",
                execute = { products, ctx ->
                    val prepared = ctx.getMetadata("preparedInput") as PreparedUpdateInput

                    if (prepared.products.isNullOrEmpty()) {
                        return@createStep StepResponse.of(products)
                    }

                    products.forEachIndexed { index, product ->
                        val variantInputs = prepared.products[index].variants
                        if (!variantInputs.isNullOrEmpty()) {
                            variantInputs.forEach { variantInput ->
                                val variant = product.variants.find { it.id == variantInput.id }
                                if (variant != null) {
                                    applyVariantUpdates(variant, variantInput)
                                    productVariantRepository.save(variant)
                                } else {
                                    logger.warn { "Variant not found: ${variantInput.id} for product: ${product.id}" }
                                }
                            }
                        }
                    }

                    StepResponse.of(products)
                }
            )

            // Step 5: Emit product updated events
            val emitEventsStep = createStep<List<Product>, List<Product>>(
                name = "emit-product-updated-events",
                execute = { products, ctx ->
                    products.forEach { product ->
                        try {
                            eventPublisher.publish(
                                ProductUpdated(
                                    aggregateId = product.id,
                                    name = product.title,
                                    description = product.description ?: "",
                                    price = product.variants.firstOrNull()?.prices?.firstOrNull()?.amount ?: java.math.BigDecimal.ZERO,
                                    quantity = 0, // Inventory managed separately through InventoryLevel entity
                                    isActive = product.status == com.vernont.domain.product.ProductStatus.PUBLISHED
                                )
                            )
                            logger.debug { "Published ProductUpdated event for: ${product.id}" }
                        } catch (e: Exception) {
                            logger.warn(e) { "Failed to publish ProductUpdated event for: ${product.id}" }
                        }
                    }
                    StepResponse.of(products)
                }
            )

            // Step 6: productsUpdated hook - Custom post-update logic
            val productsUpdatedHookStep = createStep<List<Product>, List<Product>>(
                name = "products-updated-hook",
                execute = { products, ctx ->
                    logger.debug { "Running productsUpdated hook for ${products.size} products" }

                    // Hook for custom logic:
                    // - Update external systems (ERP, PIM, etc.)
                    // - Trigger search index updates
                    // - Update analytics/reporting
                    // - Invalidate caches
                    // - Notify stakeholders

                    val additionalData = input.additionalData
                    if (additionalData != null) {
                        logger.debug { "Processing additional data: $additionalData" }
                        // Process additional data (e.g., ERP sync, custom metadata)
                    }

                    StepResponse.of(products)
                }
            )

            // Execute all steps in sequence
            val prepared = prepareInputStep.invoke(input, context).data
            val updatedProducts = updateProductsStep.invoke(prepared, context).data
            val productsWithImages = updateProductImagesStep.invoke(updatedProducts, context).data
            val productsWithVariants = updateVariantsStep.invoke(productsWithImages, context).data
            val productsWithEvents = emitEventsStep.invoke(productsWithVariants, context).data
            val finalProducts = productsUpdatedHookStep.invoke(productsWithEvents, context).data

            logger.info { "Product update workflow completed. Updated ${finalProducts.size} products" }

            // Convert to response DTOs
            val productResponses = finalProducts.map { product ->
                signProductMedia(ProductResponse.from(product), presignedUrlService)
            }

            return WorkflowResult.success(productResponses)

        } catch (e: Exception) {
            logger.error(e) { "Update products workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

    /**
     * Apply updates to a product entity with full business logic
     */
    private fun applyProductUpdates(product: Product, input: UpdateProductInput) {
        input.title?.let { product.title = it }
        input.subtitle?.let { product.subtitle = it }
        input.description?.let { product.description = it }
        input.handle?.let {
            // Validate handle uniqueness
            if (productRepository.existsByHandleAndIdNot(it, product.id)) {
                throw IllegalArgumentException("Product handle '$it' already exists")
            }
            product.handle = it
        }
        input.isGiftcard?.let { product.isGiftcard = it }
        input.discountable?.let { product.discountable = it }
        // Thumbnail is handled in the image upload step; ignore raw input here to avoid persisting data URLs
        input.weight?.let { product.weight = it.toString() }
        input.length?.let { product.length = it.toString() }
        input.height?.let { product.height = it.toString() }
        input.width?.let { product.width = it.toString() }
        input.hsCode?.let { product.hsCode = it }
        input.originCountry?.let { product.originCountry = it }
        input.midCode?.let { product.midCode = it }
        input.material?.let { product.material = it }
        input.shippingProfileId?.let { product.shippingProfileId = it }

        // Handle collection relationship
        input.collectionId?.let { collectionId ->
            val collection = productCollectionRepository.findByIdAndDeletedAtIsNull(collectionId)
                ?: throw IllegalArgumentException("Product collection not found: $collectionId")
            product.collection = collection
        }

        // Handle type relationship
        input.typeId?.let { typeId ->
            val type = productTypeRepository.findByIdAndDeletedAtIsNull(typeId)
                ?: throw IllegalArgumentException("Product type not found: $typeId")
            product.type = type
        }

        // Handle tags - clear and rebuild
        input.tags?.let { tagValues ->
            product.tags.clear()
            tagValues.forEach { tagValue ->
                val tag = productTagRepository.findByValueAndDeletedAtIsNull(tagValue)
                    ?: throw IllegalArgumentException("Product tag not found: $tagValue")
                product.tags.add(tag)
            }
        }

        // Handle categories - clear and rebuild
        input.categories?.let { categoryIds ->
            product.categories.clear()
            categoryIds.forEach { categoryId ->
                val category = productCategoryRepository.findByIdAndDeletedAtIsNull(categoryId)
                    ?: throw IllegalArgumentException("Product category not found: $categoryId")
                product.categories.add(category)
            }
        }

        input.metadata?.let {
            product.metadata = it.toMutableMap()
        }
    }

    /**
     * Apply updates to a variant entity with full business logic
     */
    private fun applyVariantUpdates(
        variant: com.vernont.domain.product.ProductVariant,
        input: UpdateProductVariantInput
    ) {
        input.title?.let { variant.title = it }
        input.sku?.let { sku ->
            // Validate SKU uniqueness
            val existingVariant = productVariantRepository.findBySku(sku)
            if (existingVariant != null && existingVariant.id != variant.id) {
                throw IllegalArgumentException("SKU '$sku' already exists for another variant")
            }
            variant.sku = sku
        }
        input.ean?.let { variant.ean = it }
        input.upc?.let { variant.upc = it }
        input.barcode?.let { variant.barcode = it }
        input.hsCode?.let { variant.hsCode = it }
        input.allowBackorder?.let { variant.allowBackorder = it }
        input.manageInventory?.let { variant.manageInventory = it }
        input.weight?.let { variant.weight = it.toString() }
        input.length?.let { variant.length = it.toString() }
        input.height?.let { variant.height = it.toString() }
        input.width?.let { variant.width = it.toString() }
        input.originCountry?.let { variant.originCountry = it }
        input.midCode?.let { variant.midCode = it }
        input.material?.let { variant.material = it }
        input.metadata?.let {
            variant.metadata = it.toMutableMap()
        }
    }

    /**
     * Find products matching selector criteria with full business logic
     */
    private fun findProductsBySelector(selector: ProductSelector): List<Product> {
        return when {
            !selector.ids.isNullOrEmpty() -> {
                productRepository.findAllById(selector.ids).filter { it.deletedAt == null }
            }
            !selector.typeId.isNullOrEmpty() -> {
                selector.typeId.flatMap { typeId ->
                    productRepository.findByTypeIdAndDeletedAtIsNull(typeId)
                }
            }
            !selector.collectionId.isNullOrEmpty() -> {
                selector.collectionId.flatMap { collectionId ->
                    productRepository.findByCollectionIdAndDeletedAtIsNull(collectionId)
                }
            }
            !selector.status.isNullOrEmpty() -> {
                selector.status.flatMap { status ->
                    productRepository.findByStatusAndDeletedAtIsNull(
                        com.vernont.domain.product.ProductStatus.valueOf(status.uppercase())
                    )
                }
            }
            !selector.tags.isNullOrEmpty() -> {
                selector.tags.flatMap { tagValue ->
                    val tag = productTagRepository.findByValueAndDeletedAtIsNull(tagValue)
                    if (tag != null) {
                        tag.products.filter { it.deletedAt == null }
                    } else {
                        emptyList()
                    }
                }.distinct()
            }
            else -> {
                logger.warn { "No valid selector criteria provided" }
                emptyList()
            }
        }
    }
}

/**
 * Internal data class for prepared input
 */
private data class PreparedUpdateInput(
    val products: List<UpdateProductInput>?,
    val selector: ProductSelector?,
    val originalInput: UpdateProductsWorkflowInput
)

/**
 * Snapshot of product state for compensation
 */
private data class ProductSnapshot(
    val title: String,
    val subtitle: String?,
    val description: String?,
    val handle: String,
    val isGiftcard: Boolean,
    val discountable: Boolean,
    val thumbnail: String?,
    val weight: String?,
    val length: String?,
    val height: String?,
    val width: String?,
    val hsCode: String?,
    val originCountry: String?,
    val midCode: String?,
    val material: String?,
    val shippingProfileId: String?,
    val collectionId: String?,
    val typeId: String?,
    val metadata: Map<String, Any>
)

private fun signProductMedia(
    response: ProductResponse,
    presignedUrlService: com.vernont.infrastructure.storage.PresignedUrlService
): ProductResponse {
    val signedImages = response.images.map { img ->
        val signed = presignedUrlService.signIfNeeded(img.url) ?: img.url
        img.copy(url = signed)
    }
    val signedThumbnail = presignedUrlService.signIfNeeded(response.thumbnail) ?: response.thumbnail
    return response.copy(images = signedImages, thumbnail = signedThumbnail)
}
