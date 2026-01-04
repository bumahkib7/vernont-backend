package com.vernont.workflow.flows.returns

import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnItem
import com.vernont.domain.returns.ReturnStatus
import com.vernont.application.order.OrderEventService
import com.vernont.events.EventPublisher
import com.vernont.events.InventoryAdjusted
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.returns.ReturnRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Input for receiving a return
 */
data class ReceiveReturnInput(
    val returnId: String,
    val receivedBy: String? = null,
    val notes: String? = null
)

/**
 * Receive Return Workflow
 *
 * This workflow marks returned items as received at the warehouse.
 * After this step, the return is ready for refund processing.
 *
 * Steps:
 * 1. Load return and validate status is APPROVED
 * 2. Update status to RECEIVED
 * 3. Set receivedAt timestamp
 * 4. Restore inventory for returned items
 * 5. Create OrderEvent (RETURN_RECEIVED)
 * 6. Return updated Return
 */
@Component
@WorkflowTypes(ReceiveReturnInput::class, ReturnResponse::class)
class ReceiveReturnWorkflow(
    private val returnRepository: ReturnRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val orderEventService: OrderEventService,
    private val eventPublisher: EventPublisher
) : Workflow<ReceiveReturnInput, ReturnResponse> {

    override val name = WorkflowConstants.ReceiveReturn.NAME

    @Transactional
    override suspend fun execute(
        input: ReceiveReturnInput,
        context: WorkflowContext
    ): WorkflowResult<ReturnResponse> {
        logger.info { "Starting receive return workflow for return: ${input.returnId}" }

        try {
            // Step 1: Load return and validate status
            val getReturnStep = createStep<String, Return>(
                name = "get-return-for-receive",
                execute = { returnId, ctx ->
                    logger.debug { "Loading return: $returnId" }

                    val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(returnId)
                        ?: throw IllegalArgumentException("Return not found: $returnId")

                    if (returnRequest.status != ReturnStatus.APPROVED) {
                        throw IllegalStateException(
                            "Can only receive approved returns. Current status: ${returnRequest.status}"
                        )
                    }

                    ctx.addMetadata("return", returnRequest)
                    StepResponse.of(returnRequest)
                }
            )

            // Step 2 & 3: Mark as received
            val markReceivedStep = createStep<Return, Return>(
                name = "mark-return-received",
                execute = { returnRequest, ctx ->
                    logger.debug { "Marking return as received: ${returnRequest.id}" }

                    returnRequest.markReceived()
                    val savedReturn = returnRepository.save(returnRequest)

                    logger.info { "Return marked as received: ${savedReturn.id}" }

                    StepResponse.of(savedReturn)
                },
                compensate = { returnRequest, ctx ->
                    // Rollback to APPROVED if workflow fails
                    try {
                        val ret = returnRepository.findByIdAndDeletedAtIsNull(returnRequest.id)
                        if (ret != null && ret.status == ReturnStatus.RECEIVED) {
                            ret.status = ReturnStatus.APPROVED
                            ret.receivedAt = null
                            returnRepository.save(ret)
                            logger.info { "Compensated: Reverted return ${ret.id} to APPROVED" }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate return status: ${returnRequest.id}" }
                    }
                }
            )

            // Step 4: Restore inventory for returned items
            val restoreInventoryStep = createStep<Return, Int>(
                name = "restore-inventory",
                execute = { returnRequest, ctx ->
                    logger.debug { "Restoring inventory for return: ${returnRequest.id}" }

                    var totalRestored = 0
                    val restoredItems = mutableListOf<RestoredInventoryItem>()

                    for (item in returnRequest.items) {
                        val variantId = item.variantId ?: continue

                        // Find inventory levels for this variant
                        val inventoryLevels = inventoryLevelRepository.findByVariantId(variantId)

                        if (inventoryLevels.isEmpty()) {
                            logger.warn { "No inventory levels found for variant $variantId, skipping restoration" }
                            continue
                        }

                        // Use the first inventory level (could enhance to use specific location)
                        val inventoryLevel = inventoryLevels.first()

                        // Restore the stock
                        inventoryLevel.adjustStockQuantity(item.quantity)
                        inventoryLevelRepository.save(inventoryLevel)

                        // Track for compensation
                        restoredItems.add(RestoredInventoryItem(
                            inventoryLevelId = inventoryLevel.id,
                            quantity = item.quantity
                        ))

                        // Publish inventory adjusted event
                        eventPublisher.publish(
                            InventoryAdjusted(
                                aggregateId = inventoryLevel.id,
                                inventoryItemId = inventoryLevel.inventoryItem?.id ?: "",
                                locationId = inventoryLevel.location?.id ?: "",
                                adjustment = item.quantity,
                                reason = "Return received: ${returnRequest.id}"
                            )
                        )

                        totalRestored += item.quantity
                        logger.info { "Restored ${item.quantity} units for variant $variantId at level ${inventoryLevel.id}" }
                    }

                    // Store for potential compensation
                    ctx.addMetadata("restoredItems", restoredItems)

                    logger.info { "Total inventory restored: $totalRestored units for return ${returnRequest.id}" }
                    StepResponse.of(totalRestored)
                },
                compensate = { _, ctx ->
                    // Reverse the inventory adjustments if workflow fails
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val restoredItems = ctx.getMetadata("restoredItems") as? List<RestoredInventoryItem> ?: emptyList()

                        for (item in restoredItems) {
                            val level = inventoryLevelRepository.findByIdAndDeletedAtIsNull(item.inventoryLevelId)
                            if (level != null) {
                                level.adjustStockQuantity(-item.quantity)
                                inventoryLevelRepository.save(level)
                                logger.info { "Compensated: Removed ${item.quantity} units from level ${item.inventoryLevelId}" }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate inventory restoration" }
                    }
                }
            )

            // Step 5: Record order event
            val recordEventStep = createStep<Return, Unit>(
                name = "record-return-received-event",
                execute = { returnRequest, _ ->
                    logger.debug { "Recording return received event" }

                    orderEventService.recordReturnReceived(
                        orderId = returnRequest.orderId,
                        returnId = returnRequest.id,
                        receivedBy = input.receivedBy
                    )

                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val returnRequest = getReturnStep.invoke(input.returnId, context).data
            val receivedReturn = markReceivedStep.invoke(returnRequest, context).data
            restoreInventoryStep.invoke(receivedReturn, context)
            recordEventStep.invoke(receivedReturn, context)

            // Reload to get fresh state
            val finalReturn = withContext(Dispatchers.IO) {
                returnRepository.findByIdAndDeletedAtIsNull(input.returnId)
            }?: throw IllegalStateException("Return not found after update")

            logger.info { "Receive return workflow completed. Return ID: ${finalReturn.id}" }

            return WorkflowResult.success(ReturnResponse.from(finalReturn))

        } catch (e: Exception) {
            logger.error(e) { "Receive return workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}

/**
 * Tracks restored inventory for compensation
 */
private data class RestoredInventoryItem(
    val inventoryLevelId: String,
    val quantity: Int
)
