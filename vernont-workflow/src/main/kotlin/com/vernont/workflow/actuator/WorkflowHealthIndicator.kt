package com.vernont.workflow.actuator

import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.service.WorkflowExecutionService
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Health indicator for the workflow engine
 */
@Component
class WorkflowHealthIndicator(
    private val workflowEngine: WorkflowEngine,
    private val workflowExecutionService: WorkflowExecutionService
) : HealthIndicator {

    override fun health(): Health {
        return try {
            val isEngineHealthy = workflowEngine.isHealthy()
            
            if (!isEngineHealthy) {
                return Health.down()
                    .withDetail("reason", "Workflow engine is not healthy")
                    .build()
            }

            // Check for stuck executions
            val recentFailures = workflowExecutionService.findRecentFailures(
                Instant.now().minus(1, ChronoUnit.HOURS),
                org.springframework.data.domain.PageRequest.of(0, 10)
            )

            val registeredWorkflows = workflowEngine.listWorkflows()

            Health.up()
                .withDetail("registeredWorkflows", registeredWorkflows.size)
                .withDetail("recentFailuresCount", recentFailures.totalElements)
                .withDetail("workflows", registeredWorkflows.map { it.name })
                .build()

        } catch (e: Exception) {
            Health.down()
                .withException(e)
                .build()
        }
    }
}