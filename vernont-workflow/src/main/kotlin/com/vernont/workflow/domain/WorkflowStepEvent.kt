package com.vernont.workflow.domain

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Entity for persisting workflow step execution events
 * Used for historical analysis, debugging, and replay capabilities
 */
@Entity
@Table(name = "workflow_step_event")
class WorkflowStepEvent : BaseEntity() {

    @Column(name = "execution_id", nullable = false, length = 36)
    var executionId: String = ""

    @Column(name = "workflow_name", nullable = false)
    var workflowName: String = ""

    @Column(name = "step_name", nullable = false)
    var stepName: String = ""

    @Column(name = "step_index", nullable = false)
    var stepIndex: Int = 0

    @Column(name = "total_steps")
    var totalSteps: Int? = null

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    var status: StepEventStatus = StepEventStatus.RUNNING

    @Column(name = "input_data", columnDefinition = "TEXT")
    var inputData: String? = null

    @Column(name = "output_data", columnDefinition = "TEXT")
    var outputData: String? = null

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null

    @Column(name = "error_type")
    var errorType: String? = null

    @Column(name = "duration_ms")
    var durationMs: Long? = null

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now()

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    fun markCompleted(output: String?, duration: Long) {
        this.status = StepEventStatus.COMPLETED
        this.outputData = output
        this.durationMs = duration
        this.completedAt = Instant.now()
    }

    fun markFailed(error: String?, errorType: String?, duration: Long) {
        this.status = StepEventStatus.FAILED
        this.errorMessage = error
        this.errorType = errorType
        this.durationMs = duration
        this.completedAt = Instant.now()
    }

    companion object {
        fun create(
            executionId: String,
            workflowName: String,
            stepName: String,
            stepIndex: Int,
            totalSteps: Int? = null,
            inputData: String? = null
        ): WorkflowStepEvent = WorkflowStepEvent().apply {
            this.executionId = executionId
            this.workflowName = workflowName
            this.stepName = stepName
            this.stepIndex = stepIndex
            this.totalSteps = totalSteps
            this.status = StepEventStatus.RUNNING
            this.inputData = inputData
            this.startedAt = Instant.now()
        }
    }
}

enum class StepEventStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
