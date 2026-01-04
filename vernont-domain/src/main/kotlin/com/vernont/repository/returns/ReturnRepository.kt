package com.vernont.repository.returns

import com.vernont.domain.returns.Return
import com.vernont.domain.returns.ReturnStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReturnRepository : JpaRepository<Return, String> {

    @EntityGraph(value = "Return.withItems", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Return>

    @EntityGraph(value = "Return.withItems", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Return?

    fun findByOrderId(orderId: String): List<Return>

    fun findByOrderIdAndDeletedAtIsNull(orderId: String): List<Return>

    @EntityGraph(value = "Return.withItems", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithItemsByOrderIdAndDeletedAtIsNull(orderId: String): List<Return>

    fun findByCustomerId(customerId: String): List<Return>

    fun findByCustomerIdAndDeletedAtIsNull(customerId: String): List<Return>

    @EntityGraph(value = "Return.withItems", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByCustomerIdAndDeletedAtIsNull(customerId: String): List<Return>

    fun findByStatus(status: ReturnStatus): List<Return>

    fun findByStatusAndDeletedAtIsNull(status: ReturnStatus): List<Return>

    fun findByDeletedAtIsNull(): List<Return>

    // Paginated queries
    fun findByDeletedAtIsNull(pageable: Pageable): Page<Return>

    fun findByStatusAndDeletedAtIsNull(status: ReturnStatus, pageable: Pageable): Page<Return>

    @Query("""
        SELECT r FROM Return r
        WHERE r.deletedAt IS NULL
        AND (:status IS NULL OR r.status = :status)
        ORDER BY r.requestedAt DESC
    """)
    fun findAllWithFilters(
        @Param("status") status: ReturnStatus?,
        pageable: Pageable
    ): Page<Return>

    @Query("""
        SELECT r FROM Return r
        WHERE r.deletedAt IS NULL
        AND (r.customerId = :customerId OR r.customerEmail = :email)
        ORDER BY r.requestedAt DESC
    """)
    fun findAllByCustomerIdOrEmailAndDeletedAtIsNull(
        @Param("customerId") customerId: String,
        @Param("email") email: String
    ): List<Return>

    @Query("""
        SELECT r FROM Return r
        WHERE r.deletedAt IS NULL
        AND (r.customerId = :customerId OR r.customerEmail = :email)
        ORDER BY r.requestedAt DESC
    """)
    fun findAllByCustomerIdOrEmailAndDeletedAtIsNull(
        @Param("customerId") customerId: String,
        @Param("email") email: String,
        pageable: Pageable
    ): Page<Return>

    // Count queries
    @Query("SELECT COUNT(r) FROM Return r WHERE r.status = :status AND r.deletedAt IS NULL")
    fun countByStatus(@Param("status") status: ReturnStatus): Long

    @Query("SELECT COUNT(r) FROM Return r WHERE r.customerId = :customerId AND r.deletedAt IS NULL")
    fun countByCustomerId(@Param("customerId") customerId: String): Long

    @Query("SELECT COUNT(r) FROM Return r WHERE r.orderId = :orderId AND r.deletedAt IS NULL")
    fun countByOrderId(@Param("orderId") orderId: String): Long

    // Check for existing returns on order line items
    @Query("""
        SELECT ri.orderLineItemId FROM Return r
        JOIN r.items ri
        WHERE r.orderId = :orderId
        AND r.status NOT IN ('REJECTED', 'CANCELED')
        AND r.deletedAt IS NULL
    """)
    fun findReturnedLineItemIdsByOrderId(@Param("orderId") orderId: String): List<String>

    // Search by order display ID or customer email
    @Query("""
        SELECT r FROM Return r
        WHERE r.deletedAt IS NULL
        AND (
            CAST(r.orderDisplayId AS string) LIKE %:query%
            OR LOWER(r.customerEmail) LIKE LOWER(CONCAT('%', :query, '%'))
        )
        ORDER BY r.requestedAt DESC
    """)
    fun searchByDisplayIdOrEmail(
        @Param("query") query: String,
        pageable: Pageable
    ): Page<Return>
}
