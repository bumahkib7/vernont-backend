package com.vernont.repository.customer

import com.vernont.domain.customer.UserFavorite
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserFavoriteRepository : JpaRepository<UserFavorite, String> {

    @EntityGraph(value = "UserFavorite.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByUserIdAndDeletedAtIsNull(userId: String): List<UserFavorite>

    fun findByUserIdAndProductIdAndDeletedAtIsNull(userId: String, productId: String): UserFavorite?

    fun existsByUserIdAndProductIdAndDeletedAtIsNull(userId: String, productId: String): Boolean

    @EntityGraph(value = "UserFavorite.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByAlertEnabledTrueAndDeletedAtIsNull(): List<UserFavorite>
}
