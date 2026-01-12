package com.vernont.api.controller.admin

import com.vernont.application.payment.*
import com.vernont.domain.payment.RefundReasonConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

// ============================================================================
// Response DTOs
// ============================================================================

data class AdminRefundReasonDto(
    val id: String,
    val value: String,
    val label: String,
    val description: String?,
    val display_order: Int,
    val is_active: Boolean,
    val requires_note: Boolean,
    val created_at: Instant?,
    val updated_at: Instant?
) {
    companion object {
        fun from(reason: RefundReasonConfig): AdminRefundReasonDto {
            return AdminRefundReasonDto(
                id = reason.id,
                value = reason.value,
                label = reason.label,
                description = reason.description,
                display_order = reason.displayOrder,
                is_active = reason.isActive,
                requires_note = reason.requiresNote,
                created_at = reason.createdAt,
                updated_at = reason.updatedAt
            )
        }
    }
}

data class RefundReasonsListResponse(
    val refund_reasons: List<AdminRefundReasonDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class RefundReasonResponse(
    val refund_reason: AdminRefundReasonDto
)

// ============================================================================
// Request DTOs
// ============================================================================

data class CreateRefundReasonRequest(
    val value: String,
    val label: String,
    val description: String? = null,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val requiresNote: Boolean = false
)

data class UpdateRefundReasonRequest(
    val value: String? = null,
    val label: String? = null,
    val description: String? = null,
    val displayOrder: Int? = null,
    val isActive: Boolean? = null,
    val requiresNote: Boolean? = null
)

// ============================================================================
// Controller
// ============================================================================

@RestController
@RequestMapping("/admin/refund-reasons")
@Tag(name = "Admin Refund Reasons", description = "Refund reason configuration endpoints")
class AdminRefundReasonController(
    private val refundReasonConfigService: RefundReasonConfigService
) {

    @Operation(summary = "List all refund reasons")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun listRefundReasons(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) active: Boolean?
    ): ResponseEntity<RefundReasonsListResponse> {
        logger.info { "GET /admin/refund-reasons - limit=$limit, offset=$offset, q=$q, active=$active" }

        val reasons = refundReasonConfigService.list(active = active, searchQuery = q)
        val count = reasons.size.toLong()
        val paginatedReasons = reasons.drop(offset).take(limit.coerceAtMost(100))

        return ResponseEntity.ok(RefundReasonsListResponse(
            refund_reasons = paginatedReasons.map { AdminRefundReasonDto.from(it) },
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get refund reason by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun getRefundReason(@PathVariable id: String): ResponseEntity<RefundReasonResponse> {
        logger.info { "GET /admin/refund-reasons/$id" }

        return try {
            val reason = refundReasonConfigService.getById(id)
            ResponseEntity.ok(RefundReasonResponse(
                refund_reason = AdminRefundReasonDto.from(reason)
            ))
        } catch (e: RefundReasonNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Create a new refund reason")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun createRefundReason(@RequestBody request: CreateRefundReasonRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/refund-reasons - value=${request.value}, label=${request.label}" }

        return try {
            val input = CreateRefundReasonInput(
                value = request.value,
                label = request.label,
                description = request.description,
                displayOrder = request.displayOrder,
                isActive = request.isActive,
                requiresNote = request.requiresNote
            )

            val savedReason = refundReasonConfigService.create(input)
            logger.info { "Created refund reason ${savedReason.id}" }

            ResponseEntity.status(HttpStatus.CREATED).body(RefundReasonResponse(
                refund_reason = AdminRefundReasonDto.from(savedReason)
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        } catch (e: RefundReasonValueExistsException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        }
    }

    @Operation(summary = "Update a refund reason")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun updateRefundReason(
        @PathVariable id: String,
        @RequestBody request: UpdateRefundReasonRequest
    ): ResponseEntity<Any> {
        logger.info { "PUT /admin/refund-reasons/$id" }

        return try {
            val input = UpdateRefundReasonInput(
                value = request.value,
                label = request.label,
                description = request.description,
                displayOrder = request.displayOrder,
                isActive = request.isActive,
                requiresNote = request.requiresNote
            )

            val savedReason = refundReasonConfigService.update(id, input)
            logger.info { "Updated refund reason $id" }

            ResponseEntity.ok(RefundReasonResponse(
                refund_reason = AdminRefundReasonDto.from(savedReason)
            ))
        } catch (e: RefundReasonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: RefundReasonValueExistsException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        }
    }

    @Operation(summary = "Delete a refund reason")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun deleteRefundReason(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "DELETE /admin/refund-reasons/$id" }

        return try {
            refundReasonConfigService.delete(id)
            logger.info { "Soft deleted refund reason $id" }

            ResponseEntity.ok(mapOf(
                "id" to id,
                "deleted" to true
            ))
        } catch (e: RefundReasonNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Reorder refund reasons")
    @PostMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun reorderRefundReasons(@RequestBody request: List<String>): ResponseEntity<Any> {
        logger.info { "POST /admin/refund-reasons/reorder - ${request.size} items" }

        refundReasonConfigService.reorder(request)
        logger.info { "Reordered ${request.size} refund reasons" }

        return ResponseEntity.ok(mapOf(
            "message" to "Refund reasons reordered successfully"
        ))
    }

    @Operation(summary = "Seed default refund reasons")
    @PostMapping("/seed")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun seedRefundReasons(): ResponseEntity<Any> {
        logger.info { "POST /admin/refund-reasons/seed" }

        return try {
            val saved = refundReasonConfigService.seedDefaults()
            logger.info { "Seeded ${saved.size} default refund reasons" }

            ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
                "message" to "Seeded ${saved.size} default refund reasons",
                "count" to saved.size
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        }
    }
}
