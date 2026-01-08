package com.vernont.workflow.scheduler

import com.vernont.workflow.repository.WorkflowStepEventRepository
import com.vernont.workflow.service.WorkflowExecutionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

/**
 * Scheduled tasks for workflow engine maintenance
 */
@Component
class WorkflowMaintenanceScheduler(
    private val workflowExecutionService: WorkflowExecutionService,
    private val stepEventRepository: WorkflowStepEventRepository,
    @param:Value("\${app.workflow.cleanup.retention-days:30}")
    private val retentionDays: Long,
    @param:Value("\${app.workflow.cleanup.enabled:true}")
    private val cleanupEnabled: Boolean,
    @param:Value("\${app.workflow.stale-step.threshold-minutes:30}")
    private val staleStepThresholdMinutes: Long
) {

    /**
     * Handle timeout executions every minute
     */
    @Scheduled(fixedRate = 60000) // Every minute
    fun handleTimeoutExecutions() {
        try {
            val timedOutExecutions = workflowExecutionService.handleTimeoutExecutions()
            if (timedOutExecutions.isNotEmpty()) {
                logger.info { "Handled ${timedOutExecutions.size} timed out workflow executions" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to handle timeout executions" }
        }
    }

    /**
     * Auto-retry failed executions every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    fun autoRetryFailedExecutions() {
        try {
            val since = Instant.now().minus(1, ChronoUnit.HOURS)
            val retryableExecutions = workflowExecutionService.findRetryableExecutions(since)
            
            logger.info { "Found ${retryableExecutions.size} retryable executions" }
            
            retryableExecutions.forEach { execution ->
                try {
                    workflowExecutionService.retryExecution(execution.id)
                    logger.info { "Auto-retried execution: ${execution.id}" }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to auto-retry execution: ${execution.id}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to auto-retry executions" }
        }
    }

    /**
     * Cleanup old executions daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    fun cleanupOldExecutions() {
        if (!cleanupEnabled) {
            return
        }
        
        try {
            val cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
            val deletedCount = workflowExecutionService.cleanupOldExecutions(cutoffDate)
            logger.info { "Cleaned up $deletedCount old workflow executions older than $retentionDays days" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cleanup old executions" }
        }
    }

    /**
     * Log workflow statistics every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    fun logWorkflowStatistics() {
        try {
            val since = Instant.now().minus(1, ChronoUnit.HOURS)

            // This would require extending the service to get overall stats
            logger.info { "Workflow engine is running - hourly maintenance check completed" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to log workflow statistics" }
        }
    }

    /**
     * Detect and log stale RUNNING steps every 5 minutes.
     * Steps stuck in RUNNING status beyond the threshold are likely orphaned
     * (e.g., process crashed, network partition, unhandled exception).
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    fun detectStaleRunningSteps() {
        try {
            val threshold = Instant.now().minus(staleStepThresholdMinutes, ChronoUnit.MINUTES)
            val staleSteps = stepEventRepository.findStaleRunningSteps(threshold)

            if (staleSteps.isNotEmpty()) {
                logger.warn {
                    "Found ${staleSteps.size} stale RUNNING steps (threshold: ${staleStepThresholdMinutes}m): " +
                        staleSteps.joinToString { "${it.workflowName}/${it.stepName} (execution=${it.executionId})" }
                }

                // Log details for each stale step
                staleSteps.forEach { step ->
                    val stuckDuration = java.time.Duration.between(step.startedAt, Instant.now())
                    logger.warn {
                        "Stale step: workflow=${step.workflowName}, step=${step.stepName}, " +
                            "executionId=${step.executionId}, stepIndex=${step.stepIndex}, " +
                            "startedAt=${step.startedAt}, stuckFor=${stuckDuration.toMinutes()}m"
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to detect stale running steps" }
        }
    }
}
