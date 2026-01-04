package com.vernont.repository.product

import com.vernont.domain.product.ProductVariant
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductVariantRepository : JpaRepository<ProductVariant, String> {

    @EntityGraph(value = "ProductVariant.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<ProductVariant>

    @EntityGraph(value = "ProductVariant.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): ProductVariant?

    fun findBySku(sku: String): ProductVariant?

    fun findBySkuAndDeletedAtIsNull(sku: String): ProductVariant?

    fun findByBarcode(barcode: String): ProductVariant?

    fun findByBarcodeAndDeletedAtIsNull(barcode: String): ProductVariant?

    fun findByProductId(productId: String): List<ProductVariant>

    fun findByProductIdAndDeletedAtIsNull(productId: String): List<ProductVariant>

    @EntityGraph(value = "ProductVariant.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByProductIdAndDeletedAtIsNull(productId: String): List<ProductVariant>

    fun findByDeletedAtIsNull(): List<ProductVariant>

    fun existsBySku(sku: String): Boolean

    fun existsBySkuAndIdNot(sku: String, id: String): Boolean

    fun existsByBarcode(barcode: String): Boolean

    fun existsByBarcodeAndIdNot(barcode: String, id: String): Boolean

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.allowBackorder = true AND pv.deletedAt IS NULL")
    fun findBackorderableByProductId(@Param("productId") productId: String): List<ProductVariant>

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.manageInventory = false AND pv.deletedAt IS NULL")
    fun findUnmanagedInventoryByProductId(@Param("productId") productId: String): List<ProductVariant>

    @Query("SELECT COUNT(pv) FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.deletedAt IS NULL")
    fun countByProductId(@Param("productId") productId: String): Long

    @Query("SELECT pv FROM ProductVariant pv LEFT JOIN FETCH pv.product LEFT JOIN FETCH pv.prices WHERE pv.id = :id AND pv.deletedAt IS NULL")
    fun findWithProductById(@Param("id") id: String): ProductVariant?
}
