package com.vernont.repository.order

import com.vernont.domain.order.OrderEvent
import com.vernont.domain.order.OrderEventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderEventRepository : JpaRepository<OrderEvent, String> {

    fun findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAsc(orderId: String): List<OrderEvent>

    fun findByOrderIdAndDeletedAtIsNullOrderByCreatedAtDesc(orderId: String): List<OrderEvent>

    fun findByOrderIdAndEventTypeAndDeletedAtIsNull(orderId: String, eventType: OrderEventType): List<OrderEvent>

    fun findFirstByOrderIdAndEventTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
        orderId: String,
        eventType: OrderEventType
    ): OrderEvent?

    @Query("""
        SELECT e FROM OrderEvent e
        WHERE e.orderId = :orderId
        AND e.eventType IN :eventTypes
        AND e.deletedAt IS NULL
        ORDER BY e.createdAt ASC
    """)
    fun findByOrderIdAndEventTypesOrderByCreatedAtAsc(
        @Param("orderId") orderId: String,
        @Param("eventTypes") eventTypes: List<OrderEventType>
    ): List<OrderEvent>

    @Query("""
        SELECT e FROM OrderEvent e
        WHERE e.orderId = :orderId
        AND e.deletedAt IS NULL
        ORDER BY e.createdAt DESC
        LIMIT :limit
    """)
    fun findRecentByOrderId(
        @Param("orderId") orderId: String,
        @Param("limit") limit: Int
    ): List<OrderEvent>

    @Query("""
        SELECT e FROM OrderEvent e
        WHERE e.orderId IN :orderIds
        AND e.deletedAt IS NULL
        ORDER BY e.createdAt DESC
    """)
    fun findByOrderIdsOrderByCreatedAtDesc(
        @Param("orderIds") orderIds: List<String>
    ): List<OrderEvent>

    fun countByOrderIdAndDeletedAtIsNull(orderId: String): Long

    fun existsByOrderIdAndEventTypeAndDeletedAtIsNull(orderId: String, eventType: OrderEventType): Boolean
}
