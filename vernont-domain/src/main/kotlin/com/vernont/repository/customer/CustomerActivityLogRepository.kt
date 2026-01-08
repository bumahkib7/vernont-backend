package com.vernont.repository.customer

import com.vernont.domain.customer.CustomerActivityLog
import com.vernont.domain.customer.CustomerActivityType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface CustomerActivityLogRepository : JpaRepository<CustomerActivityLog, String> {

    /**
     * Find all activity for a customer, ordered by most recent first
     */
    fun findByCustomerIdOrderByOccurredAtDesc(customerId: String): List<CustomerActivityLog>

    /**
     * Find activity for a customer with pagination
     */
    fun findByCustomerIdOrderByOccurredAtDesc(customerId: String, pageable: Pageable): Page<CustomerActivityLog>

    /**
     * Find activity by type for a customer
     */
    fun findByCustomerIdAndActivityTypeOrderByOccurredAtDesc(
        customerId: String,
        activityType: CustomerActivityType
    ): List<CustomerActivityLog>

    /**
     * Find activity within a date range for a customer
     */
    fun findByCustomerIdAndOccurredAtBetweenOrderByOccurredAtDesc(
        customerId: String,
        startDate: Instant,
        endDate: Instant
    ): List<CustomerActivityLog>

    /**
     * Count activity by type for a customer
     */
    fun countByCustomerIdAndActivityType(customerId: String, activityType: CustomerActivityType): Long

    /**
     * Find recent activity across all customers (for admin dashboard)
     */
    fun findByOccurredAtAfterOrderByOccurredAtDesc(after: Instant, pageable: Pageable): Page<CustomerActivityLog>

    /**
     * Find activity performed by a specific admin user
     */
    fun findByPerformedByOrderByOccurredAtDesc(performedBy: String, pageable: Pageable): Page<CustomerActivityLog>

    /**
     * Find activity for customer with optional activity type filter
     */
    @Query("""
        SELECT a FROM CustomerActivityLog a
        WHERE a.customerId = :customerId
        AND (:activityType IS NULL OR a.activityType = :activityType)
        ORDER BY a.occurredAt DESC
    """)
    fun findByCustomerIdAndOptionalType(
        @Param("customerId") customerId: String,
        @Param("activityType") activityType: CustomerActivityType?,
        pageable: Pageable
    ): Page<CustomerActivityLog>

    /**
     * Search activity with multiple filters using native query to handle null parameters properly
     */
    @Query(
        value = """
        SELECT * FROM customer_activity_log
        WHERE (CAST(:customerId AS VARCHAR) IS NULL OR customer_id = :customerId)
        AND (CAST(:activityType AS VARCHAR) IS NULL OR activity_type = :activityType)
        AND (CAST(:startDate AS TIMESTAMP) IS NULL OR occurred_at >= :startDate)
        AND (CAST(:endDate AS TIMESTAMP) IS NULL OR occurred_at <= :endDate)
        ORDER BY occurred_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM customer_activity_log
        WHERE (CAST(:customerId AS VARCHAR) IS NULL OR customer_id = :customerId)
        AND (CAST(:activityType AS VARCHAR) IS NULL OR activity_type = :activityType)
        AND (CAST(:startDate AS TIMESTAMP) IS NULL OR occurred_at >= :startDate)
        AND (CAST(:endDate AS TIMESTAMP) IS NULL OR occurred_at <= :endDate)
        """,
        nativeQuery = true
    )
    fun findByFilters(
        @Param("customerId") customerId: String?,
        @Param("activityType") activityType: String?,
        @Param("startDate") startDate: Instant?,
        @Param("endDate") endDate: Instant?,
        pageable: Pageable
    ): Page<CustomerActivityLog>

    /**
     * Get distinct activity types for a customer (to show available filters)
     */
    @Query("SELECT DISTINCT a.activityType FROM CustomerActivityLog a WHERE a.customerId = :customerId")
    fun findDistinctActivityTypesByCustomerId(@Param("customerId") customerId: String): List<CustomerActivityType>

    /**
     * Delete all activity for a customer (for GDPR compliance)
     */
    fun deleteByCustomerId(customerId: String)
}
