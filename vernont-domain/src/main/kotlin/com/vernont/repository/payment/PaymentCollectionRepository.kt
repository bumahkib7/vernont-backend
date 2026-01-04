package com.vernont.repository.payment

import com.vernont.domain.payment.PaymentCollection
import com.vernont.domain.payment.PaymentCollectionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

/**
 * Repository for PaymentCollection entities
 * Provides CRUD operations and custom queries for payment collections
 */
@Repository
interface PaymentCollectionRepository : JpaRepository<PaymentCollection, String> {

    /**
     * Find payment collections by currency code
     */
    fun findByCurrencyCode(currencyCode: String): List<PaymentCollection>

    /**
     * Find payment collections by region ID
     */
    fun findByRegionId(regionId: String): List<PaymentCollection>

    /**
     * Find payment collections by status
     */
    fun findByStatus(status: PaymentCollectionStatus): List<PaymentCollection>

    /**
     * Find payment collections that are not deleted
     */
    fun findByDeletedAtIsNull(): List<PaymentCollection>

    /**
     * Find payment collections by status that are not deleted
     */
    fun findByStatusAndDeletedAtIsNull(status: PaymentCollectionStatus): List<PaymentCollection>

    /**
     * Find completed payment collections
     */
    @Query("""
        SELECT pc FROM PaymentCollection pc 
        WHERE pc.completedAt IS NOT NULL 
        AND pc.deletedAt IS NULL
        ORDER BY pc.completedAt DESC
    """)
    fun findCompletedCollections(): List<PaymentCollection>

    /**
     * Find payment collections completed after a certain date
     */
    @Query("""
        SELECT pc FROM PaymentCollection pc 
        WHERE pc.completedAt > :afterDate 
        AND pc.deletedAt IS NULL
        ORDER BY pc.completedAt DESC
    """)
    fun findCompletedAfter(@Param("afterDate") afterDate: Instant): List<PaymentCollection>

    /**
     * Find pending payment collections (not paid)
     */
    fun findByStatusInAndDeletedAtIsNull(statuses: List<PaymentCollectionStatus>): List<PaymentCollection>

    /**
     * Count payment collections by status
     */
    fun countByStatus(status: PaymentCollectionStatus): Long

    /**
     * Find payment collections by currency code and region
     */
    fun findByCurrencyCodeAndRegionIdAndDeletedAtIsNull(currencyCode: String, regionId: String): List<PaymentCollection>

    /**
     * Find payment collections with sessions
     */
    @Query("""
        SELECT DISTINCT pc FROM PaymentCollection pc 
        LEFT JOIN FETCH pc.paymentSessions ps
        WHERE pc.deletedAt IS NULL
        AND ps.deletedAt IS NULL
    """)
    fun findWithSessions(): List<PaymentCollection>

    /**
     * Find payment collection with sessions by ID
     */
    @Query("""
        SELECT pc FROM PaymentCollection pc 
        LEFT JOIN FETCH pc.paymentSessions ps
        WHERE pc.id = :id 
        AND pc.deletedAt IS NULL
    """)
    fun findByIdWithSessions(@Param("id") id: String): Optional<PaymentCollection>

    /**
     * Find payment collections with payments
     */
    @Query("""
        SELECT DISTINCT pc FROM PaymentCollection pc 
        LEFT JOIN FETCH pc.payments p
        WHERE pc.deletedAt IS NULL
        AND p.deletedAt IS NULL
    """)
    fun findWithPayments(): List<PaymentCollection>

    /**
     * Check if a payment collection is fully paid
     */
    @Query("""
        SELECT pc.status = 'PAID' FROM PaymentCollection pc 
        WHERE pc.id = :id 
        AND pc.deletedAt IS NULL
    """)
    fun isFullyPaid(@Param("id") id: String): Boolean

    /**
     * Find payment collections that require action
     */
    @Query("""
        SELECT pc FROM PaymentCollection pc 
        WHERE pc.status = 'REQUIRES_ACTION' 
        AND pc.deletedAt IS NULL
    """)
    fun findRequiringActionAndDeletedAtIsNull(): List<PaymentCollection>

}