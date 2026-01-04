package com.vernont.repository.promotion

import com.vernont.domain.promotion.PromotionRule
import com.vernont.domain.promotion.PromotionRuleType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PromotionRuleRepository : JpaRepository<PromotionRule, String> {

    @EntityGraph(value = "PromotionRule.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<PromotionRule>

    @EntityGraph(value = "PromotionRule.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): PromotionRule?

    @EntityGraph(value = "PromotionRule.withPromotion", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithPromotionById(id: String): PromotionRule?

    fun findByPromotionId(promotionId: String): List<PromotionRule>

    fun findByPromotionIdAndDeletedAtIsNull(promotionId: String): List<PromotionRule>

    fun findByType(type: PromotionRuleType): List<PromotionRule>

    fun findByTypeAndDeletedAtIsNull(type: PromotionRuleType): List<PromotionRule>

    fun findByDeletedAtIsNull(): List<PromotionRule>

    @Query("SELECT pr FROM PromotionRule pr WHERE pr.promotion.id = :promotionId AND pr.type = :type AND pr.deletedAt IS NULL")
    fun findByPromotionIdAndType(@Param("promotionId") promotionId: String, @Param("type") type: PromotionRuleType): List<PromotionRule>

    @Query("SELECT COUNT(pr) FROM PromotionRule pr WHERE pr.promotion.id = :promotionId AND pr.deletedAt IS NULL")
    fun countByPromotionId(@Param("promotionId") promotionId: String): Long

    @Query("SELECT COUNT(pr) FROM PromotionRule pr WHERE pr.type = :type AND pr.deletedAt IS NULL")
    fun countByType(@Param("type") type: PromotionRuleType): Long
}
