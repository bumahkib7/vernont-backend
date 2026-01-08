package com.vernont.repository.pricing

import com.vernont.domain.pricing.PriceChangeLog
import com.vernont.domain.pricing.PriceChangeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PriceChangeLogRepository : JpaRepository<PriceChangeLog, String> {

    /**
     * Find all change logs for a variant, most recent first
     */
    fun findByVariantIdOrderByChangedAtDesc(variantId: String): List<PriceChangeLog>

    /**
     * Find all change logs for a variant with pagination
     */
    fun findByVariantIdOrderByChangedAtDesc(variantId: String, pageable: Pageable): Page<PriceChangeLog>

    /**
     * Find all change logs for a product, most recent first
     */
    fun findByProductIdOrderByChangedAtDesc(productId: String): List<PriceChangeLog>

    /**
     * Find recent change logs across all variants
     */
    fun findAllByOrderByChangedAtDesc(pageable: Pageable): Page<PriceChangeLog>

    /**
     * Find change logs by change type
     */
    fun findByChangeTypeOrderByChangedAtDesc(changeType: PriceChangeType, pageable: Pageable): Page<PriceChangeLog>

    /**
     * Find change logs by a specific user
     */
    fun findByChangedByOrderByChangedAtDesc(changedBy: String, pageable: Pageable): Page<PriceChangeLog>

    /**
     * Find change logs within a date range
     */
    fun findByChangedAtBetweenOrderByChangedAtDesc(
        startDate: Instant,
        endDate: Instant,
        pageable: Pageable
    ): Page<PriceChangeLog>

    /**
     * Find change logs for a specific rule
     */
    fun findByRuleIdOrderByChangedAtDesc(ruleId: String, pageable: Pageable): Page<PriceChangeLog>

    /**
     * Count changes for a variant
     */
    fun countByVariantId(variantId: String): Long

    /**
     * Count changes by type
     */
    fun countByChangeType(changeType: PriceChangeType): Long

    /**
     * Get recent activity (for real-time feed)
     */
    @Query("""
        SELECT p FROM PriceChangeLog p
        WHERE p.changedAt >= :since
        ORDER BY p.changedAt DESC
    """)
    fun findRecentActivity(@Param("since") since: Instant, pageable: Pageable): Page<PriceChangeLog>

    /**
     * Get activity summary by change type in a time period
     */
    @Query("""
        SELECT p.changeType, COUNT(p)
        FROM PriceChangeLog p
        WHERE p.changedAt >= :since
        GROUP BY p.changeType
    """)
    fun countByChangeTypeSince(@Param("since") since: Instant): List<Array<Any>>

    /**
     * Search change logs with filters
     */
    @Query("""
        SELECT p FROM PriceChangeLog p
        WHERE (:variantId IS NULL OR p.variantId = :variantId)
        AND (:productId IS NULL OR p.productId = :productId)
        AND (:changeType IS NULL OR p.changeType = :changeType)
        AND (:changedBy IS NULL OR p.changedBy = :changedBy)
        AND (:startDate IS NULL OR p.changedAt >= :startDate)
        AND (:endDate IS NULL OR p.changedAt <= :endDate)
        ORDER BY p.changedAt DESC
    """)
    fun findByFilters(
        @Param("variantId") variantId: String?,
        @Param("productId") productId: String?,
        @Param("changeType") changeType: PriceChangeType?,
        @Param("changedBy") changedBy: String?,
        @Param("startDate") startDate: Instant?,
        @Param("endDate") endDate: Instant?,
        pageable: Pageable
    ): Page<PriceChangeLog>

    /**
     * Get the last price change for a variant
     */
    fun findFirstByVariantIdOrderByChangedAtDesc(variantId: String): PriceChangeLog?

    /**
     * Get variants with most price changes
     */
    @Query("""
        SELECT p.variantId, COUNT(p) as changeCount
        FROM PriceChangeLog p
        WHERE p.changedAt >= :since
        GROUP BY p.variantId
        ORDER BY changeCount DESC
    """)
    fun findMostChangedVariants(@Param("since") since: Instant, pageable: Pageable): List<Array<Any>>
}
