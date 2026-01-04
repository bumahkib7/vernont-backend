package com.vernont.domain.payment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

/**
 * Payment Collection entity - equivalent to Medusa's PaymentCollection
 * Groups payment sessions for a cart or order
 */
@Entity
@Table(
    name = "payment_collection",
    indexes = [
        Index(name = "idx_payment_collection_currency_code", columnList = "currency_code"),
        Index(name = "idx_payment_collection_region_id", columnList = "region_id"),
        Index(name = "idx_payment_collection_status", columnList = "status")
    ]
)
class PaymentCollection : BaseEntity() {

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(precision = 19, scale = 4, nullable = false)
    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "region_id")
    var regionId: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentCollectionStatus = PaymentCollectionStatus.NOT_PAID

    @Column(name = "completed_at")
    var completedAt: java.time.Instant? = null

    @OneToMany(mappedBy = "paymentCollectionId", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var paymentSessions: MutableSet<PaymentSession> = mutableSetOf()

    @OneToMany(mappedBy = "paymentCollection", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var payments: MutableSet<Payment> = mutableSetOf()


    fun addPaymentSession(session: PaymentSession) {
        paymentSessions.add(session)
        session.paymentCollectionId = this.id
    }

    fun removePaymentSession(session: PaymentSession) {
        paymentSessions.remove(session)
        session.paymentCollectionId = ""
    }

    fun addPayment(payment: Payment) {
        payments.add(payment)
        payment.paymentCollection = this
    }

    fun removePayment(payment: Payment) {
        payments.remove(payment)
        payment.paymentCollection = null
    }

    fun markAsPaid() {
        this.status = PaymentCollectionStatus.PAID
        this.completedAt = java.time.Instant.now()
    }

    fun markAsPartiallyPaid() {
        this.status = PaymentCollectionStatus.PARTIALLY_PAID
    }

    fun markAsRefunded() {
        this.status = PaymentCollectionStatus.REFUNDED
    }

    fun markAsPartiallyRefunded() {
        this.status = PaymentCollectionStatus.PARTIALLY_REFUNDED
    }

    fun markAsCanceled() {
        this.status = PaymentCollectionStatus.CANCELED
    }

    fun isPaid(): Boolean = status == PaymentCollectionStatus.PAID

    fun isPartiallyPaid(): Boolean = status == PaymentCollectionStatus.PARTIALLY_PAID

    fun isNotPaid(): Boolean = status == PaymentCollectionStatus.NOT_PAID
}

/**
 * Payment Collection Status enumeration
 */
enum class PaymentCollectionStatus {
    NOT_PAID,
    PAID,
    PARTIALLY_PAID,
    REFUNDED,
    PARTIALLY_REFUNDED,
    CANCELED,
    REQUIRES_ACTION
}