package com.vernont.repository.returns

import com.vernont.domain.returns.ReturnItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReturnItemRepository : JpaRepository<ReturnItem, String> {

    fun findByIdAndDeletedAtIsNull(id: String): ReturnItem?

    @Query("SELECT ri FROM ReturnItem ri WHERE ri.returnRequest.id = :returnId AND ri.deletedAt IS NULL")
    fun findByReturnId(@Param("returnId") returnId: String): List<ReturnItem>

    fun findByOrderLineItemId(orderLineItemId: String): List<ReturnItem>

    fun findByOrderLineItemIdAndDeletedAtIsNull(orderLineItemId: String): List<ReturnItem>

    @Query("SELECT ri FROM ReturnItem ri WHERE ri.variantId = :variantId AND ri.deletedAt IS NULL")
    fun findByVariantId(@Param("variantId") variantId: String): List<ReturnItem>

    @Query("SELECT SUM(ri.quantity) FROM ReturnItem ri WHERE ri.orderLineItemId = :orderLineItemId AND ri.deletedAt IS NULL AND ri.returnRequest.status NOT IN ('REJECTED', 'CANCELED')")
    fun sumReturnedQuantityByOrderLineItemId(@Param("orderLineItemId") orderLineItemId: String): Int?

    fun findByDeletedAtIsNull(): List<ReturnItem>
}
