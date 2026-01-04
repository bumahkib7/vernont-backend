package com.vernont.api.dto.admin

import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnItem
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

// =========================================================================
// Response DTOs
// =========================================================================

data class AdminReturn(
    val id: String,
    val orderId: String,
    val orderDisplayId: Int?,
    val customerId: String?,
    val customerEmail: String?,
    val status: String,
    val reason: String,
    val reasonNote: String?,
    val refundAmount: Int,           // In cents
    val currencyCode: String,
    val items: List<AdminReturnItem>,
    val requestedAt: OffsetDateTime,
    val approvedAt: OffsetDateTime?,
    val receivedAt: OffsetDateTime?,
    val refundedAt: OffsetDateTime?,
    val rejectedAt: OffsetDateTime?,
    val rejectionReason: String?,
    val returnDeadline: OffsetDateTime,
    val daysRemaining: Long,
    val refundId: String?,
    val canReceive: Boolean,
    val canRefund: Boolean,
    val canReject: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        fun from(returnRequest: Return): AdminReturn {
            return AdminReturn(
                id = returnRequest.id,
                orderId = returnRequest.orderId,
                orderDisplayId = returnRequest.orderDisplayId,
                customerId = returnRequest.customerId,
                customerEmail = returnRequest.customerEmail,
                status = returnRequest.status.name.lowercase(),
                reason = returnRequest.reason.name.lowercase().replace("_", " "),
                reasonNote = returnRequest.reasonNote,
                refundAmount = returnRequest.refundAmount.multiply(BigDecimal(100)).toInt(),
                currencyCode = returnRequest.currencyCode,
                items = returnRequest.items.map { AdminReturnItem.from(it) },
                requestedAt = returnRequest.requestedAt.atOffset(ZoneOffset.UTC),
                approvedAt = returnRequest.approvedAt?.atOffset(ZoneOffset.UTC),
                receivedAt = returnRequest.receivedAt?.atOffset(ZoneOffset.UTC),
                refundedAt = returnRequest.refundedAt?.atOffset(ZoneOffset.UTC),
                rejectedAt = returnRequest.rejectedAt?.atOffset(ZoneOffset.UTC),
                rejectionReason = returnRequest.rejectionReason,
                returnDeadline = returnRequest.returnDeadline.atOffset(ZoneOffset.UTC),
                daysRemaining = returnRequest.getDaysRemaining(),
                refundId = returnRequest.refundId,
                canReceive = returnRequest.status == com.vernont.domain.returns.ReturnStatus.APPROVED,
                canRefund = returnRequest.canProcessRefund(),
                canReject = returnRequest.status in listOf(
                    com.vernont.domain.returns.ReturnStatus.REQUESTED,
                    com.vernont.domain.returns.ReturnStatus.APPROVED
                ),
                createdAt = returnRequest.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = returnRequest.updatedAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

data class AdminReturnItem(
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
        fun from(item: ReturnItem): AdminReturnItem {
            return AdminReturnItem(
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

data class AdminReturnSummary(
    val id: String,
    val orderId: String,
    val orderDisplayId: Int?,
    val customerEmail: String?,
    val status: String,
    val reason: String,
    val refundAmount: Int,
    val currencyCode: String,
    val itemCount: Int,
    val requestedAt: OffsetDateTime,
    val canReceive: Boolean,
    val canRefund: Boolean
) {
    companion object {
        fun from(returnRequest: Return): AdminReturnSummary {
            return AdminReturnSummary(
                id = returnRequest.id,
                orderId = returnRequest.orderId,
                orderDisplayId = returnRequest.orderDisplayId,
                customerEmail = returnRequest.customerEmail,
                status = returnRequest.status.name.lowercase(),
                reason = returnRequest.reason.name.lowercase().replace("_", " "),
                refundAmount = returnRequest.refundAmount.multiply(BigDecimal(100)).toInt(),
                currencyCode = returnRequest.currencyCode,
                itemCount = returnRequest.getTotalItemCount(),
                requestedAt = returnRequest.requestedAt.atOffset(ZoneOffset.UTC),
                canReceive = returnRequest.status == com.vernont.domain.returns.ReturnStatus.APPROVED,
                canRefund = returnRequest.canProcessRefund()
            )
        }
    }
}

data class AdminReturnListResponse(
    val returns: List<AdminReturnSummary>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class AdminReturnResponse(
    val return_request: AdminReturn
)

// =========================================================================
// Request DTOs
// =========================================================================

data class ReceiveReturnRequest(
    val receivedBy: String? = null,
    val notes: String? = null
)

data class RejectReturnRequest(
    val reason: String,
    val rejectedBy: String? = null
)

data class ProcessRefundRequest(
    val processedBy: String? = null
)
