package com.vernont.workflow.domain

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Workflow execution entity following nexus-domain patterns
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
        WorkflowExecutionStatus.TIMEOUT
    )
    
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
    TIMEOUT
}