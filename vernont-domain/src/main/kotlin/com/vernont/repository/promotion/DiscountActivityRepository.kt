package com.vernont.repository.promotion

import com.vernont.domain.promotion.DiscountActivity
import com.vernont.domain.promotion.DiscountActivityType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface DiscountActivityRepository : JpaRepository<DiscountActivity, String> {

    fun findByPromotionId(promotionId: String, pageable: Pageable): Page<DiscountActivity>

    fun findByActivityType(activityType: DiscountActivityType, pageable: Pageable): Page<DiscountActivity>

    @Query("SELECT a FROM DiscountActivity a ORDER BY a.createdAt DESC")
    fun findRecentActivity(pageable: Pageable): Page<DiscountActivity>

    @Query("SELECT a FROM DiscountActivity a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
    fun findActivitySince(@Param("since") since: Instant, pageable: Pageable): Page<DiscountActivity>

    @Query("SELECT a FROM DiscountActivity a WHERE a.actorId = :actorId ORDER BY a.createdAt DESC")
    fun findByActorId(@Param("actorId") actorId: String, pageable: Pageable): Page<DiscountActivity>
}
