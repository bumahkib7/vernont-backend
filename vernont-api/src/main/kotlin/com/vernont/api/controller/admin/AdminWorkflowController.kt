package com.vernont.api.controller.admin

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class WorkflowExecutionDto(
    val id: String,
    val workflowName: String,
    val status: String,
    val inputData: String? = null,
    val outputData: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 0,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val compensatedAt: String? = null,
    val parentExecutionId: String? = null,
    val correlationId: String? = null
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

/**
 * Temporary stub endpoints to satisfy the admin UI until the real workflow service is wired.
 */
@RestController
@RequestMapping("/admin/workflows")
class AdminWorkflowController {

    @GetMapping("/{workflowName}/executions")
    fun listExecutions(
        @PathVariable workflowName: String,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "50") size: Int
    ): ResponseEntity<WorkflowPage<WorkflowExecutionDto>> {
        return ResponseEntity.ok(
            WorkflowPage(
                content = emptyList(),
                totalElements = 0,
                totalPages = 0
            )
        )
    }

    @GetMapping("/executions")
    fun listAllExecutions(
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "50") size: Int
    ): ResponseEntity<WorkflowPage<WorkflowExecutionDto>> {
        return ResponseEntity.ok(
            WorkflowPage(
                content = emptyList(),
                totalElements = 0,
                totalPages = 0
            )
        )
    }

    @GetMapping("/{workflowName}/statistics")
    fun statistics(
        @PathVariable workflowName: String,
        @RequestParam("since") since: String
    ): ResponseEntity<WorkflowStatisticsDto> {
        return ResponseEntity.ok(
            WorkflowStatisticsDto(
                workflowName = workflowName,
                totalExecutions = 0,
                completedExecutions = 0,
                failedExecutions = 0,
                averageDurationMs = 0
            )
        )
    }
}
