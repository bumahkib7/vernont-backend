package com.vernont.repository.pricing

import com.vernont.domain.pricing.PricingRule
import com.vernont.domain.pricing.PricingRuleStatus
import com.vernont.domain.pricing.PricingRuleType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PricingRuleRepository : JpaRepository<PricingRule, String> {

    /**
     * Find all non-deleted rules
     */
    fun findByDeletedAtIsNull(): List<PricingRule>

    /**
     * Find all non-deleted rules with pagination
     */
    fun findByDeletedAtIsNull(pageable: Pageable): Page<PricingRule>

    /**
     * Find a rule by ID if not deleted
     */
    fun findByIdAndDeletedAtIsNull(id: String): PricingRule?

    /**
     * Find rules by status
     */
    fun findByStatusAndDeletedAtIsNull(status: PricingRuleStatus): List<PricingRule>

    /**
     * Find rules by type
     */
    fun findByTypeAndDeletedAtIsNull(type: PricingRuleType): List<PricingRule>

    /**
     * Find active rules ordered by priority
     */
    @Query("""
        SELECT r FROM PricingRule r
        WHERE r.status = 'ACTIVE'
        AND r.deletedAt IS NULL
        AND (r.startAt IS NULL OR r.startAt <= :now)
        AND (r.endAt IS NULL OR r.endAt >= :now)
        ORDER BY r.priority DESC
    """)
    fun findActiveRules(@Param("now") now: Instant = Instant.now()): List<PricingRule>

    /**
     * Find scheduled rules that should become active
     */
    @Query("""
        SELECT r FROM PricingRule r
        WHERE r.status = 'SCHEDULED'
        AND r.deletedAt IS NULL
        AND r.startAt IS NOT NULL
        AND r.startAt <= :now
    """)
    fun findScheduledRulesToActivate(@Param("now") now: Instant): List<PricingRule>

    /**
     * Find active rules that should become inactive (expired)
     */
    @Query("""
        SELECT r FROM PricingRule r
        WHERE r.status = 'ACTIVE'
        AND r.deletedAt IS NULL
        AND r.endAt IS NOT NULL
        AND r.endAt < :now
    """)
    fun findExpiredRules(@Param("now") now: Instant): List<PricingRule>

    /**
     * Find rules that apply to a specific product
     */
    @Query("""
        SELECT r FROM PricingRule r
        WHERE r.deletedAt IS NULL
        AND r.status = 'ACTIVE'
        AND (r.targetType IS NULL OR r.targetType = 'ALL' OR :productId IN (SELECT t FROM r.targetIds t))
        ORDER BY r.priority DESC
    """)
    fun findActiveRulesForProduct(@Param("productId") productId: String): List<PricingRule>

    /**
     * Check if a rule with the given name exists
     */
    fun existsByNameAndDeletedAtIsNull(name: String): Boolean

    /**
     * Check if a rule with the given name exists (excluding a specific ID)
     */
    fun existsByNameAndIdNotAndDeletedAtIsNull(name: String, id: String): Boolean

    /**
     * Search rules by name
     */
    @Query("""
        SELECT r FROM PricingRule r
        WHERE r.deletedAt IS NULL
        AND LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY r.priority DESC
    """)
    fun searchByName(@Param("query") query: String, pageable: Pageable): Page<PricingRule>
}
