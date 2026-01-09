package com.vernont.workflow.domain

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Workflow execution entity following nexus-domain patterns.
 * Supports idempotency for safe retries and phased execution.
 */
@Entity
@Table(
    name = "workflow_executions",
    indexes = [
        Index(name = "idx_workflow_name", columnList = "workflow_name"),
        Index(name = "idx_workflow_status", columnList = "status"),
        Index(name = "idx_workflow_created", columnList = "created_at"),
        Index(name = "idx_workflow_parent", columnList = "parent_execution_id")
    ]
)
class WorkflowExecution : BaseEntity() {

    @Column(name = "workflow_name", nullable = false)
    var workflowName: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WorkflowExecutionStatus = WorkflowExecutionStatus.RUNNING

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    var errorStackTrace: String? = null

    @Column(name = "input_data", columnDefinition = "TEXT")
    var inputData: String? = null

    @Column(name = "output_data", columnDefinition = "TEXT")
    var outputData: String? = null

    @Column(name = "context_data", columnDefinition = "TEXT")
    var contextData: String? = null

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    @Column(name = "max_retries", nullable = false)
    var maxRetries: Int = 3

    @Column(name = "timeout_seconds")
    var timeoutSeconds: Long? = null

    @Column(name = "parent_execution_id")
    var parentExecutionId: String? = null

    @Column(name = "correlation_id")
    var correlationId: String? = null

    @Column(name = "execution_version", nullable = false)
    var executionVersion: Int = 1

    // ============================================================================
    // IDEMPOTENCY SUPPORT
    // ============================================================================

    /**
     * Client-provided or deterministic key for duplicate prevention.
     * Format examples: "create-product:my-handle" or UUID from Idempotency-Key header.
     */
    @Column(name = "idempotency_key")
    var idempotencyKey: String? = null

    /**
     * ID of the primary entity created by this workflow (e.g., product_id).
     * Used for quick lookup without parsing resultPayload.
     */
    @Column(name = "result_id", length = 100)
    var resultId: String? = null

    /**
     * Cached JSON response for idempotent replay.
     * When a duplicate request arrives, return this instead of re-executing.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb")
    var resultPayload: Map<String, Any?>? = null

    /**
     * When this idempotency record expires and can be cleaned up.
     * Default: 24 hours from creation.
     */
    @Column(name = "expires_at")
    var expiresAt: Instant? = null
    
    // Business methods
    fun markAsCompleted(outputData: String? = null) {
        this.status = WorkflowExecutionStatus.COMPLETED
        this.completedAt = Instant.now()
        this.outputData = outputData
    }
    
    fun markAsFailed(error: Throwable) {
        this.status = WorkflowExecutionStatus.FAILED
        this.completedAt = Instant.now()
        this.errorMessage = error.message
        this.errorStackTrace = error.stackTraceToString()
    }
    
    fun markAsCompensated() {
        this.status = WorkflowExecutionStatus.COMPENSATED
        this.completedAt = Instant.now()
    }
    
    fun pause() {
        this.status = WorkflowExecutionStatus.PAUSED
    }
    
    fun resume() {
        this.status = WorkflowExecutionStatus.RUNNING
    }
    
    fun cancel() {
        this.status = WorkflowExecutionStatus.CANCELLED
        this.completedAt = Instant.now()
    }
    
    fun incrementRetry() {
        this.retryCount++
        this.status = WorkflowExecutionStatus.RUNNING
        this.completedAt = null
        this.errorMessage = null
        this.errorStackTrace = null
    }
    
    fun canRetry(): Boolean = retryCount < maxRetries && !isTerminalState()
    
    fun isRunning(): Boolean = status == WorkflowExecutionStatus.RUNNING
    fun isCompleted(): Boolean = status == WorkflowExecutionStatus.COMPLETED
    fun isFailed(): Boolean = status == WorkflowExecutionStatus.FAILED
    fun isPaused(): Boolean = status == WorkflowExecutionStatus.PAUSED
    fun isCompensated(): Boolean = status == WorkflowExecutionStatus.COMPENSATED
    fun isCancelled(): Boolean = status == WorkflowExecutionStatus.CANCELLED
    fun isTimedOut(): Boolean = status == WorkflowExecutionStatus.TIMEOUT
    
    fun isTerminalState(): Boolean = status in listOf(
        WorkflowExecutionStatus.COMPLETED,
        WorkflowExecutionStatus.FAILED,
        WorkflowExecutionStatus.COMPENSATED,
        WorkflowExecutionStatus.CANCELLED,
        WorkflowExecutionStatus.TIMEOUT,
        WorkflowExecutionStatus.CLEANED_UP
    )

    // ============================================================================
    // IDEMPOTENCY HELPER METHODS
    // ============================================================================

    /**
     * Create a new execution for an idempotent workflow
     */
    companion object {
        const val DEFAULT_EXPIRY_HOURS = 24L

        fun createIdempotent(
            workflowName: String,
            idempotencyKey: String,
            inputData: String? = null,
            correlationId: String? = null,
            expiryHours: Long = DEFAULT_EXPIRY_HOURS
        ): WorkflowExecution {
            return WorkflowExecution().apply {
                this.workflowName = workflowName
                this.idempotencyKey = idempotencyKey
                this.inputData = inputData
                this.correlationId = correlationId
                this.status = WorkflowExecutionStatus.RUNNING
                this.expiresAt = Instant.now().plus(expiryHours, ChronoUnit.HOURS)
            }
        }
    }

    /**
     * Mark as completed with idempotent result caching
     */
    fun markAsCompletedWithResult(resultId: String, resultPayload: Map<String, Any?>) {
        this.status = WorkflowExecutionStatus.COMPLETED
        this.completedAt = Instant.now()
        this.resultId = resultId
        this.resultPayload = resultPayload
    }

    /**
     * Mark as cleaned up (resources reclaimed after failure)
     */
    fun markAsCleanedUp() {
        this.status = WorkflowExecutionStatus.CLEANED_UP
        this.completedAt = Instant.now()
    }

    fun isCleanedUp(): Boolean = status == WorkflowExecutionStatus.CLEANED_UP

    /**
     * Check if this execution can be retried (after failure or cleanup)
     */
    fun canRetryAfterFailure(): Boolean = status in listOf(
        WorkflowExecutionStatus.FAILED,
        WorkflowExecutionStatus.CLEANED_UP
    )

    /**
     * Check if idempotency record has expired
     */
    fun isExpired(): Boolean = expiresAt?.isBefore(Instant.now()) == true
    
    fun getDurationMillis(): Long? {
        return completedAt?.let { it.toEpochMilli() - createdAt.toEpochMilli() }
    }
}

enum class WorkflowExecutionStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    COMPENSATED,
    PAUSED,
    CANCELLED,
    TIMEOUT,
    /**
     * Resources cleaned up after failure (can retry)
     */
    CLEANED_UP
}