package com.vernont.domain.review

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ProductReviewStats - Cached aggregated review statistics for a product
 *
 * This is denormalized data for performance - allows fast retrieval
 * of review stats without aggregation queries
 *
 * Updated via triggers or application events when reviews change
 */
@Entity
@Table(
    name = "product_review_stats",
    indexes = [
        Index(name = "idx_review_stats_product_id", columnList = "product_id", unique = true),
        Index(name = "idx_review_stats_avg_rating", columnList = "average_rating"),
        Index(name = "idx_review_stats_total_reviews", columnList = "total_reviews")
    ]
)
class ProductReviewStats : BaseEntity() {

    @Column(name = "product_id", nullable = false, unique = true)
    var productId: String = ""

    // ==================== Counts ====================

    @Column(name = "total_reviews", nullable = false)
    var totalReviews: Int = 0

    @Column(name = "approved_reviews", nullable = false)
    var approvedReviews: Int = 0

    @Column(name = "pending_reviews", nullable = false)
    var pendingReviews: Int = 0

    @Column(name = "verified_purchase_count", nullable = false)
    var verifiedPurchaseCount: Int = 0

    @Column(name = "with_images_count", nullable = false)
    var withImagesCount: Int = 0

    // ==================== Ratings ====================

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    var averageRating: BigDecimal = BigDecimal.ZERO

    @Column(name = "rating_sum", nullable = false)
    var ratingSum: Long = 0

    // Rating distribution (how many 5-star, 4-star, etc.)
    @Column(name = "five_star_count", nullable = false)
    var fiveStarCount: Int = 0

    @Column(name = "four_star_count", nullable = false)
    var fourStarCount: Int = 0

    @Column(name = "three_star_count", nullable = false)
    var threeStarCount: Int = 0

    @Column(name = "two_star_count", nullable = false)
    var twoStarCount: Int = 0

    @Column(name = "one_star_count", nullable = false)
    var oneStarCount: Int = 0

    // ==================== Percentages (pre-calculated) ====================

    @Column(name = "five_star_percent", nullable = false)
    var fiveStarPercent: Int = 0

    @Column(name = "four_star_percent", nullable = false)
    var fourStarPercent: Int = 0

    @Column(name = "three_star_percent", nullable = false)
    var threeStarPercent: Int = 0

    @Column(name = "two_star_percent", nullable = false)
    var twoStarPercent: Int = 0

    @Column(name = "one_star_percent", nullable = false)
    var oneStarPercent: Int = 0

    // ==================== Recommendation ====================

    @Column(name = "would_recommend_count", nullable = false)
    var wouldRecommendCount: Int = 0

    @Column(name = "recommendation_percent", nullable = false)
    var recommendationPercent: Int = 0

    // ==================== Methods ====================

    /**
     * Recalculate all statistics from raw counts
     */
    fun recalculate() {
        // Calculate average
        if (approvedReviews > 0) {
            averageRating = BigDecimal.valueOf(ratingSum)
                .divide(BigDecimal.valueOf(approvedReviews.toLong()), 2, RoundingMode.HALF_UP)
        } else {
            averageRating = BigDecimal.ZERO
        }

        // Calculate percentages
        if (approvedReviews > 0) {
            fiveStarPercent = (fiveStarCount * 100) / approvedReviews
            fourStarPercent = (fourStarCount * 100) / approvedReviews
            threeStarPercent = (threeStarCount * 100) / approvedReviews
            twoStarPercent = (twoStarCount * 100) / approvedReviews
            oneStarPercent = (oneStarCount * 100) / approvedReviews
            recommendationPercent = (wouldRecommendCount * 100) / approvedReviews
        } else {
            fiveStarPercent = 0
            fourStarPercent = 0
            threeStarPercent = 0
            twoStarPercent = 0
            oneStarPercent = 0
            recommendationPercent = 0
        }
    }

    /**
     * Add a review to stats
     */
    fun addReview(rating: Int, isVerified: Boolean, hasImages: Boolean, isApproved: Boolean) {
        totalReviews++

        if (isApproved) {
            approvedReviews++
            ratingSum += rating

            when (rating) {
                5 -> fiveStarCount++
                4 -> fourStarCount++
                3 -> threeStarCount++
                2 -> twoStarCount++
                1 -> oneStarCount++
            }

            // Consider 4-5 stars as "would recommend"
            if (rating >= 4) wouldRecommendCount++
        }

        if (isVerified) verifiedPurchaseCount++
        if (hasImages) withImagesCount++

        recalculate()
    }

    /**
     * Remove a review from stats
     */
    fun removeReview(rating: Int, isVerified: Boolean, hasImages: Boolean, wasApproved: Boolean) {
        totalReviews = maxOf(0, totalReviews - 1)

        if (wasApproved) {
            approvedReviews = maxOf(0, approvedReviews - 1)
            ratingSum = maxOf(0, ratingSum - rating)

            when (rating) {
                5 -> fiveStarCount = maxOf(0, fiveStarCount - 1)
                4 -> fourStarCount = maxOf(0, fourStarCount - 1)
                3 -> threeStarCount = maxOf(0, threeStarCount - 1)
                2 -> twoStarCount = maxOf(0, twoStarCount - 1)
                1 -> oneStarCount = maxOf(0, oneStarCount - 1)
            }

            if (rating >= 4) wouldRecommendCount = maxOf(0, wouldRecommendCount - 1)
        }

        if (isVerified) verifiedPurchaseCount = maxOf(0, verifiedPurchaseCount - 1)
        if (hasImages) withImagesCount = maxOf(0, withImagesCount - 1)

        recalculate()
    }

    /**
     * Get rating distribution as a map
     */
    fun getRatingDistribution(): Map<Int, RatingDistribution> {
        return mapOf(
            5 to RatingDistribution(5, fiveStarCount, fiveStarPercent),
            4 to RatingDistribution(4, fourStarCount, fourStarPercent),
            3 to RatingDistribution(3, threeStarCount, threeStarPercent),
            2 to RatingDistribution(2, twoStarCount, twoStarPercent),
            1 to RatingDistribution(1, oneStarCount, oneStarPercent)
        )
    }
}

/**
 * Rating distribution data
 */
data class RatingDistribution(
    val stars: Int,
    val count: Int,
    val percent: Int
)
