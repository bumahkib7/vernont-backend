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
     * Find all step events for a workflow execution
     */
    fun findByExecutionIdOrderByStepIndexAsc(executionId: String): List<WorkflowStepEvent>

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
     * Get step duration statistics by workflow
     */
    @Query("""
        SELECT s.stepName, AVG(s.durationMs), MAX(s.durationMs), MIN(s.durationMs), COUNT(s)
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
    ): List<Array<Any>>

    /**
     * Count step events by status for a workflow
     */
    @Query("""
        SELECT s.status, COUNT(s)
        FROM WorkflowStepEvent s
        WHERE s.workflowName = :workflowName
        AND s.startedAt > :since
        GROUP BY s.status
    """)
    fun countByStatusForWorkflow(
        @Param("workflowName") workflowName: String,
        @Param("since") since: Instant
    ): List<Array<Any>>

    /**
     * Delete old step events (for cleanup)
     */
    fun deleteByStartedAtBefore(before: Instant): Int
}
