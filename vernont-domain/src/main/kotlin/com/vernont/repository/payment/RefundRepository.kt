package com.vernont.repository.payment

import com.vernont.domain.payment.Refund
import com.vernont.domain.payment.RefundReason
import com.vernont.domain.payment.RefundStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RefundRepository : JpaRepository<Refund, String> {

    @EntityGraph(value = "Refund.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Refund>

    @EntityGraph(value = "Refund.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Refund?

    @EntityGraph(value = "Refund.withPayment", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithPaymentById(id: String): Refund?

    /**
     * @deprecated Use findByPaymentIdAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findByPaymentIdAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findByPaymentIdAndDeletedAtIsNull(paymentId)"))
    fun findByPaymentId(paymentId: String): List<Refund>

    fun findByPaymentIdAndDeletedAtIsNull(paymentId: String): List<Refund>

    /**
     * @deprecated Use findByOrderIdAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findByOrderIdAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findByOrderIdAndDeletedAtIsNull(orderId)"))
    fun findByOrderId(orderId: String): List<Refund>

    fun findByOrderIdAndDeletedAtIsNull(orderId: String): List<Refund>

    /**
     * @deprecated Use findByStatusAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findByStatusAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findByStatusAndDeletedAtIsNull(status)"))
    fun findByStatus(status: RefundStatus): List<Refund>

    fun findByStatusAndDeletedAtIsNull(status: RefundStatus): List<Refund>

    /**
     * @deprecated Use findByReasonAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findByReasonAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findByReasonAndDeletedAtIsNull(reason)"))
    fun findByReason(reason: RefundReason): List<Refund>

    fun findByReasonAndDeletedAtIsNull(reason: RefundReason): List<Refund>

    fun findByDeletedAtIsNull(): List<Refund>

    @Query("SELECT r FROM Refund r WHERE r.payment.id = :paymentId AND r.status = :status AND r.deletedAt IS NULL")
    fun findByPaymentIdAndStatus(@Param("paymentId") paymentId: String, @Param("status") status: RefundStatus): List<Refund>

    @Query("SELECT r FROM Refund r WHERE r.orderId = :orderId AND r.status = :status AND r.deletedAt IS NULL")
    fun findByOrderIdAndStatus(@Param("orderId") orderId: String, @Param("status") status: RefundStatus): List<Refund>

    @Query("SELECT SUM(r.amount) FROM Refund r WHERE r.payment.id = :paymentId AND r.status = :status AND r.deletedAt IS NULL")
    fun sumAmountByPaymentIdAndStatus(@Param("paymentId") paymentId: String, @Param("status") status: RefundStatus): java.math.BigDecimal?

    @Query("SELECT SUM(r.amount) FROM Refund r WHERE r.orderId = :orderId AND r.status = :status AND r.deletedAt IS NULL")
    fun sumAmountByOrderIdAndStatus(@Param("orderId") orderId: String, @Param("status") status: RefundStatus): java.math.BigDecimal?

    @Query("SELECT COUNT(r) FROM Refund r WHERE r.payment.id = :paymentId AND r.deletedAt IS NULL")
    fun countByPaymentId(@Param("paymentId") paymentId: String): Long

    @Query("SELECT COUNT(r) FROM Refund r WHERE r.orderId = :orderId AND r.deletedAt IS NULL")
    fun countByOrderId(@Param("orderId") orderId: String): Long

    @Query("SELECT COUNT(r) FROM Refund r WHERE r.status = :status AND r.deletedAt IS NULL")
    fun countByStatus(@Param("status") status: RefundStatus): Long
}
