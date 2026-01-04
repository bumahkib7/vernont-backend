package com.vernont.repository.promotion

import com.vernont.domain.promotion.Promotion
import com.vernont.domain.promotion.PromotionType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PromotionRepository : JpaRepository<Promotion, String> {

    @EntityGraph(value = "Promotion.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Promotion>

    @EntityGraph(value = "Promotion.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Promotion?

    @EntityGraph(value = "Promotion.withRules", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithRulesById(id: String): Promotion?

    @EntityGraph(value = "Promotion.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findSummaryById(id: String): Promotion?

    fun findByCode(code: String): Promotion?

    fun findByCodeAndDeletedAtIsNull(code: String): Promotion?

    @Query("SELECT p FROM Promotion p WHERE p.code = :code AND p.isActive = true AND p.deletedAt IS NULL")
    fun findByCodeAndIsActiveTrueAndDeletedAtIsNull(@Param("code") code: String): Promotion?

    @EntityGraph(value = "Promotion.full", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT p FROM Promotion p WHERE p.code = :code AND p.isActive = true AND p.isDisabled = false AND p.deletedAt IS NULL")
    fun findValidByCode(@Param("code") code: String): Promotion?

    fun findByIsActive(isActive: Boolean): List<Promotion>

    fun findByIsActiveAndDeletedAtIsNull(isActive: Boolean): List<Promotion>

    fun findByType(type: PromotionType): List<Promotion>

    fun findByTypeAndDeletedAtIsNull(type: PromotionType): List<Promotion>

    fun findByDeletedAtIsNull(): List<Promotion>

    @EntityGraph(value = "Promotion.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByDeletedAtIsNull(): List<Promotion>

    @Query("SELECT p FROM Promotion p WHERE p.isActive = true AND p.isDisabled = false AND p.deletedAt IS NULL")
    fun findAllActive(): List<Promotion>

    @Query("SELECT p FROM Promotion p WHERE p.isActive = true AND p.isDisabled = false AND (p.startsAt IS NULL OR p.startsAt <= :now) AND (p.endsAt IS NULL OR p.endsAt > :now) AND p.deletedAt IS NULL")
    fun findAllCurrentlyValid(@Param("now") now: Instant = Instant.now()): List<Promotion>

    @Query("SELECT p FROM Promotion p WHERE p.isActive = true AND p.isDisabled = false AND (p.usageLimit IS NULL OR p.usageCount < p.usageLimit) AND p.deletedAt IS NULL")
    fun findAllWithinUsageLimit(): List<Promotion>

    @Query("SELECT p FROM Promotion p WHERE p.usageLimit IS NOT NULL AND p.usageCount >= p.usageLimit AND p.deletedAt IS NULL")
    fun findAllReachedUsageLimit(): List<Promotion>

    @Query("SELECT p FROM Promotion p WHERE p.endsAt IS NOT NULL AND p.endsAt < :now AND p.deletedAt IS NULL")
    fun findAllExpired(@Param("now") now: Instant = Instant.now()): List<Promotion>

    @Query("SELECT COUNT(p) FROM Promotion p WHERE p.isActive = true AND p.deletedAt IS NULL")
    fun countActivePromotions(): Long

    @Query("SELECT COUNT(p) FROM Promotion p WHERE p.deletedAt IS NULL")
    fun countAllPromotions(): Long

    fun existsByCode(code: String): Boolean

    fun existsByCodeAndIdNot(code: String, id: String): Boolean

    @Query("SELECT p FROM Promotion p WHERE LOWER(p.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND p.deletedAt IS NULL")
    fun searchByCode(@Param("searchTerm") searchTerm: String): List<Promotion>
}
