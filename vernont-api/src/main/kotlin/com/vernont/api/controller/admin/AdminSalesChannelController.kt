package com.vernont.api.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.store.SalesChannel
import com.vernont.repository.store.SalesChannelRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/sales-channels")
@Tag(name = "Admin Sales Channels", description = "Sales channel management endpoints")
class AdminSalesChannelController(
    private val salesChannelRepository: SalesChannelRepository
) {

    @Operation(summary = "List all sales channels")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun listSalesChannels(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<SalesChannelsResponse> {
        logger.info { "GET /admin/sales-channels - limit=$limit, offset=$offset, q=$q" }

        val channels = if (q.isNullOrBlank()) {
            salesChannelRepository.findByDeletedAtIsNull()
        } else {
            salesChannelRepository.searchByName(q)
        }

        val paged = channels.drop(offset).take(limit)

        return ResponseEntity.ok(SalesChannelsResponse(
            sales_channels = paged.map { SalesChannelDto.from(it) },
            count = channels.size,
            limit = limit,
            offset = offset
        ))
    }

    @Operation(summary = "Get sales channel by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun getSalesChannel(@PathVariable id: String): ResponseEntity<SalesChannelResponse> {
        logger.info { "GET /admin/sales-channels/$id" }

        val channel = salesChannelRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(SalesChannelResponse(sales_channel = SalesChannelDto.from(channel)))
    }

    @Operation(summary = "Create a new sales channel")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun createSalesChannel(@RequestBody request: CreateSalesChannelRequest): ResponseEntity<SalesChannelResponse> {
        logger.info { "POST /admin/sales-channels - name=${request.name}" }

        if (salesChannelRepository.existsByName(request.name)) {
            return ResponseEntity.badRequest().build()
        }

        val channel = SalesChannel().apply {
            name = request.name
            description = request.description
            isActive = request.is_active ?: true
        }

        val saved = salesChannelRepository.save(channel)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            SalesChannelResponse(sales_channel = SalesChannelDto.from(saved))
        )
    }

    @Operation(summary = "Update a sales channel")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun updateSalesChannel(
        @PathVariable id: String,
        @RequestBody request: UpdateSalesChannelRequest
    ): ResponseEntity<SalesChannelResponse> {
        logger.info { "PUT /admin/sales-channels/$id" }

        val channel = salesChannelRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        request.name?.let {
            if (salesChannelRepository.existsByNameAndIdNot(it, id)) {
                return ResponseEntity.badRequest().build()
            }
            channel.name = it
        }
        request.description?.let { channel.description = it }
        request.is_active?.let { channel.isActive = it }

        val saved = salesChannelRepository.save(channel)
        return ResponseEntity.ok(SalesChannelResponse(sales_channel = SalesChannelDto.from(saved)))
    }

    @Operation(summary = "Delete a sales channel")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteSalesChannel(@PathVariable id: String): ResponseEntity<DeleteResponse> {
        logger.info { "DELETE /admin/sales-channels/$id" }

        val channel = salesChannelRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        channel.softDelete()
        salesChannelRepository.save(channel)

        return ResponseEntity.ok(DeleteResponse(id = id, deleted = true))
    }
}

// ============================================================================
// Request DTOs
// ============================================================================

data class CreateSalesChannelRequest(
    val name: String,
    val description: String? = null,
    val is_active: Boolean? = true
)

data class UpdateSalesChannelRequest(
    val name: String? = null,
    val description: String? = null,
    val is_active: Boolean? = null
)

// ============================================================================
// Response DTOs
// ============================================================================

data class SalesChannelsResponse(
    val sales_channels: List<SalesChannelDto>,
    val count: Int,
    val limit: Int,
    val offset: Int
)

data class SalesChannelResponse(
    val sales_channel: SalesChannelDto
)

data class DeleteResponse(
    val id: String,
    val deleted: Boolean
)

data class SalesChannelDto(
    val id: String,
    val name: String,
    val description: String?,

    @JsonProperty("is_active")
    val isActive: Boolean,

    @JsonProperty("is_disabled")
    val isDisabled: Boolean,

    @JsonProperty("created_at")
    val createdAt: Instant,

    @JsonProperty("updated_at")
    val updatedAt: Instant
) {
    companion object {
        fun from(channel: SalesChannel): SalesChannelDto {
            return SalesChannelDto(
                id = channel.id,
                name = channel.name,
                description = channel.description,
                isActive = channel.isActive,
                isDisabled = channel.isDisabled,
                createdAt = channel.createdAt,
                updatedAt = channel.updatedAt
            )
        }
    }
}
