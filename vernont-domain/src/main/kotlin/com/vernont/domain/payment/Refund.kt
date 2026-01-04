package com.vernont.domain.payment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

@Entity
@Table(
    name = "refund",
    indexes = [
        Index(name = "idx_refund_payment_id", columnList = "payment_id"),
        Index(name = "idx_refund_order_id", columnList = "order_id"),
        Index(name = "idx_refund_status", columnList = "status"),
        Index(name = "idx_refund_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Refund.full",
    attributeNodes = [
        NamedAttributeNode("payment")
    ]
)
@NamedEntityGraph(
    name = "Refund.withPayment",
    attributeNodes = [
        NamedAttributeNode("payment")
    ]
)
class Refund : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    var payment: Payment? = null

    @Column(name = "order_id")
    var orderId: String? = null

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal = BigDecimal.ZERO

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RefundStatus = RefundStatus.PENDING

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var reason: RefundReason = RefundReason.OTHER

    @Column(columnDefinition = "TEXT")
    var note: String? = null

    @Column(columnDefinition = "jsonb")
    var data: String? = null

    fun succeed() {
        require(status == RefundStatus.PENDING) { "Refund must be in pending status to succeed" }
        this.status = RefundStatus.SUCCEEDED
        payment?.recalculateRefundedAmount()
    }

    fun fail() {
        require(status == RefundStatus.PENDING) { "Refund must be in pending status to fail" }
        this.status = RefundStatus.FAILED
    }

    fun cancel() {
        require(status == RefundStatus.PENDING) { "Refund must be in pending status to cancel" }
        this.status = RefundStatus.CANCELED
    }

    fun updateReason(newReason: RefundReason, newNote: String? = null) {
        this.reason = newReason
        newNote?.let { this.note = it }
    }

    fun isSuccessful(): Boolean {
        return status == RefundStatus.SUCCEEDED
    }

    fun isFailed(): Boolean {
        return status == RefundStatus.FAILED
    }

    fun isPending(): Boolean {
        return status == RefundStatus.PENDING
    }

    fun isCanceled(): Boolean {
        return status == RefundStatus.CANCELED
    }
}

enum class RefundStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELED
}

enum class RefundReason {
    DISCOUNT,
    RETURN,
    SWAP,
    CLAIM,
    CANCEL,
    OTHER
}
