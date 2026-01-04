package com.vernont.repository.promotion

import com.vernont.domain.promotion.Discount
import com.vernont.domain.promotion.DiscountType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface DiscountRepository : JpaRepository<Discount, String> {

    @EntityGraph(value = "Discount.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Discount>

    @EntityGraph(value = "Discount.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Discount?

    @EntityGraph(value = "Discount.withPromotion", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithPromotionById(id: String): Discount?

    fun findByOrderId(orderId: String): List<Discount>

    fun findByOrderIdAndDeletedAtIsNull(orderId: String): List<Discount>

    fun findByCartId(cartId: String): List<Discount>

    fun findByCartIdAndDeletedAtIsNull(cartId: String): List<Discount>

    fun findByPromotionId(promotionId: String): List<Discount>

    fun findByPromotionIdAndDeletedAtIsNull(promotionId: String): List<Discount>

    fun findByCode(code: String): List<Discount>

    fun findByCodeAndDeletedAtIsNull(code: String): List<Discount>

    fun findByType(type: DiscountType): List<Discount>

    fun findByTypeAndDeletedAtIsNull(type: DiscountType): List<Discount>

    fun findByDeletedAtIsNull(): List<Discount>

    @Query("SELECT d FROM Discount d WHERE d.orderId = :orderId AND d.isApplied = true AND d.deletedAt IS NULL")
    fun findAppliedByOrderId(@Param("orderId") orderId: String): List<Discount>

    @Query("SELECT d FROM Discount d WHERE d.cartId = :cartId AND d.isApplied = true AND d.deletedAt IS NULL")
    fun findAppliedByCartId(@Param("cartId") cartId: String): List<Discount>

    @Query("SELECT d FROM Discount d WHERE d.cartId = :cartId AND d.code = :code AND d.deletedAt IS NULL")
    fun findByCartIdAndCode(@Param("cartId") cartId: String, @Param("code") code: String): Discount?

    @Query("SELECT d FROM Discount d WHERE d.orderId = :orderId AND d.code = :code AND d.deletedAt IS NULL")
    fun findByOrderIdAndCode(@Param("orderId") orderId: String, @Param("code") code: String): Discount?

    @Query("SELECT SUM(d.amount) FROM Discount d WHERE d.orderId = :orderId AND d.isApplied = true AND d.deletedAt IS NULL")
    fun sumAmountByOrderId(@Param("orderId") orderId: String): java.math.BigDecimal?

    @Query("SELECT SUM(d.amount) FROM Discount d WHERE d.cartId = :cartId AND d.isApplied = true AND d.deletedAt IS NULL")
    fun sumAmountByCartId(@Param("cartId") cartId: String): java.math.BigDecimal?

    @Query("SELECT COUNT(d) FROM Discount d WHERE d.orderId = :orderId AND d.deletedAt IS NULL")
    fun countByOrderId(@Param("orderId") orderId: String): Long

    @Query("SELECT COUNT(d) FROM Discount d WHERE d.cartId = :cartId AND d.deletedAt IS NULL")
    fun countByCartId(@Param("cartId") cartId: String): Long

    @Query("SELECT COUNT(d) FROM Discount d WHERE d.promotion.id = :promotionId AND d.deletedAt IS NULL")
    fun countByPromotionId(@Param("promotionId") promotionId: String): Long

    @Query("SELECT COUNT(d) FROM Discount d WHERE d.promotion.id = :promotionId AND d.isApplied = true AND d.deletedAt IS NULL")
    fun countAppliedByPromotionId(@Param("promotionId") promotionId: String): Long
}
