package com.vernont.workflow.flows.product

import com.vernont.domain.product.ProductVariant
import com.vernont.domain.product.ProductVariantPrice
import com.vernont.events.EventPublisher
import com.vernont.events.ProductUpdated
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.product.ProductVariantPriceRepository
import com.vernont.repository.product.ProductVariantRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Response for variant update
 */
data class UpdateProductVariantResponse(
    val id: String,
    val productId: String,
    val title: String,
    val sku: String?,
    val barcode: String?,
    val prices: List<VariantPriceResponse>,
    val allowBackorder: Boolean,
    val manageInventory: Boolean
)

data class VariantPriceResponse(
    val id: String,
    val currencyCode: String,
    val amount: BigDecimal,
    val compareAtPrice: BigDecimal?,
    val regionId: String?
)

/**
 * Update Product Variant Workflow (Admin)
 *
 * This workflow updates an existing product variant including its prices,
 * inventory settings, and metadata.
 *
 * Steps:
 * 1. Load variant and validate it exists
 * 2. Validate SKU uniqueness (if changed)
 * 3. Validate barcode uniqueness (if changed)
 * 4. Validate prices are non-negative
 * 5. Update variant fields
 * 6. Update or create prices
 * 7. Publish ProductUpdated event
 * 8. Return updated variant
 */
@Component
@WorkflowTypes(UpdateProductVariantInput::class, UpdateProductVariantResponse::class)
class UpdateProductVariantWorkflow(
    private val productVariantRepository: ProductVariantRepository,
    private val productVariantPriceRepository: ProductVariantPriceRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val eventPublisher: EventPublisher
) : Workflow<UpdateProductVariantInput, UpdateProductVariantResponse> {

    override val name = WorkflowConstants.UpdateProductVariant.NAME

    @Transactional
    override suspend fun execute(
        input: UpdateProductVariantInput,
        context: WorkflowContext
    ): WorkflowResult<UpdateProductVariantResponse> {
        logger.info { "Starting update product variant workflow for variant: ${input.id}" }

        try {
            // Step 1: Load and validate variant
            val loadVariantStep = createStep<String, ProductVariant>(
                name = "load-variant-for-update",
                execute = { variantId, ctx ->
                    logger.debug { "Loading variant: $variantId" }

                    val variant = productVariantRepository.findByIdAndDeletedAtIsNull(variantId)
                        ?: throw IllegalArgumentException("Product variant not found: $variantId")

                    ctx.addMetadata("originalVariant", variant)
                    ctx.addMetadata("originalSku", variant.sku ?: "")
                    ctx.addMetadata("originalBarcode", variant.barcode ?: "")
                    StepResponse.of(variant)
                }
            )

            // Step 2: Validate SKU uniqueness
            val validateSkuStep = createStep<ProductVariant, Unit>(
                name = "validate-sku-uniqueness",
                execute = { variant, _ ->
                    if (input.sku != null && input.sku != variant.sku) {
                        logger.debug { "Validating SKU uniqueness: ${input.sku}" }

                        // Check if SKU exists for another variant
                        if (productVariantRepository.existsBySkuAndIdNot(input.sku, variant.id)) {
                            throw IllegalArgumentException("SKU '${input.sku}' already exists for another variant")
                        }

                        // Also check inventory items
                        val existingItem = inventoryItemRepository.findBySkuAndDeletedAtIsNull(input.sku)
                        if (existingItem != null) {
                            throw IllegalArgumentException("SKU '${input.sku}' already exists in inventory")
                        }
                    }
                    StepResponse.of(Unit)
                }
            )

            // Step 3: Validate barcode uniqueness
            val validateBarcodeStep = createStep<ProductVariant, Unit>(
                name = "validate-barcode-uniqueness",
                execute = { variant, _ ->
                    if (input.barcode != null && input.barcode != variant.barcode) {
                        logger.debug { "Validating barcode uniqueness: ${input.barcode}" }

                        if (productVariantRepository.existsByBarcodeAndIdNot(input.barcode, variant.id)) {
                            throw IllegalArgumentException("Barcode '${input.barcode}' already exists for another variant")
                        }
                    }
                    StepResponse.of(Unit)
                }
            )

            // Step 4: Validate prices
            val validatePricesStep = createStep<Unit, Unit>(
                name = "validate-variant-prices",
                execute = { _, _ ->
                    input.prices?.forEach { price ->
                        if (price.amount < BigDecimal.ZERO) {
                            throw IllegalArgumentException(
                                "Price cannot be negative for currency ${price.currencyCode}: ${price.amount}"
                            )
                        }
                    }
                    StepResponse.of(Unit)
                }
            )

            // Step 5: Update variant fields
            val updateVariantStep = createStep<ProductVariant, ProductVariant>(
                name = "update-variant-fields",
                execute = { variant, ctx ->
                    logger.debug { "Updating variant fields for: ${variant.id}" }

                    // Update only provided fields
                    input.title?.let { variant.title = it }
                    input.sku?.let { variant.sku = it }
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

                    val savedVariant = productVariantRepository.save(variant)
                    logger.info { "Variant fields updated: ${savedVariant.id}" }
                    StepResponse.of(savedVariant)
                },
                compensate = { _, ctx ->
                    // Revert variant changes if workflow fails
                    try {
                        val originalSku = (ctx.getMetadata("originalSku") as? String)?.ifEmpty { null }
                        val originalBarcode = (ctx.getMetadata("originalBarcode") as? String)?.ifEmpty { null }
                        val variant = productVariantRepository.findByIdAndDeletedAtIsNull(input.id)
                        if (variant != null) {
                            variant.sku = originalSku
                            variant.barcode = originalBarcode
                            productVariantRepository.save(variant)
                            logger.info { "Compensated: Reverted variant ${variant.id} SKU and barcode" }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate variant update" }
                    }
                }
            )

            // Step 6: Update prices
            val updatePricesStep = createStep<ProductVariant, ProductVariant>(
                name = "update-variant-prices",
                execute = { variant, ctx ->
                    if (input.prices != null) {
                        logger.debug { "Updating prices for variant: ${variant.id}" }

                        // Store original prices for compensation
                        ctx.addMetadata("originalPrices", variant.prices.toList())

                        // Clear existing prices and add new ones
                        variant.prices.clear()

                        for (priceInput in input.prices) {
                            val price = ProductVariantPrice().apply {
                                currencyCode = priceInput.currencyCode
                                amount = priceInput.amount
                                regionId = priceInput.regionId
                            }
                            variant.addPrice(price)
                        }

                        val savedVariant = productVariantRepository.save(variant)
                        logger.info { "Variant prices updated: ${savedVariant.id}, ${input.prices.size} prices" }
                        StepResponse.of(savedVariant)
                    } else {
                        StepResponse.of(variant)
                    }
                }
            )

            // Step 7: Publish event
            val publishEventStep = createStep<ProductVariant, Unit>(
                name = "publish-variant-updated-event",
                execute = { variant, _ ->
                    logger.debug { "Publishing variant updated event" }

                    val productId = variant.product?.id ?: ""
                    val defaultPrice = variant.prices.firstOrNull()?.amount ?: BigDecimal.ZERO

                    eventPublisher.publish(
                        ProductUpdated(
                            aggregateId = productId,
                            name = variant.title,
                            description = "Variant ${variant.id} updated",
                            price = defaultPrice,
                            quantity = 0, // Quantity managed separately through inventory
                            isActive = true
                        )
                    )

                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val variant = loadVariantStep.invoke(input.id, context).data
            validateSkuStep.invoke(variant, context)
            validateBarcodeStep.invoke(variant, context)
            validatePricesStep.invoke(Unit, context)
            val updatedVariant = updateVariantStep.invoke(variant, context).data
            val finalVariant = updatePricesStep.invoke(updatedVariant, context).data
            publishEventStep.invoke(finalVariant, context)

            val response = UpdateProductVariantResponse(
                id = finalVariant.id,
                productId = finalVariant.product?.id ?: "",
                title = finalVariant.title,
                sku = finalVariant.sku,
                barcode = finalVariant.barcode,
                prices = finalVariant.prices.map { price ->
                    VariantPriceResponse(
                        id = price.id,
                        currencyCode = price.currencyCode,
                        amount = price.amount,
                        compareAtPrice = price.compareAtPrice,
                        regionId = price.regionId
                    )
                },
                allowBackorder = finalVariant.allowBackorder,
                manageInventory = finalVariant.manageInventory
            )

            logger.info { "Update product variant workflow completed. Variant: ${finalVariant.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Update product variant workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
