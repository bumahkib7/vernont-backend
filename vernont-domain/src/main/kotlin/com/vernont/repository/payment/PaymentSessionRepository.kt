package com.vernont.repository.payment

import com.vernont.domain.payment.PaymentSession
import com.vernont.domain.payment.PaymentSessionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for PaymentSession entities
 * Provides CRUD operations and custom queries for payment sessions
 */
@Repository
interface PaymentSessionRepository : JpaRepository<PaymentSession, String> {

    /**
     * Find payment sessions by payment collection ID
     */
    fun findByPaymentCollectionId(paymentCollectionId: String): List<PaymentSession>

    /**
     * Find payment sessions by payment collection ID and status
     */
    fun findByPaymentCollectionIdAndStatus(paymentCollectionId: String, status: PaymentSessionStatus): List<PaymentSession>

    /**
     * Find payment sessions by provider ID
     */
    fun findByProviderId(providerId: String): List<PaymentSession>

    /**
     * Find payment sessions by status
     */
    fun findByStatus(status: PaymentSessionStatus): List<PaymentSession>

    /**
     * Find payment sessions that are not deleted
     */
    fun findByDeletedAtIsNull(): List<PaymentSession>

    /**
     * Find payment sessions by payment collection ID that are not deleted
     */
    fun findByPaymentCollectionIdAndDeletedAtIsNull(paymentCollectionId: String): List<PaymentSession>

    /**
     * Find payment sessions by payment collection ID and status that are not deleted
     */
    fun findByPaymentCollectionIdAndStatusAndDeletedAtIsNull(
        paymentCollectionId: String, 
        status: PaymentSessionStatus
    ): List<PaymentSession>

    /**
     * Find active payment sessions (not error, not canceled)
     */
    @Query("""
        SELECT ps FROM PaymentSession ps 
        WHERE ps.paymentCollectionId = :paymentCollectionId 
        AND ps.status NOT IN ('ERROR', 'CANCELED') 
        AND ps.deletedAt IS NULL
    """)
    fun findActiveByPaymentCollectionId(@Param("paymentCollectionId") paymentCollectionId: String): List<PaymentSession>

    /**
     * Find authorized payment sessions
     */
    @Query("""
        SELECT ps FROM PaymentSession ps 
        WHERE ps.status = 'AUTHORIZED' 
        AND ps.deletedAt IS NULL
        ORDER BY ps.authorizedAt DESC
    """)
    fun findAuthorizedSessions(): List<PaymentSession>

    /**
     * Find payment sessions authorized before a certain date
     */
    @Query("""
        SELECT ps FROM PaymentSession ps 
        WHERE ps.status = 'AUTHORIZED' 
        AND ps.authorizedAt < :beforeDate 
        AND ps.deletedAt IS NULL
    """)
    fun findAuthorizedSessionsBefore(@Param("beforeDate") beforeDate: Instant): List<PaymentSession>

    /**
     * Count payment sessions by status for a payment collection
     */
    fun countByPaymentCollectionIdAndStatus(paymentCollectionId: String, status: PaymentSessionStatus): Long

    /**
     * Find payment session by payment collection ID and provider ID
     */
    fun findByPaymentCollectionIdAndProviderIdAndDeletedAtIsNull(
        paymentCollectionId: String, 
        providerId: String
    ): Optional<PaymentSession>

    /**
     * Check if a payment collection has any successful payment sessions
     */
    @Query("""
        SELECT COUNT(ps) > 0 FROM PaymentSession ps 
        WHERE ps.paymentCollectionId = :paymentCollectionId 
        AND ps.status IN ('AUTHORIZED', 'CAPTURED') 
        AND ps.deletedAt IS NULL
    """)
    fun hasSuccessfulSessions(@Param("paymentCollectionId") paymentCollectionId: String): Boolean

    /**
     * Find payment sessions that require action
     */
    fun findByStatusAndDeletedAtIsNull(status: PaymentSessionStatus): List<PaymentSession>

    /**
     * Find payment sessions by currency code
     */
    fun findByCurrencyCodeAndDeletedAtIsNull(currencyCode: String): List<PaymentSession>
}