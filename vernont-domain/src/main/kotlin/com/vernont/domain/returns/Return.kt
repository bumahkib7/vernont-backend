package com.vernont.domain.returns

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Represents a return/RMA request for an order.
 *
 * Return Policy:
 * - 14-day return window from order completion date
 * - Auto-approved if within the return window
 * - Refund item price only (customer pays return shipping)
 */
@Entity
@Table(
    name = "return_request",
    indexes = [
        Index(name = "idx_return_order_id", columnList = "order_id"),
        Index(name = "idx_return_customer_id", columnList = "customer_id"),
        Index(name = "idx_return_status", columnList = "status"),
        Index(name = "idx_return_requested_at", columnList = "requested_at"),
        Index(name = "idx_return_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Return.withItems",
    attributeNodes = [
        NamedAttributeNode("items")
    ]
)
class Return : BaseEntity() {

    @Column(name = "order_id", nullable = false)
    var orderId: String = ""

    @Column(name = "order_display_id")
    var orderDisplayId: Int? = null

    @Column(name = "customer_id")
    var customerId: String? = null

    @Column(name = "customer_email")
    var customerEmail: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReturnStatus = ReturnStatus.REQUESTED

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var reason: ReturnReason = ReturnReason.OTHER

    @Column(name = "reason_note", columnDefinition = "TEXT")
    var reasonNote: String? = null

    @Column(name = "refund_amount", precision = 19, scale = 4, nullable = false)
    var refundAmount: BigDecimal = BigDecimal.ZERO

    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = "GBP"

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Instant = Instant.now()

    @Column(name = "approved_at")
    var approvedAt: Instant? = null

    @Column(name = "received_at")
    var receivedAt: Instant? = null

    @Column(name = "refunded_at")
    var refundedAt: Instant? = null

    @Column(name = "rejected_at")
    var rejectedAt: Instant? = null

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    var rejectionReason: String? = null

    @Column(name = "return_deadline", nullable = false)
    var returnDeadline: Instant = Instant.now().plus(14, ChronoUnit.DAYS)

    @Column(name = "refund_id")
    var refundId: String? = null

    @OneToMany(mappedBy = "returnRequest", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var items: MutableSet<ReturnItem> = mutableSetOf()

    // =====================
    // Business Methods
    // =====================

    /**
     * Add an item to this return request
     */
    fun addItem(item: ReturnItem) {
        items.add(item)
        item.returnRequest = this
        recalculateRefundAmount()
    }

    /**
     * Remove an item from this return request
     */
    fun removeItem(item: ReturnItem) {
        items.remove(item)
        item.returnRequest = null
        recalculateRefundAmount()
    }

    /**
     * Recalculate the total refund amount based on items
     */
    fun recalculateRefundAmount() {
        refundAmount = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.total) }
    }

    /**
     * Check if return is within the 14-day window
     */
    fun isWithinReturnWindow(): Boolean {
        return Instant.now().isBefore(returnDeadline)
    }

    /**
     * Approve this return request
     */
    fun approve() {
        require(status == ReturnStatus.REQUESTED) { "Can only approve a requested return" }
        status = ReturnStatus.APPROVED
        approvedAt = Instant.now()
    }

    /**
     * Mark the returned items as received
     */
    fun markReceived() {
        require(status == ReturnStatus.APPROVED) { "Can only receive an approved return" }
        status = ReturnStatus.RECEIVED
        receivedAt = Instant.now()
    }

    /**
     * Mark refund as processed
     */
    fun markRefunded(refundId: String) {
        require(status == ReturnStatus.RECEIVED) { "Can only refund a received return" }
        status = ReturnStatus.REFUNDED
        refundedAt = Instant.now()
        this.refundId = refundId
    }

    /**
     * Reject this return request
     */
    fun reject(reason: String) {
        require(status == ReturnStatus.REQUESTED || status == ReturnStatus.APPROVED) {
            "Can only reject a requested or approved return"
        }
        status = ReturnStatus.REJECTED
        rejectedAt = Instant.now()
        rejectionReason = reason
    }

    /**
     * Cancel this return request (customer-initiated)
     */
    fun cancel() {
        require(status == ReturnStatus.REQUESTED || status == ReturnStatus.APPROVED) {
            "Can only cancel a requested or approved return"
        }
        status = ReturnStatus.CANCELED
    }

    /**
     * Check if return can be canceled
     */
    fun canCancel(): Boolean {
        return status == ReturnStatus.REQUESTED || status == ReturnStatus.APPROVED
    }

    /**
     * Check if return can be processed for refund
     */
    fun canProcessRefund(): Boolean {
        return status == ReturnStatus.RECEIVED
    }

    /**
     * Get days remaining until return deadline
     */
    fun getDaysRemaining(): Long {
        val now = Instant.now()
        return if (now.isBefore(returnDeadline)) {
            ChronoUnit.DAYS.between(now, returnDeadline)
        } else {
            0
        }
    }

    /**
     * Get the total number of items being returned
     */
    fun getTotalItemCount(): Int {
        return items.sumOf { it.quantity }
    }

    companion object {
        const val RETURN_WINDOW_DAYS = 14L

        /**
         * Create a new return request for an order
         */
        fun create(
            orderId: String,
            orderDisplayId: Int?,
            customerId: String?,
            customerEmail: String?,
            reason: ReturnReason,
            reasonNote: String?,
            currencyCode: String,
            orderCreatedAt: Instant
        ): Return {
            return Return().apply {
                this.orderId = orderId
                this.orderDisplayId = orderDisplayId
                this.customerId = customerId
                this.customerEmail = customerEmail
                this.reason = reason
                this.reasonNote = reasonNote
                this.currencyCode = currencyCode
                this.requestedAt = Instant.now()
                this.returnDeadline = orderCreatedAt.plus(RETURN_WINDOW_DAYS, ChronoUnit.DAYS)
            }
        }
    }
}
