package com.vernont.api.dto.store

import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnItem
import com.vernont.domain.returns.ReturnReason
import com.vernont.domain.returns.ReturnStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// =========================================================================
// Request DTOs
// =========================================================================

data class CreateReturnRequest(
    val orderId: String,
    val items: List<ReturnItemRequest>,
    val reason: String,
    val reasonNote: String? = null
)

data class ReturnItemRequest(
    val orderLineItemId: String,
    val quantity: Int
)

// =========================================================================
// Response DTOs
// =========================================================================

data class StoreReturn(
    val id: String,
    val orderId: String,
    val orderDisplayId: Int?,
    val status: String,
    val reason: String,
    val reasonNote: String?,
    val refundAmount: Int,           // In cents
    val currencyCode: String,
    val items: List<StoreReturnItem>,
    val requestedAt: OffsetDateTime,
    val approvedAt: OffsetDateTime?,
    val receivedAt: OffsetDateTime?,
    val refundedAt: OffsetDateTime?,
    val returnDeadline: OffsetDateTime,
    val daysRemaining: Long,
    val canCancel: Boolean
) {
    companion object {
        fun from(returnRequest: Return): StoreReturn {
            return StoreReturn(
                id = returnRequest.id,
                orderId = returnRequest.orderId,
                orderDisplayId = returnRequest.orderDisplayId,
                status = returnRequest.status.name.lowercase(),
                reason = returnRequest.reason.name.lowercase().replace("_", " "),
                reasonNote = returnRequest.reasonNote,
                refundAmount = returnRequest.refundAmount.multiply(BigDecimal(100)).toInt(),
                currencyCode = returnRequest.currencyCode,
                items = returnRequest.items.map { StoreReturnItem.from(it) },
                requestedAt = returnRequest.requestedAt.atOffset(ZoneOffset.UTC),
                approvedAt = returnRequest.approvedAt?.atOffset(ZoneOffset.UTC),
                receivedAt = returnRequest.receivedAt?.atOffset(ZoneOffset.UTC),
                refundedAt = returnRequest.refundedAt?.atOffset(ZoneOffset.UTC),
                returnDeadline = returnRequest.returnDeadline.atOffset(ZoneOffset.UTC),
                daysRemaining = returnRequest.getDaysRemaining(),
                canCancel = returnRequest.canCancel()
            )
        }
    }
}

data class StoreReturnItem(
    val id: String,
    val orderLineItemId: String,
    val variantId: String?,
    val title: String,
    val description: String?,
    val thumbnail: String?,
    val quantity: Int,
    val unitPrice: Int,              // In cents
    val total: Int                   // In cents
) {
    companion object {
        fun from(item: ReturnItem): StoreReturnItem {
            return StoreReturnItem(
                id = item.id,
                orderLineItemId = item.orderLineItemId,
                variantId = item.variantId,
                title = item.title,
                description = item.description,
                thumbnail = item.thumbnail,
                quantity = item.quantity,
                unitPrice = item.unitPrice.multiply(BigDecimal(100)).toInt(),
                total = item.total.multiply(BigDecimal(100)).toInt()
            )
        }
    }
}

data class StoreReturnResponse(
    val return_request: StoreReturn   // Using snake_case for API consistency
)

data class StoreReturnListResponse(
    val returns: List<StoreReturn>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

// =========================================================================
// Return Eligibility DTOs
// =========================================================================

data class ReturnEligibilityResponse(
    val eligible: Boolean,
    val deadline: OffsetDateTime?,
    val daysRemaining: Long?,
    val reason: String?,
    val items: List<EligibleItem>
)

data class EligibleItem(
    val orderLineItemId: String,
    val variantId: String?,
    val title: String,
    val thumbnail: String?,
    val quantity: Int,
    val returnableQuantity: Int,
    val unitPrice: Int,              // In cents
    val currencyCode: String
)
