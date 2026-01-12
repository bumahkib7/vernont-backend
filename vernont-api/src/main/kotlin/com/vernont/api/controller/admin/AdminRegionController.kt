package com.vernont.api.controller.admin

import com.vernont.api.dto.StoreCountryDto
import com.vernont.api.dto.StoreRegionDto
import com.vernont.domain.region.Region
import com.vernont.repository.fulfillment.FulfillmentProviderRepository
import com.vernont.repository.payment.PaymentProviderRepository
import com.vernont.repository.region.CountryRepository
import com.vernont.repository.region.RegionRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowOptions
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.region.CreateRegionRequest
import com.vernont.workflow.flows.region.CreateRegionsInput
import com.vernont.workflow.flows.region.CreateRegionsOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

private val adminRegionLogger = KotlinLogging.logger {}

// Response DTOs for Admin
data class AdminRegionDto(
    val id: String,
    val name: String,
    val currency_code: String,
    val automatic_taxes: Boolean,
    val tax_code: String?,
    val gift_cards_taxable: Boolean,
    val tax_rate: BigDecimal,
    val tax_inclusive: Boolean,
    val countries: List<StoreCountryDto>,
    val payment_providers: List<String>,
    val fulfillment_providers: List<String>,
    val created_at: Instant?,
    val updated_at: Instant?,
    val metadata: Map<String, Any?>?
) {
    companion object {
        fun from(region: Region): AdminRegionDto {
            return AdminRegionDto(
                id = region.id,
                name = region.name,
                currency_code = region.currencyCode,
                automatic_taxes = region.automaticTaxes,
                tax_code = region.taxCode,
                gift_cards_taxable = region.giftCardsTaxable,
                tax_rate = region.taxRate,
                tax_inclusive = region.taxInclusive,
                countries = region.countries.map { StoreCountryDto.from(it) },
                payment_providers = region.paymentProviders.map { it.id },
                fulfillment_providers = region.fulfillmentProviders.map { it.id },
                created_at = region.createdAt,
                updated_at = region.updatedAt,
                metadata = region.metadata
            )
        }
    }
}

data class RegionsListResponse(
    val regions: List<AdminRegionDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class RegionResponse(
    val region: AdminRegionDto
)

data class UpdateRegionRequest(
    val name: String? = null,
    val currencyCode: String? = null,
    val automaticTaxes: Boolean? = null,
    val taxCode: String? = null,
    val giftCardsTaxable: Boolean? = null,
    val taxRate: BigDecimal? = null,
    val taxInclusive: Boolean? = null,
    val countryCodes: List<String>? = null,
    val paymentProviderIds: List<String>? = null,
    val fulfillmentProviderIds: List<String>? = null,
    val metadata: Map<String, Any?>? = null
)

@RestController
@RequestMapping("/admin/regions")
@Tag(name = "Admin Regions", description = "Region management endpoints")
class AdminRegionController(
    private val workflowEngine: WorkflowEngine,
    private val regionRepository: RegionRepository,
    private val countryRepository: CountryRepository,
    private val paymentProviderRepository: PaymentProviderRepository,
    private val fulfillmentProviderRepository: FulfillmentProviderRepository
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

    // =========================================================================
    // List & Get
    // =========================================================================

    @Operation(summary = "List all regions")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun listRegions(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<RegionsListResponse> {
        adminRegionLogger.info { "GET /admin/regions - limit=$limit, offset=$offset, q=$q" }

        var regions = regionRepository.findAllWithDetailsByDeletedAtIsNull()

        // Filter by search query
        if (!q.isNullOrBlank()) {
            val searchTerm = q.lowercase()
            regions = regions.filter { region ->
                region.name.lowercase().contains(searchTerm) ||
                region.currencyCode.lowercase().contains(searchTerm) ||
                region.countries.any { it.name.lowercase().contains(searchTerm) }
            }
        }

        // Sort by name
        regions = regions.sortedBy { it.name }

        val count = regions.size.toLong()
        val paginatedRegions = regions.drop(offset).take(limit.coerceAtMost(100))

        val items = paginatedRegions.map { AdminRegionDto.from(it) }

        return ResponseEntity.ok(RegionsListResponse(
            regions = items,
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get region details by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun getRegion(@PathVariable id: String): ResponseEntity<RegionResponse> {
        adminRegionLogger.info { "GET /admin/regions/$id" }

        val region = regionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(RegionResponse(
            region = AdminRegionDto.from(region)
        ))
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Operation(summary = "Update an existing region")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun updateRegion(
        @PathVariable id: String,
        @RequestBody request: UpdateRegionRequest
    ): ResponseEntity<Any> {
        adminRegionLogger.info { "PUT /admin/regions/$id" }

        val region = regionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Validate unique name if being changed
        if (request.name != null && request.name != region.name) {
            if (regionRepository.existsByNameAndIdNot(request.name, id)) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "A region with this name already exists"
                ))
            }
            region.name = request.name
        }

        // Update fields if provided
        request.currencyCode?.let { region.currencyCode = it.uppercase() }
        request.automaticTaxes?.let { region.automaticTaxes = it }
        request.taxCode?.let { region.taxCode = it }
        request.giftCardsTaxable?.let { region.giftCardsTaxable = it }
        request.taxRate?.let { region.taxRate = it }
        request.taxInclusive?.let { region.taxInclusive = it }
        request.metadata?.let { region.metadata = it.toMutableMap() }

        // Update countries if provided
        if (request.countryCodes != null) {
            region.countries.clear()
            request.countryCodes.forEach { iso2 ->
                val code = iso2.uppercase()
                val country = countryRepository.findByIso2(code).orElse(null)
                if (country != null) {
                    region.countries.add(country)
                } else {
                    adminRegionLogger.warn { "Country with ISO2 code $code not found, skipping" }
                }
            }
        }

        // Update payment providers if provided
        if (request.paymentProviderIds != null) {
            val providers = paymentProviderRepository.findByIdIn(request.paymentProviderIds)
            region.paymentProviders.clear()
            region.paymentProviders.addAll(providers)
        }

        // Update fulfillment providers if provided
        if (request.fulfillmentProviderIds != null) {
            val providers = fulfillmentProviderRepository.findByIdIn(request.fulfillmentProviderIds)
            region.fulfillmentProviders.clear()
            region.fulfillmentProviders.addAll(providers)
        }

        val savedRegion = regionRepository.save(region)
        adminRegionLogger.info { "Region $id updated successfully" }

        return ResponseEntity.ok(RegionResponse(
            region = AdminRegionDto.from(savedRegion)
        ))
    }

    // =========================================================================
    // Delete
    // =========================================================================

    @Operation(summary = "Delete a region (soft delete)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun deleteRegion(@PathVariable id: String): ResponseEntity<Any> {
        adminRegionLogger.info { "DELETE /admin/regions/$id" }

        val region = regionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Soft delete
        region.softDelete()
        regionRepository.save(region)

        adminRegionLogger.info { "Region $id soft deleted successfully" }

        return ResponseEntity.ok(mapOf(
            "id" to id,
            "deleted" to true
        ))
    }
}
