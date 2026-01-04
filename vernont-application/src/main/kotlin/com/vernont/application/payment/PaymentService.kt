package com.vernont.application.payment

import com.vernont.domain.payment.dto.*
import com.vernont.domain.payment.Payment
import com.vernont.domain.payment.PaymentStatus
import com.vernont.domain.payment.Refund
import com.vernont.domain.payment.RefundStatus
import com.vernont.domain.payment.RefundReason
import com.vernont.events.*
import com.vernont.repository.payment.PaymentProviderRepository
import com.vernont.repository.payment.PaymentRepository
import com.vernont.repository.payment.RefundRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentProviderRepository: PaymentProviderRepository,
    private val refundRepository: RefundRepository,
    private val eventPublisher: EventPublisher
) {

    /**
     * Create a new payment
     */
    fun createPayment(request: CreatePaymentRequest): PaymentResponse {
        logger.info { "Creating payment for amount ${request.amount} ${request.currencyCode}" }

        val provider = paymentProviderRepository.findByIdAndDeletedAtIsNull(request.providerId)
            ?: throw PaymentProviderNotFoundException("Payment provider not found: ${request.providerId}")

        if (!provider.isActive) {
            throw PaymentProviderDisabledException("Payment provider is disabled: ${provider.name}")
        }

        val payment = Payment().apply {
            this.provider = provider
            this.currencyCode = request.currencyCode
            this.amount = request.amount
            this.orderId = request.orderId
            this.cartId = request.cartId
            this.externalId = request.externalId
            this.data = request.data
            this.status = PaymentStatus.PENDING
        }

        val saved = paymentRepository.save(payment)

        eventPublisher.publish(
            PaymentCreated(
                aggregateId = saved.id,
                paymentId = saved.id,
                orderId = saved.orderId,
                amount = saved.amount,
                currencyCode = saved.currencyCode
            )
        )

        logger.info { "Payment created: ${saved.id}" }
        return PaymentResponse.from(saved)
    }

    /**
     * Authorize a payment
     */
    fun authorizePayment(request: AuthorizePaymentRequest): PaymentResponse {
        logger.info { "Authorizing payment: ${request.paymentId}" }

        val payment = paymentRepository.findByIdAndDeletedAtIsNull(request.paymentId)
            ?: throw PaymentNotFoundException("Payment not found: ${request.paymentId}")

        payment.apply {
            authorize()
            request.externalId?.let { externalId = it }
            request.data?.let { data = it }
        }

        val authorized = paymentRepository.save(payment)

        eventPublisher.publish(
            PaymentAuthorized(
                aggregateId = authorized.id,
                orderId = authorized.orderId ?: "",
                amount = authorized.amount,
                currency = authorized.currencyCode,
                providerId = authorized.provider?.id ?: "",
                paymentMethodId = null
            )
        )

        logger.info { "Payment authorized: ${authorized.id}" }
        return PaymentResponse.from(authorized)
    }

    /**
     * Capture a payment
     */
    fun capturePayment(request: CapturePaymentRequest): PaymentResponse {
        logger.info { "Capturing payment: ${request.paymentId}" }

        val payment = paymentRepository.findByIdAndDeletedAtIsNull(request.paymentId)
            ?: throw PaymentNotFoundException("Payment not found: ${request.paymentId}")

        // Validate capture amount if specified
        request.amount?.let { captureAmount ->
            if (captureAmount > payment.amount) {
                throw InvalidPaymentAmountException("Capture amount cannot exceed payment amount")
            }
            // For partial capture, update the payment amount
            payment.amount = captureAmount
        }

        payment.capture()
        val captured = paymentRepository.save(payment)

        eventPublisher.publish(
            PaymentCaptured(
                aggregateId = captured.id,
                orderId = captured.orderId ?: "",
                amount = captured.amount,
                currency = captured.currencyCode,
                providerId = captured.provider?.id ?: "",
                capturedAmount = captured.amount
            )
        )

        logger.info { "Payment captured: ${captured.id}" }
        return PaymentResponse.from(captured)
    }

    /**
     * Refund a payment
     */
    fun refundPayment(request: RefundPaymentRequest): PaymentResponse {
        logger.info { "Refunding payment ${request.paymentId}: ${request.amount}" }

        val payment = paymentRepository.findWithRefundsById(request.paymentId)
            ?: throw PaymentNotFoundException("Payment not found: ${request.paymentId}")

        if (!payment.canRefund(request.amount)) {
            throw InvalidRefundException(
                "Cannot refund ${request.amount}. Remaining amount: ${payment.getRemainingAmount()}"
            )
        }

        val refund = Refund().apply {
            amount = request.amount
            reason = request.reason?.let {
                try {
                    RefundReason.valueOf(it.uppercase())
                } catch (e: IllegalArgumentException) {
                    RefundReason.OTHER
                }
            } ?: RefundReason.OTHER
            note = request.note
            status = RefundStatus.PENDING
        }

        payment.addRefund(refund)
        refundRepository.save(refund)

        // Simulate refund processing (in real implementation, this would call payment provider)
        refund.succeed()
        payment.recalculateRefundedAmount()

        val updated = paymentRepository.save(payment)

        eventPublisher.publish(
            PaymentRefunded(
                aggregateId = updated.id,
                paymentId = updated.id,
                orderId = updated.orderId,
                refundId = refund.id,
                amount = refund.amount,
                createdBy = request.createdBy
            )
        )

        logger.info { "Payment refunded: ${updated.id}, refund: ${refund.id}" }
        return PaymentResponse.from(updated)
    }

    /**
     * Cancel a payment
     */
    fun cancelPayment(request: CancelPaymentRequest): PaymentResponse {
        logger.info { "Canceling payment: ${request.paymentId}" }

        val payment = paymentRepository.findByIdAndDeletedAtIsNull(request.paymentId)
            ?: throw PaymentNotFoundException("Payment not found: ${request.paymentId}")

        payment.cancel()
        val canceled = paymentRepository.save(payment)

        eventPublisher.publish(
            PaymentCanceled(
                aggregateId = canceled.id,
                paymentId = canceled.id,
                orderId = canceled.orderId,
                reason = request.reason
            )
        )

        logger.info { "Payment canceled: ${canceled.id}" }
        return PaymentResponse.from(canceled)
    }

    /**
     * Mark payment as failed
     */
    fun markPaymentFailed(paymentId: String, reason: String? = null): PaymentResponse {
        logger.info { "Marking payment as failed: $paymentId" }

        val payment = paymentRepository.findByIdAndDeletedAtIsNull(paymentId)
            ?: throw PaymentNotFoundException("Payment not found: $paymentId")

        payment.fail()
        val failed = paymentRepository.save(payment)

        eventPublisher.publish(
            PaymentFailed(
                aggregateId = failed.id,
                orderId = failed.orderId ?: "",
                amount = failed.amount,
                currency = failed.currencyCode,
                reason = reason ?: "Unknown failure",
                providerId = failed.provider?.id ?: ""
            )
        )

        logger.info { "Payment marked as failed: ${failed.id}" }
        return PaymentResponse.from(failed)
    }

    /**
     * Get payment by ID
     */
    @Transactional(readOnly = true)
    fun getPayment(id: String): PaymentResponse {
        val payment = paymentRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw PaymentNotFoundException("Payment not found: $id")

        return PaymentResponse.from(payment)
    }

    /**
     * Get payments for an order
     */
    @Transactional(readOnly = true)
    fun getPaymentsByOrder(orderId: String): List<PaymentSummaryResponse> {
        val payments = paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)
        return payments.map { PaymentSummaryResponse.from(it) }
    }

    /**
     * Get payments for a cart
     */
    @Transactional(readOnly = true)
    fun getPaymentsByCart(cartId: String): List<PaymentSummaryResponse> {
        val payments = paymentRepository.findByCartIdAndDeletedAtIsNull(cartId)
        return payments.map { PaymentSummaryResponse.from(it) }
    }

    /**
     * Get payment by external ID
     */
    @Transactional(readOnly = true)
    fun getPaymentByExternalId(externalId: String): PaymentResponse {
        val payment = paymentRepository.findByExternalIdAndDeletedAtIsNull(externalId)
            ?: throw PaymentNotFoundException("Payment not found with external ID: $externalId")

        return PaymentResponse.from(payment)
    }

    /**
     * List payments with pagination
     */
    @Transactional(readOnly = true)
    fun listPayments(pageable: Pageable): Page<PaymentSummaryResponse> {
        val page = paymentRepository.findAll(pageable)
        return page.map { PaymentSummaryResponse.from(it) }
    }

    /**
     * List payments by status
     */
    @Transactional(readOnly = true)
    fun listPaymentsByStatus(status: PaymentStatus, pageable: Pageable): Page<PaymentSummaryResponse> {
        val payments = paymentRepository.findByStatusAndDeletedAtIsNull(status)
        val start = pageable.offset.toInt()
        val end = minOf(start + pageable.pageSize, payments.size)

        val pageContent = if (start < payments.size) {
            payments.subList(start, end).map { PaymentSummaryResponse.from(it) }
        } else {
            emptyList()
        }

        return org.springframework.data.domain.PageImpl(
            pageContent,
            pageable,
            payments.size.toLong()
        )
    }

    /**
     * Get payment statistics
     */
    @Transactional(readOnly = true)
    fun getPaymentStatistics(): PaymentStatistics {
        val allPayments = paymentRepository.findByDeletedAtIsNull()

        val totalAmount = allPayments
            .filter { it.status == PaymentStatus.CAPTURED }
            .fold(BigDecimal.ZERO) { acc, payment -> acc.add(payment.amount) }

        val totalRefunded = allPayments
            .fold(BigDecimal.ZERO) { acc, payment -> acc.add(payment.amountRefunded) }

        val successfulCount = allPayments.count { it.isSuccessful() }
        val failedCount = allPayments.count { it.status == PaymentStatus.FAILED }
        val pendingCount = allPayments.count { it.status == PaymentStatus.PENDING }

        return PaymentStatistics(
            totalAmount = totalAmount,
            totalRefunded = totalRefunded,
            successfulPayments = successfulCount.toLong(),
            failedPayments = failedCount.toLong(),
            pendingPayments = pendingCount.toLong()
        )
    }
}

/**
 * Payment statistics
 */
data class PaymentStatistics(
    val totalAmount: BigDecimal,
    val totalRefunded: BigDecimal,
    val successfulPayments: Long,
    val failedPayments: Long,
    val pendingPayments: Long
)

// Custom exceptions
class PaymentNotFoundException(message: String) : RuntimeException(message)
class PaymentProviderNotFoundException(message: String) : RuntimeException(message)
class PaymentProviderDisabledException(message: String) : RuntimeException(message)
class InvalidPaymentAmountException(message: String) : RuntimeException(message)
class InvalidRefundException(message: String) : RuntimeException(message)
