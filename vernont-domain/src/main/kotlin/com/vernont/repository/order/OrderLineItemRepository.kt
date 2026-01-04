package com.vernont.repository.order

import com.vernont.domain.order.OrderLineItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderLineItemRepository : JpaRepository<OrderLineItem, String> {

    fun findByOrderId(orderId: String): List<OrderLineItem>

    fun findByOrderIdAndDeletedAtIsNull(orderId: String): List<OrderLineItem>

    fun findByVariantId(variantId: String): List<OrderLineItem>

    fun findByVariantIdAndDeletedAtIsNull(variantId: String): List<OrderLineItem>

    fun findByIdAndDeletedAtIsNull(id: String): OrderLineItem?

    fun findByDeletedAtIsNull(): List<OrderLineItem>

    @Query("SELECT oli FROM OrderLineItem oli WHERE oli.order.id = :orderId AND oli.variantId = :variantId AND oli.deletedAt IS NULL")
    fun findByOrderIdAndVariantId(@Param("orderId") orderId: String, @Param("variantId") variantId: String): OrderLineItem?

    @Query("SELECT COUNT(oli) FROM OrderLineItem oli WHERE oli.order.id = :orderId AND oli.deletedAt IS NULL")
    fun countByOrderId(@Param("orderId") orderId: String): Long

    @Query("SELECT SUM(oli.quantity) FROM OrderLineItem oli WHERE oli.variantId = :variantId AND oli.deletedAt IS NULL")
    fun sumQuantityByVariantId(@Param("variantId") variantId: String): Long?

    @Query("SELECT oli FROM OrderLineItem oli WHERE oli.order.customerId = :customerId AND oli.deletedAt IS NULL")
    fun findByCustomerId(@Param("customerId") customerId: String): List<OrderLineItem>
}
