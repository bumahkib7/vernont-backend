package com.vernont.domain.intervention

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Severity level for intervention items
 */
enum class InterventionSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Status of an intervention item
 */
enum class InterventionStatus {
    /**
     * Waiting for human review
     */
    PENDING,

    /**
     * Being worked on
     */
    IN_PROGRESS,

    /**
     * Issue resolved
     */
    RESOLVED,

    /**
     * Acknowledged but no action taken
     */
    IGNORED
}

/**
 * Queue item for operations requiring human review.
 *
 * Use cases:
 * - Failed product cleanup that couldn't auto-recover
 * - Orphaned S3 images that need manual review
 * - Payment/refund edge cases
 * - Data inconsistencies detected by jobs
 */
@Entity
@Table(
    name = "human_intervention_queue",
    indexes = [
        Index(name = "idx_intervention_pending", columnList = "status, severity, created_at"),
        Index(name = "idx_intervention_entity", columnList = "entity_type, entity_id")
    ]
)
class HumanInterventionItem : BaseEntity() {

    /**
     * Type of intervention needed (e.g., FAILED_PRODUCT_CLEANUP, ORPHANED_IMAGES)
     */
    @Column(name = "intervention_type", nullable = false, length = 100)
    var interventionType: String = ""

    /**
     * Type of entity involved (e.g., Product, Order, Fulfillment)
     */
    @Column(name = "entity_type", nullable = false, length = 100)
    var entityType: String = ""

    /**
     * ID of the entity involved
     */
    @Column(name = "entity_id", nullable = false, length = 100)
    var entityId: String = ""

    /**
     * Severity level
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var severity: InterventionSeverity = InterventionSeverity.MEDIUM

    /**
     * Current status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: InterventionStatus = InterventionStatus.PENDING

    /**
     * Short title for dashboard display
     */
    @Column(nullable = false)
    var title: String = ""

    /**
     * Detailed description of the issue
     */
    @Column(columnDefinition = "TEXT")
    var description: String? = null

    /**
     * Error message that triggered this intervention
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null

    /**
     * Additional context as JSON (e.g., stack trace, related IDs)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_data", columnDefinition = "jsonb")
    var contextData: Map<String, Any?>? = null

    /**
     * When the intervention was resolved
     */
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    /**
     * Who resolved this intervention
     */
    @Column(name = "resolved_by")
    var resolvedBy: String? = null

    /**
     * Notes on how it was resolved
     */
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    var resolutionNotes: String? = null

    /**
     * Number of auto-retry attempts
     */
    @Column(name = "auto_retry_count", nullable = false)
    var autoRetryCount: Int = 0

    /**
     * Max auto-retries before giving up
     */
    @Column(name = "max_auto_retries", nullable = false)
    var maxAutoRetries: Int = 3

    /**
     * When to attempt next auto-retry
     */
    @Column(name = "next_auto_retry_at")
    var nextAutoRetryAt: Instant? = null

    companion object {
        fun create(
            interventionType: String,
            entityType: String,
            entityId: String,
            title: String,
            description: String? = null,
            errorMessage: String? = null,
            severity: InterventionSeverity = InterventionSeverity.MEDIUM,
            contextData: Map<String, Any?>? = null,
            maxAutoRetries: Int = 3
        ): HumanInterventionItem {
            return HumanInterventionItem().apply {
                this.interventionType = interventionType
                this.entityType = entityType
                this.entityId = entityId
                this.title = title
                this.description = description
                this.errorMessage = errorMessage
                this.severity = severity
                this.contextData = contextData
                this.status = InterventionStatus.PENDING
                this.maxAutoRetries = maxAutoRetries
            }
        }
    }

    /**
     * Mark as being worked on
     */
    fun startWorking(userId: String) {
        this.status = InterventionStatus.IN_PROGRESS
        this.updatedBy = userId
    }

    /**
     * Mark as resolved
     */
    fun resolve(userId: String, notes: String? = null) {
        this.status = InterventionStatus.RESOLVED
        this.resolvedAt = Instant.now()
        this.resolvedBy = userId
        this.resolutionNotes = notes
    }

    /**
     * Mark as ignored (acknowledged but no action)
     */
    fun ignore(userId: String, reason: String? = null) {
        this.status = InterventionStatus.IGNORED
        this.resolvedAt = Instant.now()
        this.resolvedBy = userId
        this.resolutionNotes = reason
    }

    /**
     * Schedule auto-retry with exponential backoff
     */
    fun scheduleAutoRetry() {
        if (autoRetryCount < maxAutoRetries) {
            autoRetryCount++
            val backoffMinutes = (1L shl (autoRetryCount - 1).coerceAtMost(6)) // 1, 2, 4, 8, 16, 32, 64 minutes
            nextAutoRetryAt = Instant.now().plus(backoffMinutes, ChronoUnit.MINUTES)
        }
    }

    /**
     * Check if auto-retry is due
     */
    fun isAutoRetryDue(): Boolean {
        return status == InterventionStatus.PENDING &&
                nextAutoRetryAt != null &&
                nextAutoRetryAt!!.isBefore(Instant.now()) &&
                autoRetryCount < maxAutoRetries
    }

    /**
     * Check if this can still be auto-retried
     */
    fun canAutoRetry(): Boolean = autoRetryCount < maxAutoRetries
}
