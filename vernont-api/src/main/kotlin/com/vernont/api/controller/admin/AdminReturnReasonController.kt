package com.vernont.api.controller.admin

import com.vernont.application.returns.*
import com.vernont.domain.returns.ReturnReasonConfig
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

data class AdminReturnReasonDto(
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
        fun from(reason: ReturnReasonConfig): AdminReturnReasonDto {
            return AdminReturnReasonDto(
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

data class ReturnReasonsListResponse(
    val return_reasons: List<AdminReturnReasonDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class ReturnReasonResponse(
    val return_reason: AdminReturnReasonDto
)

// ============================================================================
// Request DTOs
// ============================================================================

data class CreateReturnReasonRequest(
    val value: String,
    val label: String,
    val description: String? = null,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val requiresNote: Boolean = false
)

data class UpdateReturnReasonRequest(
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
@RequestMapping("/admin/return-reasons")
@Tag(name = "Admin Return Reasons", description = "Return reason configuration endpoints")
class AdminReturnReasonController(
    private val returnReasonConfigService: ReturnReasonConfigService
) {

    @Operation(summary = "List all return reasons")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun listReturnReasons(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) active: Boolean?
    ): ResponseEntity<ReturnReasonsListResponse> {
        logger.info { "GET /admin/return-reasons - limit=$limit, offset=$offset, q=$q, active=$active" }

        val reasons = returnReasonConfigService.list(active = active, searchQuery = q)
        val count = reasons.size.toLong()
        val paginatedReasons = reasons.drop(offset).take(limit.coerceAtMost(100))

        return ResponseEntity.ok(ReturnReasonsListResponse(
            return_reasons = paginatedReasons.map { AdminReturnReasonDto.from(it) },
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get return reason by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun getReturnReason(@PathVariable id: String): ResponseEntity<ReturnReasonResponse> {
        logger.info { "GET /admin/return-reasons/$id" }

        return try {
            val reason = returnReasonConfigService.getById(id)
            ResponseEntity.ok(ReturnReasonResponse(
                return_reason = AdminReturnReasonDto.from(reason)
            ))
        } catch (e: ReturnReasonNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Create a new return reason")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun createReturnReason(@RequestBody request: CreateReturnReasonRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/return-reasons - value=${request.value}, label=${request.label}" }

        return try {
            val input = CreateReturnReasonInput(
                value = request.value,
                label = request.label,
                description = request.description,
                displayOrder = request.displayOrder,
                isActive = request.isActive,
                requiresNote = request.requiresNote
            )

            val savedReason = returnReasonConfigService.create(input)
            logger.info { "Created return reason ${savedReason.id}" }

            ResponseEntity.status(HttpStatus.CREATED).body(ReturnReasonResponse(
                return_reason = AdminReturnReasonDto.from(savedReason)
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        } catch (e: ReturnReasonValueExistsException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        }
    }

    @Operation(summary = "Update a return reason")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun updateReturnReason(
        @PathVariable id: String,
        @RequestBody request: UpdateReturnReasonRequest
    ): ResponseEntity<Any> {
        logger.info { "PUT /admin/return-reasons/$id" }

        return try {
            val input = UpdateReturnReasonInput(
                value = request.value,
                label = request.label,
                description = request.description,
                displayOrder = request.displayOrder,
                isActive = request.isActive,
                requiresNote = request.requiresNote
            )

            val savedReason = returnReasonConfigService.update(id, input)
            logger.info { "Updated return reason $id" }

            ResponseEntity.ok(ReturnReasonResponse(
                return_reason = AdminReturnReasonDto.from(savedReason)
            ))
        } catch (e: ReturnReasonNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: ReturnReasonValueExistsException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        }
    }

    @Operation(summary = "Delete a return reason")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun deleteReturnReason(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "DELETE /admin/return-reasons/$id" }

        return try {
            returnReasonConfigService.delete(id)
            logger.info { "Soft deleted return reason $id" }

            ResponseEntity.ok(mapOf(
                "id" to id,
                "deleted" to true
            ))
        } catch (e: ReturnReasonNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Reorder return reasons")
    @PostMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun reorderReturnReasons(@RequestBody request: List<String>): ResponseEntity<Any> {
        logger.info { "POST /admin/return-reasons/reorder - ${request.size} items" }

        returnReasonConfigService.reorder(request)
        logger.info { "Reordered ${request.size} return reasons" }

        return ResponseEntity.ok(mapOf(
            "message" to "Return reasons reordered successfully"
        ))
    }

    @Operation(summary = "Seed default return reasons")
    @PostMapping("/seed")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun seedReturnReasons(): ResponseEntity<Any> {
        logger.info { "POST /admin/return-reasons/seed" }

        return try {
            val saved = returnReasonConfigService.seedDefaults()
            logger.info { "Seeded ${saved.size} default return reasons" }

            ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
                "message" to "Seeded ${saved.size} default return reasons",
                "count" to saved.size
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("message" to e.message))
        }
    }
}
