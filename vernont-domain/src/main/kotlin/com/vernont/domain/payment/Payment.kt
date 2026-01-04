package com.vernont.domain.payment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "payment",
    indexes = [
        Index(name = "idx_payment_order_id", columnList = "order_id"),
        Index(name = "idx_payment_cart_id", columnList = "cart_id"),
        Index(name = "idx_payment_provider_id", columnList = "provider_id"),
        Index(name = "idx_payment_status", columnList = "status"),
        Index(name = "idx_payment_external_id", columnList = "external_id"),
        Index(name = "idx_payment_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Payment.full",
    attributeNodes = [
        NamedAttributeNode("provider"),
        NamedAttributeNode("refunds")
    ]
)
@NamedEntityGraph(
    name = "Payment.withProvider",
    attributeNodes = [
        NamedAttributeNode("provider")
    ]
)
@NamedEntityGraph(
    name = "Payment.withRefunds",
    attributeNodes = [
        NamedAttributeNode("refunds")
    ]
)
class Payment : BaseEntity() {

    @Column(name = "order_id")
    var orderId: String? = null

    @Column(name = "cart_id")
    var cartId: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    var provider: PaymentProvider? = null

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "amount_refunded", nullable = false, precision = 19, scale = 4)
    var amountRefunded: BigDecimal = BigDecimal.ZERO

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING

    @Column(name = "external_id")
    var externalId: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var data: Map<String, Any>? = null

    @Column(name = "captured_at")
    var capturedAt: Instant? = null

    @Column(name = "authorized_at")
    var authorizedAt: Instant? = null

    @Column(name = "captured_amount", precision = 19, scale = 4)
    var capturedAmount: BigDecimal? = null

    @Column(name = "canceled_at")
    var canceledAt: Instant? = null

    @OneToMany(mappedBy = "payment", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var refunds: MutableSet<Refund> = mutableSetOf()

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_session_id")
    var paymentSession: PaymentSession? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_collection_id")
    var paymentCollection: PaymentCollection? = null

    fun authorize() {
        require(status == PaymentStatus.PENDING) { "Payment must be in pending status to authorize" }
        this.status = PaymentStatus.AUTHORIZED
    }

    fun capture() {
        require(status == PaymentStatus.AUTHORIZED) { "Payment must be authorized before capture" }
        this.status = PaymentStatus.CAPTURED
        this.capturedAt = Instant.now()
    }

    fun cancel() {
        require(status in listOf(PaymentStatus.PENDING, PaymentStatus.AUTHORIZED)) { "Cannot cancel payment in current status" }
        this.status = PaymentStatus.CANCELED
        this.canceledAt = Instant.now()
    }

    fun fail() {
        this.status = PaymentStatus.FAILED
    }

    fun addRefund(refund: Refund) {
        refunds.add(refund)
        refund.payment = this
        recalculateRefundedAmount()
    }

    fun removeRefund(refund: Refund) {
        refunds.remove(refund)
        refund.payment = null
        recalculateRefundedAmount()
    }

    fun recalculateRefundedAmount() {
        this.amountRefunded = refunds
            .filter { it.status == RefundStatus.SUCCEEDED }
            .fold(BigDecimal.ZERO) { acc, refund -> acc.add(refund.amount) }

        if (amountRefunded >= amount) {
            this.status = PaymentStatus.REFUNDED
        } else if (amountRefunded > BigDecimal.ZERO) {
            this.status = PaymentStatus.PARTIALLY_REFUNDED
        }
    }

    fun getRemainingAmount(): BigDecimal {
        return amount.subtract(amountRefunded)
    }

    fun canRefund(refundAmount: BigDecimal): Boolean {
        return status in listOf(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED) &&
               getRemainingAmount() >= refundAmount
    }

    fun isSuccessful(): Boolean {
        return status in listOf(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED)
    }

    fun isCanceled(): Boolean {
        return status == PaymentStatus.CANCELED
    }

    fun isRefunded(): Boolean {
        return status == PaymentStatus.REFUNDED
    }

    fun isPartiallyRefunded(): Boolean {
        return status == PaymentStatus.PARTIALLY_REFUNDED
    }
}

enum class PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    CANCELED,
    FAILED,
    REQUIRES_ACTION
}
