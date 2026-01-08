package com.vernont.workflow.events

import java.time.Instant

/**
 * Event types for workflow execution tracking
 */
enum class WorkflowEventType {
    WORKFLOW_STARTED,
    WORKFLOW_COMPLETED,
    WORKFLOW_FAILED,
    STEP_STARTED,
    STEP_COMPLETED,
    STEP_FAILED
}

/**
 * Status of workflow/step execution
 */
enum class ExecutionStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED
}

/**
 * Event published for workflow execution tracking
 * Sent to both Kafka (persistence) and WebSocket (real-time UI)
 */
data class WorkflowExecutionEvent(
    val eventType: WorkflowEventType,
    val executionId: String,
    val workflowName: String,
    val stepName: String? = null,
    val stepIndex: Int? = null,
    val totalSteps: Int? = null,
    val status: ExecutionStatus,
    val input: String? = null,      // JSON serialized input
    val output: String? = null,     // JSON serialized output
    val error: String? = null,
    val errorType: String? = null,
    val durationMs: Long? = null,
    val timestamp: Instant = Instant.now(),
    val correlationId: String? = null,
    val parentExecutionId: String? = null
) {
    companion object {
        fun workflowStarted(
            executionId: String,
            workflowName: String,
            input: String?,
            correlationId: String? = null,
            parentExecutionId: String? = null
        ) = WorkflowExecutionEvent(
            eventType = WorkflowEventType.WORKFLOW_STARTED,
            executionId = executionId,
            workflowName = workflowName,
            status = ExecutionStatus.RUNNING,
            input = input,
            correlationId = correlationId,
            parentExecutionId = parentExecutionId
        )

        fun workflowCompleted(
            executionId: String,
            workflowName: String,
            output: String?,
            durationMs: Long,
            correlationId: String? = null
        ) = WorkflowExecutionEvent(
            eventType = WorkflowEventType.WORKFLOW_COMPLETED,
            executionId = executionId,
            workflowName = workflowName,
            status = ExecutionStatus.COMPLETED,
            output = output,
            durationMs = durationMs,
            correlationId = correlationId
        )

        fun workflowFailed(
            executionId: String,
            workflowName: String,
            error: String?,
            errorType: String?,
            durationMs: Long,
            correlationId: String? = null
        ) = WorkflowExecutionEvent(
            eventType = WorkflowEventType.WORKFLOW_FAILED,
            executionId = executionId,
            workflowName = workflowName,
            status = ExecutionStatus.FAILED,
            error = error,
            errorType = errorType,
            durationMs = durationMs,
            correlationId = correlationId
        )

        fun stepStarted(
            executionId: String,
            workflowName: String,
            stepName: String,
            stepIndex: Int,
            totalSteps: Int,
            input: String?,
            correlationId: String? = null
        ) = WorkflowExecutionEvent(
            eventType = WorkflowEventType.STEP_STARTED,
            executionId = executionId,
            workflowName = workflowName,
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            status = ExecutionStatus.RUNNING,
            input = input,
            correlationId = correlationId
        )

        fun stepCompleted(
            executionId: String,
            workflowName: String,
            stepName: String,
            stepIndex: Int,
            totalSteps: Int,
            output: String?,
            durationMs: Long,
            correlationId: String? = null
        ) = WorkflowExecutionEvent(
            eventType = WorkflowEventType.STEP_COMPLETED,
            executionId = executionId,
            workflowName = workflowName,
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            status = ExecutionStatus.COMPLETED,
            output = output,
            durationMs = durationMs,
            correlationId = correlationId
        )

        fun stepFailed(
            executionId: String,
            workflowName: String,
            stepName: String,
            stepIndex: Int,
            totalSteps: Int,
            error: String?,
            errorType: String?,
            durationMs: Long,
            correlationId: String? = null
        ) = WorkflowExecutionEvent(
            eventType = WorkflowEventType.STEP_FAILED,
            executionId = executionId,
            workflowName = workflowName,
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            status = ExecutionStatus.FAILED,
            error = error,
            errorType = errorType,
            durationMs = durationMs,
            correlationId = correlationId
        )
    }
}
