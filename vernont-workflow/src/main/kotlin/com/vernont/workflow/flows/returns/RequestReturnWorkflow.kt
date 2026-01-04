package com.vernont.workflow.flows.returns

import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderLineItem
import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnItem
import com.vernont.domain.returns.ReturnReason
import com.vernont.domain.returns.ReturnStatus
import com.vernont.application.order.OrderEventService
import com.vernont.repository.order.OrderLineItemRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.returns.ReturnItemRepository
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
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

/**
 * Input for a single return item
 */
data class ReturnItemInput(
    val orderLineItemId: String,
    val quantity: Int,
    val reason: ReturnReason? = null,
    val note: String? = null
)

/**
 * Input for requesting a return
 */
data class RequestReturnInput(
    val orderId: String,
    val customerId: String?,
    val customerEmail: String?,
    val items: List<ReturnItemInput>,
    val reason: ReturnReason,
    val reasonNote: String? = null
)

/**
 * Response for return request
 */
data class ReturnResponse(
    val id: String,
    val orderId: String,
    val orderDisplayId: Int?,
    val status: ReturnStatus,
    val reason: ReturnReason,
    val reasonNote: String?,
    val refundAmount: BigDecimal,
    val currencyCode: String,
    val items: List<ReturnItemResponse>,
    val requestedAt: Instant,
    val approvedAt: Instant?,
    val returnDeadline: Instant,
    val daysRemaining: Long
) {
    companion object {
        fun from(returnRequest: Return): ReturnResponse {
            return ReturnResponse(
                id = returnRequest.id,
                orderId = returnRequest.orderId,
                orderDisplayId = returnRequest.orderDisplayId,
                status = returnRequest.status,
                reason = returnRequest.reason,
                reasonNote = returnRequest.reasonNote,
                refundAmount = returnRequest.refundAmount,
                currencyCode = returnRequest.currencyCode,
                items = returnRequest.items.map { ReturnItemResponse.from(it) },
                requestedAt = returnRequest.requestedAt,
                approvedAt = returnRequest.approvedAt,
                returnDeadline = returnRequest.returnDeadline,
                daysRemaining = returnRequest.getDaysRemaining()
            )
        }
    }
}

data class ReturnItemResponse(
    val id: String,
    val orderLineItemId: String,
    val variantId: String?,
    val title: String,
    val description: String?,
    val thumbnail: String?,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val total: BigDecimal
) {
    companion object {
        fun from(item: ReturnItem): ReturnItemResponse {
            return ReturnItemResponse(
                id = item.id,
                orderLineItemId = item.orderLineItemId,
                variantId = item.variantId,
                title = item.title,
                description = item.description,
                thumbnail = item.thumbnail,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                total = item.total
            )
        }
    }
}

/**
 * Request Return Workflow
 *
 * This workflow handles the creation of a return request for an order.
 * Returns are auto-approved if within the 14-day window from order creation.
 *
 * Steps:
 * 1. Load order and validate ownership
 * 2. Check return eligibility (14-day window from order.createdAt)
 * 3. Validate items exist and quantities
 * 4. Check items not already returned
 * 5. Calculate refund amount (sum of item prices)
 * 6. Create Return entity with status = APPROVED (auto-approve within 14 days)
 * 7. Create ReturnItem entities
 * 8. Update OrderLineItem.returnedQuantity
 * 9. Create OrderEvent (RETURN_REQUESTED + RETURN_APPROVED)
 * 10. Return the created Return
 */
@Component
@WorkflowTypes(RequestReturnInput::class, ReturnResponse::class)
class RequestReturnWorkflow(
    private val orderRepository: OrderRepository,
    private val orderLineItemRepository: OrderLineItemRepository,
    private val returnRepository: ReturnRepository,
    private val returnItemRepository: ReturnItemRepository,
    private val orderEventService: OrderEventService
) : Workflow<RequestReturnInput, ReturnResponse> {

    override val name = WorkflowConstants.RequestReturn.NAME

    @Transactional
    override suspend fun execute(
        input: RequestReturnInput,
        context: WorkflowContext
    ): WorkflowResult<ReturnResponse> {
        logger.info { "Starting request return workflow for order: ${input.orderId}" }

        try {
            // Step 1: Load order and validate ownership
            val getOrderStep = createStep<String, Order>(
                name = "get-order-for-return",
                execute = { orderId, ctx ->
                    logger.debug { "Loading order: $orderId" }

                    val order = orderRepository.findWithItemsById(orderId)
                        ?: throw IllegalArgumentException("Order not found: $orderId")

                    if (order.deletedAt != null) {
                        throw IllegalStateException("Order is deleted: $orderId")
                    }

                    // Validate ownership
                    if (input.customerId != null && order.customerId != input.customerId) {
                        throw IllegalArgumentException("Order does not belong to this customer")
                    }

                    ctx.addMetadata("order", order)
                    StepResponse.of(order)
                }
            )

            // Step 2: Check return eligibility (14-day window)
            val checkEligibilityStep = createStep<Order, Instant>(
                name = "check-return-eligibility",
                execute = { order, ctx ->
                    logger.debug { "Checking return eligibility for order: ${order.id}" }

                    // Calculate return deadline (14 days from order creation)
                    val orderCreatedAt = order.createdAt
                    val returnDeadline = orderCreatedAt.plus(Return.RETURN_WINDOW_DAYS, ChronoUnit.DAYS)

                    if (Instant.now().isAfter(returnDeadline)) {
                        val daysSinceOrder = ChronoUnit.DAYS.between(orderCreatedAt, Instant.now())
                        throw IllegalStateException(
                            "Return window has expired. Order was placed $daysSinceOrder days ago. " +
                            "Returns must be requested within ${Return.RETURN_WINDOW_DAYS} days of order."
                        )
                    }

                    ctx.addMetadata("returnDeadline", returnDeadline)
                    logger.info { "Order is within return window. Deadline: $returnDeadline" }

                    StepResponse.of(returnDeadline)
                }
            )

            // Step 3 & 4: Validate items and check not already returned
            val validateItemsStep = createStep<RequestReturnInput, List<OrderLineItem>>(
                name = "validate-return-items",
                execute = { inp, ctx ->
                    logger.debug { "Validating return items" }

                    val order = ctx.getMetadata("order") as Order
                    val orderLineItems = order.items.associateBy { it.id }
                    val validatedItems = mutableListOf<OrderLineItem>()

                    for (returnItem in inp.items) {
                        val lineItem = orderLineItems[returnItem.orderLineItemId]
                            ?: throw IllegalArgumentException(
                                "Order line item not found: ${returnItem.orderLineItemId}"
                            )

                        // Check quantity is valid
                        if (returnItem.quantity <= 0) {
                            throw IllegalArgumentException(
                                "Return quantity must be positive for item: ${lineItem.title}"
                            )
                        }

                        // Check not returning more than shipped
                        val availableForReturn = lineItem.shippedQuantity - lineItem.returnedQuantity
                        if (returnItem.quantity > availableForReturn) {
                            throw IllegalArgumentException(
                                "Cannot return ${returnItem.quantity} of '${lineItem.title}'. " +
                                "Only $availableForReturn available for return."
                            )
                        }

                        // Check if already has pending return for this item
                        val existingReturnQuantity = returnItemRepository
                            .sumReturnedQuantityByOrderLineItemId(returnItem.orderLineItemId) ?: 0

                        val totalAfterReturn = existingReturnQuantity + returnItem.quantity
                        if (totalAfterReturn > lineItem.shippedQuantity) {
                            throw IllegalArgumentException(
                                "Cannot return ${returnItem.quantity} of '${lineItem.title}'. " +
                                "Already have pending/completed returns for $existingReturnQuantity units."
                            )
                        }

                        validatedItems.add(lineItem)
                    }

                    ctx.addMetadata("validatedLineItems", validatedItems)
                    logger.info { "Validated ${validatedItems.size} items for return" }

                    StepResponse.of(validatedItems)
                }
            )

            // Step 5 & 6: Create Return entity (auto-approved within 14-day window)
            val createReturnStep = createStep<RequestReturnInput, Return>(
                name = "create-return-entity",
                execute = { inp, ctx ->
                    logger.debug { "Creating return entity" }

                    val order = ctx.getMetadata("order") as Order
                    val returnDeadline = ctx.getMetadata("returnDeadline") as Instant

                    val returnRequest = Return.create(
                        orderId = order.id,
                        orderDisplayId = order.displayId,
                        customerId = inp.customerId,
                        customerEmail = inp.customerEmail ?: order.email,
                        reason = inp.reason,
                        reasonNote = inp.reasonNote,
                        currencyCode = order.currencyCode,
                        orderCreatedAt = order.createdAt
                    )

                    // Auto-approve since we're within the 14-day window
                    returnRequest.approve()

                    val savedReturn = returnRepository.save(returnRequest)
                    ctx.addMetadata("returnId", savedReturn.id)

                    logger.info { "Created return: ${savedReturn.id} with status: ${savedReturn.status}" }

                    StepResponse.of(savedReturn)
                }
            )

            // Step 7: Create ReturnItem entities
            val createReturnItemsStep = createStep<RequestReturnInput, Return>(
                name = "create-return-items",
                execute = { inp, ctx ->
                    logger.debug { "Creating return items" }

                    val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(
                        ctx.getMetadata("returnId") as String
                    ) ?: throw IllegalStateException("Return not found after creation")

                    val order = ctx.getMetadata("order") as Order
                    val orderLineItems = order.items.associateBy { it.id }

                    for (itemInput in inp.items) {
                        val lineItem = orderLineItems[itemInput.orderLineItemId]!!

                        val returnItem = ReturnItem.create(
                            orderLineItemId = itemInput.orderLineItemId,
                            variantId = lineItem.variantId,
                            title = lineItem.title,
                            description = lineItem.description,
                            thumbnail = lineItem.thumbnail,
                            quantity = itemInput.quantity,
                            unitPrice = lineItem.unitPrice
                        )

                        returnRequest.addItem(returnItem)
                    }

                    val savedReturn = returnRepository.save(returnRequest)

                    logger.info { "Created ${inp.items.size} return items, total refund: ${savedReturn.refundAmount}" }

                    StepResponse.of(savedReturn)
                }
            )

            // Step 8: Update OrderLineItem.returnedQuantity
            val updateLineItemsStep = createStep<RequestReturnInput, Unit>(
                name = "update-order-line-items",
                execute = { inp, ctx ->
                    logger.debug { "Updating order line item returned quantities" }

                    for (itemInput in inp.items) {
                        val lineItem = orderLineItemRepository.findById(itemInput.orderLineItemId).orElseThrow {
                            IllegalArgumentException("Order line item not found: ${itemInput.orderLineItemId}")
                        }

                        lineItem.returnItem(itemInput.quantity)
                        orderLineItemRepository.save(lineItem)

                        logger.debug { "Updated line item ${lineItem.id}: returnedQuantity = ${lineItem.returnedQuantity}" }
                    }

                    StepResponse.of(Unit)
                },
                compensate = { inp, ctx ->
                    // Rollback returned quantities if workflow fails
                    for (itemInput in inp.items) {
                        try {
                            val lineItem = orderLineItemRepository.findById(itemInput.orderLineItemId).orElse(null)
                            if (lineItem != null) {
                                lineItem.returnedQuantity = maxOf(0, lineItem.returnedQuantity - itemInput.quantity)
                                orderLineItemRepository.save(lineItem)
                                logger.info { "Compensated: Reverted returnedQuantity for ${lineItem.id}" }
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate line item: ${itemInput.orderLineItemId}" }
                        }
                    }
                }
            )

            // Step 9: Record order events
            val recordEventsStep = createStep<Return, Unit>(
                name = "record-return-events",
                execute = { returnRequest, ctx ->
                    logger.debug { "Recording return events" }

                    // Record RETURN_REQUESTED event
                    orderEventService.recordReturnRequested(
                        orderId = returnRequest.orderId,
                        returnId = returnRequest.id,
                        itemCount = returnRequest.getTotalItemCount(),
                        refundAmount = returnRequest.refundAmount,
                        currencyCode = returnRequest.currencyCode,
                        requestedBy = input.customerEmail ?: input.customerId
                    )

                    // Since we auto-approve, also record RETURN_APPROVED
                    orderEventService.recordReturnApproved(
                        orderId = returnRequest.orderId,
                        returnId = returnRequest.id,
                        approvedBy = null // Auto-approved by system
                    )

                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val order = getOrderStep.invoke(input.orderId, context).data
            checkEligibilityStep.invoke(order, context)
            validateItemsStep.invoke(input, context)
            createReturnStep.invoke(input, context)
            val returnRequest = createReturnItemsStep.invoke(input, context).data
            updateLineItemsStep.invoke(input, context)
            recordEventsStep.invoke(returnRequest, context)

            // Reload to get fresh state
            val finalReturn = returnRepository.findByIdAndDeletedAtIsNull(returnRequest.id)
                ?: throw IllegalStateException("Return not found after creation")

            logger.info { "Request return workflow completed. Return ID: ${finalReturn.id}" }

            return WorkflowResult.success(ReturnResponse.from(finalReturn))

        } catch (e: Exception) {
            logger.error(e) { "Request return workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
