package com.vernont.workflow.flows.inventory

import com.vernont.domain.inventory.InventoryLevel
import com.vernont.events.EventPublisher
import com.vernont.events.InventoryAdjusted
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.StockLocationRepository
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

private val logger = KotlinLogging.logger {}

/**
 * Reason for inventory adjustment
 */
enum class AdjustmentReason {
    RESTOCK,           // Received new inventory
    DAMAGED,           // Items damaged
    LOST,              // Items lost or missing
    FOUND,             // Previously lost items found
    CORRECTION,        // Manual correction
    RETURN_RECEIVED,   // Items returned by customer
    TRANSFER_IN,       // Transfer from another location
    TRANSFER_OUT,      // Transfer to another location
    CYCLE_COUNT,       // Inventory count adjustment
    OTHER              // Other reason
}

/**
 * Input for adjusting inventory
 */
data class AdjustInventoryInput(
    val inventoryLevelId: String? = null,
    val inventoryItemId: String? = null,
    val locationId: String? = null,
    val sku: String? = null,
    val adjustment: Int,
    val reason: AdjustmentReason,
    val note: String? = null,
    val adjustedBy: String? = null
)

/**
 * Response for inventory adjustment
 */
data class AdjustInventoryResponse(
    val inventoryLevelId: String,
    val inventoryItemId: String,
    val locationId: String,
    val previousQuantity: Int,
    val adjustment: Int,
    val newQuantity: Int,
    val reason: AdjustmentReason,
    val note: String?
)

/**
 * Adjust Inventory Workflow (Admin)
 *
 * This workflow handles inventory adjustments for various reasons:
 * - Restocking (receiving new inventory)
 * - Damage/loss recording
 * - Cycle count corrections
 * - Manual adjustments
 *
 * Steps:
 * 1. Resolve inventory level (by ID, SKU+location, or inventoryItemId+location)
 * 2. Validate adjustment (check for negative stock if decreasing)
 * 3. Apply adjustment
 * 4. Publish InventoryAdjusted event
 * 5. Return updated inventory level
 */
@Component
@WorkflowTypes(AdjustInventoryInput::class, AdjustInventoryResponse::class)
class AdjustInventoryWorkflow(
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val eventPublisher: EventPublisher
) : Workflow<AdjustInventoryInput, AdjustInventoryResponse> {

    override val name = WorkflowConstants.AdjustInventory.NAME

    @Transactional
    override suspend fun execute(
        input: AdjustInventoryInput,
        context: WorkflowContext
    ): WorkflowResult<AdjustInventoryResponse> {
        logger.info { "Starting adjust inventory workflow" }

        try {
            // Step 1: Resolve inventory level
            val resolveInventoryLevelStep = createStep<AdjustInventoryInput, InventoryLevel>(
                name = "resolve-inventory-level",
                execute = { inp, ctx ->
                    logger.debug { "Resolving inventory level" }

                    val inventoryLevel: InventoryLevel = when {
                        // Direct ID lookup
                        inp.inventoryLevelId != null -> {
                            inventoryLevelRepository.findByIdAndDeletedAtIsNull(inp.inventoryLevelId)
                                ?: throw IllegalArgumentException("Inventory level not found: ${inp.inventoryLevelId}")
                        }
                        // Lookup by SKU and location
                        inp.sku != null && inp.locationId != null -> {
                            val item = inventoryItemRepository.findBySkuAndDeletedAtIsNull(inp.sku)
                                ?: throw IllegalArgumentException("Inventory item not found with SKU: ${inp.sku}")
                            inventoryLevelRepository.findByInventoryItemIdAndLocationId(item.id, inp.locationId)
                                ?: throw IllegalArgumentException(
                                    "No inventory level found for SKU ${inp.sku} at location ${inp.locationId}"
                                )
                        }
                        // Lookup by inventory item ID and location
                        inp.inventoryItemId != null && inp.locationId != null -> {
                            inventoryLevelRepository.findByInventoryItemIdAndLocationId(inp.inventoryItemId, inp.locationId)
                                ?: throw IllegalArgumentException(
                                    "No inventory level found for item ${inp.inventoryItemId} at location ${inp.locationId}"
                                )
                        }
                        else -> {
                            throw IllegalArgumentException(
                                "Must provide either inventoryLevelId, or (sku + locationId), or (inventoryItemId + locationId)"
                            )
                        }
                    }

                    ctx.addMetadata("inventoryLevel", inventoryLevel)
                    ctx.addMetadata("previousQuantity", inventoryLevel.stockedQuantity)
                    StepResponse.of(inventoryLevel)
                }
            )

            // Step 2: Validate and apply adjustment
            val applyAdjustmentStep = createStep<InventoryLevel, InventoryLevel>(
                name = "apply-inventory-adjustment",
                execute = { level, ctx ->
                    logger.debug { "Applying inventory adjustment of ${input.adjustment} to level ${level.id}" }

                    val previousQty = level.stockedQuantity

                    // Validate that adjustment won't result in negative stock
                    val newQuantity = previousQty + input.adjustment
                    if (newQuantity < 0) {
                        throw IllegalStateException(
                            "Cannot adjust inventory: would result in negative stock. " +
                            "Current: $previousQty, Adjustment: ${input.adjustment}, Would be: $newQuantity"
                        )
                    }

                    // Apply the adjustment
                    level.adjustStockQuantity(input.adjustment)
                    val savedLevel = inventoryLevelRepository.save(level)

                    logger.info {
                        "Inventory adjusted: level=${savedLevel.id}, " +
                        "previous=$previousQty, adjustment=${input.adjustment}, new=${savedLevel.stockedQuantity}"
                    }

                    StepResponse.of(savedLevel)
                },
                compensate = { _, ctx ->
                    // Rollback the adjustment if workflow fails later
                    try {
                        val level = ctx.getMetadata("inventoryLevel") as? InventoryLevel
                        val previousQty = ctx.getMetadata("previousQuantity") as? Int
                        if (level != null && previousQty != null) {
                            val currentLevel = inventoryLevelRepository.findByIdAndDeletedAtIsNull(level.id)
                            if (currentLevel != null) {
                                val reverseAdjustment = previousQty - currentLevel.stockedQuantity
                                currentLevel.stockedQuantity = previousQty
                                currentLevel.recalculateAvailableQuantity()
                                inventoryLevelRepository.save(currentLevel)
                                logger.info { "Compensated: Reverted inventory level ${level.id} to $previousQty" }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate inventory adjustment" }
                    }
                }
            )

            // Step 3: Publish event
            val publishEventStep = createStep<InventoryLevel, Unit>(
                name = "publish-inventory-event",
                execute = { level, ctx ->
                    logger.debug { "Publishing inventory adjusted event" }

                    val previousQty = ctx.getMetadata("previousQuantity") as Int

                    eventPublisher.publish(
                        InventoryAdjusted(
                            aggregateId = level.id,
                            inventoryItemId = level.inventoryItem?.id ?: "",
                            locationId = level.location?.id ?: "",
                            adjustment = input.adjustment,
                            reason = "${input.reason}: ${input.note ?: "No note provided"}"
                        )
                    )

                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val inventoryLevel = resolveInventoryLevelStep.invoke(input, context).data
            val updatedLevel = applyAdjustmentStep.invoke(inventoryLevel, context).data
            publishEventStep.invoke(updatedLevel, context)

            val previousQty = context.getMetadata("previousQuantity") as Int

            val response = AdjustInventoryResponse(
                inventoryLevelId = updatedLevel.id,
                inventoryItemId = updatedLevel.inventoryItem?.id ?: "",
                locationId = updatedLevel.location?.id ?: "",
                previousQuantity = previousQty,
                adjustment = input.adjustment,
                newQuantity = updatedLevel.stockedQuantity,
                reason = input.reason,
                note = input.note
            )

            logger.info { "Adjust inventory workflow completed successfully" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Adjust inventory workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
