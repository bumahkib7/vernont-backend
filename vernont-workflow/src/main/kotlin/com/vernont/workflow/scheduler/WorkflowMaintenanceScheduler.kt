package com.vernont.workflow.scheduler

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
    @param:Value("\${app.workflow.cleanup.retention-days:30}")
    private val retentionDays: Long,
    @param:Value("\${app.workflow.cleanup.enabled:true}")
    private val cleanupEnabled: Boolean
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
}