package com.vernont.repository.marketing

import com.vernont.domain.marketing.UserBrandInterest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserBrandInterestRepository : JpaRepository<UserBrandInterest, String> {

    fun findByUserIdAndBrandId(userId: String, brandId: String): UserBrandInterest?

    @Query("""
        SELECT ubi FROM UserBrandInterest ubi
        WHERE ubi.userId = :userId
        ORDER BY ubi.interestScore DESC, ubi.lastInteractionAt DESC
    """)
    fun findTopBrandsByUserId(userId: String): List<UserBrandInterest>

    @Query("""
        SELECT ubi FROM UserBrandInterest ubi
        WHERE ubi.brand.id = :brandId
        ORDER BY ubi.interestScore DESC, ubi.lastInteractionAt DESC
    """)
    fun findTopUsersByBrandId(brandId: String): List<UserBrandInterest>
}
