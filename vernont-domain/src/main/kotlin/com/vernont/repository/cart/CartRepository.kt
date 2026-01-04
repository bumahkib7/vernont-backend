package com.vernont.repository.cart

import com.vernont.domain.cart.Cart
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

@Repository
interface CartRepository : JpaRepository<Cart, String> {

    /**
     * Find cart by ID with payment sessions loaded
     */
    @Query("""
        SELECT c FROM Cart c 
        LEFT JOIN FETCH c.items ci
        WHERE c.id = :id 
        AND c.deletedAt IS NULL
    """)
    fun findByIdWithSessions(@Param("id") id: String): Optional<Cart>

    /**
     * Find cart by ID with items loaded
     */
    @Query("""
        SELECT c FROM Cart c 
        LEFT JOIN FETCH c.items ci
        WHERE c.id = :id 
        AND c.deletedAt IS NULL
    """)
    fun findByIdWithItems(@Param("id") id: String): Optional<Cart>

    @EntityGraph(value = "Cart.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): Optional<Cart>

    @EntityGraph(value = "Cart.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Cart?

    @EntityGraph(value = "Cart.withItems", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithItemsById(id: String): Cart?

    @EntityGraph(value = "Cart.withItems", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithItemsByIdAndDeletedAtIsNull(id: String): Cart?

    @EntityGraph(value = "Cart.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findSummaryById(id: String): Cart?

    fun findByCustomerId(customerId: String): Cart?

    fun findByCustomerIdAndDeletedAtIsNull(customerId: String): Cart?

    @EntityGraph(value = "Cart.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithFullDetailsByCustomerId(customerId: String): Cart?

    fun findByEmail(email: String): Cart?

    fun findByEmailAndDeletedAtIsNull(email: String): Cart?

    fun findByCustomerIdAndCompletedAtIsNull(customerId: String): Cart?

    @EntityGraph(value = "Cart.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findActiveByCustomerId(customerId: String): Cart?

    fun findByDeletedAtIsNull(): List<Cart>

    @Query("SELECT c FROM Cart c WHERE c.customerId = :customerId AND c.completedAt IS NULL AND c.deletedAt IS NULL")
    fun findActiveCartByCustomerId( @Param("customerId") customerId: String): Cart?

    @Query("SELECT c FROM Cart c WHERE c.email = :email AND c.completedAt IS NULL AND c.deletedAt IS NULL")
    fun findActiveCartByEmail( @Param("email") email: String): Cart?

    @Query("SELECT c FROM Cart c WHERE c.completedAt IS NOT NULL AND c.deletedAt IS NULL")
    fun findAllCompleted(): List<Cart>

    @Query("SELECT c FROM Cart c WHERE c.completedAt IS NULL AND c.deletedAt IS NULL")
    fun findAllActive(): List<Cart>

    @Query("SELECT COUNT(c) FROM Cart c WHERE c.customerId = :customerId AND c.deletedAt IS NULL")
    fun countByCustomerId( @Param("customerId") customerId: String): Long

    @Query("SELECT COUNT(c) FROM Cart c WHERE c.completedAt IS NULL AND c.deletedAt IS NULL")
    fun countActiveCarts(): Long

    @Query("SELECT c FROM Cart c WHERE c.regionId = :regionId AND c.deletedAt IS NULL")
    fun findByRegionId( @Param("regionId") regionId: String): List<Cart>

    @Query("SELECT c FROM Cart c WHERE c.updatedAt < :threshold AND c.completedAt IS NULL AND c.deletedAt IS NULL")
    fun findAbandonedCarts( @Param("threshold") threshold: Instant): List<Cart>
}
