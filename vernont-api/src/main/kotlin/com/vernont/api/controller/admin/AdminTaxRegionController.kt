package com.vernont.api.controller.admin

import com.vernont.domain.region.TaxRate
import com.vernont.repository.region.RegionRepository
import com.vernont.repository.region.TaxRateRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

// DTOs for Tax Rates
data class AdminTaxRateDto(
    val id: String,
    val name: String,
    val code: String?,
    val rate: Double,
    val region_id: String,
    val region_name: String?,
    val product_types: String?,
    val product_categories: String?,
    val shipping_option_id: String?,
    val created_at: Instant?,
    val updated_at: Instant?,
    val metadata: Map<String, Any?>?
) {
    companion object {
        fun from(taxRate: TaxRate): AdminTaxRateDto {
            return AdminTaxRateDto(
                id = taxRate.id,
                name = taxRate.name,
                code = taxRate.code,
                rate = taxRate.rate,
                region_id = taxRate.region?.id ?: "",
                region_name = taxRate.region?.name,
                product_types = taxRate.productTypes,
                product_categories = taxRate.productCategories,
                shipping_option_id = taxRate.shippingOptionId,
                created_at = taxRate.createdAt,
                updated_at = taxRate.updatedAt,
                metadata = taxRate.metadata
            )
        }
    }
}

// Response for listing tax rates grouped by region
data class TaxRegionDto(
    val region_id: String,
    val region_name: String,
    val currency_code: String,
    val default_tax_rate: Double,
    val tax_rates: List<AdminTaxRateDto>,
    val tax_rate_count: Int
)

data class TaxRegionsListResponse(
    val tax_regions: List<TaxRegionDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class TaxRatesListResponse(
    val tax_rates: List<AdminTaxRateDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class TaxRateResponse(
    val tax_rate: AdminTaxRateDto
)

data class CreateTaxRateRequest(
    val name: String,
    val code: String? = null,
    val rate: Double,
    val regionId: String,
    val productTypes: String? = null,
    val productCategories: String? = null,
    val shippingOptionId: String? = null,
    val metadata: Map<String, Any?>? = null
)

data class UpdateTaxRateRequest(
    val name: String? = null,
    val code: String? = null,
    val rate: Double? = null,
    val productTypes: String? = null,
    val productCategories: String? = null,
    val shippingOptionId: String? = null,
    val metadata: Map<String, Any?>? = null
)

@RestController
@RequestMapping("/admin/tax-regions")
@Tag(name = "Admin Tax Regions", description = "Tax rate and tax region management endpoints")
class AdminTaxRegionController(
    private val taxRateRepository: TaxRateRepository,
    private val regionRepository: RegionRepository
) {

    // =========================================================================
    // List Tax Regions (grouped by region)
    // =========================================================================

    @Operation(summary = "List all tax regions with their tax rates")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun listTaxRegions(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<TaxRegionsListResponse> {
        logger.info { "GET /admin/tax-regions - limit=$limit, offset=$offset, q=$q" }

        // Get all regions that have tax configuration or automatic taxes
        var regions = regionRepository.findAllWithDetailsByDeletedAtIsNull()

        // Filter by search query
        if (!q.isNullOrBlank()) {
            val searchTerm = q.lowercase()
            regions = regions.filter { region ->
                region.name.lowercase().contains(searchTerm) ||
                region.currencyCode.lowercase().contains(searchTerm)
            }
        }

        // Sort by name
        regions = regions.sortedBy { it.name }

        val count = regions.size.toLong()
        val paginatedRegions = regions.drop(offset).take(limit.coerceAtMost(100))

        // Build tax region DTOs with tax rates for each region
        val taxRegions = paginatedRegions.map { region ->
            val taxRates = taxRateRepository.findByRegionIdAndDeletedAtIsNull(region.id)
            TaxRegionDto(
                region_id = region.id,
                region_name = region.name,
                currency_code = region.currencyCode,
                default_tax_rate = region.taxRate.toDouble() * 100, // Convert from decimal to percentage
                tax_rates = taxRates.map { AdminTaxRateDto.from(it) },
                tax_rate_count = taxRates.size
            )
        }

        return ResponseEntity.ok(TaxRegionsListResponse(
            tax_regions = taxRegions,
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    // =========================================================================
    // Tax Rates CRUD
    // =========================================================================

    @Operation(summary = "List all tax rates")
    @GetMapping("/rates")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun listTaxRates(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) regionId: String?,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<TaxRatesListResponse> {
        logger.info { "GET /admin/tax-regions/rates - limit=$limit, offset=$offset, regionId=$regionId, q=$q" }

        var taxRates = if (regionId != null) {
            taxRateRepository.findAllWithRegionByRegionIdAndDeletedAtIsNull(regionId)
        } else {
            taxRateRepository.findAllWithRegionByDeletedAtIsNull()
        }

        // Filter by search query
        if (!q.isNullOrBlank()) {
            val searchTerm = q.lowercase()
            taxRates = taxRates.filter { rate ->
                rate.name.lowercase().contains(searchTerm) ||
                rate.code?.lowercase()?.contains(searchTerm) == true ||
                rate.region?.name?.lowercase()?.contains(searchTerm) == true
            }
        }

        // Sort by region name, then rate name
        taxRates = taxRates.sortedWith(
            compareBy({ it.region?.name }, { it.name })
        )

        val count = taxRates.size.toLong()
        val paginatedRates = taxRates.drop(offset).take(limit.coerceAtMost(100))

        return ResponseEntity.ok(TaxRatesListResponse(
            tax_rates = paginatedRates.map { AdminTaxRateDto.from(it) },
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get tax rate by ID")
    @GetMapping("/rates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun getTaxRate(@PathVariable id: String): ResponseEntity<TaxRateResponse> {
        logger.info { "GET /admin/tax-regions/rates/$id" }

        val taxRate = taxRateRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(TaxRateResponse(
            tax_rate = AdminTaxRateDto.from(taxRate)
        ))
    }

    @Operation(summary = "Create a new tax rate")
    @PostMapping("/rates")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun createTaxRate(@RequestBody request: CreateTaxRateRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/tax-regions/rates - name=${request.name}, regionId=${request.regionId}" }

        // Validate region exists
        val region = regionRepository.findByIdAndDeletedAtIsNull(request.regionId)
            ?: return ResponseEntity.badRequest().body(mapOf(
                "message" to "Region not found"
            ))

        // Validate rate is non-negative
        if (request.rate < 0) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Tax rate must be non-negative"
            ))
        }

        // Validate unique name within region
        if (taxRateRepository.existsByNameAndRegionIdAndDeletedAtIsNull(request.name, request.regionId)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "A tax rate with this name already exists in this region"
            ))
        }

        // Validate unique code if provided
        if (request.code != null && taxRateRepository.existsByCodeAndDeletedAtIsNull(request.code)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "A tax rate with this code already exists"
            ))
        }

        val taxRate = TaxRate().apply {
            name = request.name
            code = request.code
            rate = request.rate
            this.region = region
            productTypes = request.productTypes
            productCategories = request.productCategories
            shippingOptionId = request.shippingOptionId
            metadata = request.metadata?.toMutableMap()
        }

        val savedTaxRate = taxRateRepository.save(taxRate)
        logger.info { "Created tax rate ${savedTaxRate.id} for region ${region.name}" }

        return ResponseEntity.status(HttpStatus.CREATED).body(TaxRateResponse(
            tax_rate = AdminTaxRateDto.from(savedTaxRate)
        ))
    }

    @Operation(summary = "Update a tax rate")
    @PutMapping("/rates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun updateTaxRate(
        @PathVariable id: String,
        @RequestBody request: UpdateTaxRateRequest
    ): ResponseEntity<Any> {
        logger.info { "PUT /admin/tax-regions/rates/$id" }

        val taxRate = taxRateRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Validate unique name within region if being changed
        if (request.name != null && request.name != taxRate.name) {
            val regionId = taxRate.region?.id ?: ""
            if (taxRateRepository.existsByNameAndRegionIdAndIdNotAndDeletedAtIsNull(request.name, regionId, id)) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "A tax rate with this name already exists in this region"
                ))
            }
            taxRate.name = request.name
        }

        // Validate unique code if being changed
        if (request.code != null && request.code != taxRate.code) {
            if (taxRateRepository.existsByCodeAndIdNotAndDeletedAtIsNull(request.code, id)) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "A tax rate with this code already exists"
                ))
            }
            taxRate.code = request.code
        }

        // Update rate if provided
        if (request.rate != null) {
            if (request.rate < 0) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "Tax rate must be non-negative"
                ))
            }
            taxRate.rate = request.rate
        }

        // Update other fields
        request.productTypes?.let { taxRate.productTypes = it }
        request.productCategories?.let { taxRate.productCategories = it }
        request.shippingOptionId?.let { taxRate.shippingOptionId = it }
        request.metadata?.let { taxRate.metadata = it.toMutableMap() }

        val savedTaxRate = taxRateRepository.save(taxRate)
        logger.info { "Updated tax rate $id" }

        return ResponseEntity.ok(TaxRateResponse(
            tax_rate = AdminTaxRateDto.from(savedTaxRate)
        ))
    }

    @Operation(summary = "Delete a tax rate")
    @DeleteMapping("/rates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun deleteTaxRate(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "DELETE /admin/tax-regions/rates/$id" }

        val taxRate = taxRateRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Soft delete
        taxRate.softDelete()
        taxRateRepository.save(taxRate)

        logger.info { "Soft deleted tax rate $id" }

        return ResponseEntity.ok(mapOf(
            "id" to id,
            "deleted" to true
        ))
    }
}
