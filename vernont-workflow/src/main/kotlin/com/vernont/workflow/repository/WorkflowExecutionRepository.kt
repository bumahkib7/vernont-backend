package com.vernont.workflow.repository

import com.vernont.workflow.domain.WorkflowExecution
import com.vernont.workflow.domain.WorkflowExecutionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import java.time.Instant

/**
 * Repository for WorkflowExecution following nexus-domain patterns
 */
@Repository
interface WorkflowExecutionRepository : JpaRepository<WorkflowExecution, String> {

    // ============================================================================
    // IDEMPOTENCY QUERIES
    // ============================================================================

    /**
     * Find by idempotency key with pessimistic lock for safe concurrent access.
     * Uses SELECT ... FOR UPDATE to prevent race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT w FROM WorkflowExecution w
        WHERE w.idempotencyKey = :key
        AND w.workflowName = :workflowName
        AND w.deletedAt IS NULL
    """)
    fun findByIdempotencyKeyForUpdate(
        @Param("key") key: String,
        @Param("workflowName") workflowName: String
    ): WorkflowExecution?

    /**
     * Find by idempotency key without lock (for read-only checks)
     */
    @Query("""
        SELECT w FROM WorkflowExecution w
        WHERE w.idempotencyKey = :key
        AND w.workflowName = :workflowName
        AND w.deletedAt IS NULL
    """)
    fun findByIdempotencyKey(
        @Param("key") key: String,
        @Param("workflowName") workflowName: String
    ): WorkflowExecution?

    /**
     * Find by result ID (e.g., product ID)
     */
    fun findByResultIdAndDeletedAtIsNull(resultId: String): WorkflowExecution?

    /**
     * Find stale running executions (for recovery)
     */
    @Query("""
        SELECT w FROM WorkflowExecution w
        WHERE w.status = 'RUNNING'
        AND w.createdAt < :threshold
        AND w.deletedAt IS NULL
    """)
    fun findStaleRunningExecutions(@Param("threshold") threshold: Instant): List<WorkflowExecution>

    /**
     * Find expired idempotency records for cleanup
     */
    @Query("""
        SELECT w FROM WorkflowExecution w
        WHERE w.expiresAt IS NOT NULL
        AND w.expiresAt < :now
        AND w.status IN ('COMPLETED', 'FAILED', 'CLEANED_UP')
        AND w.deletedAt IS NULL
    """)
    fun findExpiredExecutions(@Param("now") now: Instant): List<WorkflowExecution>
    
    /**
     * Find executions by workflow name
     */
    fun findByWorkflowNameOrderByCreatedAtDesc(
        workflowName: String,
        pageable: Pageable
    ): Page<WorkflowExecution>
    
    /**
     * Find executions by status (enum)
     */
    fun findByStatusOrderByCreatedAtDesc(
        status: WorkflowExecutionStatus,
        pageable: Pageable
    ): Page<WorkflowExecution>

    /**
     * Find executions by status (string) for API queries
     */
    @Query("SELECT w FROM WorkflowExecution w WHERE CAST(w.status AS string) = :status ORDER BY w.createdAt DESC")
    fun findByStatusOrderByCreatedAtDesc(
        @Param("status") status: String,
        pageable: Pageable
    ): Page<WorkflowExecution>

    /**
     * Find executions by workflow name and status
     */
    @Query("SELECT w FROM WorkflowExecution w WHERE w.workflowName = :workflowName AND CAST(w.status AS string) = :status ORDER BY w.createdAt DESC")
    fun findByWorkflowNameAndStatusOrderByCreatedAtDesc(
        @Param("workflowName") workflowName: String,
        @Param("status") status: String,
        pageable: Pageable
    ): Page<WorkflowExecution>

    /**
     * Find executions created after a timestamp
     */
    fun findByCreatedAtAfterOrderByCreatedAtDesc(
        createdAt: Instant,
        pageable: Pageable
    ): Page<WorkflowExecution>

    /**
     * Find executions with status in list (for active queries)
     */
    @Query("SELECT w FROM WorkflowExecution w WHERE CAST(w.status AS string) IN :statuses ORDER BY w.createdAt DESC")
    fun findByStatusInOrderByCreatedAtDesc(
        @Param("statuses") statuses: List<String>,
        pageable: Pageable
    ): Page<WorkflowExecution>

    /**
     * Find running executions that have exceeded their timeout.
     *
     * A workflow is timed out if:
     *   created_at + timeout_seconds * 1s < :threshold
     */
    @Query(
        value = """
            SELECT *
            FROM workflow_executions w
            WHERE w.status = 'RUNNING'
              AND w.timeout_seconds IS NOT NULL
              AND (w.created_at + make_interval(secs => w.timeout_seconds)) < :threshold
        """,
        nativeQuery = true
    )
    fun findTimedOutExecutions(@Param("threshold") threshold: Instant): List<WorkflowExecution>


    /**
     * Find executions that can be retried
     */
    @Query("""
        SELECT w FROM WorkflowExecution w
        WHERE w.status = 'FAILED'
        AND w.retryCount < w.maxRetries
        AND w.createdAt > :since
    """)
    fun findRetryableExecutions(@Param("since") since: Instant): List<WorkflowExecution>
    
    /**
     * Find executions by correlation ID (for distributed workflows)
     */
    fun findByCorrelationIdOrderByCreatedAtAsc(correlationId: String): List<WorkflowExecution>
    
    /**
     * Find child executions
     */
    fun findByParentExecutionIdOrderByCreatedAtAsc(parentExecutionId: String): List<WorkflowExecution>
    
    /**
     * Find executions by creator within time range
     */
    fun findByCreatedByAndCreatedAtBetweenOrderByCreatedAtDesc(
        createdBy: String,
        startTime: Instant,
        endTime: Instant,
        pageable: Pageable
    ): Page<WorkflowExecution>
    
    /**
     * Count executions by workflow name and status
     */
    fun countByWorkflowNameAndStatus(workflowName: String, status: WorkflowExecutionStatus): Long
    
    /**
     * Find recent failed executions for monitoring
     */
    @Query("""
        SELECT w FROM WorkflowExecution w
        WHERE w.status = 'FAILED'
        AND w.createdAt >= :since
        ORDER BY w.createdAt DESC
    """)
    fun findRecentFailures(@Param("since") since: Instant, pageable: Pageable): Page<WorkflowExecution>
    
    /**
     * Find long-running executions
     */
    @Query("""
        SELECT w FROM WorkflowExecution w
        WHERE w.status = 'RUNNING'
        AND w.createdAt < :threshold
        ORDER BY w.createdAt ASC
    """)
    fun findLongRunningExecutions(@Param("threshold") threshold: Instant): List<WorkflowExecution>
    
    /**
     * Get execution statistics by workflow name
     */
    @Query("""
        SELECT w.status as status, COUNT(w) as count
        FROM WorkflowExecution w
        WHERE w.workflowName = :workflowName
        AND w.createdAt >= :since
        GROUP BY w.status
    """)
    fun getExecutionStatsByWorkflow(
        @Param("workflowName") workflowName: String,
        @Param("since") since: Instant
    ): List<WorkflowExecutionStats>
    
    /**
     * Find executions needing cleanup (old completed executions)
     */
    @Query("""
        SELECT w FROM WorkflowExecution w
        WHERE w.status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT')
        AND w.completedAt < :threshold
    """)
    fun findExecutionsForCleanup(@Param("threshold") threshold: Instant): List<WorkflowExecution>
    
    /**
     * Delete old executions (for cleanup)
     */
    fun deleteByCompletedAtBeforeAndStatusIn(
        completedAt: Instant,
        statuses: List<WorkflowExecutionStatus>
    ): Long
}

/**
 * Statistics projection
 */
interface WorkflowExecutionStats {
    val status: WorkflowExecutionStatus
    val count: Long
}