package com.vernont.repository.inventory

import com.vernont.domain.inventory.InventoryLevel
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InventoryLevelRepository : JpaRepository<InventoryLevel, String> {

    @EntityGraph(value = "InventoryLevel.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<InventoryLevel>

    @EntityGraph(value = "InventoryLevel.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): InventoryLevel?

    @EntityGraph(value = "InventoryLevel.withItem", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithItemById(id: String): InventoryLevel?

    @EntityGraph(value = "InventoryLevel.withLocation", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithLocationById(id: String): InventoryLevel?

    fun findByInventoryItemId(inventoryItemId: String): List<InventoryLevel>

    fun findByInventoryItemIdAndDeletedAtIsNull(inventoryItemId: String): List<InventoryLevel>

    fun findByLocationId(locationId: String): List<InventoryLevel>

    fun findByLocationIdAndDeletedAtIsNull(locationId: String): List<InventoryLevel>

    @Query("SELECT il FROM InventoryLevel il WHERE il.location.id = :locationId AND il.availableQuantity > :minQuantity AND il.deletedAt IS NULL")
    fun findByLocationIdAndAvailableQuantityGreaterThan(
        @Param("locationId") locationId: String,
        @Param("minQuantity") minQuantity: Int
    ): List<InventoryLevel>

    @Query("SELECT il FROM InventoryLevel il WHERE il.inventoryItem.id = :inventoryItemId AND il.location.id = :locationId AND il.deletedAt IS NULL")
    fun findByInventoryItemIdAndLocationId(
        @Param("inventoryItemId") inventoryItemId: String,
        @Param("locationId") locationId: String
    ): InventoryLevel?

    fun findByDeletedAtIsNull(): List<InventoryLevel>

    @Query("SELECT il FROM InventoryLevel il WHERE il.availableQuantity > 0 AND il.deletedAt IS NULL")
    fun findAllWithAvailableStock(): List<InventoryLevel>

    @Query("SELECT il FROM InventoryLevel il WHERE il.availableQuantity <= 0 AND il.deletedAt IS NULL")
    fun findAllOutOfStock(): List<InventoryLevel>

    @Query("SELECT il FROM InventoryLevel il WHERE il.availableQuantity <= :threshold AND il.deletedAt IS NULL")
    fun findLowStock(@Param("threshold") threshold: Int): List<InventoryLevel>

    @Query("SELECT SUM(il.availableQuantity) FROM InventoryLevel il WHERE il.inventoryItem.id = :inventoryItemId AND il.deletedAt IS NULL")
    fun sumAvailableQuantityByInventoryItemId(@Param("inventoryItemId") inventoryItemId: String): Long?

    @Query("SELECT SUM(il.stockedQuantity) FROM InventoryLevel il WHERE il.inventoryItem.id = :inventoryItemId AND il.deletedAt IS NULL")
    fun sumStockedQuantityByInventoryItemId(@Param("inventoryItemId") inventoryItemId: String): Long?

    @Query("SELECT COUNT(il) FROM InventoryLevel il WHERE il.inventoryItem.id = :inventoryItemId AND il.deletedAt IS NULL")
    fun countByInventoryItemId(@Param("inventoryItemId") inventoryItemId: String): Long

    @Query("SELECT COUNT(il) FROM InventoryLevel il WHERE il.location.id = :locationId AND il.deletedAt IS NULL")
    fun countByLocationId(@Param("locationId") locationId: String): Long

    @Query("""
        SELECT il FROM InventoryLevel il 
        JOIN il.inventoryItem ii 
        JOIN ProductVariantInventoryItem pvii ON ii.id = pvii.inventoryItemId 
        WHERE pvii.variant.id = :variantId AND il.deletedAt IS NULL
    """)
    fun findByVariantId(@Param("variantId") variantId: String): List<InventoryLevel>
}
