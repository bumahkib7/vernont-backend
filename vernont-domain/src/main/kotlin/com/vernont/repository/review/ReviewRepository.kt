package com.vernont.repository.review

import com.vernont.domain.review.Review
import com.vernont.domain.review.ReviewStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface ReviewRepository : JpaRepository<Review, String>, JpaSpecificationExecutor<Review> {

    // ==================== Basic Queries ====================

    fun findByIdAndDeletedAtIsNull(id: String): Review?

    @EntityGraph("Review.withCustomer")
    fun findWithCustomerByIdAndDeletedAtIsNull(id: String): Review?

    fun findByProductIdAndCustomerIdAndDeletedAtIsNull(productId: String, customerId: String): Review?

    fun existsByProductIdAndCustomerIdAndDeletedAtIsNull(productId: String, customerId: String): Boolean

    // ==================== Product Reviews ====================

    @EntityGraph("Review.withCustomer")
    fun findByProductIdAndStatusAndDeletedAtIsNull(
        productId: String,
        status: ReviewStatus,
        pageable: Pageable
    ): Page<Review>

    @EntityGraph("Review.withCustomer")
    fun findByProductIdAndStatusInAndDeletedAtIsNull(
        productId: String,
        statuses: List<ReviewStatus>,
        pageable: Pageable
    ): Page<Review>

    // ==================== Filtered Queries ====================

    @Query("""
        SELECT r FROM Review r
        WHERE r.productId = :productId
        AND r.status = :status
        AND r.deletedAt IS NULL
        AND (:rating IS NULL OR r.rating = :rating)
        AND (:verifiedOnly = false OR r.verifiedPurchase = true)
        AND (:withImagesOnly = false OR r.images IS NOT NULL AND SIZE(r.images) > 0)
    """)
    @EntityGraph("Review.withCustomer")
    fun findFilteredReviews(
        @Param("productId") productId: String,
        @Param("status") status: ReviewStatus,
        @Param("rating") rating: Int?,
        @Param("verifiedOnly") verifiedOnly: Boolean,
        @Param("withImagesOnly") withImagesOnly: Boolean,
        pageable: Pageable
    ): Page<Review>

    // ==================== Customer Reviews ====================

    @EntityGraph("Review.withCustomer")
    fun findByCustomerIdAndDeletedAtIsNull(customerId: String, pageable: Pageable): Page<Review>

    fun countByCustomerIdAndDeletedAtIsNull(customerId: String): Long

    // ==================== Statistics Queries ====================

    @Query("""
        SELECT COUNT(r) FROM Review r
        WHERE r.productId = :productId
        AND r.status = 'APPROVED'
        AND r.deletedAt IS NULL
    """)
    fun countApprovedByProductId(@Param("productId") productId: String): Long

    @Query("""
        SELECT COALESCE(AVG(r.rating), 0) FROM Review r
        WHERE r.productId = :productId
        AND r.status = 'APPROVED'
        AND r.deletedAt IS NULL
    """)
    fun getAverageRatingByProductId(@Param("productId") productId: String): Double

    @Query("""
        SELECT r.rating, COUNT(r) FROM Review r
        WHERE r.productId = :productId
        AND r.status = 'APPROVED'
        AND r.deletedAt IS NULL
        GROUP BY r.rating
    """)
    fun getRatingDistributionByProductId(@Param("productId") productId: String): List<Array<Any>>

    @Query("""
        SELECT COUNT(r) FROM Review r
        WHERE r.productId = :productId
        AND r.status = 'APPROVED'
        AND r.verifiedPurchase = true
        AND r.deletedAt IS NULL
    """)
    fun countVerifiedByProductId(@Param("productId") productId: String): Long

    @Query("""
        SELECT COUNT(r) FROM Review r
        WHERE r.productId = :productId
        AND r.status = 'APPROVED'
        AND r.images IS NOT NULL
        AND SIZE(r.images) > 0
        AND r.deletedAt IS NULL
    """)
    fun countWithImagesByProductId(@Param("productId") productId: String): Long

    // ==================== Moderation Queries ====================

    fun findByStatusAndDeletedAtIsNull(status: ReviewStatus, pageable: Pageable): Page<Review>

    @Query("""
        SELECT r FROM Review r
        WHERE r.status = 'PENDING'
        AND r.deletedAt IS NULL
        ORDER BY r.createdAt ASC
    """)
    fun findPendingReviews(pageable: Pageable): Page<Review>

    @Query("""
        SELECT r FROM Review r
        WHERE r.status = 'FLAGGED'
        OR r.reportCount >= :reportThreshold
        AND r.deletedAt IS NULL
        ORDER BY r.reportCount DESC
    """)
    fun findFlaggedReviews(@Param("reportThreshold") reportThreshold: Int, pageable: Pageable): Page<Review>

    // ==================== Bulk Operations ====================

    @Modifying
    @Query("UPDATE Review r SET r.helpfulCount = r.helpfulCount + 1 WHERE r.id = :reviewId")
    fun incrementHelpfulCount(@Param("reviewId") reviewId: String)

    @Modifying
    @Query("UPDATE Review r SET r.helpfulCount = r.helpfulCount - 1 WHERE r.id = :reviewId AND r.helpfulCount > 0")
    fun decrementHelpfulCount(@Param("reviewId") reviewId: String)

    @Modifying
    @Query("UPDATE Review r SET r.notHelpfulCount = r.notHelpfulCount + 1 WHERE r.id = :reviewId")
    fun incrementNotHelpfulCount(@Param("reviewId") reviewId: String)

    @Modifying
    @Query("UPDATE Review r SET r.notHelpfulCount = r.notHelpfulCount - 1 WHERE r.id = :reviewId AND r.notHelpfulCount > 0")
    fun decrementNotHelpfulCount(@Param("reviewId") reviewId: String)

    @Modifying
    @Query("UPDATE Review r SET r.reportCount = r.reportCount + 1 WHERE r.id = :reviewId")
    fun incrementReportCount(@Param("reviewId") reviewId: String)

    // ==================== Featured Reviews ====================

    @EntityGraph("Review.withCustomer")
    fun findByProductIdAndIsFeaturedTrueAndStatusAndDeletedAtIsNull(
        productId: String,
        status: ReviewStatus
    ): List<Review>

    // ==================== Search ====================

    @Query("""
        SELECT r FROM Review r
        WHERE r.productId = :productId
        AND r.status = 'APPROVED'
        AND r.deletedAt IS NULL
        AND (LOWER(r.title) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(r.content) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    @EntityGraph("Review.withCustomer")
    fun searchReviews(
        @Param("productId") productId: String,
        @Param("query") query: String,
        pageable: Pageable
    ): Page<Review>
}
