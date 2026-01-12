package com.vernont.api.controller.store

import com.vernont.domain.auth.UserContext
import com.vernont.domain.auth.getCurrentUserContext
import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnReason
import com.vernont.api.dto.store.*
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.returns.ReturnItemRepository
import com.vernont.repository.returns.ReturnRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.returns.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/store/returns")
class StoreReturnController(
    private val workflowEngine: WorkflowEngine,
    private val returnRepository: ReturnRepository,
    private val returnItemRepository: ReturnItemRepository,
    private val orderRepository: OrderRepository
) {

    /**
     * Create a return request
     * POST /store/returns
     */
    @PostMapping
    suspend fun createReturn(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestBody request: CreateReturnRequest,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).body(
                mapOf("message" to "Authentication required")
            )

        // Validate order exists and belongs to customer
        val order = orderRepository.findWithItemsById(request.orderId)
            ?: return ResponseEntity.status(404).body(
                mapOf("message" to "Order not found")
            )

        // Check authorization
        val isOwner = order.customerId == context.customerId
        val isEmailMatch = order.email.equals(context.email, ignoreCase = true)
        if (!isOwner && !isEmailMatch) {
            return ResponseEntity.status(403).body(
                mapOf("message" to "You are not authorized to return this order")
            )
        }

        // Parse reason
        val reason = try {
            ReturnReason.valueOf(request.reason.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(
                mapOf("message" to "Invalid return reason: ${request.reason}")
            )
        }

        val input = RequestReturnInput(
            orderId = request.orderId,
            customerId = context.customerId,
            customerEmail = context.email,
            items = request.items.map { item ->
                ReturnItemInput(
                    orderLineItemId = item.orderLineItemId,
                    quantity = item.quantity
                )
            },
            reason = reason,
            reasonNote = request.reasonNote
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.RequestReturn.NAME,
            input = input,
            inputType = RequestReturnInput::class,
            outputType = ReturnResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(result.data.id)
                    ?: return ResponseEntity.status(500).body(
                        mapOf("message" to "Return created but not found")
                    )
                ResponseEntity.status(201).body(
                    StoreReturnResponse(return_request = StoreReturn.from(returnRequest))
                )
            }
            is WorkflowResult.Failure -> ResponseEntity.badRequest().body(
                mapOf(
                    "message" to "Failed to create return",
                    "error" to (result.error.message ?: "Unknown error")
                )
            )
        }
    }

    /**
     * List customer's returns
     * GET /store/returns
     */
    @GetMapping
    fun listReturns(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<StoreReturnListResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        // Find returns by customer ID or email
        val returns = if (context.customerId != null) {
            returnRepository.findAllByCustomerIdOrEmailAndDeletedAtIsNull(
                context.customerId!!,
                context.email
            )
        } else {
            returnRepository.findAllByCustomerIdOrEmailAndDeletedAtIsNull(
                "",
                context.email
            )
        }

        val storeReturns = returns.map { StoreReturn.from(it) }

        // Apply pagination manually (could be optimized with proper pagination query)
        val paginatedReturns = storeReturns.drop(offset).take(limit)

        return ResponseEntity.ok(
            StoreReturnListResponse(
                returns = paginatedReturns,
                count = storeReturns.size,
                offset = offset,
                limit = limit
            )
        )
    }

    /**
     * Get return details
     * GET /store/returns/{id}
     */
    @GetMapping("/{id}")
    fun getReturn(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable id: String
    ): ResponseEntity<Any> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.status(404).body(
                mapOf("message" to "Return not found")
            )

        // Check authorization
        val isOwner = returnRequest.customerId == context.customerId
        val isEmailMatch = returnRequest.customerEmail?.equals(context.email, ignoreCase = true) == true
        if (!isOwner && !isEmailMatch) {
            return ResponseEntity.status(403).body(
                mapOf("message" to "You are not authorized to view this return")
            )
        }

        return ResponseEntity.ok(
            StoreReturnResponse(return_request = StoreReturn.from(returnRequest))
        )
    }

    /**
     * Cancel a return
     * DELETE /store/returns/{id}
     */
    @DeleteMapping("/{id}")
    suspend fun cancelReturn(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable id: String,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).body(
                mapOf("message" to "Authentication required")
            )

        val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.status(404).body(
                mapOf("message" to "Return not found")
            )

        // Check authorization
        val isOwner = returnRequest.customerId == context.customerId
        val isEmailMatch = returnRequest.customerEmail?.equals(context.email, ignoreCase = true) == true
        if (!isOwner && !isEmailMatch) {
            return ResponseEntity.status(403).body(
                mapOf("message" to "You are not authorized to cancel this return")
            )
        }

        val input = CancelReturnInput(
            returnId = id,
            customerId = context.customerId,
            canceledBy = context.email
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CancelReturn.NAME,
            input = input,
            inputType = CancelReturnInput::class,
            outputType = ReturnResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        return when (result) {
            is WorkflowResult.Success -> ResponseEntity.ok(
                mapOf("message" to "Return canceled successfully")
            )
            is WorkflowResult.Failure -> ResponseEntity.badRequest().body(
                mapOf(
                    "message" to "Failed to cancel return",
                    "error" to (result.error.message ?: "Unknown error")
                )
            )
        }
    }

    /**
     * Check return eligibility for an order
     * GET /store/orders/{orderId}/return-eligibility
     */
    @GetMapping("/orders/{orderId}/return-eligibility")
    fun checkReturnEligibility(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable orderId: String
    ): ResponseEntity<Any> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val order = orderRepository.findWithItemsById(orderId)
            ?: return ResponseEntity.status(404).body(
                mapOf("message" to "Order not found")
            )

        // Check authorization
        val isOwner = order.customerId == context.customerId
        val isEmailMatch = order.email.equals(context.email, ignoreCase = true)
        if (!isOwner && !isEmailMatch) {
            return ResponseEntity.status(403).body(
                mapOf("message" to "You are not authorized to view this order")
            )
        }

        // Calculate return deadline
        val returnDeadline = order.createdAt.plus(Return.RETURN_WINDOW_DAYS, ChronoUnit.DAYS)
        val now = Instant.now()
        val daysRemaining = if (now.isBefore(returnDeadline)) {
            ChronoUnit.DAYS.between(now, returnDeadline)
        } else {
            0L
        }

        val eligible = now.isBefore(returnDeadline)

        // Build eligible items
        // Check if order fulfillment status indicates shipped/delivered
        val shippedStatuses = setOf(
            com.vernont.domain.order.FulfillmentStatus.SHIPPED,
            com.vernont.domain.order.FulfillmentStatus.PARTIALLY_SHIPPED,
            com.vernont.domain.order.FulfillmentStatus.FULFILLED,
            com.vernont.domain.order.FulfillmentStatus.PARTIALLY_FULFILLED
        )
        val orderIsShipped = order.fulfillmentStatus in shippedStatuses

        val eligibleItems = order.items.mapNotNull { item ->
            // Calculate returnable quantity (shipped - already returned)
            val pendingReturns = returnItemRepository.sumReturnedQuantityByOrderLineItemId(item.id) ?: 0

            // If order is marked as shipped but line item shippedQuantity is 0,
            // fall back to using the full quantity (data was set without proper workflow)
            val effectiveShippedQuantity = if (item.shippedQuantity > 0) {
                item.shippedQuantity
            } else if (orderIsShipped) {
                item.quantity  // Fallback: assume all items shipped
            } else {
                0
            }

            val returnableQuantity = effectiveShippedQuantity - pendingReturns

            if (returnableQuantity > 0) {
                EligibleItem(
                    orderLineItemId = item.id,
                    variantId = item.variantId,
                    title = item.title,
                    thumbnail = item.thumbnail,
                    quantity = item.quantity,
                    returnableQuantity = returnableQuantity,
                    unitPrice = item.unitPrice.multiply(BigDecimal(100)).toInt(),
                    currencyCode = item.currencyCode
                )
            } else {
                null
            }
        }

        val response = ReturnEligibilityResponse(
            eligible = eligible && eligibleItems.isNotEmpty(),
            deadline = if (eligible) returnDeadline.atOffset(ZoneOffset.UTC) else null,
            daysRemaining = if (eligible) daysRemaining else null,
            reason = when {
                !eligible -> "Return window has expired (${Return.RETURN_WINDOW_DAYS} days)"
                eligibleItems.isEmpty() -> "No items available for return"
                else -> null
            },
            items = eligibleItems
        )

        return ResponseEntity.ok(response)
    }

    /**
     * Get available return reasons
     * GET /store/returns/reasons
     */
    @GetMapping("/reasons")
    fun getReturnReasons(): ResponseEntity<List<ReturnReasonDto>> {
        val reasons = ReturnReason.entries.map { reason ->
            ReturnReasonDto(
                value = reason.name,
                label = reason.name.replace("_", " ")
                    .lowercase()
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                requiresNote = reason == ReturnReason.OTHER
            )
        }
        return ResponseEntity.ok(reasons)
    }
}

data class ReturnReasonDto(
    val value: String,
    val label: String,
    val requiresNote: Boolean = false
)
