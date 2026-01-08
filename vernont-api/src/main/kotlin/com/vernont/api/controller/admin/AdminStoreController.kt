package com.vernont.api.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.application.store.CreateStoreRequest
import com.vernont.application.store.StoreNotFoundException
import com.vernont.application.store.StoreService
import com.vernont.application.store.UpdateStoreRequest
import com.vernont.domain.store.Store
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/stores")
@Tag(name = "Admin Stores", description = "Store management endpoints")
class AdminStoreController(
    private val storeService: StoreService
) {

    // =========================================================================
    // List & Get
    // =========================================================================

    @Operation(summary = "List all stores")
    @GetMapping
    fun listStores(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<AdminStoresResponse> {
        logger.info { "GET /admin/stores - limit=$limit, offset=$offset, q=$q" }

        val result = storeService.listStores(limit, offset, q)

        return ResponseEntity.ok(AdminStoresResponse(
            stores = result.stores.map { StoreDto.from(it) },
            count = result.total,
            limit = result.limit,
            offset = result.offset
        ))
    }

    @Operation(summary = "Get store by ID")
    @GetMapping("/{storeId}")
    fun getStore(@PathVariable storeId: String): ResponseEntity<AdminStoreResponse> {
        logger.info { "GET /admin/stores/$storeId" }

        return try {
            val store = storeService.getStore(storeId)
            ResponseEntity.ok(AdminStoreResponse(store = StoreDto.from(store)))
        } catch (e: StoreNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    // =========================================================================
    // Create & Update
    // =========================================================================

    @Operation(summary = "Create a new store")
    @PostMapping
    fun createStore(@RequestBody request: AdminCreateStoreRequest): ResponseEntity<AdminStoreResponse> {
        logger.info { "POST /admin/stores - name=${request.name}" }

        val serviceRequest = CreateStoreRequest(
            name = request.name,
            defaultCurrencyCode = request.defaultCurrencyCode ?: "GBP",
            swapLinkTemplate = request.swapLinkTemplate,
            paymentLinkTemplate = request.paymentLinkTemplate,
            inviteLinkTemplate = request.inviteLinkTemplate
        )

        val store = storeService.createStore(serviceRequest)
        return ResponseEntity.ok(AdminStoreResponse(store = StoreDto.from(store)))
    }

    @Operation(summary = "Update a store")
    @PutMapping("/{storeId}")
    fun updateStore(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateStoreRequest
    ): ResponseEntity<AdminStoreResponse> {
        logger.info { "PUT /admin/stores/$storeId" }

        return try {
            val serviceRequest = UpdateStoreRequest(
                name = request.name,
                defaultCurrencyCode = request.defaultCurrencyCode,
                swapLinkTemplate = request.swapLinkTemplate,
                paymentLinkTemplate = request.paymentLinkTemplate,
                inviteLinkTemplate = request.inviteLinkTemplate,
                defaultSalesChannelId = request.defaultSalesChannelId,
                defaultRegionId = request.defaultRegionId,
                defaultLocationId = request.defaultLocationId
            )

            val store = storeService.updateStore(storeId, serviceRequest)
            ResponseEntity.ok(AdminStoreResponse(store = StoreDto.from(store)))
        } catch (e: StoreNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Delete a store")
    @DeleteMapping("/{storeId}")
    fun deleteStore(@PathVariable storeId: String): ResponseEntity<Void> {
        logger.info { "DELETE /admin/stores/$storeId" }

        return try {
            storeService.deleteStore(storeId)
            ResponseEntity.noContent().build()
        } catch (e: StoreNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }
}

// ============================================================================
// Request DTOs
// ============================================================================

data class AdminCreateStoreRequest(
    val name: String,

    @JsonProperty("default_currency_code")
    val defaultCurrencyCode: String? = null,

    @JsonProperty("swap_link_template")
    val swapLinkTemplate: String? = null,

    @JsonProperty("payment_link_template")
    val paymentLinkTemplate: String? = null,

    @JsonProperty("invite_link_template")
    val inviteLinkTemplate: String? = null
)

data class AdminUpdateStoreRequest(
    val name: String? = null,

    @JsonProperty("default_currency_code")
    val defaultCurrencyCode: String? = null,

    @JsonProperty("swap_link_template")
    val swapLinkTemplate: String? = null,

    @JsonProperty("payment_link_template")
    val paymentLinkTemplate: String? = null,

    @JsonProperty("invite_link_template")
    val inviteLinkTemplate: String? = null,

    @JsonProperty("default_sales_channel_id")
    val defaultSalesChannelId: String? = null,

    @JsonProperty("default_region_id")
    val defaultRegionId: String? = null,

    @JsonProperty("default_location_id")
    val defaultLocationId: String? = null
)

// ============================================================================
// Response DTOs
// ============================================================================

data class AdminStoresResponse(
    val stores: List<StoreDto>,
    val count: Int,
    val limit: Int,
    val offset: Int
)

data class AdminStoreResponse(
    val store: StoreDto
)

data class StoreDto(
    val id: String,
    val name: String,

    @JsonProperty("default_currency_code")
    val defaultCurrencyCode: String,

    @JsonProperty("swap_link_template")
    val swapLinkTemplate: String?,

    @JsonProperty("payment_link_template")
    val paymentLinkTemplate: String?,

    @JsonProperty("invite_link_template")
    val inviteLinkTemplate: String?,

    @JsonProperty("default_sales_channel_id")
    val defaultSalesChannelId: String?,

    @JsonProperty("default_region_id")
    val defaultRegionId: String?,

    @JsonProperty("default_location_id")
    val defaultLocationId: String?,

    @JsonProperty("created_at")
    val createdAt: Instant,

    @JsonProperty("updated_at")
    val updatedAt: Instant
) {
    companion object {
        fun from(store: Store): StoreDto {
            return StoreDto(
                id = store.id,
                name = store.name,
                defaultCurrencyCode = store.defaultCurrencyCode,
                swapLinkTemplate = store.swapLinkTemplate,
                paymentLinkTemplate = store.paymentLinkTemplate,
                inviteLinkTemplate = store.inviteLinkTemplate,
                defaultSalesChannelId = store.defaultSalesChannelId,
                defaultRegionId = store.defaultRegionId,
                defaultLocationId = store.defaultLocationId,
                createdAt = store.createdAt,
                updatedAt = store.updatedAt
            )
        }
    }
}
