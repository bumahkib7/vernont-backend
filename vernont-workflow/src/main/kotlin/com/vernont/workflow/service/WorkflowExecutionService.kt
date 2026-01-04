package com.vernont.workflow.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.workflow.domain.WorkflowExecution
import com.vernont.workflow.domain.WorkflowExecutionStatus
import com.vernont.workflow.repository.WorkflowExecutionRepository
import com.vernont.workflow.repository.WorkflowExecutionStats
import com.vernont.domain.common.BaseEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for managing workflow executions following nexus-application patterns
 */
@Service
@Transactional
class WorkflowExecutionService(
    private val workflowExecutionRepository: WorkflowExecutionRepository,
    private val objectMapper: ObjectMapper
) {
    
    /**
     * Create a new workflow execution
     */
    fun createExecution(
        workflowName: String,
        inputData: Any?,
        parentExecutionId: String? = null,
        correlationId: String? = null,
        maxRetries: Int = 3,
        timeoutSeconds: Long? = null
    ): WorkflowExecution {
        var attempts = 0
        var lastError: Exception? = null

        while (attempts < 3) {
            attempts++
            try {
                val execution = WorkflowExecution().apply {
                    // Regenerate id each attempt to avoid rare collisions
                    this.id = BaseEntity.generateId()
                    this.workflowName = workflowName
                    this.inputData = inputData?.let { serializeData(it) }
                    this.parentExecutionId = parentExecutionId
                    this.correlationId = correlationId
                    this.maxRetries = maxRetries
                    this.timeoutSeconds = timeoutSeconds
                    this.status = WorkflowExecutionStatus.RUNNING
                }

                return workflowExecutionRepository.save(execution).also {
                    logger.info { "Created workflow execution: ${it.id} for workflow: $workflowName" }
                }
            } catch (ex: DataIntegrityViolationException) {
                lastError = ex
                logger.warn(ex) { "WorkflowExecution ID collision on attempt $attempts for workflow $workflowName; retrying with new id" }
            }
        }

        throw lastError ?: IllegalStateException("Failed to create workflow execution for $workflowName after retries")
    }
    
    /**
     * Update execution status
     */
    fun updateExecution(execution: WorkflowExecution): WorkflowExecution {
        return workflowExecutionRepository.save(execution)
    }
    
    /**
     * Mark execution as completed
     */
    fun completeExecution(executionId: String, outputData: Any? = null): WorkflowExecution {
        val execution = getExecution(executionId)
        execution.markAsCompleted(outputData?.let { serializeData(it) })
        return updateExecution(execution)
    }
    
    /**
     * Mark execution as failed
     */
    fun failExecution(executionId: String, error: Throwable): WorkflowExecution {
        val execution = getExecution(executionId)
        execution.markAsFailed(error)
        return updateExecution(execution)
    }
    
    /**
     * Mark execution as compensated
     */
    fun compensateExecution(executionId: String): WorkflowExecution {
        val execution = getExecution(executionId)
        execution.markAsCompensated()
        return updateExecution(execution)
    }
    
    /**
     * Pause execution
     */
    fun pauseExecution(executionId: String): WorkflowExecution {
        val execution = getExecution(executionId)
        execution.pause()
        return updateExecution(execution)
    }
    
    /**
     * Resume execution
     */
    fun resumeExecution(executionId: String): WorkflowExecution {
        val execution = getExecution(executionId)
        execution.resume()
        return updateExecution(execution)
    }
    
    /**
     * Cancel execution
     */
    fun cancelExecution(executionId: String): WorkflowExecution {
        val execution = getExecution(executionId)
        execution.cancel()
        return updateExecution(execution)
    }
    
    /**
     * Retry failed execution
     */
    fun retryExecution(executionId: String): WorkflowExecution {
        val execution = getExecution(executionId)
        
        if (!execution.canRetry()) {
            throw IllegalStateException("Execution $executionId cannot be retried")
        }
        
        execution.incrementRetry()
        return updateExecution(execution)
    }
    
    /**
     * Get execution by ID
     */
    @Transactional(readOnly = true)
    fun getExecution(executionId: String): WorkflowExecution {
        return workflowExecutionRepository.findById(executionId)
            .orElseThrow { WorkflowExecutionNotFoundException("Execution not found: $executionId") }
    }
    
    /**
     * Find executions by workflow name
     */
    @Transactional(readOnly = true)
    fun findExecutionsByWorkflow(workflowName: String, pageable: Pageable): Page<WorkflowExecution> {
        return workflowExecutionRepository.findByWorkflowNameOrderByCreatedAtDesc(workflowName, pageable)
    }
    
    /**
     * Find executions by status
     */
    @Transactional(readOnly = true)
    fun findExecutionsByStatus(status: WorkflowExecutionStatus, pageable: Pageable): Page<WorkflowExecution> {
        return workflowExecutionRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
    }
    
    /**
     * Find executions by correlation ID
     */
    @Transactional(readOnly = true)
    fun findExecutionsByCorrelationId(correlationId: String): List<WorkflowExecution> {
        return workflowExecutionRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId)
    }
    
    /**
     * Find child executions
     */
    @Transactional(readOnly = true)
    fun findChildExecutions(parentExecutionId: String): List<WorkflowExecution> {
        return workflowExecutionRepository.findByParentExecutionIdOrderByCreatedAtAsc(parentExecutionId)
    }
    
    /**
     * Get workflow execution statistics
     */
    @Transactional(readOnly = true)
    fun getWorkflowStatistics(workflowName: String, since: Instant): List<WorkflowExecutionStats> {
        return workflowExecutionRepository.getExecutionStatsByWorkflow(workflowName, since)
    }
    
    /**
     * Find timed out executions
     */
    @Transactional(readOnly = true)
    fun findTimedOutExecutions(): List<WorkflowExecution> {
        return workflowExecutionRepository.findTimedOutExecutions(Instant.now())
    }
    
    /**
     * Find retryable executions
     */
    @Transactional(readOnly = true)
    fun findRetryableExecutions(since: Instant): List<WorkflowExecution> {
        return workflowExecutionRepository.findRetryableExecutions(since)
    }
    
    /**
     * Find recent failures for monitoring
     */
    @Transactional(readOnly = true)
    fun findRecentFailures(since: Instant, pageable: Pageable): Page<WorkflowExecution> {
        return workflowExecutionRepository.findRecentFailures(since, pageable)
    }
    
    /**
     * Cleanup old executions
     */
    fun cleanupOldExecutions(olderThan: Instant): Long {
        val terminalStates = listOf(
            WorkflowExecutionStatus.COMPLETED,
            WorkflowExecutionStatus.FAILED,
            WorkflowExecutionStatus.CANCELLED,
            WorkflowExecutionStatus.TIMEOUT
        )
        
        return workflowExecutionRepository.deleteByCompletedAtBeforeAndStatusIn(olderThan, terminalStates).also {
            logger.info { "Cleaned up $it old workflow executions" }
        }
    }
    
    /**
     * Handle timeout for executions
     */
    fun handleTimeoutExecutions(): List<WorkflowExecution> {
        val timedOutExecutions = findTimedOutExecutions()
        
        return timedOutExecutions.map { execution ->
            execution.status = WorkflowExecutionStatus.TIMEOUT
            execution.completedAt = Instant.now()
            execution.errorMessage = "Workflow execution timed out after ${execution.timeoutSeconds} seconds"
            updateExecution(execution)
        }.also {
            if (it.isNotEmpty()) {
                logger.warn { "Marked ${it.size} executions as timed out" }
            }
        }
    }
    
    /**
     * Serialize data for persistence
     */
    private fun serializeData(data: Any): String {
        return try {
            objectMapper.writeValueAsString(data)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to serialize workflow data: ${data::class.simpleName}" }
            data.toString()
        }
    }
    
    /**
     * Deserialize data from persistence
     */
    fun <T> deserializeData(json: String?, clazz: Class<T>): T? {
        return if (json.isNullOrBlank()) {
            null
        } else {
            try {
                objectMapper.readValue(json, clazz)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to deserialize workflow data to ${clazz.simpleName}" }
                null
            }
        }
    }
}

class WorkflowExecutionNotFoundException(message: String) : RuntimeException(message)
