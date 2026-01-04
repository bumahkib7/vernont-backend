package com.vernont.repository.inventory

import com.vernont.domain.inventory.InventoryItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InventoryItemRepository : JpaRepository<InventoryItem, String> {

    /**
     * @deprecated Use findBySkuAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findBySkuAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findBySkuAndDeletedAtIsNull(sku)"))
    fun findBySku(sku: String): InventoryItem?

    fun findBySkuAndDeletedAtIsNull(sku: String): InventoryItem?

    fun findByIdAndDeletedAtIsNull(id: String): InventoryItem?

    fun findByDeletedAtIsNull(): List<InventoryItem>

    /**
     * @deprecated Use existsBySkuAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use existsBySkuAndDeletedAtIsNull to respect soft delete")
    fun existsBySku(sku: String): Boolean

    fun existsBySkuAndDeletedAtIsNull(sku: String): Boolean

    /**
     * @deprecated Use existsBySkuAndIdNotAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use existsBySkuAndIdNotAndDeletedAtIsNull to respect soft delete")
    fun existsBySkuAndIdNot(sku: String, id: String): Boolean

    fun existsBySkuAndIdNotAndDeletedAtIsNull(sku: String, id: String): Boolean

    @Query("SELECT ii FROM InventoryItem ii WHERE ii.requiresShipping = :requiresShipping AND ii.deletedAt IS NULL")
    fun findByRequiresShipping(@Param("requiresShipping") requiresShipping: Boolean): List<InventoryItem>

    @Query("SELECT ii FROM InventoryItem ii WHERE LOWER(ii.sku) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND ii.deletedAt IS NULL")
    fun searchBySku(@Param("searchTerm") searchTerm: String): List<InventoryItem>

    @Query("SELECT COUNT(ii) FROM InventoryItem ii WHERE ii.deletedAt IS NULL")
    fun countActiveInventoryItems(): Long

    @Query("""
        SELECT ii FROM InventoryItem ii
        WHERE ii.id = :id AND ii.deletedAt IS NULL
    """)
    fun findWithLevelsById(@Param("id") id: String): InventoryItem?

    @Query("""
        SELECT ii FROM InventoryItem ii
        WHERE ii.sku = :sku AND ii.deletedAt IS NULL
    """)
    fun findWithLevelsBySku(@Param("sku") sku: String): InventoryItem?

    @Query("""
        SELECT DISTINCT ii FROM InventoryItem ii
        LEFT JOIN ii.inventoryLevels il
        WHERE (il.stockedQuantity - il.reservedQuantity) < :threshold
        AND ii.deletedAt IS NULL
    """)
    fun findLowStockItems(@Param("threshold") threshold: Int): List<InventoryItem>
}
