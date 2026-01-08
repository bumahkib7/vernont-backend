package com.vernont.repository.promotion

import com.vernont.domain.promotion.DiscountRedemption
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Repository
interface DiscountRedemptionRepository : JpaRepository<DiscountRedemption, String> {

    fun findByPromotionId(promotionId: String, pageable: Pageable): Page<DiscountRedemption>

    fun findByCustomerId(customerId: String, pageable: Pageable): Page<DiscountRedemption>

    fun findByOrderId(orderId: String): List<DiscountRedemption>

    fun countByPromotionId(promotionId: String): Long

    fun countByPromotionIdAndCustomerId(promotionId: String, customerId: String): Long

    @Query("SELECT SUM(r.discountAmount) FROM DiscountRedemption r WHERE r.promotion.id = :promotionId")
    fun sumDiscountAmountByPromotionId(@Param("promotionId") promotionId: String): BigDecimal?

    @Query("SELECT COUNT(r) FROM DiscountRedemption r WHERE r.redeemedAt >= :since")
    fun countRedemptionsSince(@Param("since") since: Instant): Long

    @Query("SELECT SUM(r.discountAmount) FROM DiscountRedemption r WHERE r.redeemedAt >= :since")
    fun sumDiscountAmountSince(@Param("since") since: Instant): BigDecimal?

    @Query("SELECT r FROM DiscountRedemption r ORDER BY r.redeemedAt DESC")
    fun findRecentRedemptions(pageable: Pageable): Page<DiscountRedemption>

    @Query("""
        SELECT r.promotion.id as promotionId, COUNT(r) as redemptionCount, SUM(r.discountAmount) as totalDiscount
        FROM DiscountRedemption r
        GROUP BY r.promotion.id
        ORDER BY COUNT(r) DESC
    """)
    fun findTopPerformingPromotions(pageable: Pageable): List<Map<String, Any>>
}
