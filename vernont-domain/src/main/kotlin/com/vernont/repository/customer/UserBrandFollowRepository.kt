package com.vernont.repository.customer

import com.vernont.domain.customer.UserBrandFollow
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface UserBrandFollowRepository : JpaRepository<UserBrandFollow, String> {
    @EntityGraph(value = "UserBrandFollow.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByUserIdAndDeletedAtIsNull(userId: String): List<UserBrandFollow>

    fun findByUserIdAndBrandIdAndDeletedAtIsNull(userId: String, brandId: String): UserBrandFollow?
}
