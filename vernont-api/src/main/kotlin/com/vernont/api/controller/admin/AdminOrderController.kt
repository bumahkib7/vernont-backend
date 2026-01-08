package com.vernont.api.controller.admin

import com.vernont.application.shipping.ShipEngineService
import com.vernont.domain.order.dto.OrderResponse
import com.vernont.domain.order.dto.OrderSummaryResponse
import com.vernont.repository.order.OrderRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.order.*
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/admin/orders")
class AdminOrderController(
    private val orderRepository: OrderRepository,
    private val workflowEngine: WorkflowEngine,
    private val shipEngineService: ShipEngineService
) {

    data class CancelOrderRequest(
        val canceledBy: String? = null,
        val reason: String? = null
    )

    data class FulfillOrderRequest(
        val trackingNumber: String? = null,
        val carrier: String? = null,
        val fulfilledBy: String? = null
    )

    data class ShipOrderRequest(
        val trackingNumber: String? = null,
        val carrier: String? = null,
        val shippedBy: String? = null,
        // ShipEngine integration fields
        val useShipEngine: Boolean? = null,
        val carrierId: String? = null,
        val serviceCode: String? = null,
        val packageWeight: Double? = null,
        val packageLength: Double? = null,
        val packageWidth: Double? = null,
        val packageHeight: Double? = null
    )

    @GetMapping("/drafts")
    fun listDraftOrders(
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int
    ): ResponseEntity<Any> {
        // Draft orders are orders with NOT_PAID payment status that haven't been canceled
        // This could be admin-created orders or incomplete checkouts
        val draftOrders = orderRepository.findByDeletedAtIsNull()
            .filter { it.paymentStatus == com.vernont.domain.order.PaymentStatus.NOT_PAID &&
                     it.status != com.vernont.domain.order.OrderStatus.CANCELED }

        val count = draftOrders.size
        val paginatedOrders = draftOrders
            .drop(offset)
            .take(limit.coerceAtMost(100))
            .map { OrderSummaryResponse.from(it) }

        return ResponseEntity.ok(mapOf(
            "orders" to paginatedOrders,
            "limit" to limit,
            "offset" to offset,
            "count" to count
        ))
    }

    @GetMapping("/shipping/config")
    fun getShippingConfig(): ResponseEntity<Any> {
        return try {
            val config = shipEngineService.getConfig()
            ResponseEntity.ok(mapOf(
                "shipEngineEnabled" to config.enabled,
                "shipEngineConfigured" to config.isConfigured,
                "sandboxMode" to config.sandboxMode,
                "defaultCarrierId" to config.defaultCarrierId,
                "defaultServiceCode" to config.defaultServiceCode,
                "availableCarriers" to config.availableCarriers
            ))
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf(
                "shipEngineEnabled" to false,
                "shipEngineConfigured" to false,
                "sandboxMode" to true,
                "defaultCarrierId" to "",
                "defaultServiceCode" to "usps_priority_mail",
                "availableCarriers" to listOf(
                    mapOf("code" to "usps", "name" to "USPS"),
                    mapOf("code" to "ups", "name" to "UPS"),
                    mapOf("code" to "fedex", "name" to "FedEx"),
                    mapOf("code" to "dhl_express", "name" to "DHL Express")
                )
            ))
        }
    }

    @GetMapping
    fun listOrders(
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) fields: String?,
        @RequestParam(required = false) order: String?
    ): ResponseEntity<Any> {
        val allOrders = orderRepository.findByDeletedAtIsNull()
        val count = allOrders.size
        val paginatedOrders = allOrders
            .drop(offset)
            .take(limit.coerceAtMost(100))
            .map { OrderSummaryResponse.from(it) }

        return ResponseEntity.ok(mapOf(
            "orders" to paginatedOrders,
            "limit" to limit,
            "offset" to offset,
            "count" to count
        ))
    }

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: String): ResponseEntity<Any> {
        val order = orderRepository.findWithItemsById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(OrderResponse.from(order))
    }

    @PostMapping("/{id}/cancel")
    fun cancelOrder(
        @PathVariable id: String,
        @RequestBody(required = false) body: CancelOrderRequest?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> = runBlocking {
        val input = CancelOrderInput(
            orderId = id,
            canceledBy = body?.canceledBy,
            reason = body?.reason
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CancelOrder.NAME,
            input = input,
            inputType = CancelOrderInput::class,
            outputType = Unit::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        when (result) {
            is WorkflowResult.Success ->
                ResponseEntity.ok(mapOf("message" to "Order canceled successfully"))

            is WorkflowResult.Failure ->
                ResponseEntity.badRequest().body(
                    mapOf(
                        "message" to "Failed to cancel order",
                        "error" to (result.error.message ?: "Unknown error")
                    )
                )
        }
    }

    @PostMapping("/{id}/fulfill")
    fun fulfillOrder(
        @PathVariable id: String,
        @RequestBody(required = false) body: FulfillOrderRequest?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> = runBlocking {
        val input = FulfillOrderInput(
            orderId = id,
            locationId = null,
            providerId = null,
            noNotification = false,
            metadata = body?.fulfilledBy?.let { mapOf("fulfilled_by" to it) }
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.FulfillOrder.NAME,
            input = input,
            inputType = FulfillOrderInput::class,
            outputType = FulfillOrderResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        when (result) {
            is WorkflowResult.Success -> {
                val response = result.data
                val order = orderRepository.findWithItemsById(id)
                ResponseEntity.ok(mapOf(
                    "order" to order?.let { OrderResponse.from(it) },
                    "fulfillmentId" to response.fulfillmentId,
                    "message" to response.message
                ))
            }
            is WorkflowResult.Failure ->
                ResponseEntity.badRequest().body(
                    mapOf(
                        "message" to "Failed to fulfill order",
                        "error" to (result.error.message ?: "Unknown error")
                    )
                )
        }
    }

    @PostMapping("/{id}/ship")
    fun shipOrder(
        @PathVariable id: String,
        @RequestBody(required = false) body: ShipOrderRequest?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> = runBlocking {
        val input = ShipOrderInput(
            orderId = id,
            trackingNumber = body?.trackingNumber,
            carrier = body?.carrier,
            trackingUrl = null,
            noNotification = false,
            metadata = body?.shippedBy?.let { mapOf("shipped_by" to it) },
            // ShipEngine integration fields
            useShipEngine = body?.useShipEngine ?: false,
            carrierId = body?.carrierId,
            serviceCode = body?.serviceCode,
            packageWeight = body?.packageWeight,
            packageLength = body?.packageLength,
            packageWidth = body?.packageWidth,
            packageHeight = body?.packageHeight
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.ShipOrder.NAME,
            input = input,
            inputType = ShipOrderInput::class,
            outputType = ShipOrderResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        when (result) {
            is WorkflowResult.Success -> {
                val response = result.data
                val order = orderRepository.findWithItemsById(id)
                ResponseEntity.ok(mapOf(
                    "order" to order?.let { OrderResponse.from(it) },
                    "fulfillmentId" to response.fulfillmentId,
                    "trackingNumbers" to response.trackingNumbers,
                    "trackingUrls" to response.trackingUrls,
                    "labelUrls" to response.labelUrls,
                    "shipEngineLabelId" to response.shipEngineLabelId,
                    "carrier" to response.carrier,
                    "shippingCost" to response.shippingCost,
                    "message" to response.message
                ))
            }
            is WorkflowResult.Failure ->
                ResponseEntity.badRequest().body(
                    mapOf(
                        "message" to "Failed to ship order",
                        "error" to (result.error.message ?: "Unknown error")
                    )
                )
        }
    }

    @PostMapping("/{id}/complete")
    fun completeOrder(
        @PathVariable id: String,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> = runBlocking {
        val input = CompleteOrderInput(
            orderId = id,
            metadata = null
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CompleteOrder.NAME,
            input = input,
            inputType = CompleteOrderInput::class,
            outputType = CompleteOrderResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        when (result) {
            is WorkflowResult.Success -> {
                val response = result.data
                val order = orderRepository.findWithItemsById(id)
                ResponseEntity.ok(mapOf(
                    "order" to order?.let { OrderResponse.from(it) },
                    "message" to response.message
                ))
            }
            is WorkflowResult.Failure ->
                ResponseEntity.badRequest().body(
                    mapOf(
                        "message" to "Failed to complete order",
                        "error" to (result.error.message ?: "Unknown error")
                    )
                )
        }
    }

}
