package com.vernont.repository.giftcard

import com.vernont.domain.giftcard.GiftCard
import com.vernont.domain.giftcard.GiftCardStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface GiftCardRepository : JpaRepository<GiftCard, String> {

    /**
     * Find a gift card by its code
     */
    fun findByCode(code: String): GiftCard?

    /**
     * Find a gift card by code ignoring case
     */
    fun findByCodeIgnoreCase(code: String): GiftCard?

    /**
     * Find a gift card by code with pessimistic lock for redemption
     * Prevents race conditions during concurrent redemptions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GiftCard g WHERE UPPER(g.code) = UPPER(:code)")
    fun findByCodeForRedemption(@Param("code") code: String): GiftCard?

    /**
     * Check if a code already exists
     */
    fun existsByCode(code: String): Boolean

    /**
     * Find gift cards issued to a specific customer
     */
    fun findByIssuedToCustomerIdOrderByCreatedAtDesc(customerId: String): List<GiftCard>

    /**
     * Find gift cards redeemed by a specific customer
     */
    fun findByRedeemedByCustomerIdOrderByFirstRedeemedAtDesc(customerId: String): List<GiftCard>

    /**
     * Find gift cards by status
     */
    fun findByStatusOrderByCreatedAtDesc(status: GiftCardStatus, pageable: Pageable): Page<GiftCard>

    /**
     * Find active gift cards with balance
     */
    fun findByStatusAndRemainingAmountGreaterThanOrderByCreatedAtDesc(
        status: GiftCardStatus,
        remainingAmount: Int,
        pageable: Pageable
    ): Page<GiftCard>

    /**
     * Find expired gift cards that need status update
     */
    @Query("""
        SELECT g FROM GiftCard g
        WHERE g.status = 'ACTIVE'
        AND g.expiresAt IS NOT NULL
        AND g.expiresAt < :now
    """)
    fun findExpiredGiftCards(@Param("now") now: Instant): List<GiftCard>

    /**
     * Find gift cards expiring soon (for notifications)
     */
    @Query("""
        SELECT g FROM GiftCard g
        WHERE g.status = 'ACTIVE'
        AND g.expiresAt IS NOT NULL
        AND g.expiresAt BETWEEN :start AND :end
        AND g.remainingAmount > 0
    """)
    fun findExpiringBetween(
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): List<GiftCard>

    /**
     * Search gift cards with filters
     * Note: Gift card codes are generated using uppercase characters only,
     * so case-insensitive search is not needed.
     * Note: Using CAST to force type inference and avoid PostgreSQL bytea binding issues with null parameters.
     */
    @Query("""
        SELECT g FROM GiftCard g
        WHERE (CAST(:status AS string) IS NULL OR g.status = :status)
        AND (CAST(:customerId AS string) IS NULL OR g.issuedToCustomerId = :customerId OR g.redeemedByCustomerId = :customerId)
        AND (CAST(:code AS string) IS NULL OR g.code LIKE CONCAT('%', CAST(:code AS string), '%'))
        ORDER BY g.createdAt DESC
    """)
    fun findByFilters(
        @Param("status") status: GiftCardStatus?,
        @Param("customerId") customerId: String?,
        @Param("code") code: String?,
        pageable: Pageable
    ): Page<GiftCard>

    /**
     * Get total unredeemed value
     */
    @Query("SELECT COALESCE(SUM(g.remainingAmount), 0) FROM GiftCard g WHERE g.status = 'ACTIVE'")
    fun getTotalUnredeemedValue(): Long

    /**
     * Get total issued value in a date range
     */
    @Query("""
        SELECT COALESCE(SUM(g.initialAmount), 0) FROM GiftCard g
        WHERE g.createdAt BETWEEN :start AND :end
    """)
    fun getTotalIssuedValueBetween(
        @Param("start") start: Instant,
        @Param("end") end: Instant
    ): Long

    /**
     * Count active gift cards
     */
    fun countByStatus(status: GiftCardStatus): Long

    /**
     * Find all active gift cards (not deleted)
     */
    fun findByDeletedAtIsNull(): List<GiftCard>

    /**
     * Find by ID and not deleted
     */
    fun findByIdAndDeletedAtIsNull(id: String): GiftCard?

    /**
     * Atomically redeem amount from gift card - prevents race conditions
     * Returns number of rows updated (1 if successful, 0 if insufficient balance or invalid)
     */
    @Modifying
    @Query("""
        UPDATE GiftCard g
        SET g.remainingAmount = g.remainingAmount - :amount,
            g.updatedAt = CURRENT_TIMESTAMP,
            g.firstRedeemedAt = CASE WHEN g.firstRedeemedAt IS NULL THEN CURRENT_TIMESTAMP ELSE g.firstRedeemedAt END,
            g.redeemedByCustomerId = CASE WHEN g.redeemedByCustomerId IS NULL THEN :customerId ELSE g.redeemedByCustomerId END,
            g.status = CASE WHEN (g.remainingAmount - :amount) <= 0 THEN 'FULLY_REDEEMED' ELSE g.status END,
            g.fullyRedeemedAt = CASE WHEN (g.remainingAmount - :amount) <= 0 THEN CURRENT_TIMESTAMP ELSE g.fullyRedeemedAt END
        WHERE g.id = :giftCardId
        AND g.remainingAmount >= :amount
        AND g.status = 'ACTIVE'
    """)
    fun atomicRedeem(
        @Param("giftCardId") giftCardId: String,
        @Param("amount") amount: Int,
        @Param("customerId") customerId: String
    ): Int
}
