package com.vernont.domain.inventory.dto

import com.vernont.domain.inventory.InventoryItem
import com.vernont.domain.inventory.InventoryLevel
import com.vernont.domain.inventory.StockLocation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

/**
 * Request to create an inventory item
 */
data class CreateInventoryItemRequest(
    val sku: String? = null,
    val hsCode: String? = null,
    val originCountry: String? = null,
    val midCode: String? = null,
    val material: String? = null,
    val weight: Int? = null,
    val length: Int? = null,
    val height: Int? = null,
    val width: Int? = null,
    val requiresShipping: Boolean = true
)

/**
 * Request to update an inventory item
 */
data class UpdateInventoryItemRequest(
    val sku: String? = null,
    val hsCode: String? = null,
    val originCountry: String? = null,
    val midCode: String? = null,
    val material: String? = null,
    val weight: Int? = null,
    val length: Int? = null,
    val height: Int? = null,
    val width: Int? = null,
    val requiresShipping: Boolean? = null
)

/**
 * Request to reserve inventory
 */
data class ReserveInventoryRequest(
    @field:NotBlank
    val inventoryItemId: String,

    @field:NotBlank
    val locationId: String,

    @field:Positive
    val quantity: Int
)

/**
 * Request to release inventory reservation
 */
data class ReleaseInventoryRequest(
    @field:NotBlank
    val inventoryItemId: String,

    @field:NotBlank
    val locationId: String,

    @field:Positive
    val quantity: Int
)

/**
 * Request to adjust inventory stock
 */
data class AdjustInventoryStockRequest(
    @field:NotBlank
    val inventoryItemId: String,

    @field:NotBlank
    val locationId: String,

    @field:NotNull
    val adjustment: Int, // Can be positive or negative

    val reason: String? = null
)

/**
 * Request to create stock location
 */
data class CreateStockLocationRequest(
    @field:NotBlank
    val name: String,

    val address: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val metadata: String? = null
)

/**
 * Request to update stock location
 */
data class UpdateStockLocationRequest(
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val metadata: String? = null
)

/**
 * Inventory item response DTO
 */
data class InventoryItemResponse(
    val id: String,
    val sku: String?,
    val hsCode: String?,
    val originCountry: String?,
    val midCode: String?,
    val material: String?,
    val weight: Int?,
    val length: Int?,
    val height: Int?,
    val width: Int?,
    val requiresShipping: Boolean,
    val totalStockQuantity: Int,
    val totalAvailableQuantity: Int,
    val inventoryLevels: List<InventoryLevelResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(item: InventoryItem): InventoryItemResponse {
            return InventoryItemResponse(
                id = item.id,
                sku = item.sku,
                hsCode = item.hsCode,
                originCountry = item.originCountry,
                midCode = item.midCode,
                material = item.material,
                weight = item.weight,
                length = item.length,
                height = item.height,
                width = item.width,
                requiresShipping = item.requiresShipping,
                totalStockQuantity = item.getTotalStockQuantity(),
                totalAvailableQuantity = item.getTotalAvailableQuantity(),
                inventoryLevels = item.inventoryLevels.map { InventoryLevelResponse.from(it) },
                createdAt = item.createdAt,
                updatedAt = item.updatedAt
            )
        }
    }
}

/**
 * Inventory item summary response (without levels)
 */
data class InventoryItemSummaryResponse(
    val id: String,
    val sku: String?,
    val totalStockQuantity: Int,
    val totalAvailableQuantity: Int,
    val requiresShipping: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(item: InventoryItem): InventoryItemSummaryResponse {
            return InventoryItemSummaryResponse(
                id = item.id,
                sku = item.sku,
                totalStockQuantity = item.getTotalStockQuantity(),
                totalAvailableQuantity = item.getTotalAvailableQuantity(),
                requiresShipping = item.requiresShipping,
                createdAt = item.createdAt
            )
        }
    }
}

/**
 * Inventory level response DTO
 */
data class InventoryLevelResponse(
    val id: String,
    val inventoryItemId: String,
    val location: StockLocationSummary?,
    val stockedQuantity: Int,
    val reservedQuantity: Int,
    val incomingQuantity: Int,
    val availableQuantity: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(level: InventoryLevel): InventoryLevelResponse {
            return InventoryLevelResponse(
                id = level.id,
                inventoryItemId = level.inventoryItem?.id ?: "",
                location = level.location?.let { StockLocationSummary.from(it) },
                stockedQuantity = level.stockedQuantity,
                reservedQuantity = level.reservedQuantity,
                incomingQuantity = level.incomingQuantity,
                availableQuantity = level.availableQuantity,
                createdAt = level.createdAt,
                updatedAt = level.updatedAt
            )
        }
    }
}

/**
 * Stock location response DTO
 */
data class StockLocationResponse(
    val id: String,
    val name: String,
    val address: String?,
    val city: String?,
    val countryCode: String?,
    val postalCode: String?,
    val phone: String?,
    val metadata: MutableMap<String, Any?>?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(location: StockLocation): StockLocationResponse {
            return StockLocationResponse(
                id = location.id,
                name = location.name,
                address = location.address,
                city = location.city,
                countryCode = location.countryCode,
                postalCode = location.postalCode,
                phone = location.phone,
                metadata = location.metadata,
                createdAt = location.createdAt,
                updatedAt = location.updatedAt
            )
        }
    }
}

/**
 * Stock location summary DTO
 */
data class StockLocationSummary(
    val id: String,
    val name: String,
    val city: String?,
    val countryCode: String?
) {
    companion object {
        fun from(location: StockLocation): StockLocationSummary {
            return StockLocationSummary(
                id = location.id,
                name = location.name,
                city = location.city,
                countryCode = location.countryCode
            )
        }
    }
}
