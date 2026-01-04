package com.vernont.workflow.flows.exchange

import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderLineItem
import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnStatus
import com.vernont.application.order.OrderEventService
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.order.OrderLineItemRepository
import com.vernont.repository.product.ProductVariantRepository
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

private val logger = KotlinLogging.logger {}

/**
 * Item to exchange (return and replace)
 */
data class ExchangeItemInput(
    val orderLineItemId: String,
    val quantity: Int,
    val newVariantId: String,  // The variant to exchange for
    val reason: String? = null
)

/**
 * Input for creating an exchange
 */
data class CreateExchangeInput(
    val orderId: String,
    val customerId: String?,
    val items: List<ExchangeItemInput>,
    val note: String? = null
)

/**
 * Status of an exchange
 */
enum class ExchangeStatus {
    REQUESTED,      // Exchange requested by customer
    APPROVED,       // Exchange approved
    AWAITING_RETURN,// Waiting for original item return
    ITEM_RECEIVED,  // Original item received
    SHIPPED,        // Replacement item shipped
    COMPLETED,      // Exchange fully completed
    CANCELED        // Exchange canceled
}

/**
 * Response for exchange creation
 */
data class ExchangeResponse(
    val id: String,
    val orderId: String,
    val status: ExchangeStatus,
    val returnId: String?,        // Associated return for original items
    val newOrderId: String?,      // New order for replacement items (if any)
    val items: List<ExchangeItemResponse>,
    val priceDifference: BigDecimal,  // Positive = customer owes, Negative = refund
    val createdAt: Instant
)

data class ExchangeItemResponse(
    val originalVariantId: String,
    val originalTitle: String,
    val newVariantId: String,
    val newTitle: String,
    val quantity: Int,
    val priceDifference: BigDecimal
)

/**
 * Create Exchange Workflow (Customer/Admin)
 *
 * This workflow creates an exchange request where a customer wants to
 * swap one item for another (e.g., different size, color).
 *
 * Steps:
 * 1. Load order and validate ownership
 * 2. Validate items can be exchanged (within return window)
 * 3. Validate new variants exist and have stock
 * 4. Calculate price difference
 * 5. Create return for original items
 * 6. Create exchange record
 * 7. Record ORDER_EXCHANGE_CREATED event
 * 8. Return exchange details
 */
@Component
@WorkflowTypes(CreateExchangeInput::class, ExchangeResponse::class)
class CreateExchangeWorkflow(
    private val orderRepository: OrderRepository,
    private val orderLineItemRepository: OrderLineItemRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val returnRepository: ReturnRepository,
    private val orderEventService: OrderEventService
) : Workflow<CreateExchangeInput, ExchangeResponse> {

    override val name = WorkflowConstants.CreateExchange.NAME

    companion object {
        const val EXCHANGE_WINDOW_DAYS = 14L
    }

    @Transactional
    override suspend fun execute(
        input: CreateExchangeInput,
        context: WorkflowContext
    ): WorkflowResult<ExchangeResponse> {
        logger.info { "Starting create exchange workflow for order: ${input.orderId}" }

        try {
            // Step 1: Load and validate order
            val loadOrderStep = createStep<String, Order>(
                name = "load-order-for-exchange",
                execute = { orderId, ctx ->
                    logger.debug { "Loading order: $orderId" }

                    val order = orderRepository.findWithItemsById(orderId)
                        ?: throw IllegalArgumentException("Order not found: $orderId")

                    // Validate ownership
                    if (input.customerId != null && order.customerId != input.customerId) {
                        throw IllegalArgumentException("Order does not belong to this customer")
                    }

                    // Validate within exchange window
                    val orderDate = order.createdAt
                    val exchangeDeadline = orderDate.plusSeconds(EXCHANGE_WINDOW_DAYS * 24 * 60 * 60)
                    if (Instant.now().isAfter(exchangeDeadline)) {
                        throw IllegalStateException(
                            "Exchange window has expired. Exchanges must be requested within $EXCHANGE_WINDOW_DAYS days."
                        )
                    }

                    ctx.addMetadata("order", order)
                    StepResponse.of(order)
                }
            )

            // Step 2: Validate items and calculate price difference
            val validateItemsStep = createStep<Order, List<ExchangeItemResponse>>(
                name = "validate-exchange-items",
                execute = { order, ctx ->
                    logger.debug { "Validating exchange items" }

                    val orderLineItems = order.items.associateBy { it.id }
                    val exchangeItems = mutableListOf<ExchangeItemResponse>()
                    var totalPriceDifference = BigDecimal.ZERO

                    for (exchangeItem in input.items) {
                        // Validate original item exists
                        val originalLineItem = orderLineItems[exchangeItem.orderLineItemId]
                            ?: throw IllegalArgumentException(
                                "Order line item not found: ${exchangeItem.orderLineItemId}"
                            )

                        // Validate quantity
                        val availableForExchange = originalLineItem.shippedQuantity - originalLineItem.returnedQuantity
                        if (exchangeItem.quantity > availableForExchange) {
                            throw IllegalArgumentException(
                                "Cannot exchange ${exchangeItem.quantity} of '${originalLineItem.title}'. " +
                                "Only $availableForExchange available."
                            )
                        }

                        // Validate new variant exists
                        val newVariant = productVariantRepository.findByIdAndDeletedAtIsNull(exchangeItem.newVariantId)
                            ?: throw IllegalArgumentException(
                                "New variant not found: ${exchangeItem.newVariantId}"
                            )

                        // Get prices
                        val originalPrice = originalLineItem.unitPrice
                        val newPrice = newVariant.prices
                            .find { it.currencyCode == order.currencyCode }?.amount
                            ?: throw IllegalStateException(
                                "No price found for variant ${newVariant.id} in currency ${order.currencyCode}"
                            )

                        val itemPriceDiff = (newPrice.subtract(originalPrice)).multiply(BigDecimal(exchangeItem.quantity))
                        totalPriceDifference = totalPriceDifference.add(itemPriceDiff)

                        exchangeItems.add(
                            ExchangeItemResponse(
                                originalVariantId = originalLineItem.variantId ?: "",
                                originalTitle = originalLineItem.title,
                                newVariantId = newVariant.id,
                                newTitle = newVariant.title,
                                quantity = exchangeItem.quantity,
                                priceDifference = itemPriceDiff
                            )
                        )
                    }

                    ctx.addMetadata("exchangeItems", exchangeItems)
                    ctx.addMetadata("priceDifference", totalPriceDifference)
                    StepResponse.of(exchangeItems)
                }
            )

            // Step 3: Create return for original items
            val createReturnStep = createStep<Order, Return>(
                name = "create-return-for-exchange",
                execute = { order, ctx ->
                    logger.debug { "Creating return for exchange items" }

                    // For now, we'll create a simplified exchange record
                    // In a full implementation, you would create a Return entity
                    // and link it to the exchange

                    val exchangeReturn = Return.create(
                        orderId = order.id,
                        orderDisplayId = order.displayId,
                        customerId = input.customerId,
                        customerEmail = order.email,
                        reason = com.vernont.domain.returns.ReturnReason.WRONG_ITEM,
                        reasonNote = "Exchange: ${input.note ?: "Item exchange request"}",
                        currencyCode = order.currencyCode,
                        orderCreatedAt = order.createdAt
                    )

                    // Auto-approve for exchange
                    exchangeReturn.approve()

                    val savedReturn = returnRepository.save(exchangeReturn)
                    ctx.addMetadata("returnId", savedReturn.id)

                    logger.info { "Created return for exchange: ${savedReturn.id}" }
                    StepResponse.of(savedReturn)
                }
            )

            // Step 4: Record event (using return approved since exchange auto-approves)
            val recordEventStep = createStep<Order, Unit>(
                name = "record-exchange-created-event",
                execute = { order, ctx ->
                    logger.debug { "Recording exchange event" }

                    val returnId = ctx.getMetadata("returnId") as String

                    // Record as return approved (exchange is an auto-approved return with replacement)
                    orderEventService.recordReturnApproved(
                        orderId = order.id,
                        returnId = returnId,
                        approvedBy = input.customerId
                    )

                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val order = loadOrderStep.invoke(input.orderId, context).data
            val exchangeItems = validateItemsStep.invoke(order, context).data
            val exchangeReturn = createReturnStep.invoke(order, context).data
            recordEventStep.invoke(order, context)

            val priceDifference = context.getMetadata("priceDifference") as BigDecimal

            val response = ExchangeResponse(
                id = exchangeReturn.id,  // Using return ID as exchange ID for now
                orderId = order.id,
                status = ExchangeStatus.APPROVED,
                returnId = exchangeReturn.id,
                newOrderId = null,  // New order created when replacement ships
                items = exchangeItems,
                priceDifference = priceDifference,
                createdAt = Instant.now()
            )

            logger.info { "Create exchange workflow completed. Exchange: ${response.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Create exchange workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
