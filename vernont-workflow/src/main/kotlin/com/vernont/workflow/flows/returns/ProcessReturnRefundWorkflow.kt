package com.vernont.workflow.flows.returns

import com.vernont.domain.order.Order
import com.vernont.domain.order.PaymentStatus
import com.vernont.domain.payment.RefundReason
import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnStatus
import com.vernont.application.order.OrderEventService
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.payment.PaymentRepository
import com.vernont.repository.returns.ReturnRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.flows.payment.RefundPaymentInput
import com.vernont.workflow.flows.payment.RefundPaymentWorkflow
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Input for processing a return refund
 */
data class ProcessReturnRefundInput(
    val returnId: String,
    val processedBy: String? = null
)

/**
 * Process Return Refund Workflow
 *
 * This workflow processes the refund for a received return.
 * It uses the RefundPaymentWorkflow to process the actual refund.
 *
 * Steps:
 * 1. Load return with order
 * 2. Validate status is RECEIVED
 * 3. Find order's payment
 * 4. Call RefundPaymentWorkflow with the return amount
 * 5. Link refund to return
 * 6. Update return status to REFUNDED
 * 7. Update order.paymentStatus (PARTIALLY_REFUNDED or REFUNDED)
 * 8. Create OrderEvent (PAYMENT_REFUNDED)
 * 9. Return updated Return
 */
@Component
@WorkflowTypes(ProcessReturnRefundInput::class, ReturnResponse::class)
class ProcessReturnRefundWorkflow(
    private val returnRepository: ReturnRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val refundPaymentWorkflow: RefundPaymentWorkflow,
    private val orderEventService: OrderEventService
) : Workflow<ProcessReturnRefundInput, ReturnResponse> {

    override val name = WorkflowConstants.ProcessReturnRefund.NAME

    @Transactional
    override suspend fun execute(
        input: ProcessReturnRefundInput,
        context: WorkflowContext
    ): WorkflowResult<ReturnResponse> {
        logger.info { "Starting process return refund workflow for return: ${input.returnId}" }

        try {
            // Step 1: Load return
            val getReturnStep = createStep<String, Return>(
                name = "get-return-for-refund",
                execute = { returnId, ctx ->
                    logger.debug { "Loading return: $returnId" }

                    val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(returnId)
                        ?: throw IllegalArgumentException("Return not found: $returnId")

                    ctx.addMetadata("return", returnRequest)
                    StepResponse.of(returnRequest)
                }
            )

            // Step 2: Validate status
            val validateStatusStep = createStep<Return, Unit>(
                name = "validate-return-status",
                execute = { returnRequest, _ ->
                    logger.debug { "Validating return status: ${returnRequest.status}" }

                    if (!returnRequest.canProcessRefund()) {
                        throw IllegalStateException(
                            "Cannot process refund. Return status must be RECEIVED. Current: ${returnRequest.status}"
                        )
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 3: Find order's payment
            val findPaymentStep = createStep<Return, String>(
                name = "find-order-payment",
                execute = { returnRequest, ctx ->
                    logger.debug { "Finding payment for order: ${returnRequest.orderId}" }

                    val payments = paymentRepository.findByOrderIdAndDeletedAtIsNull(returnRequest.orderId)

                    // Find a captured payment to refund
                    val capturedPayment = payments.find {
                        it.status == com.vernont.domain.payment.PaymentStatus.CAPTURED ||
                        it.status == com.vernont.domain.payment.PaymentStatus.PARTIALLY_REFUNDED
                    } ?: throw IllegalStateException(
                        "No captured payment found for order: ${returnRequest.orderId}"
                    )

                    // Validate refund amount
                    val availableForRefund = capturedPayment.amount - capturedPayment.amountRefunded
                    if (returnRequest.refundAmount > availableForRefund) {
                        throw IllegalStateException(
                            "Refund amount ${returnRequest.refundAmount} exceeds available balance $availableForRefund"
                        )
                    }

                    ctx.addMetadata("paymentId", capturedPayment.id)
                    ctx.addMetadata("payment", capturedPayment)

                    logger.info { "Found payment ${capturedPayment.id} for refund" }

                    StepResponse.of(capturedPayment.id)
                }
            )

            // Step 4: Process refund
            val processRefundStep = createStep<Return, String>(
                name = "process-refund-payment",
                execute = { returnRequest, ctx ->
                    logger.debug { "Processing refund for return: ${returnRequest.id}" }

                    val paymentId = ctx.getMetadata("paymentId") as String

                    val refundInput = RefundPaymentInput(
                        paymentId = paymentId,
                        amount = returnRequest.refundAmount,
                        reason = RefundReason.RETURN,
                        note = "Refund for return ${returnRequest.id}",
                        createdBy = input.processedBy
                    )

                    val refundResult = refundPaymentWorkflow.execute(refundInput, context)

                    if (refundResult.isFailure()) {
                        val failure = refundResult as WorkflowResult.Failure
                        throw IllegalStateException(
                            "Failed to process refund: ${failure.error.message}"
                        )
                    }

                    val refund = refundResult.getOrThrow()
                    ctx.addMetadata("refundId", refund.id)

                    logger.info { "Refund processed: ${refund.id}, amount: ${refund.amount}" }

                    StepResponse.of(refund.id)
                }
            )

            // Step 5 & 6: Update return status to REFUNDED
            val updateReturnStep = createStep<String, Return>(
                name = "update-return-refunded",
                execute = { refundId, _ ->
                    logger.debug { "Updating return status to REFUNDED" }

                    val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(input.returnId)
                        ?: throw IllegalStateException("Return not found after refund")

                    returnRequest.markRefunded(refundId)
                    val savedReturn = returnRepository.save(returnRequest)

                    logger.info { "Return ${savedReturn.id} marked as REFUNDED" }

                    StepResponse.of(savedReturn)
                }
            )

            // Step 7: Update order payment status
            val updateOrderStep = createStep<Return, Order>(
                name = "update-order-payment-status",
                execute = { returnRequest, _ ->
                    logger.debug { "Updating order payment status" }

                    val order = orderRepository.findById(returnRequest.orderId).orElseThrow {
                        IllegalStateException("Order not found: ${returnRequest.orderId}")
                    }

                    // Check if order is fully or partially refunded
                    val totalRefundedForOrder = calculateTotalRefundedForOrder(order.id)
                    val orderTotal = order.total

                    if (totalRefundedForOrder >= orderTotal) {
                        order.paymentStatus = PaymentStatus.REFUNDED
                        logger.info { "Order ${order.id} fully refunded" }
                    } else if (totalRefundedForOrder > BigDecimal.ZERO) {
                        order.paymentStatus = PaymentStatus.PARTIALLY_REFUNDED
                        logger.info { "Order ${order.id} partially refunded: $totalRefundedForOrder of $orderTotal" }
                    }

                    val savedOrder = orderRepository.save(order)
                    StepResponse.of(savedOrder)
                }
            )

            // Step 8: Record payment refunded event
            val recordEventStep = createStep<Return, Unit>(
                name = "record-refund-event",
                execute = { returnRequest, ctx ->
                    logger.debug { "Recording refund event" }

                    val refundId = ctx.getMetadata("refundId") as String

                    orderEventService.recordPaymentRefunded(
                        orderId = returnRequest.orderId,
                        refundId = refundId,
                        amount = returnRequest.refundAmount,
                        currencyCode = returnRequest.currencyCode,
                        reason = "Return refund",
                        refundedBy = input.processedBy
                    )

                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val returnRequest = getReturnStep.invoke(input.returnId, context).data
            validateStatusStep.invoke(returnRequest, context)
            findPaymentStep.invoke(returnRequest, context)
            processRefundStep.invoke(returnRequest, context)
            val refundedReturn = updateReturnStep.invoke(context.getMetadata("refundId") as String, context).data
            updateOrderStep.invoke(refundedReturn, context)
            recordEventStep.invoke(refundedReturn, context)

            // Reload to get fresh state
            val finalReturn = withContext(Dispatchers.IO) {
                returnRepository.findByIdAndDeletedAtIsNull(input.returnId)
            }
                ?: throw IllegalStateException("Return not found after update")

            logger.info { "Process return refund workflow completed. Return ID: ${finalReturn.id}" }

            return WorkflowResult.success(ReturnResponse.from(finalReturn))

        } catch (e: Exception) {
            logger.error(e) { "Process return refund workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

    private fun calculateTotalRefundedForOrder(orderId: String): BigDecimal {
        val returns = returnRepository.findByOrderIdAndDeletedAtIsNull(orderId)
        return returns
            .filter { it.status == ReturnStatus.REFUNDED }
            .fold(BigDecimal.ZERO) { acc, ret -> acc.add(ret.refundAmount) }
    }
}
