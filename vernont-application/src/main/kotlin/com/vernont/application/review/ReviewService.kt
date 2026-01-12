package com.vernont.application.review

import com.vernont.domain.review.*
import com.vernont.repository.customer.CustomerRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.product.ProductRepository
import com.vernont.repository.review.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

private val logger = KotlinLogging.logger {}

// ==================== DTOs ====================

data class CreateReviewRequest(
    val productId: String,
    val rating: Int,
    val title: String,
    val content: String,
    val pros: List<String>? = null,
    val cons: List<String>? = null,
    val images: List<ReviewImageRequest>? = null,
    val variantId: String? = null
)

data class ReviewImageRequest(
    val url: String,
    val thumbnailUrl: String? = null,
    val caption: String? = null
)

data class UpdateReviewRequest(
    val rating: Int? = null,
    val title: String? = null,
    val content: String? = null,
    val pros: List<String>? = null,
    val cons: List<String>? = null,
    val images: List<ReviewImageRequest>? = null
)

data class ReviewResponse(
    val id: String,
    val productId: String,
    val customerId: String,
    val customerName: String,
    val customerAvatar: String?,
    val rating: Int,
    val title: String,
    val content: String,
    val pros: List<String>?,
    val cons: List<String>?,
    val images: List<ReviewImage>?,
    val verifiedPurchase: Boolean,
    val variantTitle: String?,
    val helpfulCount: Int,
    val notHelpfulCount: Int,
    val status: ReviewStatus,
    val isFeatured: Boolean,
    val isEdited: Boolean,
    val adminResponse: String?,
    val adminResponseAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(review: Review): ReviewResponse = ReviewResponse(
            id = review.id,
            productId = review.productId ?: "",
            customerId = review.customerId ?: "",
            customerName = review.customerName,
            customerAvatar = review.customerAvatar,
            rating = review.rating,
            title = review.title,
            content = review.content,
            pros = review.pros,
            cons = review.cons,
            images = review.images,
            verifiedPurchase = review.verifiedPurchase,
            variantTitle = review.variantTitle,
            helpfulCount = review.helpfulCount,
            notHelpfulCount = review.notHelpfulCount,
            status = review.status,
            isFeatured = review.isFeatured,
            isEdited = review.isEdited,
            adminResponse = review.adminResponse,
            adminResponseAt = review.adminResponseAt,
            createdAt = review.createdAt,
            updatedAt = review.updatedAt
        )
    }
}

data class ReviewStatsResponse(
    val productId: String,
    val averageRating: BigDecimal,
    val totalReviews: Int,
    val verifiedPurchaseCount: Int,
    val withImagesCount: Int,
    val recommendationPercent: Int,
    val ratingDistribution: List<RatingDistributionItem>
)

data class RatingDistributionItem(
    val stars: Int,
    val count: Int,
    val percent: Int
)

data class ReviewListResponse(
    val reviews: List<ReviewResponse>,
    val page: Int,
    val size: Int,
    val total: Long,
    val stats: ReviewStatsResponse?
)

enum class ReviewSortBy {
    NEWEST,
    OLDEST,
    HIGHEST_RATED,
    LOWEST_RATED,
    MOST_HELPFUL
}

data class ReviewFilters(
    val rating: Int? = null,
    val verifiedOnly: Boolean = false,
    val withImagesOnly: Boolean = false,
    val searchQuery: String? = null
)

// ==================== Exceptions ====================

class ReviewNotFoundException(message: String) : RuntimeException(message)
class ReviewAlreadyExistsException(message: String) : RuntimeException(message)
class ReviewNotAllowedException(message: String) : RuntimeException(message)

// ==================== Service ====================

@Service
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val reviewVoteRepository: ReviewVoteRepository,
    private val reviewStatsRepository: ProductReviewStatsRepository,
    private val reviewReportRepository: ReviewReportRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository
) {

    // ==================== Create Review ====================

    @Transactional
    fun createReview(customerId: String, request: CreateReviewRequest): ReviewResponse {
        logger.info { "Creating review for product ${request.productId} by customer $customerId" }

        // Validate product exists
        val product = productRepository.findByIdAndDeletedAtIsNull(request.productId)
            ?: throw ReviewNotAllowedException("Product not found: ${request.productId}")

        // Check if customer already reviewed this product
        if (reviewRepository.existsByProductIdAndCustomerIdAndDeletedAtIsNull(request.productId, customerId)) {
            throw ReviewAlreadyExistsException("You have already reviewed this product")
        }

        // Get customer info
        val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
            ?: throw ReviewNotAllowedException("Customer not found")

        // Check if verified purchase
        val verifiedPurchase = checkVerifiedPurchase(customerId, request.productId, request.variantId)
        val orderInfo = if (verifiedPurchase) {
            getOrderInfo(customerId, request.productId, request.variantId)
        } else null

        // Create review
        val review = Review().apply {
            this.product = product
            this.customer = customer
            this.customerName = buildCustomerName(customer)
            this.customerAvatar = null // Could be added later
            this.rating = request.rating.coerceIn(1, 5)
            this.title = request.title.trim()
            this.content = request.content.trim()
            this.pros = request.pros?.filter { it.isNotBlank() }?.toMutableList()
            this.cons = request.cons?.filter { it.isNotBlank() }?.toMutableList()
            this.images = request.images?.mapIndexed { index, img ->
                ReviewImage(
                    url = img.url,
                    thumbnailUrl = img.thumbnailUrl,
                    caption = img.caption,
                    sortOrder = index
                )
            }?.toMutableList()
            this.verifiedPurchase = verifiedPurchase
            this.orderId = orderInfo?.first
            this.variantId = request.variantId ?: orderInfo?.second
            this.variantTitle = orderInfo?.third
            // Auto-approve verified purchases, otherwise pending moderation
            this.status = if (verifiedPurchase) ReviewStatus.APPROVED else ReviewStatus.PENDING
        }

        val savedReview = reviewRepository.save(review)

        // Update stats if approved
        if (savedReview.status == ReviewStatus.APPROVED) {
            updateProductStats(request.productId)
        }

        logger.info { "Created review ${savedReview.id} for product ${request.productId}" }
        return ReviewResponse.from(savedReview)
    }

    // ==================== Update Review ====================

    @Transactional
    fun updateReview(reviewId: String, customerId: String, request: UpdateReviewRequest): ReviewResponse {
        logger.info { "Updating review $reviewId by customer $customerId" }

        val review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")

        // Check ownership
        if (review.customerId != customerId) {
            throw ReviewNotAllowedException("You can only edit your own reviews")
        }

        val oldRating = review.rating
        val wasApproved = review.status == ReviewStatus.APPROVED

        // Update fields
        request.rating?.let { review.rating = it.coerceIn(1, 5) }
        request.title?.let { review.title = it.trim() }
        request.content?.let { review.content = it.trim() }
        request.pros?.let { review.pros = it.filter { p -> p.isNotBlank() }.toMutableList() }
        request.cons?.let { review.cons = it.filter { c -> c.isNotBlank() }.toMutableList() }
        request.images?.let {
            review.images = it.mapIndexed { index, img ->
                ReviewImage(
                    url = img.url,
                    thumbnailUrl = img.thumbnailUrl,
                    caption = img.caption,
                    sortOrder = index
                )
            }.toMutableList()
        }

        review.isEdited = true
        review.editedAt = Instant.now()

        // If non-verified review was edited, set back to pending
        if (!review.verifiedPurchase && review.status == ReviewStatus.APPROVED) {
            review.status = ReviewStatus.PENDING
        }

        val savedReview = reviewRepository.save(review)

        // Update stats if rating changed or status changed
        if (oldRating != savedReview.rating || wasApproved != (savedReview.status == ReviewStatus.APPROVED)) {
            updateProductStats(savedReview.productId!!)
        }

        logger.info { "Updated review $reviewId" }
        return ReviewResponse.from(savedReview)
    }

    // ==================== Delete Review ====================

    @Transactional
    fun deleteReview(reviewId: String, customerId: String) {
        logger.info { "Deleting review $reviewId by customer $customerId" }

        val review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")

        // Check ownership
        if (review.customerId != customerId) {
            throw ReviewNotAllowedException("You can only delete your own reviews")
        }

        val productId = review.productId!!
        val wasApproved = review.status == ReviewStatus.APPROVED

        review.softDelete()
        reviewRepository.save(review)

        // Update stats
        if (wasApproved) {
            updateProductStats(productId)
        }

        logger.info { "Deleted review $reviewId" }
    }

    // ==================== Get Reviews ====================

    @Transactional(readOnly = true)
    fun getProductReviews(
        productId: String,
        filters: ReviewFilters = ReviewFilters(),
        sortBy: ReviewSortBy = ReviewSortBy.MOST_HELPFUL,
        page: Int = 0,
        size: Int = 10,
        includeStats: Boolean = true
    ): ReviewListResponse {
        logger.debug { "Getting reviews for product $productId, page=$page, size=$size, sortBy=$sortBy, filters=$filters" }

        val sort = when (sortBy) {
            ReviewSortBy.NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt")
            ReviewSortBy.OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt")
            ReviewSortBy.HIGHEST_RATED -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))
            ReviewSortBy.LOWEST_RATED -> Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))
            ReviewSortBy.MOST_HELPFUL -> Sort.by(Sort.Direction.DESC, "helpfulCount").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        }

        val pageable = PageRequest.of(page, size, sort)

        val reviewsPage = if (filters.searchQuery != null) {
            reviewRepository.searchReviews(productId, filters.searchQuery, pageable)
        } else {
            reviewRepository.findFilteredReviews(
                productId = productId,
                status = ReviewStatus.APPROVED,
                rating = filters.rating,
                verifiedOnly = filters.verifiedOnly,
                withImagesOnly = filters.withImagesOnly,
                pageable = pageable
            )
        }

        val stats = if (includeStats) getProductStats(productId) else null

        return ReviewListResponse(
            reviews = reviewsPage.content.map { ReviewResponse.from(it) },
            page = page,
            size = size,
            total = reviewsPage.totalElements,
            stats = stats
        )
    }

    @Transactional(readOnly = true)
    fun getCustomerReviews(customerId: String, page: Int = 0, size: Int = 10): ReviewListResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val reviewsPage = reviewRepository.findByCustomerIdAndDeletedAtIsNull(customerId, pageable)

        return ReviewListResponse(
            reviews = reviewsPage.content.map { ReviewResponse.from(it) },
            page = page,
            size = size,
            total = reviewsPage.totalElements,
            stats = null
        )
    }

    @Transactional(readOnly = true)
    fun getReviewById(reviewId: String): ReviewResponse {
        val review = reviewRepository.findWithCustomerByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")
        return ReviewResponse.from(review)
    }

    // ==================== Stats ====================

    @Transactional(readOnly = true)
    fun getProductStats(productId: String): ReviewStatsResponse {
        val stats = reviewStatsRepository.findByProductId(productId)

        return if (stats != null) {
            ReviewStatsResponse(
                productId = productId,
                averageRating = stats.averageRating,
                totalReviews = stats.approvedReviews,
                verifiedPurchaseCount = stats.verifiedPurchaseCount,
                withImagesCount = stats.withImagesCount,
                recommendationPercent = stats.recommendationPercent,
                ratingDistribution = listOf(
                    RatingDistributionItem(5, stats.fiveStarCount, stats.fiveStarPercent),
                    RatingDistributionItem(4, stats.fourStarCount, stats.fourStarPercent),
                    RatingDistributionItem(3, stats.threeStarCount, stats.threeStarPercent),
                    RatingDistributionItem(2, stats.twoStarCount, stats.twoStarPercent),
                    RatingDistributionItem(1, stats.oneStarCount, stats.oneStarPercent)
                )
            )
        } else {
            // Calculate on-the-fly if no cached stats
            calculateProductStats(productId)
        }
    }

    @Transactional(readOnly = true)
    fun getProductStatsForMultiple(productIds: List<String>): Map<String, ReviewStatsResponse> {
        val statsList = reviewStatsRepository.findByProductIds(productIds)
        return statsList.associate { stats ->
            stats.productId to ReviewStatsResponse(
                productId = stats.productId,
                averageRating = stats.averageRating,
                totalReviews = stats.approvedReviews,
                verifiedPurchaseCount = stats.verifiedPurchaseCount,
                withImagesCount = stats.withImagesCount,
                recommendationPercent = stats.recommendationPercent,
                ratingDistribution = listOf(
                    RatingDistributionItem(5, stats.fiveStarCount, stats.fiveStarPercent),
                    RatingDistributionItem(4, stats.fourStarCount, stats.fourStarPercent),
                    RatingDistributionItem(3, stats.threeStarCount, stats.threeStarPercent),
                    RatingDistributionItem(2, stats.twoStarCount, stats.twoStarPercent),
                    RatingDistributionItem(1, stats.oneStarCount, stats.oneStarPercent)
                )
            )
        }
    }

    // ==================== Voting ====================

    @Transactional
    fun voteHelpful(reviewId: String, customerId: String, isHelpful: Boolean): ReviewResponse {
        logger.info { "Voting ${if (isHelpful) "helpful" else "not helpful"} on review $reviewId by customer $customerId" }

        val review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")

        // Can't vote on own review
        if (review.customerId == customerId) {
            throw ReviewNotAllowedException("You cannot vote on your own review")
        }

        val existingVote = reviewVoteRepository.findByReviewIdAndCustomerId(reviewId, customerId)
        val newVoteType = if (isHelpful) VoteType.HELPFUL else VoteType.NOT_HELPFUL

        if (existingVote != null) {
            if (existingVote.voteType == newVoteType) {
                // Remove vote (toggle off)
                reviewVoteRepository.delete(existingVote)
                if (isHelpful) {
                    reviewRepository.decrementHelpfulCount(reviewId)
                } else {
                    reviewRepository.decrementNotHelpfulCount(reviewId)
                }
            } else {
                // Change vote
                val oldVoteType = existingVote.voteType
                existingVote.voteType = newVoteType
                reviewVoteRepository.save(existingVote)

                // Update counts
                if (oldVoteType == VoteType.HELPFUL) {
                    reviewRepository.decrementHelpfulCount(reviewId)
                    reviewRepository.incrementNotHelpfulCount(reviewId)
                } else {
                    reviewRepository.decrementNotHelpfulCount(reviewId)
                    reviewRepository.incrementHelpfulCount(reviewId)
                }
            }
        } else {
            // New vote
            val vote = ReviewVote().apply {
                this.review = review
                this.customerId = customerId
                this.voteType = newVoteType
            }
            reviewVoteRepository.save(vote)

            if (isHelpful) {
                reviewRepository.incrementHelpfulCount(reviewId)
            } else {
                reviewRepository.incrementNotHelpfulCount(reviewId)
            }
        }

        // Refresh and return
        return ReviewResponse.from(reviewRepository.findByIdAndDeletedAtIsNull(reviewId)!!)
    }

    // ==================== Reporting ====================

    @Transactional
    fun reportReview(reviewId: String, customerId: String, reason: ReportReason, description: String?) {
        logger.info { "Reporting review $reviewId by customer $customerId for reason $reason" }

        val review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")

        // Check if already reported by this customer
        if (reviewReportRepository.existsByReviewIdAndCustomerId(reviewId, customerId)) {
            throw ReviewAlreadyExistsException("You have already reported this review")
        }

        val report = ReviewReport().apply {
            this.review = review
            this.customerId = customerId
            this.reason = reason
            this.description = description
        }

        reviewReportRepository.save(report)
        reviewRepository.incrementReportCount(reviewId)

        // Auto-flag if too many reports
        val reportCount = reviewReportRepository.countByReviewId(reviewId)
        if (reportCount >= 3 && review.status == ReviewStatus.APPROVED) {
            review.status = ReviewStatus.FLAGGED
            reviewRepository.save(review)
        }

        logger.info { "Reported review $reviewId" }
    }

    // ==================== Admin Operations ====================

    @Transactional
    fun moderateReview(reviewId: String, adminId: String, approved: Boolean, note: String?): ReviewResponse {
        logger.info { "Moderating review $reviewId, approved=$approved by admin $adminId" }

        val review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")

        val wasApproved = review.status == ReviewStatus.APPROVED

        review.status = if (approved) ReviewStatus.APPROVED else ReviewStatus.REJECTED
        review.moderatedAt = Instant.now()
        review.moderatedBy = adminId
        review.moderationNote = note

        val savedReview = reviewRepository.save(review)

        // Update stats if status changed
        if (wasApproved != approved) {
            updateProductStats(savedReview.productId!!)
        }

        logger.info { "Moderated review $reviewId, new status: ${savedReview.status}" }
        return ReviewResponse.from(savedReview)
    }

    @Transactional
    fun addAdminResponse(reviewId: String, adminId: String, response: String): ReviewResponse {
        logger.info { "Adding admin response to review $reviewId" }

        val review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")

        review.adminResponse = response.trim()
        review.adminResponseAt = Instant.now()
        review.adminResponseBy = adminId

        return ReviewResponse.from(reviewRepository.save(review))
    }

    @Transactional
    fun setFeatured(reviewId: String, featured: Boolean): ReviewResponse {
        val review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")

        review.isFeatured = featured
        return ReviewResponse.from(reviewRepository.save(review))
    }

    @Transactional(readOnly = true)
    fun getPendingReviews(page: Int = 0, size: Int = 20): Page<ReviewResponse> {
        val pageable = PageRequest.of(page, size)
        return reviewRepository.findPendingReviews(pageable).map { ReviewResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun getFlaggedReviews(page: Int = 0, size: Int = 20): Page<ReviewResponse> {
        val pageable = PageRequest.of(page, size)
        return reviewRepository.findFlaggedReviews(3, pageable).map { ReviewResponse.from(it) }
    }

    // ==================== Admin Delete (hard permission check elsewhere) ====================

    @Transactional
    fun adminDeleteReview(reviewId: String, adminId: String, reason: String?) {
        logger.info { "Admin $adminId deleting review $reviewId" }

        val review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            ?: throw ReviewNotFoundException("Review not found: $reviewId")

        val productId = review.productId!!
        val wasApproved = review.status == ReviewStatus.APPROVED

        review.softDelete(adminId)
        review.moderationNote = reason
        reviewRepository.save(review)

        if (wasApproved) {
            updateProductStats(productId)
        }

        logger.info { "Admin deleted review $reviewId" }
    }

    // ==================== Featured Reviews ====================

    @Transactional(readOnly = true)
    fun getFeaturedReviews(productId: String): List<ReviewResponse> {
        return reviewRepository.findByProductIdAndIsFeaturedTrueAndStatusAndDeletedAtIsNull(
            productId, ReviewStatus.APPROVED
        ).map { ReviewResponse.from(it) }
    }

    // ==================== Helper Methods ====================

    private fun checkVerifiedPurchase(customerId: String, productId: String, variantId: String?): Boolean {
        return try {
            orderRepository.existsByCustomerIdAndProductId(customerId, productId)
        } catch (e: Exception) {
            logger.warn(e) { "Error checking verified purchase" }
            false
        }
    }

    private fun getOrderInfo(customerId: String, productId: String, variantId: String?): Triple<String, String, String>? {
        return try {
            val ordersPage = orderRepository.findFirstByCustomerIdAndProductId(
                customerId, productId, PageRequest.of(0, 1)
            )
            val order = ordersPage.content.firstOrNull()
            if (order != null) {
                val lineItem = order.items.firstOrNull { it.productId == productId }
                if (lineItem != null) {
                    Triple(order.id, lineItem.variantId ?: "", lineItem.title)
                } else null
            } else null
        } catch (e: Exception) {
            logger.warn(e) { "Error getting order info" }
            null
        }
    }

    private fun buildCustomerName(customer: com.vernont.domain.customer.Customer): String {
        val firstName = customer.firstName ?: ""
        val lastName = customer.lastName?.firstOrNull()?.toString() ?: ""
        return if (firstName.isNotBlank()) {
            if (lastName.isNotBlank()) "$firstName $lastName." else firstName
        } else {
            customer.email.substringBefore("@")
        }
    }

    @Transactional
    fun updateProductStats(productId: String) {
        logger.debug { "Updating product stats for $productId" }

        val stats = reviewStatsRepository.findByProductIdForUpdate(productId)
            ?: ProductReviewStats().apply { this.productId = productId }

        // Reset counts
        stats.totalReviews = 0
        stats.approvedReviews = 0
        stats.ratingSum = 0
        stats.fiveStarCount = 0
        stats.fourStarCount = 0
        stats.threeStarCount = 0
        stats.twoStarCount = 0
        stats.oneStarCount = 0
        stats.verifiedPurchaseCount = 0
        stats.withImagesCount = 0
        stats.wouldRecommendCount = 0

        // Get all approved reviews and calculate
        val distribution = reviewRepository.getRatingDistributionByProductId(productId)
        for (row in distribution) {
            val rating = (row[0] as Number).toInt()
            val count = (row[1] as Number).toInt()

            stats.approvedReviews += count
            stats.ratingSum += (rating * count).toLong()

            when (rating) {
                5 -> stats.fiveStarCount = count
                4 -> stats.fourStarCount = count
                3 -> stats.threeStarCount = count
                2 -> stats.twoStarCount = count
                1 -> stats.oneStarCount = count
            }

            if (rating >= 4) stats.wouldRecommendCount += count
        }

        stats.totalReviews = reviewRepository.countApprovedByProductId(productId).toInt()
        stats.verifiedPurchaseCount = reviewRepository.countVerifiedByProductId(productId).toInt()
        stats.withImagesCount = reviewRepository.countWithImagesByProductId(productId).toInt()

        stats.recalculate()
        reviewStatsRepository.save(stats)

        logger.debug { "Updated product stats for $productId: avg=${stats.averageRating}, total=${stats.approvedReviews}" }
    }

    private fun calculateProductStats(productId: String): ReviewStatsResponse {
        val totalReviews = reviewRepository.countApprovedByProductId(productId).toInt()
        val avgRating = reviewRepository.getAverageRatingByProductId(productId)
        val distribution = reviewRepository.getRatingDistributionByProductId(productId)
        val verifiedCount = reviewRepository.countVerifiedByProductId(productId).toInt()
        val withImagesCount = reviewRepository.countWithImagesByProductId(productId).toInt()

        val ratingDist = mutableMapOf<Int, Int>()
        var wouldRecommend = 0
        for (row in distribution) {
            val rating = (row[0] as Number).toInt()
            val count = (row[1] as Number).toInt()
            ratingDist[rating] = count
            if (rating >= 4) wouldRecommend += count
        }

        val recommendPercent = if (totalReviews > 0) (wouldRecommend * 100) / totalReviews else 0

        return ReviewStatsResponse(
            productId = productId,
            averageRating = BigDecimal.valueOf(avgRating).setScale(2, java.math.RoundingMode.HALF_UP),
            totalReviews = totalReviews,
            verifiedPurchaseCount = verifiedCount,
            withImagesCount = withImagesCount,
            recommendationPercent = recommendPercent,
            ratingDistribution = (5 downTo 1).map { stars ->
                val count = ratingDist[stars] ?: 0
                val percent = if (totalReviews > 0) (count * 100) / totalReviews else 0
                RatingDistributionItem(stars, count, percent)
            }
        )
    }
}
