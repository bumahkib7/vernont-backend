package com.vernont.api.controller.admin

import com.vernont.domain.inventory.MovementType
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.InventoryMovementRepository
import com.vernont.repository.inventory.StockLocationRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.inventory.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant

private val logger = KotlinLogging.logger {}

// =============================================================================
// Request/Response DTOs
// =============================================================================

data class AdjustInventoryRequest(
    val inventoryLevelId: String? = null,
    val inventoryItemId: String? = null,
    val locationId: String? = null,
    val sku: String? = null,
    val adjustment: Int,
    val reason: String,
    val note: String? = null
)

data class AdjustInventoryResponseDto(
    val inventoryLevelId: String,
    val inventoryItemId: String,
    val locationId: String,
    val previousQuantity: Int,
    val adjustment: Int,
    val newQuantity: Int,
    val reason: String,
    val note: String?,
    val message: String
)

data class CreateStockLocationRequest(
    val name: String,
    val address1: String? = null,
    val address2: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val priority: Int = 0,
    val fulfillmentEnabled: Boolean = true
)

data class StockLocationResponse(
    val id: String,
    val name: String,
    val address1: String?,
    val address2: String?,
    val city: String?,
    val countryCode: String?,
    val province: String?,
    val postalCode: String?,
    val phone: String?,
    val priority: Int,
    val fulfillmentEnabled: Boolean,
    val fullAddress: String,
    val createdAt: String
)

data class InventoryLevelResponse(
    val id: String,
    val inventoryItemId: String,
    val locationId: String,
    val locationName: String?,
    val sku: String?,
    val stockedQuantity: Int,
    val reservedQuantity: Int,
    val availableQuantity: Int,
    val incomingQuantity: Int
)

data class InventoryMovementResponse(
    val id: String,
    val eventId: String,
    val inventoryItemId: String,
    val inventoryLevelId: String?,
    val locationId: String,
    val locationName: String?,
    val sku: String?,
    val productTitle: String?,
    val movementType: String,
    val quantity: Int,
    val previousQuantity: Int?,
    val newQuantity: Int?,
    val reason: String?,
    val note: String?,
    val referenceType: String?,
    val referenceId: String?,
    val performedBy: String?,
    val occurredAt: String
)

@RestController
@RequestMapping("/admin/inventory")
@Tag(name = "Admin Inventory", description = "Inventory management endpoints")
class AdminInventoryController(
    private val workflowEngine: WorkflowEngine,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val inventoryMovementRepository: InventoryMovementRepository
) {

    // =========================================================================
    // Inventory Levels
    // =========================================================================

    @Operation(summary = "List inventory levels")
    @GetMapping("/levels")
    fun listInventoryLevels(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) locationId: String?,
        @RequestParam(required = false) sku: String?
    ): ResponseEntity<Map<String, Any>> {
        logger.info { "GET /admin/inventory/levels - offset=$offset, limit=$limit, locationId=$locationId, sku=$sku" }

        val allLevels = when {
            locationId != null -> inventoryLevelRepository.findByLocationIdAndDeletedAtIsNull(locationId)
            else -> inventoryLevelRepository.findByDeletedAtIsNull()
        }

        // Manual pagination
        val paginatedLevels = allLevels.drop(offset).take(limit)

        val response = paginatedLevels.map { level ->
            InventoryLevelResponse(
                id = level.id,
                inventoryItemId = level.inventoryItem?.id ?: "",
                locationId = level.location?.id ?: "",
                locationName = level.location?.name,
                sku = level.inventoryItem?.sku,
                stockedQuantity = level.stockedQuantity,
                reservedQuantity = level.reservedQuantity,
                availableQuantity = level.availableQuantity,
                incomingQuantity = level.incomingQuantity
            )
        }

        return ResponseEntity.ok(mapOf(
            "inventory_levels" to response,
            "count" to allLevels.size,
            "offset" to offset,
            "limit" to limit
        ))
    }

    @Operation(summary = "Get inventory level by ID")
    @GetMapping("/levels/{id}")
    fun getInventoryLevel(@PathVariable id: String): ResponseEntity<Map<String, Any>> {
        val level = inventoryLevelRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val response = InventoryLevelResponse(
            id = level.id,
            inventoryItemId = level.inventoryItem?.id ?: "",
            locationId = level.location?.id ?: "",
            locationName = level.location?.name,
            sku = level.inventoryItem?.sku,
            stockedQuantity = level.stockedQuantity,
            reservedQuantity = level.reservedQuantity,
            availableQuantity = level.availableQuantity,
            incomingQuantity = level.incomingQuantity
        )

        return ResponseEntity.ok(mapOf("inventory_level" to response))
    }

    @Operation(summary = "Adjust inventory")
    @PostMapping("/levels/adjust")
    suspend fun adjustInventory(
        @RequestBody request: AdjustInventoryRequest
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/inventory/levels/adjust - adjustment=${request.adjustment}, reason=${request.reason}" }

        val reasonEnum = try {
            AdjustmentReason.valueOf(request.reason.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Invalid reason",
                "error" to "Valid reasons: ${AdjustmentReason.entries.joinToString()}"
            ))
        }

        val input = AdjustInventoryInput(
            inventoryLevelId = request.inventoryLevelId,
            inventoryItemId = request.inventoryItemId,
            locationId = request.locationId,
            sku = request.sku,
            adjustment = request.adjustment,
            reason = reasonEnum,
            note = request.note
        )

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.AdjustInventory.NAME,
            input = input,
            inputType = AdjustInventoryInput::class,
            outputType = AdjustInventoryResponse::class,
            context = WorkflowContext()
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val data = result.data
                ResponseEntity.ok(AdjustInventoryResponseDto(
                    inventoryLevelId = data.inventoryLevelId,
                    inventoryItemId = data.inventoryItemId,
                    locationId = data.locationId,
                    previousQuantity = data.previousQuantity,
                    adjustment = data.adjustment,
                    newQuantity = data.newQuantity,
                    reason = data.reason.name,
                    note = data.note,
                    message = "Inventory adjusted successfully"
                ))
            }
            is WorkflowResult.Failure -> {
                ResponseEntity.badRequest().body(mapOf(
                    "message" to "Failed to adjust inventory",
                    "error" to (result.error.message ?: "Unknown error")
                ))
            }
        }
    }

    // =========================================================================
    // Stock Locations
    // =========================================================================

    @Operation(summary = "List stock locations")
    @GetMapping("/locations")
    fun listStockLocations(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<Map<String, Any>> {
        logger.info { "GET /admin/inventory/locations - offset=$offset, limit=$limit" }

        val allLocations = stockLocationRepository.findByDeletedAtIsNull()
        val paginatedLocations = allLocations.drop(offset).take(limit)

        val response = paginatedLocations.map { loc ->
            StockLocationResponse(
                id = loc.id,
                name = loc.name,
                address1 = loc.address1,
                address2 = loc.address2,
                city = loc.city,
                countryCode = loc.countryCode,
                province = loc.province,
                postalCode = loc.postalCode,
                phone = loc.phone,
                priority = loc.priority,
                fulfillmentEnabled = loc.fulfillmentEnabled,
                fullAddress = loc.getFullAddress(),
                createdAt = loc.createdAt.toString()
            )
        }

        return ResponseEntity.ok(mapOf(
            "stock_locations" to response,
            "count" to allLocations.size,
            "offset" to offset,
            "limit" to limit
        ))
    }

    @Operation(summary = "Get stock location by ID")
    @GetMapping("/locations/{id}")
    fun getStockLocation(@PathVariable id: String): ResponseEntity<Map<String, Any>> {
        val location = stockLocationRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val response = StockLocationResponse(
            id = location.id,
            name = location.name,
            address1 = location.address1,
            address2 = location.address2,
            city = location.city,
            countryCode = location.countryCode,
            province = location.province,
            postalCode = location.postalCode,
            phone = location.phone,
            priority = location.priority,
            fulfillmentEnabled = location.fulfillmentEnabled,
            fullAddress = location.getFullAddress(),
            createdAt = location.createdAt.toString()
        )

        return ResponseEntity.ok(mapOf("stock_location" to response))
    }

    @Operation(summary = "Create stock location")
    @PostMapping("/locations")
    suspend fun createStockLocation(
        @RequestBody request: CreateStockLocationRequest
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/inventory/locations - name=${request.name}" }

        val input = CreateStockLocationInput(
            name = request.name,
            address1 = request.address1,
            address2 = request.address2,
            city = request.city,
            countryCode = request.countryCode,
            province = request.province,
            postalCode = request.postalCode,
            phone = request.phone,
            priority = request.priority,
            fulfillmentEnabled = request.fulfillmentEnabled
        )

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.CreateStockLocation.NAME,
            input = input,
            inputType = CreateStockLocationInput::class,
            outputType = CreateStockLocationResponse::class,
            context = WorkflowContext()
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val data = result.data
                ResponseEntity.status(201).body(mapOf(
                    "stock_location" to StockLocationResponse(
                        id = data.id,
                        name = data.name,
                        address1 = data.address1,
                        address2 = data.address2,
                        city = data.city,
                        countryCode = data.countryCode,
                        province = data.province,
                        postalCode = data.postalCode,
                        phone = data.phone,
                        priority = data.priority,
                        fulfillmentEnabled = data.fulfillmentEnabled,
                        fullAddress = data.fullAddress,
                        createdAt = data.createdAt.toString()
                    ),
                    "message" to "Stock location created successfully"
                ))
            }
            is WorkflowResult.Failure -> {
                ResponseEntity.badRequest().body(mapOf(
                    "message" to "Failed to create stock location",
                    "error" to (result.error.message ?: "Unknown error")
                ))
            }
        }
    }

    @Operation(summary = "Delete stock location")
    @DeleteMapping("/locations/{id}")
    fun deleteStockLocation(@PathVariable id: String): ResponseEntity<Any> {
        val location = stockLocationRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        location.deletedAt = java.time.Instant.now()
        stockLocationRepository.save(location)

        logger.info { "Deleted stock location: $id" }
        return ResponseEntity.ok(mapOf("message" to "Stock location deleted", "id" to id))
    }

    // =========================================================================
    // Inventory Movements (Stock History)
    // =========================================================================

    @Operation(summary = "List inventory movements", description = "Get paginated list of inventory movements/stock history")
    @GetMapping("/movements")
    fun listInventoryMovements(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) locationId: String?,
        @RequestParam(required = false) inventoryItemId: String?,
        @RequestParam(required = false) inventoryLevelId: String?,
        @RequestParam(required = false) sku: String?,
        @RequestParam(required = false) movementType: String?
    ): ResponseEntity<Map<String, Any>> {
        logger.info { "GET /admin/inventory/movements - offset=$offset, limit=$limit" }

        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtMost(100))

        val movementTypeEnum = movementType?.let {
            try {
                MovementType.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val page = inventoryMovementRepository.findByFilters(
            inventoryItemId = inventoryItemId,
            locationId = locationId,
            movementType = movementTypeEnum,
            sku = sku,
            pageable = pageable
        )

        val response = page.content.map { movement ->
            InventoryMovementResponse(
                id = movement.id,
                eventId = movement.eventId,
                inventoryItemId = movement.inventoryItemId,
                inventoryLevelId = movement.inventoryLevelId,
                locationId = movement.locationId,
                locationName = movement.locationName,
                sku = movement.sku,
                productTitle = movement.productTitle,
                movementType = movement.movementType.name,
                quantity = movement.quantity,
                previousQuantity = movement.previousQuantity,
                newQuantity = movement.newQuantity,
                reason = movement.reason,
                note = movement.note,
                referenceType = movement.referenceType,
                referenceId = movement.referenceId,
                performedBy = movement.performedBy,
                occurredAt = movement.occurredAt.toString()
            )
        }

        return ResponseEntity.ok(mapOf(
            "movements" to response,
            "count" to page.totalElements,
            "offset" to offset,
            "limit" to limit
        ))
    }

    @Operation(summary = "Get movements for inventory level", description = "Get stock history for a specific inventory level")
    @GetMapping("/levels/{id}/movements")
    fun getMovementsForLevel(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<Map<String, Any>> {
        logger.info { "GET /admin/inventory/levels/$id/movements" }

        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtMost(100))
        val page = inventoryMovementRepository.findByInventoryLevelIdOrderByOccurredAtDesc(id, pageable)

        val response = page.content.map { movement ->
            InventoryMovementResponse(
                id = movement.id,
                eventId = movement.eventId,
                inventoryItemId = movement.inventoryItemId,
                inventoryLevelId = movement.inventoryLevelId,
                locationId = movement.locationId,
                locationName = movement.locationName,
                sku = movement.sku,
                productTitle = movement.productTitle,
                movementType = movement.movementType.name,
                quantity = movement.quantity,
                previousQuantity = movement.previousQuantity,
                newQuantity = movement.newQuantity,
                reason = movement.reason,
                note = movement.note,
                referenceType = movement.referenceType,
                referenceId = movement.referenceId,
                performedBy = movement.performedBy,
                occurredAt = movement.occurredAt.toString()
            )
        }

        return ResponseEntity.ok(mapOf(
            "movements" to response,
            "count" to page.totalElements,
            "offset" to offset,
            "limit" to limit
        ))
    }

    @Operation(summary = "Get movement types", description = "Get all available movement types")
    @GetMapping("/movements/types")
    fun getMovementTypes(): ResponseEntity<Map<String, Any>> {
        val types = MovementType.entries.map { mapOf(
            "value" to it.name,
            "label" to it.name.replace("_", " ").lowercase()
                .replaceFirstChar { c -> c.uppercase() }
        )}
        return ResponseEntity.ok(mapOf("movement_types" to types))
    }
}
