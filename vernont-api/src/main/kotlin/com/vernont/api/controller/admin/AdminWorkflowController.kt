package com.vernont.api.controller.admin

import com.vernont.workflow.domain.WorkflowExecution
import com.vernont.workflow.domain.WorkflowExecutionStatus
import com.vernont.workflow.domain.WorkflowStepEvent
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.repository.WorkflowExecutionRepository
import com.vernont.workflow.repository.WorkflowStepEventRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/workflows")
@PreAuthorize("hasRole('ADMIN') or hasRole('DEVELOPER')")
@Tag(name = "Admin Workflows", description = "Workflow monitoring and management")
class AdminWorkflowController(
    private val workflowEngine: WorkflowEngine,
    private val executionRepository: WorkflowExecutionRepository,
    private val stepEventRepository: WorkflowStepEventRepository
) {

    /**
     * Get list of all registered workflows
     */
    @Operation(summary = "List all registered workflow definitions")
    @GetMapping("/definitions")
    fun getWorkflowDefinitions(): ResponseEntity<List<WorkflowDefinitionDto>> {
        val workflows = workflowEngine.listWorkflows()
        return ResponseEntity.ok(workflows.map { info ->
            WorkflowDefinitionDto(
                name = info.name,
                inputType = info.inputType,
                outputType = info.outputType
            )
        })
    }

    /**
     * Get workflow executions with filtering
     */
    @Operation(summary = "Get workflow executions")
    @GetMapping("/executions")
    fun getExecutions(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) workflowName: String?,
        @RequestParam(required = false) since: Instant?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<WorkflowExecutionsResponse> {
        logger.debug { "Fetching executions: status=$status, workflow=$workflowName, since=$since, limit=$limit" }

        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit, Sort.by(Sort.Direction.DESC, "createdAt"))

        val executions = when {
            workflowName != null && status != null -> {
                executionRepository.findByWorkflowNameAndStatusOrderByCreatedAtDesc(
                    workflowName, status, pageable
                )
            }
            workflowName != null -> {
                executionRepository.findByWorkflowNameOrderByCreatedAtDesc(workflowName, pageable)
            }
            status != null -> {
                executionRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
            }
            since != null -> {
                executionRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since, pageable)
            }
            else -> {
                executionRepository.findAll(pageable)
            }
        }

        val dtos = executions.content.map { it.toDto() }

        return ResponseEntity.ok(WorkflowExecutionsResponse(
            executions = dtos,
            total = executions.totalElements,
            hasMore = executions.hasNext()
        ))
    }

    /**
     * Get a single execution with its steps
     */
    @Operation(summary = "Get workflow execution details with steps")
    @GetMapping("/executions/{id}")
    fun getExecution(@PathVariable id: String): ResponseEntity<WorkflowExecutionDetailDto> {
        val execution = executionRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val steps = stepEventRepository.findByExecutionIdOrderByStepIndexAsc(id)

        return ResponseEntity.ok(WorkflowExecutionDetailDto(
            execution = execution.toDto(),
            steps = steps.map { it.toDto() }
        ))
    }

    /**
     * Get active (running) executions for real-time monitoring, including step events
     */
    @Operation(summary = "Get currently running workflow executions with steps")
    @GetMapping("/executions/active")
    fun getActiveExecutions(): ResponseEntity<List<WorkflowExecutionWithStepsDto>> {
        val activeStatuses = listOf("RUNNING", "PAUSED")
        val pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"))

        val executions = executionRepository.findByStatusInOrderByCreatedAtDesc(activeStatuses, pageable)

        // Fetch steps for all active executions
        val executionsWithSteps = executions.content.map { execution ->
            val steps = stepEventRepository.findByExecutionIdOrderByStepIndexAsc(execution.id)
            WorkflowExecutionWithStepsDto(
                id = execution.id,
                workflowName = execution.workflowName,
                status = execution.status.name,
                inputData = execution.inputData,
                outputData = execution.outputData,
                errorMessage = execution.errorMessage,
                retryCount = execution.retryCount,
                maxRetries = execution.maxRetries,
                createdAt = execution.createdAt,
                completedAt = execution.completedAt,
                correlationId = execution.correlationId,
                parentExecutionId = execution.parentExecutionId,
                durationMs = execution.completedAt?.let { it.toEpochMilli() - execution.createdAt.toEpochMilli() },
                steps = steps.map { it.toDto() }
            )
        }

        return ResponseEntity.ok(executionsWithSteps)
    }

    /**
     * Get recent completed/failed executions for history view
     */
    @Operation(summary = "Get recently completed workflow executions with steps")
    @GetMapping("/executions/recent")
    fun getRecentExecutions(
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<WorkflowExecutionWithStepsDto>> {
        val completedStatuses = listOf("COMPLETED", "FAILED", "COMPENSATED", "CANCELLED", "TIMEOUT")
        val pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))

        val executions = executionRepository.findByStatusInOrderByCreatedAtDesc(completedStatuses, pageable)

        val executionsWithSteps = executions.content.map { execution ->
            val steps = stepEventRepository.findByExecutionIdOrderByStepIndexAsc(execution.id)
            WorkflowExecutionWithStepsDto(
                id = execution.id,
                workflowName = execution.workflowName,
                status = execution.status.name,
                inputData = execution.inputData,
                outputData = execution.outputData,
                errorMessage = execution.errorMessage,
                retryCount = execution.retryCount,
                maxRetries = execution.maxRetries,
                createdAt = execution.createdAt,
                completedAt = execution.completedAt,
                correlationId = execution.correlationId,
                parentExecutionId = execution.parentExecutionId,
                durationMs = execution.completedAt?.let { it.toEpochMilli() - execution.createdAt.toEpochMilli() },
                steps = steps.map { it.toDto() }
            )
        }

        return ResponseEntity.ok(executionsWithSteps)
    }

    /**
     * Retry a failed workflow execution
     */
    @Operation(summary = "Retry a failed workflow execution")
    @PostMapping("/executions/{id}/retry")
    fun retryExecution(@PathVariable id: String): ResponseEntity<WorkflowRetryResponse> {
        val execution = executionRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (execution.status != WorkflowExecutionStatus.FAILED) {
            return ResponseEntity.badRequest().body(
                WorkflowRetryResponse(
                    success = false,
                    message = "Only failed executions can be retried",
                    originalExecutionId = id,
                    newExecutionId = null
                )
            )
        }

        return try {
            // Re-run the workflow with the same input
            val newExecutionId = workflowEngine.retryExecution(id)

            ResponseEntity.ok(WorkflowRetryResponse(
                success = true,
                message = "Workflow retry started",
                originalExecutionId = id,
                newExecutionId = newExecutionId
            ))
        } catch (e: Exception) {
            logger.error(e) { "Failed to retry execution $id" }
            ResponseEntity.internalServerError().body(
                WorkflowRetryResponse(
                    success = false,
                    message = "Failed to retry: ${e.message}",
                    originalExecutionId = id,
                    newExecutionId = null
                )
            )
        }
    }

    /**
     * Get recent step events for live feed
     */
    @Operation(summary = "Get recent step events")
    @GetMapping("/steps/recent")
    fun getRecentSteps(
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) since: Instant?
    ): ResponseEntity<List<WorkflowStepEventDto>> {
        val pageable = PageRequest.of(0, limit)

        val steps = if (since != null) {
            stepEventRepository.findByStartedAtAfterOrderByStartedAtDesc(since, pageable)
        } else {
            stepEventRepository.findRecentStepEvents(pageable)
        }

        return ResponseEntity.ok(steps.content.map { it.toDto() })
    }

    /**
     * Get workflow statistics
     */
    @Operation(summary = "Get workflow execution statistics")
    @GetMapping("/stats")
    fun getStats(
        @RequestParam(required = false) workflowName: String?,
        @RequestParam(required = false) since: Instant?
    ): ResponseEntity<WorkflowStatsResponse> {
        val effectiveSince = since ?: Instant.now().minusSeconds(86400) // Last 24 hours

        // Collect stats per workflow
        val byWorkflowStats = mutableMapOf<String, WorkflowStats>()

        if (workflowName != null) {
            val stats = workflowEngine.getWorkflowStatistics(workflowName, effectiveSince)
            byWorkflowStats[workflowName] = WorkflowStats(
                total = stats.sumOf { it.count },
                completed = stats.filter { it.status == WorkflowExecutionStatus.COMPLETED }.sumOf { it.count },
                failed = stats.filter { it.status == WorkflowExecutionStatus.FAILED }.sumOf { it.count },
                running = stats.filter { it.status == WorkflowExecutionStatus.RUNNING }.sumOf { it.count }
            )
        } else {
            // Get stats for all workflows
            workflowEngine.listWorkflows().forEach { info ->
                val stats = workflowEngine.getWorkflowStatistics(info.name, effectiveSince)
                byWorkflowStats[info.name] = WorkflowStats(
                    total = stats.sumOf { it.count },
                    completed = stats.filter { it.status == WorkflowExecutionStatus.COMPLETED }.sumOf { it.count },
                    failed = stats.filter { it.status == WorkflowExecutionStatus.FAILED }.sumOf { it.count },
                    running = stats.filter { it.status == WorkflowExecutionStatus.RUNNING }.sumOf { it.count }
                )
            }
        }

        // Aggregate totals
        val totalExecutions = byWorkflowStats.values.sumOf { it.total }
        val completedCount = byWorkflowStats.values.sumOf { it.completed }
        val failedCount = byWorkflowStats.values.sumOf { it.failed }
        val runningCount = byWorkflowStats.values.sumOf { it.running }

        return ResponseEntity.ok(WorkflowStatsResponse(
            totalExecutions = totalExecutions,
            completedCount = completedCount,
            failedCount = failedCount,
            runningCount = runningCount,
            successRate = if (totalExecutions > 0) (completedCount.toDouble() / totalExecutions * 100) else 0.0,
            byWorkflow = byWorkflowStats
        ))
    }

    // Legacy endpoints for backwards compatibility
    @GetMapping("/{workflowName}/executions")
    fun listExecutionsByWorkflow(
        @PathVariable workflowName: String,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "50") size: Int
    ): ResponseEntity<WorkflowPage<WorkflowExecutionDto>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val executions = executionRepository.findByWorkflowNameOrderByCreatedAtDesc(workflowName, pageable)

        return ResponseEntity.ok(WorkflowPage(
            content = executions.content.map { it.toDto() },
            totalElements = executions.totalElements,
            totalPages = executions.totalPages
        ))
    }

    @GetMapping("/{workflowName}/statistics")
    fun statistics(
        @PathVariable workflowName: String,
        @RequestParam("since") since: String
    ): ResponseEntity<WorkflowStatisticsDto> {
        val sinceInstant = try {
            Instant.parse(since)
        } catch (e: Exception) {
            Instant.now().minusSeconds(86400)
        }

        val stats = workflowEngine.getWorkflowStatistics(workflowName, sinceInstant)

        return ResponseEntity.ok(WorkflowStatisticsDto(
            workflowName = workflowName,
            totalExecutions = stats.sumOf { it.count },
            completedExecutions = stats.filter { it.status == WorkflowExecutionStatus.COMPLETED }.sumOf { it.count },
            failedExecutions = stats.filter { it.status == WorkflowExecutionStatus.FAILED }.sumOf { it.count },
            averageDurationMs = 0 // TODO: Calculate from step events
        ))
    }

    // Extension functions for mapping
    private fun WorkflowExecution.toDto() = WorkflowExecutionDto(
        id = this.id,
        workflowName = this.workflowName,
        status = this.status.name,
        inputData = this.inputData,
        outputData = this.outputData,
        errorMessage = this.errorMessage,
        retryCount = this.retryCount,
        maxRetries = this.maxRetries,
        createdAt = this.createdAt,
        completedAt = this.completedAt,
        correlationId = this.correlationId,
        parentExecutionId = this.parentExecutionId,
        durationMs = if (this.completedAt != null) {
            this.completedAt!!.toEpochMilli() - this.createdAt.toEpochMilli()
        } else null
    )

    private fun WorkflowStepEvent.toDto() = WorkflowStepEventDto(
        id = this.id,
        executionId = this.executionId,
        workflowName = this.workflowName,
        stepName = this.stepName,
        stepIndex = this.stepIndex,
        totalSteps = this.totalSteps,
        status = this.status.name,
        inputData = this.inputData,
        outputData = this.outputData,
        errorMessage = this.errorMessage,
        errorType = this.errorType,
        durationMs = this.durationMs,
        startedAt = this.startedAt,
        completedAt = this.completedAt
    )
}

// DTOs

data class WorkflowDefinitionDto(
    val name: String,
    val inputType: String,
    val outputType: String
)

data class WorkflowExecutionDto(
    val id: String,
    val workflowName: String,
    val status: String,
    val inputData: String? = null,
    val outputData: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 0,
    val createdAt: Instant,
    val completedAt: Instant? = null,
    val correlationId: String? = null,
    val parentExecutionId: String? = null,
    val durationMs: Long? = null
)

data class WorkflowExecutionWithStepsDto(
    val id: String,
    val workflowName: String,
    val status: String,
    val inputData: String? = null,
    val outputData: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 0,
    val createdAt: Instant,
    val completedAt: Instant? = null,
    val correlationId: String? = null,
    val parentExecutionId: String? = null,
    val durationMs: Long? = null,
    val steps: List<WorkflowStepEventDto> = emptyList()
)

data class WorkflowStepEventDto(
    val id: String,
    val executionId: String,
    val workflowName: String,
    val stepName: String,
    val stepIndex: Int,
    val totalSteps: Int?,
    val status: String,
    val inputData: String?,
    val outputData: String?,
    val errorMessage: String?,
    val errorType: String?,
    val durationMs: Long?,
    val startedAt: Instant,
    val completedAt: Instant?
)

data class WorkflowExecutionDetailDto(
    val execution: WorkflowExecutionDto,
    val steps: List<WorkflowStepEventDto>
)

data class WorkflowExecutionsResponse(
    val executions: List<WorkflowExecutionDto>,
    val total: Long,
    val hasMore: Boolean
)

data class WorkflowStatsResponse(
    val totalExecutions: Long,
    val completedCount: Long,
    val failedCount: Long,
    val runningCount: Long,
    val successRate: Double,
    val byWorkflow: Map<String, WorkflowStats>
)

data class WorkflowStats(
    val total: Long,
    val completed: Long,
    val failed: Long,
    val running: Long
)

data class WorkflowPage<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int
)

data class WorkflowStatisticsDto(
    val workflowName: String,
    val totalExecutions: Long,
    val completedExecutions: Long,
    val failedExecutions: Long,
    val averageDurationMs: Long
)

data class WorkflowRetryResponse(
    val success: Boolean,
    val message: String,
    val originalExecutionId: String,
    val newExecutionId: String?
)
