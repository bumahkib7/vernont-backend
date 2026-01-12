package com.vernont.repository.review

import com.vernont.domain.review.ProductReviewStats
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import jakarta.persistence.LockModeType

@Repository
interface ProductReviewStatsRepository : JpaRepository<ProductReviewStats, String> {

    fun findByProductId(productId: String): ProductReviewStats?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProductReviewStats s WHERE s.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: String): ProductReviewStats?

    fun existsByProductId(productId: String): Boolean

    @Query("""
        SELECT s FROM ProductReviewStats s
        WHERE s.productId IN :productIds
    """)
    fun findByProductIds(@Param("productIds") productIds: List<String>): List<ProductReviewStats>

    @Query("""
        SELECT s FROM ProductReviewStats s
        WHERE s.averageRating >= :minRating
        ORDER BY s.averageRating DESC, s.totalReviews DESC
    """)
    fun findTopRatedProducts(@Param("minRating") minRating: java.math.BigDecimal): List<ProductReviewStats>
}
