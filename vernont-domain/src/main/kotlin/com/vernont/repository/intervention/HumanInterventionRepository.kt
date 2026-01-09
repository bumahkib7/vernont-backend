package com.vernont.repository.intervention

import com.vernont.domain.intervention.HumanInterventionItem
import com.vernont.domain.intervention.InterventionSeverity
import com.vernont.domain.intervention.InterventionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface HumanInterventionRepository : JpaRepository<HumanInterventionItem, String> {

    /**
     * Find pending interventions ordered by severity and age
     */
    @Query("""
        SELECT h FROM HumanInterventionItem h
        WHERE h.status = 'PENDING'
        AND h.deletedAt IS NULL
        ORDER BY
            CASE h.severity
                WHEN 'CRITICAL' THEN 0
                WHEN 'HIGH' THEN 1
                WHEN 'MEDIUM' THEN 2
                WHEN 'LOW' THEN 3
            END,
            h.createdAt ASC
    """)
    fun findPendingOrderedBySeverity(pageable: Pageable): Page<HumanInterventionItem>

    /**
     * Find interventions by status
     */
    fun findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
        status: InterventionStatus,
        pageable: Pageable
    ): Page<HumanInterventionItem>

    /**
     * Find interventions for a specific entity
     */
    fun findByEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        entityType: String,
        entityId: String
    ): List<HumanInterventionItem>

    /**
     * Find interventions by type
     */
    fun findByInterventionTypeAndStatusAndDeletedAtIsNull(
        interventionType: String,
        status: InterventionStatus
    ): List<HumanInterventionItem>

    /**
     * Find interventions due for auto-retry
     */
    @Query("""
        SELECT h FROM HumanInterventionItem h
        WHERE h.status = 'PENDING'
        AND h.nextAutoRetryAt IS NOT NULL
        AND h.nextAutoRetryAt <= :now
        AND h.autoRetryCount < h.maxAutoRetries
        AND h.deletedAt IS NULL
        ORDER BY h.nextAutoRetryAt
    """)
    fun findDueForAutoRetry(@Param("now") now: Instant): List<HumanInterventionItem>

    /**
     * Count pending interventions by severity
     */
    fun countByStatusAndSeverityAndDeletedAtIsNull(
        status: InterventionStatus,
        severity: InterventionSeverity
    ): Long

    /**
     * Count all pending interventions
     */
    fun countByStatusAndDeletedAtIsNull(status: InterventionStatus): Long

    /**
     * Check if there's already a pending intervention for this entity
     */
    @Query("""
        SELECT COUNT(h) > 0 FROM HumanInterventionItem h
        WHERE h.entityType = :entityType
        AND h.entityId = :entityId
        AND h.interventionType = :interventionType
        AND h.status = 'PENDING'
        AND h.deletedAt IS NULL
    """)
    fun existsPendingForEntity(
        @Param("entityType") entityType: String,
        @Param("entityId") entityId: String,
        @Param("interventionType") interventionType: String
    ): Boolean

    /**
     * Find recently resolved interventions (for audit)
     */
    @Query("""
        SELECT h FROM HumanInterventionItem h
        WHERE h.status IN ('RESOLVED', 'IGNORED')
        AND h.resolvedAt >= :since
        AND h.deletedAt IS NULL
        ORDER BY h.resolvedAt DESC
    """)
    fun findRecentlyResolved(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<HumanInterventionItem>

    /**
     * Find critical pending interventions (for alerting)
     */
    @Query("""
        SELECT h FROM HumanInterventionItem h
        WHERE h.status = 'PENDING'
        AND h.severity = 'CRITICAL'
        AND h.deletedAt IS NULL
        ORDER BY h.createdAt ASC
    """)
    fun findCriticalPending(): List<HumanInterventionItem>
}
