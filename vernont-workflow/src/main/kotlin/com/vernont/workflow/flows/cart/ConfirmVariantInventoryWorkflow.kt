package com.vernont.workflow.flows.cart

import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.product.ProductVariantRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import com.vernont.workflow.common.WorkflowConstants
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Input for variant inventory confirmation
 */
data class ConfirmVariantInventoryInput(
    val salesChannelId: String? = null,
    val variants: List<VariantInventoryItem>,
    val items: List<CartItemInventoryCheck>
)

data class VariantInventoryItem(
    val id: String,
    val manageInventory: Boolean,
    val allowBackorder: Boolean,
    val requiredQuantity: Int = 1
)

data class CartItemInventoryCheck(
    val id: String? = null,
    val variantId: String,
    val quantity: Int
)

/**
 * Output of inventory confirmation
 */
data class ConfirmVariantInventoryOutput(
    val items: List<InventoryConfirmationResult>
)

data class InventoryConfirmationResult(
    val id: String?,
    val variantId: String,
    val inventoryItemId: String?,
    val requiredQuantity: Int,
    val allowBackorder: Boolean,
    val requestedQuantity: Int,
    val availableQuantity: Int,
    val locationIds: List<String>
)

/**
 * Confirm Variant Inventory Workflow
 *
 * This workflow validates that product variants are in-stock at the specified sales channel,
 * before adding them or updating their quantity in the cart.
 *
 * If a variant doesn't have sufficient quantity in-stock, the workflow throws an error.
 * If all variants have sufficient inventory, the workflow returns the cart's items with their
 * inventory details.
 *
 * Based on Medusa's confirmVariantInventoryWorkflow
 *
 * Steps:
 * 1. Prepare inventory check input (transform data)
 * 2. Confirm inventory availability for each variant
 * 3. Return inventory confirmation results
 *
 * @see https://docs.medusajs.com/resources/commerce-modules/product/links-to-other-modules
 */
@Component
@WorkflowTypes(input = ConfirmVariantInventoryInput::class, output = ConfirmVariantInventoryOutput::class)
class ConfirmVariantInventoryWorkflow(
    private val productVariantRepository: ProductVariantRepository,
    private val inventoryLevelRepository: InventoryLevelRepository
) : Workflow<ConfirmVariantInventoryInput, ConfirmVariantInventoryOutput> {

    override val name = WorkflowConstants.ConfirmVariantInventory.NAME

    override suspend fun execute(
        input: ConfirmVariantInventoryInput,
        context: WorkflowContext
    ): WorkflowResult<ConfirmVariantInventoryOutput> {
        logger.info { "Starting confirm variant inventory workflow" }

        try {
            // Step 1: Prepare inventory check input - Transform and validate
            val prepareInputStep = createStep<ConfirmVariantInventoryInput, List<InventoryCheckItem>>(
                name = "prepare-confirm-inventory-input",
                execute = { inp, ctx ->
                    logger.debug { "Preparing inventory check for ${inp.items.size} items" }

                    val inventoryCheckItems = inp.items.map { cartItem ->
                        val variant = inp.variants.find { it.id == cartItem.variantId }
                            ?: throw IllegalArgumentException("Variant not found in input: ${cartItem.variantId}")

                        InventoryCheckItem(
                            cartItemId = cartItem.id,
                            variantId = cartItem.variantId,
                            requestedQuantity = cartItem.quantity,
                            manageInventory = variant.manageInventory,
                            allowBackorder = variant.allowBackorder,
                            requiredQuantity = variant.requiredQuantity
                        )
                    }

                    ctx.addMetadata("inventoryCheckItems", inventoryCheckItems)
                    StepResponse.of(inventoryCheckItems)
                }
            )

            // Step 2: Confirm inventory availability
            val confirmInventoryStep = createStep<List<InventoryCheckItem>, List<InventoryConfirmationResult>>(
                name = "confirm-inventory-step",
                execute = { checkItems, ctx ->
                    logger.debug { "Confirming inventory for ${checkItems.size} items" }

                    val confirmationResults = checkItems.map { checkItem ->
                        // If variant doesn't manage inventory, automatically pass
                        if (!checkItem.manageInventory) {
                            logger.debug {
                                "Variant ${checkItem.variantId} doesn't manage inventory, automatically available"
                            }
                            return@map InventoryConfirmationResult(
                                id = checkItem.cartItemId,
                                variantId = checkItem.variantId,
                                inventoryItemId = null,
                                requiredQuantity = checkItem.requiredQuantity,
                                allowBackorder = checkItem.allowBackorder,
                                requestedQuantity = checkItem.requestedQuantity,
                                availableQuantity = Int.MAX_VALUE, // Unlimited when not managing inventory
                                locationIds = emptyList()
                            )
                        }

                        // Get inventory levels for this variant
                        val inventoryLevels = inventoryLevelRepository.findByVariantId(checkItem.variantId)
                            .filter { it.deletedAt == null }

                        // Filter by sales channel if provided
                        val applicableLevels = if (input.salesChannelId != null) {
                            inventoryLevels.filter { level ->
                                // In a real implementation, you'd check if the stock location
                                // is associated with the sales channel
                                // For now, we'll include all locations
                                true
                            }
                        } else {
                            inventoryLevels
                        }

                        // Calculate total available quantity across all locations
                        val totalAvailable = applicableLevels.sumOf { it.stockedQuantity }
                        val locationIds = applicableLevels.mapNotNull { it.location?.id }

                        // Calculate total required quantity
                        val totalRequired = checkItem.requestedQuantity * checkItem.requiredQuantity

                        // Check if sufficient inventory is available
                        val hasSufficientInventory = totalAvailable >= totalRequired

                        logger.debug {
                            "Inventory check for variant ${checkItem.variantId}: " +
                            "requested=$totalRequired (${checkItem.requestedQuantity} x ${checkItem.requiredQuantity}), " +
                            "available=$totalAvailable, " +
                            "allowBackorder=${checkItem.allowBackorder}, " +
                            "sufficient=$hasSufficientInventory"
                        }

                        // If insufficient inventory and backorder not allowed, throw error
                        if (!hasSufficientInventory && !checkItem.allowBackorder) {
                            throw InsufficientInventoryException(
                                "Insufficient inventory for variant ${checkItem.variantId}. " +
                                "Requested: $totalRequired, Available: $totalAvailable. " +
                                "Backorder is not allowed for this variant."
                            )
                        }

                        // Log warning if backorder is being used
                        if (!hasSufficientInventory && checkItem.allowBackorder) {
                            logger.warn {
                                "Backorder allowed for variant ${checkItem.variantId}. " +
                                "Requested: $totalRequired, Available: $totalAvailable"
                            }
                        }

                        InventoryConfirmationResult(
                            id = checkItem.cartItemId,
                            variantId = checkItem.variantId,
                            inventoryItemId = checkItem.variantId, // In real impl, get from inventory_item link
                            requiredQuantity = checkItem.requiredQuantity,
                            allowBackorder = checkItem.allowBackorder,
                            requestedQuantity = checkItem.requestedQuantity,
                            availableQuantity = totalAvailable,
                            locationIds = locationIds
                        )
                    }

                    ctx.addMetadata("confirmationResults", confirmationResults)
                    logger.info { "Inventory confirmation completed for ${confirmationResults.size} items" }
                    StepResponse.of(confirmationResults)
                }
            )

            // Execute steps
            val checkItems = prepareInputStep.invoke(input, context).data
            val confirmationResults = confirmInventoryStep.invoke(checkItems, context).data

            logger.info {
                "Confirm variant inventory workflow completed. " +
                "Checked ${confirmationResults.size} items, all have sufficient inventory or allow backorder"
            }

            val output = ConfirmVariantInventoryOutput(items = confirmationResults)
            return WorkflowResult.success(output)

        } catch (e: InsufficientInventoryException) {
            logger.error { "Inventory confirmation failed: ${e.message}" }
            return WorkflowResult.failure(e)
        } catch (e: Exception) {
            logger.error(e) { "Confirm variant inventory workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}

/**
 * Internal data class for inventory check
 */
private data class InventoryCheckItem(
    val cartItemId: String?,
    val variantId: String,
    val requestedQuantity: Int,
    val manageInventory: Boolean,
    val allowBackorder: Boolean,
    val requiredQuantity: Int
)

/**
 * Exception thrown when inventory is insufficient
 */
class InsufficientInventoryException(message: String) : Exception(message)
