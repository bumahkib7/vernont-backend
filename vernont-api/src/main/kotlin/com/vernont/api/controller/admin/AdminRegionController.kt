package com.vernont.api.controller.admin

import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowOptions
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.region.CreateRegionRequest
import com.vernont.workflow.flows.region.CreateRegionsInput
import com.vernont.workflow.flows.region.CreateRegionsOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

private val adminRegionLogger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/regions")
class AdminRegionController(
    private val workflowEngine: WorkflowEngine
) {

    data class CreateRegionsRequest(
        val regions: List<CreateRegionRequest>
    )

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    suspend fun createRegions(
        @RequestBody body: CreateRegionsRequest,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: "req_${UUID.randomUUID()}"

        val input = CreateRegionsInput(
            regions = body.regions
        )

        adminRegionLogger.info {
            "Creating ${body.regions.size} region(s), correlationId=$correlationId"
        }

        return try {
            val result: WorkflowResult<CreateRegionsOutput> = workflowEngine.execute(
                workflowName = WorkflowConstants.CreateRegions.CREATE_REGIONS,
                input = input,
                inputType = CreateRegionsInput::class,
                outputType = CreateRegionsOutput::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    timeoutSeconds = 30
                )
            )

            when {
                result.isSuccess() -> {
                    val regions = result.getOrThrow()
                    ResponseEntity.status(HttpStatus.CREATED).body(
                        mapOf(
                            "regions" to regions,
                            "count" to regions.regions.count(),
                            "correlation_id" to correlationId
                        )
                    )
                }

                result.isFailure() -> {
                    val error = (result as WorkflowResult.Failure).error
                    adminRegionLogger.warn {
                        "Create regions failed: ${error.message}, correlationId=$correlationId"
                    }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(
                        mapOf(
                            "error" to mapOf(
                                "message" to (error.message ?: "Failed to create regions"),
                                "type" to error::class.simpleName
                            ),
                            "correlation_id" to correlationId
                        )
                    )
                }

                else -> {
                    adminRegionLogger.error { "Unexpected workflow state, correlationId=$correlationId" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        mapOf(
                            "error" to mapOf(
                                "message" to "Unexpected workflow state",
                                "code" to "INTERNAL_ERROR"
                            ),
                            "correlation_id" to correlationId
                        )
                    )
                }
            }

        } catch (e: Exception) {
            adminRegionLogger.error(e) { "Exception during regions creation, correlationId=$correlationId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "error" to mapOf(
                        "message" to "Internal server error during region creation",
                        "code" to "INTERNAL_ERROR"
                    ),
                    "correlation_id" to correlationId
                )
            )
        }
    }
}
