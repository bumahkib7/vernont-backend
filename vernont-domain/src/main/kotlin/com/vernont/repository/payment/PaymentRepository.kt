package com.vernont.repository.payment

import com.vernont.domain.payment.Payment
import com.vernont.domain.payment.PaymentStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PaymentRepository : JpaRepository<Payment, String> {

    /**
     * Find payment by ID with payment sessions loaded
     */
    @Query("""
        SELECT p FROM Payment p 
        LEFT JOIN FETCH p.paymentSession ps
        LEFT JOIN FETCH p.paymentCollection pc
        WHERE p.id = :id 
        AND p.deletedAt IS NULL
    """)
    fun findByIdWithSessions(@Param("id") id: String): Optional<Payment>

    @EntityGraph(value = "Payment.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Payment>

    @EntityGraph(value = "Payment.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Payment?

    @EntityGraph(value = "Payment.withProvider", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithProviderById(id: String): Payment?

    @EntityGraph(value = "Payment.withRefunds", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithRefundsById(id: String): Payment?

    fun findByOrderId(orderId: String): List<Payment>

    fun findByOrderIdAndDeletedAtIsNull(orderId: String): List<Payment>

    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.status = :status AND p.deletedAt IS NULL")
    fun findByOrderIdAndStatus(@Param("orderId") orderId: String, @Param("status") status: PaymentStatus): List<Payment>

    @EntityGraph(value = "Payment.full", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.status = :status AND p.deletedAt IS NULL")
    fun findFullByOrderIdAndStatus(@Param("orderId") orderId: String, @Param("status") status: PaymentStatus): List<Payment>

    fun findByCartId(cartId: String): List<Payment>

    fun findByCartIdAndDeletedAtIsNull(cartId: String): List<Payment>

    fun findByProviderId(providerId: String): List<Payment>

    fun findByProviderIdAndDeletedAtIsNull(providerId: String): List<Payment>

    fun findByStatus(status: PaymentStatus): List<Payment>

    fun findByStatusAndDeletedAtIsNull(status: PaymentStatus): List<Payment>

    fun findByExternalId(externalId: String): Payment?

    fun findByExternalIdAndDeletedAtIsNull(externalId: String): Payment?

    fun findByDeletedAtIsNull(): List<Payment>

    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.status IN :statuses AND p.deletedAt IS NULL")
    fun findByOrderIdAndStatusIn(@Param("orderId") orderId: String, @Param("statuses") statuses: List<PaymentStatus>): List<Payment>

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.orderId = :orderId AND p.status = :status AND p.deletedAt IS NULL")
    fun sumAmountByOrderIdAndStatus(@Param("orderId") orderId: String, @Param("status") status: PaymentStatus): java.math.BigDecimal?

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.orderId = :orderId AND p.deletedAt IS NULL")
    fun countByOrderId(@Param("orderId") orderId: String): Long

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status AND p.deletedAt IS NULL")
    fun countByStatus(@Param("status") status: PaymentStatus): Long

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.capturedAt IS NULL AND p.deletedAt IS NULL")
    fun findUncapturedByStatus(@Param("status") status: PaymentStatus): List<Payment>

    @Query("SELECT p FROM Payment p WHERE p.cartId = :cartId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    fun findFirstByCartIdOrderByCreatedAtDesc(@Param("cartId") cartId: String): Payment?
}
