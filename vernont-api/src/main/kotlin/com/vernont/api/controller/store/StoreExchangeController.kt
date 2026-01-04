package com.vernont.api.controller.store

import com.vernont.domain.auth.UserContext
import com.vernont.domain.auth.getCurrentUserContext
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.returns.ReturnRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.exchange.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

// =============================================================================
// Request/Response DTOs
// =============================================================================

data class ExchangeItemRequest(
    val orderLineItemId: String,
    val quantity: Int,
    val newVariantId: String,
    val reason: String? = null
)

data class CreateExchangeRequest(
    val orderId: String,
    val items: List<ExchangeItemRequest>,
    val note: String? = null
)

data class ExchangeItemDto(
    val originalVariantId: String,
    val originalTitle: String,
    val newVariantId: String,
    val newTitle: String,
    val quantity: Int,
    val priceDifference: Int  // In cents
)

data class ExchangeDto(
    val id: String,
    val orderId: String,
    val status: String,
    val returnId: String?,
    val newOrderId: String?,
    val items: List<ExchangeItemDto>,
    val priceDifference: Int,  // In cents (positive = customer owes, negative = refund)
    val currencyCode: String,
    val createdAt: String
)

@RestController
@RequestMapping("/store/exchanges")
@Tag(name = "Store Exchanges", description = "Customer exchange endpoints")
class StoreExchangeController(
    private val workflowEngine: WorkflowEngine,
    private val orderRepository: OrderRepository,
    private val returnRepository: ReturnRepository
) {

    @Operation(summary = "Create an exchange request")
    @PostMapping
    suspend fun createExchange(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestBody request: CreateExchangeRequest
    ): ResponseEntity<Any> {
        val context = userContext ?: getCurrentUserContext()

        logger.info { "POST /store/exchanges - orderId=${request.orderId}, items=${request.items.size}" }

        // Get customer ID from context
        val customerId = context?.customerId

        val input = CreateExchangeInput(
            orderId = request.orderId,
            customerId = customerId,
            items = request.items.map { item ->
                ExchangeItemInput(
                    orderLineItemId = item.orderLineItemId,
                    quantity = item.quantity,
                    newVariantId = item.newVariantId,
                    reason = item.reason
                )
            },
            note = request.note
        )

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CreateExchange.NAME,
            input = input,
            inputType = CreateExchangeInput::class,
            outputType = ExchangeResponse::class,
            context = WorkflowContext()
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val data = result.data
                val order = orderRepository.findByIdAndDeletedAtIsNull(data.orderId)

                ResponseEntity.status(201).body(mapOf(
                    "exchange" to ExchangeDto(
                        id = data.id,
                        orderId = data.orderId,
                        status = data.status.name.lowercase(),
                        returnId = data.returnId,
                        newOrderId = data.newOrderId,
                        items = data.items.map { item ->
                            ExchangeItemDto(
                                originalVariantId = item.originalVariantId,
                                originalTitle = item.originalTitle,
                                newVariantId = item.newVariantId,
                                newTitle = item.newTitle,
                                quantity = item.quantity,
                                priceDifference = item.priceDifference.multiply(BigDecimal(100)).toInt()
                            )
                        },
                        priceDifference = data.priceDifference.multiply(BigDecimal(100)).toInt(),
                        currencyCode = order?.currencyCode ?: "gbp",
                        createdAt = data.createdAt.toString()
                    ),
                    "message" to "Exchange request created successfully"
                ))
            }
            is WorkflowResult.Failure -> {
                ResponseEntity.badRequest().body(mapOf(
                    "message" to "Failed to create exchange",
                    "error" to (result.error.message ?: "Unknown error")
                ))
            }
        }
    }

    @Operation(summary = "Get exchange eligibility for an order")
    @GetMapping("/orders/{orderId}/eligibility")
    fun getExchangeEligibility(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable orderId: String
    ): ResponseEntity<Any> {
        val context = userContext ?: getCurrentUserContext()

        logger.info { "GET /store/exchanges/orders/$orderId/eligibility" }

        val order = orderRepository.findWithItemsById(orderId)
            ?: return ResponseEntity.notFound().build()

        // Validate ownership
        if (context?.customerId != null && order.customerId != context.customerId) {
            return ResponseEntity.status(403).body(mapOf(
                "message" to "Order does not belong to this customer"
            ))
        }

        // Check exchange window (14 days from order creation)
        val exchangeDeadline = order.createdAt.plusSeconds(14 * 24 * 60 * 60)
        val now = java.time.Instant.now()
        val eligible = now.isBefore(exchangeDeadline)
        val daysRemaining = if (eligible) {
            java.time.Duration.between(now, exchangeDeadline).toDays().toInt()
        } else 0

        // Get eligible items (shipped but not yet returned)
        val eligibleItems = order.items.filter { item ->
            val availableForExchange = item.shippedQuantity - item.returnedQuantity
            availableForExchange > 0
        }.map { item ->
            mapOf(
                "orderLineItemId" to item.id,
                "variantId" to item.variantId,
                "title" to item.title,
                "thumbnail" to item.thumbnail,
                "quantity" to item.quantity,
                "exchangeableQuantity" to (item.shippedQuantity - item.returnedQuantity),
                "unitPrice" to item.unitPrice.multiply(BigDecimal(100)).toInt(),
                "currencyCode" to order.currencyCode
            )
        }

        return ResponseEntity.ok(mapOf(
            "eligible" to eligible,
            "deadline" to exchangeDeadline.toString(),
            "daysRemaining" to daysRemaining,
            "reason" to if (!eligible) "Exchange window has expired" else null,
            "items" to eligibleItems
        ))
    }

    @Operation(summary = "List customer exchanges")
    @GetMapping
    fun listExchanges(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<Any> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).body(mapOf("message" to "Authentication required"))

        logger.info { "GET /store/exchanges - customerId=${context.customerId}" }

        // Exchanges are tracked via returns with EXCHANGE reason
        val customerId = context.customerId ?: return ResponseEntity.ok(mapOf(
            "exchanges" to emptyList<Any>(),
            "count" to 0,
            "offset" to offset,
            "limit" to limit
        ))

        val returns = returnRepository.findAllByCustomerIdAndDeletedAtIsNull(customerId)
            .sortedByDescending { it.requestedAt }

        // Filter to exchange-type returns (those with WRONG_ITEM reason or containing "Exchange" in notes)
        val exchanges = returns.filter { ret ->
            ret.reason == com.vernont.domain.returns.ReturnReason.WRONG_ITEM ||
            ret.reasonNote?.contains("Exchange", ignoreCase = true) == true
        }

        val paged = exchanges.drop(offset).take(limit)

        return ResponseEntity.ok(mapOf(
            "exchanges" to paged.map { ret ->
                mapOf(
                    "id" to ret.id,
                    "orderId" to ret.orderId,
                    "orderDisplayId" to ret.orderDisplayId,
                    "status" to ret.status.name.lowercase(),
                    "refundAmount" to ret.refundAmount.multiply(BigDecimal(100)).toInt(),
                    "currencyCode" to ret.currencyCode,
                    "requestedAt" to ret.requestedAt.toString(),
                    "itemCount" to ret.items.size
                )
            },
            "count" to exchanges.size,
            "offset" to offset,
            "limit" to limit
        ))
    }
}
