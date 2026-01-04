package com.vernont.repository.order

import com.vernont.domain.order.FulfillmentStatus
import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderStatus
import com.vernont.domain.order.PaymentStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OrderRepository : JpaRepository<Order, String> {

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Order>

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Order?

    @EntityGraph(value = "Order.withItems", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithItemsById(id: String): Order?

    @EntityGraph(value = "Order.withItems", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithItemsByIdAndDeletedAtIsNull(id: String): Order?

    @EntityGraph(value = "Order.withAddresses", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithAddressesById(id: String): Order?

    @EntityGraph(value = "Order.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findSummaryById(id: String): Order?

    @EntityGraph(value = "Order.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findSummaryByIdAndDeletedAtIsNull(id: String): Order?

    /**
     * @deprecated Use findByDisplayIdAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findByDisplayIdAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findByDisplayIdAndDeletedAtIsNull(displayId)"))
    fun findByDisplayId(displayId: Int): Order?

    fun findByDisplayIdAndDeletedAtIsNull(displayId: Int): Order?

    @EntityGraph(value = "Order.withItems", type = EntityGraph.EntityGraphType.LOAD)
    fun findByDisplayIdAndEmailAndDeletedAtIsNull(displayId: Int, email: String): Order?

    /**
     * @deprecated Use findByCustomerIdAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findByCustomerIdAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findByCustomerIdAndDeletedAtIsNull(customerId)"))
    fun findByCustomerId(customerId: String): List<Order>

    fun findByCustomerIdAndDeletedAtIsNull(customerId: String): List<Order>

    @EntityGraph(value = "Order.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByCustomerIdAndDeletedAtIsNull(customerId: String): List<Order>

    /**
     * @deprecated Use findByEmailAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findByEmailAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findByEmailAndDeletedAtIsNull(email)"))
    fun findByEmail(email: String): List<Order>

    fun findByEmailAndDeletedAtIsNull(email: String): List<Order>

    fun findByCartId(cartId: String): Order?

    fun findByCartIdAndDeletedAtIsNull(cartId: String): Order?

    /**
     * @deprecated Use findByStatusAndDeletedAtIsNull instead to respect soft delete
     */
    @Deprecated("Use findByStatusAndDeletedAtIsNull to respect soft delete",
        replaceWith = ReplaceWith("findByStatusAndDeletedAtIsNull(status)"))
    fun findByStatus(status: OrderStatus): List<Order>

    fun findByStatusAndDeletedAtIsNull(status: OrderStatus): List<Order>

    fun findByFulfillmentStatus(fulfillmentStatus: FulfillmentStatus): List<Order>

    fun findByFulfillmentStatusAndDeletedAtIsNull(fulfillmentStatus: FulfillmentStatus): List<Order>

    fun findByPaymentStatus(paymentStatus: PaymentStatus): List<Order>

    fun findByPaymentStatusAndDeletedAtIsNull(paymentStatus: PaymentStatus): List<Order>

    fun findByRegionId(regionId: String): List<Order>

    fun findByRegionIdAndDeletedAtIsNull(regionId: String): List<Order>

    fun findByDeletedAtIsNull(): List<Order>

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.paymentStatus = :paymentStatus AND o.deletedAt IS NULL")
    fun findByStatusAndPaymentStatus(@Param("status") status: OrderStatus, @Param("paymentStatus") paymentStatus: PaymentStatus): List<Order>

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.fulfillmentStatus = :fulfillmentStatus AND o.deletedAt IS NULL")
    fun findByStatusAndFulfillmentStatus(@Param("status") status: OrderStatus, @Param("fulfillmentStatus") fulfillmentStatus: FulfillmentStatus): List<Order>

    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId AND o.status = :status AND o.deletedAt IS NULL")
    fun findByCustomerIdAndStatus(@Param("customerId") customerId: String, @Param("status") status: OrderStatus): List<Order>

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.deletedAt IS NULL")
    fun countByStatus(@Param("status") status: OrderStatus): Long

    @Query("SELECT COUNT(o) FROM Order o WHERE o.paymentStatus = :paymentStatus AND o.deletedAt IS NULL")
    fun countByPaymentStatus(@Param("paymentStatus") paymentStatus: PaymentStatus): Long

    @Query("SELECT COUNT(o) FROM Order o WHERE o.fulfillmentStatus = :fulfillmentStatus AND o.deletedAt IS NULL")
    fun countByFulfillmentStatus(@Param("fulfillmentStatus") fulfillmentStatus: FulfillmentStatus): Long

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customerId = :customerId AND o.deletedAt IS NULL")
    fun countByCustomerId(@Param("customerId") customerId: String): Long

    /**
     * Find all orders belonging to a customer by customerId OR matching their email.
     * This allows customers to see orders they placed as guests before registering.
     */
    @EntityGraph(value = "Order.summary", type = EntityGraph.EntityGraphType.LOAD)
    @Query("""
        SELECT o FROM Order o
        WHERE o.deletedAt IS NULL
        AND (o.customerId = :customerId OR o.email = :email)
        ORDER BY o.createdAt DESC
    """)
    fun findAllByCustomerIdOrEmailAndDeletedAtIsNull(
        @Param("customerId") customerId: String,
        @Param("email") email: String
    ): List<Order>

    /**
     * Count orders by customerId OR email
     */
    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.deletedAt IS NULL
        AND (o.customerId = :customerId OR o.email = :email)
    """)
    fun countByCustomerIdOrEmail(
        @Param("customerId") customerId: String,
        @Param("email") email: String
    ): Long

    /**
     * Link guest orders to a customer by updating customerId where email matches
     * and customerId is null
     */
    @Query("""
        UPDATE Order o SET o.customerId = :customerId
        WHERE o.email = :email
        AND o.customerId IS NULL
        AND o.deletedAt IS NULL
    """)
    @org.springframework.data.jpa.repository.Modifying
    fun linkGuestOrdersToCustomer(
        @Param("customerId") customerId: String,
        @Param("email") email: String
    ): Int

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.status = :status AND o.deletedAt IS NULL")
    fun sumTotalByStatus(@Param("status") status: OrderStatus): java.math.BigDecimal?

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate AND o.deletedAt IS NULL")
    fun findByCreatedAtBetween(@Param("startDate") startDate: Instant, @Param("endDate") endDate: Instant): List<Order>

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.status = :status AND o.createdAt BETWEEN :startDate AND :endDate AND o.deletedAt IS NULL")
    fun sumTotalByStatusAndCreatedAtBetween(
        @Param("status") status: OrderStatus,
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant
    ): java.math.BigDecimal?

    @Query("SELECT COALESCE(MAX(o.displayId), 0) + 1 FROM Order o")
    fun getNextDisplayId(): Int
}
