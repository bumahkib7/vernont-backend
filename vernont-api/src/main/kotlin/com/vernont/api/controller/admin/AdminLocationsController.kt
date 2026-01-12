package com.vernont.api.controller.admin

import com.vernont.domain.fulfillment.ShippingProfile
import com.vernont.domain.fulfillment.ShippingProfileType
import com.vernont.domain.inventory.StockLocation
import com.vernont.repository.fulfillment.ShippingProfileRepository
import com.vernont.repository.inventory.StockLocationRepository
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

// =========================================================================
// Stock Location DTOs
// =========================================================================

data class AdminStockLocationDto(
    val id: String,
    val name: String,
    val address: String?,
    val address_1: String?,
    val address_2: String?,
    val city: String?,
    val country_code: String?,
    val province: String?,
    val postal_code: String?,
    val phone: String?,
    val priority: Int,
    val fulfillment_enabled: Boolean,
    val created_at: Instant?,
    val updated_at: Instant?,
    val metadata: Map<String, Any?>?
) {
    companion object {
        fun from(location: StockLocation): AdminStockLocationDto {
            return AdminStockLocationDto(
                id = location.id,
                name = location.name,
                address = location.address,
                address_1 = location.address1,
                address_2 = location.address2,
                city = location.city,
                country_code = location.countryCode,
                province = location.province,
                postal_code = location.postalCode,
                phone = location.phone,
                priority = location.priority,
                fulfillment_enabled = location.fulfillmentEnabled,
                created_at = location.createdAt,
                updated_at = location.updatedAt,
                metadata = location.metadata
            )
        }
    }
}

data class AdminStockLocationsListResponse(
    val locations: List<AdminStockLocationDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class AdminStockLocationResponse(
    val location: AdminStockLocationDto
)

data class AdminCreateStockLocationRequest(
    val name: String,
    val address: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val priority: Int = 0,
    val fulfillmentEnabled: Boolean = true,
    val metadata: Map<String, Any?>? = null
)

data class AdminUpdateStockLocationRequest(
    val name: String? = null,
    val address: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val priority: Int? = null,
    val fulfillmentEnabled: Boolean? = null,
    val metadata: Map<String, Any?>? = null
)

// =========================================================================
// Shipping Profile DTOs
// =========================================================================

data class AdminShippingProfileDto(
    val id: String,
    val name: String,
    val type: String,
    val product_count: Int,
    val created_at: Instant?,
    val updated_at: Instant?,
    val metadata: Map<String, Any?>?
) {
    companion object {
        fun from(profile: ShippingProfile): AdminShippingProfileDto {
            return AdminShippingProfileDto(
                id = profile.id,
                name = profile.name,
                type = profile.type.name.lowercase(),
                product_count = profile.productIds?.size ?: 0,
                created_at = profile.createdAt,
                updated_at = profile.updatedAt,
                metadata = profile.metadata
            )
        }
    }
}

data class ShippingProfilesListResponse(
    val profiles: List<AdminShippingProfileDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class ShippingProfileResponse(
    val profile: AdminShippingProfileDto
)

data class CreateShippingProfileRequest(
    val name: String,
    val type: String = "custom",
    val productIds: List<String>? = null,
    val metadata: Map<String, Any?>? = null
)

data class UpdateShippingProfileRequest(
    val name: String? = null,
    val type: String? = null,
    val productIds: List<String>? = null,
    val metadata: Map<String, Any?>? = null
)

@RestController
@RequestMapping("/admin/locations")
@Tag(name = "Admin Locations", description = "Stock location and shipping profile management endpoints")
class AdminLocationsController(
    private val stockLocationRepository: StockLocationRepository,
    private val shippingProfileRepository: ShippingProfileRepository
) {

    // =========================================================================
    // Stock Locations
    // =========================================================================

    @Operation(summary = "List all stock locations")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun listStockLocations(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<AdminStockLocationsListResponse> {
        logger.info { "GET /admin/locations - limit=$limit, offset=$offset, q=$q" }

        var locations = stockLocationRepository.findByDeletedAtIsNull()

        // Filter by search query
        if (!q.isNullOrBlank()) {
            val searchTerm = q.lowercase()
            locations = locations.filter { location ->
                location.name.lowercase().contains(searchTerm) ||
                location.address?.lowercase()?.contains(searchTerm) == true ||
                location.city?.lowercase()?.contains(searchTerm) == true ||
                location.countryCode?.lowercase()?.contains(searchTerm) == true
            }
        }

        // Sort by priority (highest first), then name
        locations = locations.sortedWith(
            compareByDescending<StockLocation> { it.priority }
                .thenBy { it.name }
        )

        val count = locations.size.toLong()
        val paginatedLocations = locations.drop(offset).take(limit.coerceAtMost(100))

        return ResponseEntity.ok(AdminStockLocationsListResponse(
            locations = paginatedLocations.map { AdminStockLocationDto.from(it) },
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get stock location by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun getStockLocation(@PathVariable id: String): ResponseEntity<AdminStockLocationResponse> {
        logger.info { "GET /admin/locations/$id" }

        val location = stockLocationRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(AdminStockLocationResponse(
            location = AdminStockLocationDto.from(location)
        ))
    }

    @Operation(summary = "Create a new stock location")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    @Transactional
    fun createStockLocation(@RequestBody request: AdminCreateStockLocationRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/locations - name=${request.name}" }

        // Validate unique name
        if (stockLocationRepository.existsByName(request.name)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "A location with this name already exists"
            ))
        }

        val location = StockLocation().apply {
            name = request.name
            address = request.address
            address1 = request.address1
            address2 = request.address2
            city = request.city
            countryCode = request.countryCode?.uppercase()
            province = request.province
            postalCode = request.postalCode
            phone = request.phone
            priority = request.priority
            fulfillmentEnabled = request.fulfillmentEnabled
            metadata = request.metadata?.toMutableMap()
        }

        val savedLocation = stockLocationRepository.save(location)
        logger.info { "Created stock location ${savedLocation.id}" }

        return ResponseEntity.status(HttpStatus.CREATED).body(AdminStockLocationResponse(
            location = AdminStockLocationDto.from(savedLocation)
        ))
    }

    @Operation(summary = "Update a stock location")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    @Transactional
    fun updateStockLocation(
        @PathVariable id: String,
        @RequestBody request: AdminUpdateStockLocationRequest
    ): ResponseEntity<Any> {
        logger.info { "PUT /admin/locations/$id" }

        val location = stockLocationRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Validate unique name if being changed
        if (request.name != null && request.name != location.name) {
            if (stockLocationRepository.existsByNameAndIdNot(request.name, id)) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "A location with this name already exists"
                ))
            }
            location.name = request.name
        }

        // Update fields if provided
        request.address?.let { location.address = it }
        request.address1?.let { location.address1 = it }
        request.address2?.let { location.address2 = it }
        request.city?.let { location.city = it }
        request.countryCode?.let { location.countryCode = it.uppercase() }
        request.province?.let { location.province = it }
        request.postalCode?.let { location.postalCode = it }
        request.phone?.let { location.phone = it }
        request.priority?.let { location.priority = it }
        request.fulfillmentEnabled?.let { location.fulfillmentEnabled = it }
        request.metadata?.let { location.metadata = it.toMutableMap() }

        val savedLocation = stockLocationRepository.save(location)
        logger.info { "Updated stock location $id" }

        return ResponseEntity.ok(AdminStockLocationResponse(
            location = AdminStockLocationDto.from(savedLocation)
        ))
    }

    @Operation(summary = "Delete a stock location")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun deleteStockLocation(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "DELETE /admin/locations/$id" }

        val location = stockLocationRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Soft delete
        location.softDelete()
        stockLocationRepository.save(location)

        logger.info { "Soft deleted stock location $id" }

        return ResponseEntity.ok(mapOf(
            "id" to id,
            "deleted" to true
        ))
    }

    // =========================================================================
    // Shipping Profiles
    // =========================================================================

    @Operation(summary = "List all shipping profiles")
    @GetMapping("/shipping-profiles")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun listShippingProfiles(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<ShippingProfilesListResponse> {
        logger.info { "GET /admin/locations/shipping-profiles - limit=$limit, offset=$offset, q=$q, type=$type" }

        var profiles = shippingProfileRepository.findByDeletedAtIsNull()

        // Filter by type
        if (!type.isNullOrBlank()) {
            val profileType = try {
                ShippingProfileType.valueOf(type.uppercase())
            } catch (e: Exception) {
                null
            }
            if (profileType != null) {
                profiles = profiles.filter { it.type == profileType }
            }
        }

        // Filter by search query
        if (!q.isNullOrBlank()) {
            val searchTerm = q.lowercase()
            profiles = profiles.filter { profile ->
                profile.name.lowercase().contains(searchTerm) ||
                profile.type.name.lowercase().contains(searchTerm)
            }
        }

        // Sort by type (default first), then name
        profiles = profiles.sortedWith(
            compareBy<ShippingProfile> {
                when (it.type) {
                    ShippingProfileType.DEFAULT -> 0
                    ShippingProfileType.GIFT_CARD -> 1
                    ShippingProfileType.CUSTOM -> 2
                }
            }.thenBy { it.name }
        )

        val count = profiles.size.toLong()
        val paginatedProfiles = profiles.drop(offset).take(limit.coerceAtMost(100))

        return ResponseEntity.ok(ShippingProfilesListResponse(
            profiles = paginatedProfiles.map { AdminShippingProfileDto.from(it) },
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get shipping profile by ID")
    @GetMapping("/shipping-profiles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_SERVICE', 'WAREHOUSE_MANAGER', 'DEVELOPER')")
    fun getShippingProfile(@PathVariable id: String): ResponseEntity<ShippingProfileResponse> {
        logger.info { "GET /admin/locations/shipping-profiles/$id" }

        val profile = shippingProfileRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(ShippingProfileResponse(
            profile = AdminShippingProfileDto.from(profile)
        ))
    }

    @Operation(summary = "Create a new shipping profile")
    @PostMapping("/shipping-profiles")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun createShippingProfile(@RequestBody request: CreateShippingProfileRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/locations/shipping-profiles - name=${request.name}" }

        // Validate unique name
        if (shippingProfileRepository.existsByName(request.name)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "A shipping profile with this name already exists"
            ))
        }

        // Validate type
        val profileType = try {
            ShippingProfileType.valueOf(request.type.uppercase())
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Invalid shipping profile type",
                "validTypes" to ShippingProfileType.entries.map { it.name.lowercase() }
            ))
        }

        val profile = ShippingProfile().apply {
            name = request.name
            type = profileType
            productIds = request.productIds?.toTypedArray()
            metadata = request.metadata?.toMutableMap()
        }

        val savedProfile = shippingProfileRepository.save(profile)
        logger.info { "Created shipping profile ${savedProfile.id}" }

        return ResponseEntity.status(HttpStatus.CREATED).body(ShippingProfileResponse(
            profile = AdminShippingProfileDto.from(savedProfile)
        ))
    }

    @Operation(summary = "Update a shipping profile")
    @PutMapping("/shipping-profiles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun updateShippingProfile(
        @PathVariable id: String,
        @RequestBody request: UpdateShippingProfileRequest
    ): ResponseEntity<Any> {
        logger.info { "PUT /admin/locations/shipping-profiles/$id" }

        val profile = shippingProfileRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Validate unique name if being changed
        if (request.name != null && request.name != profile.name) {
            if (shippingProfileRepository.existsByNameAndIdNot(request.name, id)) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "A shipping profile with this name already exists"
                ))
            }
            profile.name = request.name
        }

        // Validate and update type if provided
        if (request.type != null) {
            val profileType = try {
                ShippingProfileType.valueOf(request.type.uppercase())
            } catch (e: Exception) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "Invalid shipping profile type",
                    "validTypes" to ShippingProfileType.entries.map { it.name.lowercase() }
                ))
            }
            profile.type = profileType
        }

        // Update other fields
        request.productIds?.let { profile.productIds = it.toTypedArray() }
        request.metadata?.let { profile.metadata = it.toMutableMap() }

        val savedProfile = shippingProfileRepository.save(profile)
        logger.info { "Updated shipping profile $id" }

        return ResponseEntity.ok(ShippingProfileResponse(
            profile = AdminShippingProfileDto.from(savedProfile)
        ))
    }

    @Operation(summary = "Delete a shipping profile")
    @DeleteMapping("/shipping-profiles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Transactional
    fun deleteShippingProfile(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "DELETE /admin/locations/shipping-profiles/$id" }

        val profile = shippingProfileRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Don't allow deleting the default profile
        if (profile.type == ShippingProfileType.DEFAULT) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Cannot delete the default shipping profile"
            ))
        }

        // Soft delete
        profile.softDelete()
        shippingProfileRepository.save(profile)

        logger.info { "Soft deleted shipping profile $id" }

        return ResponseEntity.ok(mapOf(
            "id" to id,
            "deleted" to true
        ))
    }
}
