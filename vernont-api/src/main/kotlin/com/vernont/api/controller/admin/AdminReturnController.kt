package com.vernont.api.controller.admin

import com.vernont.api.dto.admin.*
import com.vernont.domain.returns.ReturnStatus
import com.vernont.repository.returns.ReturnRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.returns.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/admin/returns")
class AdminReturnController(
    private val returnRepository: ReturnRepository,
    private val workflowEngine: WorkflowEngine
) {

    /**
     * List all returns (with optional filtering)
     * GET /admin/returns
     */
    @GetMapping
    fun listReturns(
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<AdminReturnListResponse> {
        val returns = if (status != null) {
            try {
                val returnStatus = ReturnStatus.valueOf(status.uppercase())
                returnRepository.findByStatusAndDeletedAtIsNull(returnStatus)
            } catch (e: IllegalArgumentException) {
                returnRepository.findByDeletedAtIsNull()
            }
        } else {
            returnRepository.findByDeletedAtIsNull()
        }

        // Sort by requestedAt descending
        val sortedReturns = returns.sortedByDescending { it.requestedAt }

        // Apply search filter if provided
        val filteredReturns = if (!q.isNullOrBlank()) {
            sortedReturns.filter {
                it.customerEmail?.contains(q, ignoreCase = true) == true ||
                it.orderDisplayId?.toString()?.contains(q) == true ||
                it.id.contains(q, ignoreCase = true)
            }
        } else {
            sortedReturns
        }

        val count = filteredReturns.size
        val paginatedReturns = filteredReturns
            .drop(offset)
            .take(limit.coerceAtMost(100))
            .map { AdminReturnSummary.from(it) }

        return ResponseEntity.ok(
            AdminReturnListResponse(
                returns = paginatedReturns,
                count = count,
                offset = offset,
                limit = limit
            )
        )
    }

    /**
     * Get return details
     * GET /admin/returns/{id}
     */
    @GetMapping("/{id}")
    fun getReturn(@PathVariable id: String): ResponseEntity<Any> {
        val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            AdminReturnResponse(return_request = AdminReturn.from(returnRequest))
        )
    }

    /**
     * Mark return as received
     * POST /admin/returns/{id}/receive
     */
    @PostMapping("/{id}/receive")
    suspend fun receiveReturn(
        @PathVariable id: String,
        @RequestBody(required = false) body: ReceiveReturnRequest?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val input = ReceiveReturnInput(
            returnId = id,
            receivedBy = body?.receivedBy,
            notes = body?.notes
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.ReceiveReturn.NAME,
            input = input,
            inputType = ReceiveReturnInput::class,
            outputType = ReturnResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val updatedReturn = returnRepository.findByIdAndDeletedAtIsNull(id)
                ResponseEntity.ok(mapOf(
                    "return_request" to updatedReturn?.let { AdminReturn.from(it) },
                    "message" to "Return marked as received"
                ))
            }
            is WorkflowResult.Failure -> ResponseEntity.badRequest().body(
                mapOf(
                    "message" to "Failed to receive return",
                    "error" to (result.error.message ?: "Unknown error")
                )
            )
        }
    }

    /**
     * Process refund for a return
     * POST /admin/returns/{id}/refund
     */
    @PostMapping("/{id}/refund")
    suspend fun processRefund(
        @PathVariable id: String,
        @RequestBody(required = false) body: ProcessRefundRequest?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val input = ProcessReturnRefundInput(
            returnId = id,
            processedBy = body?.processedBy
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.ProcessReturnRefund.NAME,
            input = input,
            inputType = ProcessReturnRefundInput::class,
            outputType = ReturnResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val updatedReturn = returnRepository.findByIdAndDeletedAtIsNull(id)
                ResponseEntity.ok(mapOf(
                    "return_request" to updatedReturn?.let { AdminReturn.from(it) },
                    "message" to "Refund processed successfully"
                ))
            }
            is WorkflowResult.Failure -> ResponseEntity.badRequest().body(
                mapOf(
                    "message" to "Failed to process refund",
                    "error" to (result.error.message ?: "Unknown error")
                )
            )
        }
    }

    /**
     * Reject a return
     * POST /admin/returns/{id}/reject
     */
    @PostMapping("/{id}/reject")
    suspend fun rejectReturn(
        @PathVariable id: String,
        @RequestBody body: RejectReturnRequest,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val returnRequest = returnRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val input = RejectReturnInput(
            returnId = id,
            reason = body.reason,
            rejectedBy = body.rejectedBy
        )

        val correlationId = requestId ?: UUID.randomUUID().toString()

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.RejectReturn.NAME,
            input = input,
            inputType = RejectReturnInput::class,
            outputType = ReturnResponse::class,
            context = WorkflowContext(),
            options = WorkflowOptions(correlationId = correlationId)
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val updatedReturn = returnRepository.findByIdAndDeletedAtIsNull(id)
                ResponseEntity.ok(mapOf(
                    "return_request" to updatedReturn?.let { AdminReturn.from(it) },
                    "message" to "Return rejected"
                ))
            }
            is WorkflowResult.Failure -> ResponseEntity.badRequest().body(
                mapOf(
                    "message" to "Failed to reject return",
                    "error" to (result.error.message ?: "Unknown error")
                )
            )
        }
    }

    /**
     * Get return statistics
     * GET /admin/returns/stats
     */
    @GetMapping("/stats")
    fun getReturnStats(): ResponseEntity<Any> {
        val pendingCount = returnRepository.countByStatus(ReturnStatus.APPROVED)
        val receivedCount = returnRepository.countByStatus(ReturnStatus.RECEIVED)
        val refundedCount = returnRepository.countByStatus(ReturnStatus.REFUNDED)
        val rejectedCount = returnRepository.countByStatus(ReturnStatus.REJECTED)

        return ResponseEntity.ok(mapOf(
            "pending" to pendingCount,
            "received" to receivedCount,
            "refunded" to refundedCount,
            "rejected" to rejectedCount,
            "total" to (pendingCount + receivedCount + refundedCount + rejectedCount)
        ))
    }
}
