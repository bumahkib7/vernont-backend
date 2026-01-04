package com.vernont.domain.payment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant

/**
 * Payment Session entity - equivalent to Medusa's PaymentSession
 * Represents a payment session associated with a payment collection
 */
@Entity
@Table(
    name = "payment_session",
    indexes = [
        Index(name = "idx_payment_session_payment_collection_id", columnList = "payment_collection_id"),
        Index(name = "idx_payment_session_provider_id", columnList = "provider_id"),
        Index(name = "idx_payment_session_status", columnList = "status")
    ]
)
class PaymentSession : BaseEntity() {

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(precision = 19, scale = 4, nullable = false)
    var amount: BigDecimal = BigDecimal.ZERO

    @NotBlank
    @Column(name = "provider_id", nullable = false)
    var providerId: String = ""

    @Column(columnDefinition = "TEXT")
    var data: String? = null // JSON data stored as string

    @Column(columnDefinition = "TEXT")
    var context: String? = null // JSON context stored as string

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentSessionStatus = PaymentSessionStatus.PENDING

    @Column(name = "authorized_at")
    var authorizedAt: Instant? = null

    @Column(name = "payment_collection_id", nullable = false)
    var paymentCollectionId: String = ""

    @OneToOne(mappedBy = "paymentSession", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var payment: Payment? = null


    fun authorize() {
        this.status = PaymentSessionStatus.AUTHORIZED
        this.authorizedAt = Instant.now()
    }

    fun capture() {
        this.status = PaymentSessionStatus.CAPTURED
    }

    fun cancel() {
        this.status = PaymentSessionStatus.CANCELED
    }

    fun markAsError() {
        this.status = PaymentSessionStatus.ERROR
    }

    fun isAuthorized(): Boolean = status == PaymentSessionStatus.AUTHORIZED

    fun isCaptured(): Boolean = status == PaymentSessionStatus.CAPTURED

    fun isPending(): Boolean = status == PaymentSessionStatus.PENDING

    fun isError(): Boolean = status == PaymentSessionStatus.ERROR

    fun isCanceled(): Boolean = status == PaymentSessionStatus.CANCELED
}