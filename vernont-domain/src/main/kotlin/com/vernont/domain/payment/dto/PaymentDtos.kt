package com.vernont.domain.payment.dto

import com.vernont.domain.payment.Payment
import com.vernont.domain.payment.PaymentProvider
import com.vernont.domain.payment.PaymentStatus
import com.vernont.domain.payment.Refund
import com.vernont.domain.payment.RefundStatus
import com.vernont.domain.payment.RefundReason
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant

/**
 * Request to create a payment
 */
data class CreatePaymentRequest(
    @field:NotBlank
    val providerId: String,

    @field:NotBlank
    val currencyCode: String,

    @field:NotNull
    @field:Positive
    val amount: BigDecimal,

    val orderId: String? = null,
    val cartId: String? = null,
    val externalId: String? = null,
    val data: Map<String, Any>? = null
)

/**
 * Request to authorize a payment
 */
data class AuthorizePaymentRequest(
    @field:NotBlank
    val paymentId: String,

    val externalId: String? = null,
    val data: Map<String, Any>? = null
)

/**
 * Request to capture a payment
 */
data class CapturePaymentRequest(
    @field:NotBlank
    val paymentId: String,

    val amount: BigDecimal? = null, // If null, captures full amount
    val capturedBy: String? = null,
    val data: Map<String, Any>? = null
)

/**
 * Request to refund a payment
 */
data class RefundPaymentRequest(
    @field:NotBlank
    val paymentId: String,

    @field:NotNull
    @field:Positive
    val amount: BigDecimal,

    val reason: String? = null,
    val note: String? = null,
    val createdBy: String? = null
)

/**
 * Request to cancel a payment
 */
data class CancelPaymentRequest(
    @field:NotBlank
    val paymentId: String,

    val reason: String? = null
)

/**
 * Payment response DTO
 */
data class PaymentResponse(
    val id: String,
    val orderId: String?,
    val cartId: String?,
    val provider: PaymentProviderSummary?,
    val currencyCode: String,
    val amount: BigDecimal,
    val amountRefunded: BigDecimal,
    val status: PaymentStatus,
    val externalId: String?,
    val data: Map<String, Any>?,
    val capturedAt: Instant?,
    val canceledAt: Instant?,
    val refunds: List<RefundResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(payment: Payment): PaymentResponse {
            return PaymentResponse(
                id = payment.id,
                orderId = payment.orderId,
                cartId = payment.cartId,
                provider = payment.provider?.let { PaymentProviderSummary.from(it) },
                currencyCode = payment.currencyCode,
                amount = payment.amount,
                amountRefunded = payment.amountRefunded,
                status = payment.status,
                externalId = payment.externalId,
                data = payment.data,
                capturedAt = payment.capturedAt,
                canceledAt = payment.canceledAt,
                refunds = payment.refunds.map { RefundResponse.from(it) },
                createdAt = payment.createdAt,
                updatedAt = payment.updatedAt
            )
        }
    }
}

/**
 * Payment summary response (without refunds)
 */
data class PaymentSummaryResponse(
    val id: String,
    val orderId: String?,
    val cartId: String?,
    val currencyCode: String,
    val amount: BigDecimal,
    val amountRefunded: BigDecimal,
    val status: PaymentStatus,
    val createdAt: Instant
) {
    companion object {
        fun from(payment: Payment): PaymentSummaryResponse {
            return PaymentSummaryResponse(
                id = payment.id,
                orderId = payment.orderId,
                cartId = payment.cartId,
                currencyCode = payment.currencyCode,
                amount = payment.amount,
                amountRefunded = payment.amountRefunded,
                status = payment.status,
                createdAt = payment.createdAt
            )
        }
    }
}

/**
 * Refund response DTO
 */
data class RefundResponse(
    val id: String,
    val paymentId: String,
    val amount: BigDecimal,
    val reason: RefundReason,
    val note: String?,
    val status: RefundStatus,
    val createdAt: Instant
) {
    companion object {
        fun from(refund: Refund): RefundResponse {
            return RefundResponse(
                id = refund.id,
                paymentId = refund.payment?.id ?: "",
                amount = refund.amount,
                reason = refund.reason,
                note = refund.note,
                status = refund.status,
                createdAt = refund.createdAt
            )
        }
    }
}

/**
 * Payment provider summary DTO
 */
data class PaymentProviderSummary(
    val id: String,
    val name: String,
    val isActive: Boolean
) {
    companion object {
        fun from(provider: PaymentProvider): PaymentProviderSummary {
            return PaymentProviderSummary(
                id = provider.id,
                name = provider.name,
                isActive = provider.isActive
            )
        }
    }
}

/**
 * Payment provider response DTO
 */
data class PaymentProviderResponse(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val config: Map<String, Any>?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(provider: PaymentProvider): PaymentProviderResponse {
            return PaymentProviderResponse(
                id = provider.id,
                name = provider.name,
                isActive = provider.isActive,
                config = provider.config,
                createdAt = provider.createdAt,
                updatedAt = provider.updatedAt
            )
        }
    }
}
