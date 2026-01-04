package com.vernont.workflow.flows.returns

import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnStatus
import com.vernont.repository.order.OrderLineItemRepository
import com.vernont.repository.returns.ReturnRepository
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
 * Input for canceling a return
 */
data class CancelReturnInput(
    val returnId: String,
    val customerId: String? = null,
    val canceledBy: String? = null
)

/**
 * Cancel Return Workflow (Customer-initiated)
 *
 * This workflow allows a customer to cancel their return request
 * before the items have been received at the warehouse.
 *
 * Steps:
 * 1. Load return and validate ownership
 * 2. Validate return can be canceled (status is REQUESTED or APPROVED)
 * 3. Update status to CANCELED
 * 4. Restore OrderLineItem.returnedQuantity
 * 5. Return updated Return
 */
@Component
@WorkflowTypes(CancelReturnInput::class, ReturnResponse::class)
class CancelReturnWorkflow(
    private val returnRepository: ReturnRepository,
    private val orderLineItemRepository: OrderLineItemRepository
) : Workflow<CancelReturnInput, ReturnResponse> {

    override val name = WorkflowConstants.CancelReturn.NAME

    @Transactional
    override suspend fun execute(
        input: CancelReturnInput,
        context: WorkflowContext
    ): WorkflowResult<ReturnResponse> {
        logger.info { "Starting cancel return workflow for return: ${input.returnId}" }

        try {
            // Step 1: Load return and validate ownership
            val getReturnStep = createStep<String, Return>(
                name = "get-return-for-cancel",
                execute = { returnId, ctx ->
                    logger.debug { "Loading return: $returnId" }

                    val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(returnId)
                        ?: throw IllegalArgumentException("Return not found: $returnId")

                    // Validate ownership - customerId is required for customer-initiated cancellations
                    if (returnRequest.customerId != null) {
                        if (input.customerId == null) {
                            throw IllegalArgumentException("Customer ID is required to cancel this return")
                        }
                        if (returnRequest.customerId != input.customerId) {
                            throw IllegalArgumentException("Return does not belong to this customer")
                        }
                    }

                    ctx.addMetadata("return", returnRequest)
                    StepResponse.of(returnRequest)
                }
            )

            // Step 2: Validate return can be canceled
            val validateCancelStep = createStep<Return, Unit>(
                name = "validate-return-can-cancel",
                execute = { returnRequest, ctx ->
                    logger.debug { "Validating return can be canceled: ${returnRequest.status}" }

                    if (!returnRequest.canCancel()) {
                        throw IllegalStateException(
                            "Cannot cancel return in status: ${returnRequest.status}. " +
                            "Only REQUESTED or APPROVED returns can be canceled."
                        )
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 3: Update status to CANCELED
            val cancelReturnStep = createStep<Return, Return>(
                name = "cancel-return",
                execute = { returnRequest, ctx ->
                    logger.debug { "Canceling return: ${returnRequest.id}" }

                    returnRequest.cancel()
                    val savedReturn = returnRepository.save(returnRequest)

                    logger.info { "Return canceled: ${savedReturn.id}" }

                    StepResponse.of(savedReturn)
                }
            )

            // Step 4: Restore OrderLineItem.returnedQuantity
            val restoreLineItemsStep = createStep<Return, Unit>(
                name = "restore-line-item-quantities",
                execute = { returnRequest, ctx ->
                    logger.debug { "Restoring order line item quantities" }

                    for (returnItem in returnRequest.items) {
                        val lineItem = orderLineItemRepository.findById(returnItem.orderLineItemId).orElse(null)
                        if (lineItem != null) {
                            lineItem.returnedQuantity = maxOf(0, lineItem.returnedQuantity - returnItem.quantity)
                            orderLineItemRepository.save(lineItem)

                            logger.debug {
                                "Restored line item ${lineItem.id}: returnedQuantity = ${lineItem.returnedQuantity}"
                            }
                        }
                    }

                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val returnRequest = getReturnStep.invoke(input.returnId, context).data
            validateCancelStep.invoke(returnRequest, context)
            val canceledReturn = cancelReturnStep.invoke(returnRequest, context).data
            restoreLineItemsStep.invoke(canceledReturn, context)

            // Reload to get fresh state
            val finalReturn = returnRepository.findByIdAndDeletedAtIsNull(input.returnId)
                ?: throw IllegalStateException("Return not found after update")

            logger.info { "Cancel return workflow completed. Return ID: ${finalReturn.id}" }

            return WorkflowResult.success(ReturnResponse.from(finalReturn))

        } catch (e: Exception) {
            logger.error(e) { "Cancel return workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
