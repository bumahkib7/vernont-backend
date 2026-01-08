package com.vernont.repository.inventory

import com.vernont.domain.inventory.InventoryMovement
import com.vernont.domain.inventory.MovementType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface InventoryMovementRepository : JpaRepository<InventoryMovement, String> {

    fun findByEventId(eventId: String): InventoryMovement?

    fun existsByEventId(eventId: String): Boolean

    fun findByInventoryItemIdOrderByOccurredAtDesc(inventoryItemId: String): List<InventoryMovement>

    fun findByInventoryItemIdOrderByOccurredAtDesc(inventoryItemId: String, pageable: Pageable): Page<InventoryMovement>

    fun findByInventoryLevelIdOrderByOccurredAtDesc(inventoryLevelId: String): List<InventoryMovement>

    fun findByInventoryLevelIdOrderByOccurredAtDesc(inventoryLevelId: String, pageable: Pageable): Page<InventoryMovement>

    fun findByLocationIdOrderByOccurredAtDesc(locationId: String): List<InventoryMovement>

    fun findByLocationIdOrderByOccurredAtDesc(locationId: String, pageable: Pageable): Page<InventoryMovement>

    fun findByMovementTypeOrderByOccurredAtDesc(movementType: MovementType): List<InventoryMovement>

    fun findByMovementTypeOrderByOccurredAtDesc(movementType: MovementType, pageable: Pageable): Page<InventoryMovement>

    @Query("""
        SELECT m FROM InventoryMovement m
        WHERE m.occurredAt >= :startDate AND m.occurredAt <= :endDate
        ORDER BY m.occurredAt DESC
    """)
    fun findByDateRange(
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant
    ): List<InventoryMovement>

    @Query("""
        SELECT m FROM InventoryMovement m
        WHERE m.occurredAt >= :startDate AND m.occurredAt <= :endDate
        ORDER BY m.occurredAt DESC
    """)
    fun findByDateRange(
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant,
        pageable: Pageable
    ): Page<InventoryMovement>

    @Query("""
        SELECT m FROM InventoryMovement m
        WHERE (:inventoryItemId IS NULL OR m.inventoryItemId = :inventoryItemId)
        AND (:locationId IS NULL OR m.locationId = :locationId)
        AND (:movementType IS NULL OR m.movementType = :movementType)
        AND (:sku IS NULL OR m.sku LIKE %:sku%)
        ORDER BY m.occurredAt DESC
    """)
    fun findByFilters(
        @Param("inventoryItemId") inventoryItemId: String?,
        @Param("locationId") locationId: String?,
        @Param("movementType") movementType: MovementType?,
        @Param("sku") sku: String?,
        pageable: Pageable
    ): Page<InventoryMovement>

    @Query("""
        SELECT m FROM InventoryMovement m
        WHERE m.deletedAt IS NULL
        ORDER BY m.occurredAt DESC
    """)
    fun findAllOrderByOccurredAtDesc(pageable: Pageable): Page<InventoryMovement>

    fun findByReferenceTypeAndReferenceIdOrderByOccurredAtDesc(
        referenceType: String,
        referenceId: String
    ): List<InventoryMovement>

    @Query("""
        SELECT m FROM InventoryMovement m
        WHERE m.sku = :sku
        ORDER BY m.occurredAt DESC
    """)
    fun findBySkuOrderByOccurredAtDesc(@Param("sku") sku: String, pageable: Pageable): Page<InventoryMovement>

    @Query("""
        SELECT COUNT(m) FROM InventoryMovement m
        WHERE m.movementType = :movementType
        AND m.occurredAt >= :since
    """)
    fun countByMovementTypeSince(
        @Param("movementType") movementType: MovementType,
        @Param("since") since: Instant
    ): Long

    @Query("""
        SELECT SUM(m.quantity) FROM InventoryMovement m
        WHERE m.inventoryItemId = :inventoryItemId
        AND m.locationId = :locationId
        AND m.occurredAt >= :since
    """)
    fun sumQuantityByItemAndLocationSince(
        @Param("inventoryItemId") inventoryItemId: String,
        @Param("locationId") locationId: String,
        @Param("since") since: Instant
    ): Long?
}
