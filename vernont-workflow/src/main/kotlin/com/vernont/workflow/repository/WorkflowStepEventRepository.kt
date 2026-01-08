package com.vernont.workflow.repository

import com.vernont.workflow.domain.StepEventStatus
import com.vernont.workflow.domain.WorkflowStepEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface WorkflowStepEventRepository : JpaRepository<WorkflowStepEvent, String> {

    /**
     * Find all step events for a workflow execution ordered by step index (logical order)
     */
    fun findByExecutionIdOrderByStepIndexAsc(executionId: String): List<WorkflowStepEvent>

    /**
     * Find all step events for a workflow execution ordered by time (actual timeline).
     * Uses startedAt as primary sort, stepIndex as tie-breaker for parallel steps.
     */
    @Query("""
        SELECT s FROM WorkflowStepEvent s
        WHERE s.executionId = :executionId
        ORDER BY s.startedAt ASC, s.stepIndex ASC
    """)
    fun findTimelineByExecutionId(@Param("executionId") executionId: String): List<WorkflowStepEvent>

    /**
     * Find a specific step event by execution ID and step index.
     * Used for fallback DB lookup when in-memory tracking misses (e.g., after restart).
     */
    fun findByExecutionIdAndStepIndex(executionId: String, stepIndex: Int): WorkflowStepEvent?

    /**
     * Find step events by workflow name
     */
    fun findByWorkflowNameOrderByStartedAtDesc(workflowName: String, pageable: Pageable): Page<WorkflowStepEvent>

    /**
     * Find step events by status
     */
    fun findByStatusOrderByStartedAtDesc(status: StepEventStatus, pageable: Pageable): Page<WorkflowStepEvent>

    /**
     * Find step events since a timestamp
     */
    fun findByStartedAtAfterOrderByStartedAtDesc(since: Instant, pageable: Pageable): Page<WorkflowStepEvent>

    /**
     * Find recent step events
     */
    @Query("""
        SELECT s FROM WorkflowStepEvent s
        ORDER BY s.startedAt DESC
    """)
    fun findRecentStepEvents(pageable: Pageable): Page<WorkflowStepEvent>

    /**
     * Find failed steps for a workflow
     */
    @Query("""
        SELECT s FROM WorkflowStepEvent s
        WHERE s.workflowName = :workflowName
        AND s.status = 'FAILED'
        ORDER BY s.startedAt DESC
    """)
    fun findFailedStepsByWorkflow(
        @Param("workflowName") workflowName: String,
        pageable: Pageable
    ): Page<WorkflowStepEvent>

    /**
     * Find stale RUNNING steps (for cleanup/monitoring).
     * Steps that have been RUNNING for longer than the threshold are likely orphaned.
     */
    @Query("""
        SELECT s FROM WorkflowStepEvent s
        WHERE s.status = 'RUNNING'
        AND s.startedAt < :threshold
        ORDER BY s.startedAt ASC
    """)
    fun findStaleRunningSteps(@Param("threshold") threshold: Instant): List<WorkflowStepEvent>

    /**
     * Get step duration statistics by workflow (type-safe projection)
     */
    @Query("""
        SELECT s.stepName as stepName,
               AVG(s.durationMs) as avgDurationMs,
               MAX(s.durationMs) as maxDurationMs,
               MIN(s.durationMs) as minDurationMs,
               COUNT(s) as count
        FROM WorkflowStepEvent s
        WHERE s.workflowName = :workflowName
        AND s.status = 'COMPLETED'
        AND s.startedAt > :since
        GROUP BY s.stepName
        ORDER BY AVG(s.durationMs) DESC
    """)
    fun getStepDurationStats(
        @Param("workflowName") workflowName: String,
        @Param("since") since: Instant
    ): List<StepDurationStat>

    /**
     * Count step events by status for a workflow (type-safe projection)
     */
    @Query("""
        SELECT s.status as status, COUNT(s) as count
        FROM WorkflowStepEvent s
        WHERE s.workflowName = :workflowName
        AND s.startedAt > :since
        GROUP BY s.status
    """)
    fun countByStatusForWorkflow(
        @Param("workflowName") workflowName: String,
        @Param("since") since: Instant
    ): List<StepStatusCount>

    /**
     * Delete old step events (for cleanup)
     */
    fun deleteByStartedAtBefore(before: Instant): Int
}

/**
 * Type-safe projection for step duration statistics
 */
interface StepDurationStat {
    val stepName: String
    val avgDurationMs: Double?
    val maxDurationMs: Long?
    val minDurationMs: Long?
    val count: Long
}

/**
 * Type-safe projection for step status counts
 */
interface StepStatusCount {
    val status: StepEventStatus
    val count: Long
}
