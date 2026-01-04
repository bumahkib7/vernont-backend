package com.vernont.workflow.flows.returns

import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnStatus
import com.vernont.application.order.OrderEventService
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
 * Input for rejecting a return
 */
data class RejectReturnInput(
    val returnId: String,
    val reason: String,
    val rejectedBy: String? = null
)

/**
 * Reject Return Workflow (Admin-initiated)
 *
 * This workflow allows an admin to reject a return request.
 * Returns can be rejected if they are REQUESTED or APPROVED but not yet RECEIVED.
 *
 * Steps:
 * 1. Load return
 * 2. Validate return can be rejected (status is REQUESTED or APPROVED)
 * 3. Update status to REJECTED with reason
 * 4. Restore OrderLineItem.returnedQuantity
 * 5. Create OrderEvent (RETURN_REJECTED)
 * 6. Return updated Return
 */
@Component
@WorkflowTypes(RejectReturnInput::class, ReturnResponse::class)
class RejectReturnWorkflow(
    private val returnRepository: ReturnRepository,
    private val orderLineItemRepository: OrderLineItemRepository,
    private val orderEventService: OrderEventService
) : Workflow<RejectReturnInput, ReturnResponse> {

    override val name = WorkflowConstants.RejectReturn.NAME

    @Transactional
    override suspend fun execute(
        input: RejectReturnInput,
        context: WorkflowContext
    ): WorkflowResult<ReturnResponse> {
        logger.info { "Starting reject return workflow for return: ${input.returnId}" }

        try {
            // Step 1: Load return
            val getReturnStep = createStep<String, Return>(
                name = "get-return-for-reject",
                execute = { returnId, ctx ->
                    logger.debug { "Loading return: $returnId" }

                    val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(returnId)
                        ?: throw IllegalArgumentException("Return not found: $returnId")

                    ctx.addMetadata("return", returnRequest)
                    StepResponse.of(returnRequest)
                }
            )

            // Step 2: Validate return can be rejected
            val validateRejectStep = createStep<Return, Unit>(
                name = "validate-return-can-reject",
                execute = { returnRequest, ctx ->
                    logger.debug { "Validating return can be rejected: ${returnRequest.status}" }

                    if (returnRequest.status !in listOf(ReturnStatus.REQUESTED, ReturnStatus.APPROVED)) {
                        throw IllegalStateException(
                            "Cannot reject return in status: ${returnRequest.status}. " +
                            "Only REQUESTED or APPROVED returns can be rejected."
                        )
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 3: Reject the return
            val rejectReturnStep = createStep<Return, Return>(
                name = "reject-return",
                execute = { returnRequest, ctx ->
                    logger.debug { "Rejecting return: ${returnRequest.id}" }

                    returnRequest.reject(input.reason)
                    val savedReturn = returnRepository.save(returnRequest)

                    logger.info { "Return rejected: ${savedReturn.id}, reason: ${input.reason}" }

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

            // Step 5: Record order event
            val recordEventStep = createStep<Return, Unit>(
                name = "record-return-rejected-event",
                execute = { returnRequest, ctx ->
                    logger.debug { "Recording return rejected event" }

                    orderEventService.recordReturnRejected(
                        orderId = returnRequest.orderId,
                        returnId = returnRequest.id,
                        reason = input.reason,
                        rejectedBy = input.rejectedBy
                    )

                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val returnRequest = getReturnStep.invoke(input.returnId, context).data
            validateRejectStep.invoke(returnRequest, context)
            val rejectedReturn = rejectReturnStep.invoke(returnRequest, context).data
            restoreLineItemsStep.invoke(rejectedReturn, context)
            recordEventStep.invoke(rejectedReturn, context)

            // Reload to get fresh state
            val finalReturn = returnRepository.findByIdAndDeletedAtIsNull(input.returnId)
                ?: throw IllegalStateException("Return not found after update")

            logger.info { "Reject return workflow completed. Return ID: ${finalReturn.id}" }

            return WorkflowResult.success(ReturnResponse.from(finalReturn))

        } catch (e: Exception) {
            logger.error(e) { "Reject return workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
