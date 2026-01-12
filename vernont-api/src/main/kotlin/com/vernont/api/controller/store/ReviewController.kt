package com.vernont.api.controller.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.api.rate.RateLimited
import com.vernont.application.review.*
import com.vernont.domain.auth.getCurrentUserContext
import com.vernont.domain.review.ReportReason
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * Store Review Controller
 *
 * Comprehensive API for product reviews including:
 * - CRUD operations for reviews
 * - Voting (helpful/not helpful)
 * - Reporting inappropriate content
 * - Review statistics and filtering
 */
@RestController
@RequestMapping("/store")
@CrossOrigin(origins = ["http://localhost:8000", "http://localhost:9000", "http://localhost:3000"])
@Tag(name = "Store Reviews", description = "Product review endpoints for storefront")
class ReviewController(
    private val reviewService: ReviewService
) {

    // ==================== Product Reviews ====================

    /**
     * Get reviews for a product with filtering and sorting
     * GET /store/products/:productId/reviews
     */
    @Operation(summary = "Get product reviews")
    @GetMapping("/products/{productId}/reviews")
    fun getProductReviews(
        @PathVariable productId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(defaultValue = "MOST_HELPFUL") sort: String,
        @RequestParam(required = false) rating: Int?,
        @RequestParam(name = "verified_only", defaultValue = "false") verifiedOnly: Boolean,
        @RequestParam(name = "with_images", defaultValue = "false") withImagesOnly: Boolean,
        @RequestParam(required = false) search: String?,
        @RequestParam(name = "include_stats", defaultValue = "true") includeStats: Boolean
    ): ResponseEntity<Any> {
        logger.debug { "Getting reviews for product $productId, page=$page, sort=$sort" }

        return try {
            val sortBy = try {
                ReviewSortBy.valueOf(sort.uppercase())
            } catch (e: IllegalArgumentException) {
                ReviewSortBy.MOST_HELPFUL
            }

            val filters = ReviewFilters(
                rating = rating?.takeIf { it in 1..5 },
                verifiedOnly = verifiedOnly,
                withImagesOnly = withImagesOnly,
                searchQuery = search?.takeIf { it.isNotBlank() }
            )

            val result = reviewService.getProductReviews(
                productId = productId,
                filters = filters,
                sortBy = sortBy,
                page = page,
                size = size.coerceIn(1, 50),
                includeStats = includeStats
            )

            ResponseEntity.ok(mapOf(
                "reviews" to result.reviews.map { mapReviewResponse(it) },
                "page" to result.page,
                "size" to result.size,
                "total" to result.total,
                "stats" to result.stats?.let { mapStatsResponse(it) }
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting reviews for product $productId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to get reviews",
                    "code" to "GET_REVIEWS_FAILED"
                )
            ))
        }
    }

    /**
     * Get review statistics for a product
     * GET /store/products/:productId/reviews/stats
     */
    @Operation(summary = "Get product review statistics")
    @GetMapping("/products/{productId}/reviews/stats")
    fun getProductReviewStats(
        @PathVariable productId: String
    ): ResponseEntity<Any> {
        logger.debug { "Getting review stats for product $productId" }

        return try {
            val stats = reviewService.getProductStats(productId)
            ResponseEntity.ok(mapOf("stats" to mapStatsResponse(stats)))
        } catch (e: Exception) {
            logger.error(e) { "Error getting review stats for product $productId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to get review statistics",
                    "code" to "GET_STATS_FAILED"
                )
            ))
        }
    }

    /**
     * Get featured reviews for a product
     * GET /store/products/:productId/reviews/featured
     */
    @Operation(summary = "Get featured reviews for a product")
    @GetMapping("/products/{productId}/reviews/featured")
    fun getFeaturedReviews(
        @PathVariable productId: String
    ): ResponseEntity<Any> {
        logger.debug { "Getting featured reviews for product $productId" }

        return try {
            val reviews = reviewService.getFeaturedReviews(productId)
            ResponseEntity.ok(mapOf(
                "reviews" to reviews.map { mapReviewResponse(it) }
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting featured reviews for product $productId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to get featured reviews",
                    "code" to "GET_FEATURED_FAILED"
                )
            ))
        }
    }

    /**
     * Create a review for a product
     * POST /store/products/:productId/reviews
     *
     * Requires authentication
     */
    @Operation(summary = "Create a product review")
    @PostMapping("/products/{productId}/reviews")
    @RateLimited(
        keyPrefix = "review:create",
        perIp = true,
        perEmail = true,
        limit = 10,
        windowSeconds = 3600,
        failClosed = true
    )
    fun createReview(
        @PathVariable productId: String,
        @RequestBody request: StoreCreateReviewRequest
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required to submit a review",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val customerId = userContext.customerId
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to "Customer account required to submit a review",
                    "code" to "CUSTOMER_REQUIRED"
                )
            ))

        logger.info { "Creating review for product $productId by customer $customerId" }

        return try {
            val review = reviewService.createReview(
                customerId = customerId,
                request = CreateReviewRequest(
                    productId = productId,
                    rating = request.rating,
                    title = request.title,
                    content = request.content,
                    pros = request.pros,
                    cons = request.cons,
                    images = request.images?.map { img ->
                        ReviewImageRequest(
                            url = img.url,
                            thumbnailUrl = img.thumbnailUrl,
                            caption = img.caption
                        )
                    },
                    variantId = request.variantId
                )
            )

            ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
                "review" to mapReviewResponse(review)
            ))
        } catch (e: ReviewAlreadyExistsException) {
            logger.warn { "Customer $customerId already reviewed product $productId" }
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
                "error" to mapOf(
                    "message" to e.message,
                    "code" to "REVIEW_EXISTS"
                )
            ))
        } catch (e: ReviewNotAllowedException) {
            logger.warn { "Review not allowed: ${e.message}" }
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to e.message,
                    "code" to "REVIEW_NOT_ALLOWED"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error creating review for product $productId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to create review",
                    "code" to "CREATE_REVIEW_FAILED"
                )
            ))
        }
    }

    // ==================== Single Review Operations ====================

    /**
     * Get a single review
     * GET /store/reviews/:reviewId
     */
    @Operation(summary = "Get a review by ID")
    @GetMapping("/reviews/{reviewId}")
    fun getReview(
        @PathVariable reviewId: String
    ): ResponseEntity<Any> {
        logger.debug { "Getting review $reviewId" }

        return try {
            val review = reviewService.getReviewById(reviewId)
            ResponseEntity.ok(mapOf("review" to mapReviewResponse(review)))
        } catch (e: ReviewNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Review not found",
                    "code" to "REVIEW_NOT_FOUND"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to get review",
                    "code" to "GET_REVIEW_FAILED"
                )
            ))
        }
    }

    /**
     * Update a review
     * PATCH /store/reviews/:reviewId
     *
     * Requires authentication and ownership
     */
    @Operation(summary = "Update a review")
    @PatchMapping("/reviews/{reviewId}")
    @RateLimited(
        keyPrefix = "review:update",
        perIp = true,
        perEmail = true,
        limit = 20,
        windowSeconds = 3600,
        failClosed = true
    )
    fun updateReview(
        @PathVariable reviewId: String,
        @RequestBody request: StoreUpdateReviewRequest
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val customerId = userContext.customerId
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to "Customer account required",
                    "code" to "CUSTOMER_REQUIRED"
                )
            ))

        logger.info { "Updating review $reviewId by customer $customerId" }

        return try {
            val review = reviewService.updateReview(
                reviewId = reviewId,
                customerId = customerId,
                request = UpdateReviewRequest(
                    rating = request.rating,
                    title = request.title,
                    content = request.content,
                    pros = request.pros,
                    cons = request.cons,
                    images = request.images?.map { img ->
                        ReviewImageRequest(
                            url = img.url,
                            thumbnailUrl = img.thumbnailUrl,
                            caption = img.caption
                        )
                    }
                )
            )

            ResponseEntity.ok(mapOf("review" to mapReviewResponse(review)))
        } catch (e: ReviewNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Review not found",
                    "code" to "REVIEW_NOT_FOUND"
                )
            ))
        } catch (e: ReviewNotAllowedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to e.message,
                    "code" to "UPDATE_NOT_ALLOWED"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error updating review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to update review",
                    "code" to "UPDATE_REVIEW_FAILED"
                )
            ))
        }
    }

    /**
     * Delete a review
     * DELETE /store/reviews/:reviewId
     *
     * Requires authentication and ownership
     */
    @Operation(summary = "Delete a review")
    @DeleteMapping("/reviews/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: String
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val customerId = userContext.customerId
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to "Customer account required",
                    "code" to "CUSTOMER_REQUIRED"
                )
            ))

        logger.info { "Deleting review $reviewId by customer $customerId" }

        return try {
            reviewService.deleteReview(reviewId, customerId)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Review deleted successfully"
            ))
        } catch (e: ReviewNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Review not found",
                    "code" to "REVIEW_NOT_FOUND"
                )
            ))
        } catch (e: ReviewNotAllowedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to e.message,
                    "code" to "DELETE_NOT_ALLOWED"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error deleting review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to delete review",
                    "code" to "DELETE_REVIEW_FAILED"
                )
            ))
        }
    }

    // ==================== Voting ====================

    /**
     * Vote on a review (helpful/not helpful)
     * POST /store/reviews/:reviewId/vote
     *
     * Requires authentication
     */
    @Operation(summary = "Vote on a review")
    @PostMapping("/reviews/{reviewId}/vote")
    @RateLimited(
        keyPrefix = "review:vote",
        perIp = true,
        perEmail = true,
        limit = 100,
        windowSeconds = 3600,
        failClosed = true
    )
    fun voteOnReview(
        @PathVariable reviewId: String,
        @RequestBody request: StoreVoteRequest
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required to vote",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val customerId = userContext.customerId
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to "Customer account required to vote",
                    "code" to "CUSTOMER_REQUIRED"
                )
            ))

        logger.info { "Voting on review $reviewId by customer $customerId, helpful=${request.helpful}" }

        return try {
            val review = reviewService.voteHelpful(reviewId, customerId, request.helpful)
            ResponseEntity.ok(mapOf("review" to mapReviewResponse(review)))
        } catch (e: ReviewNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Review not found",
                    "code" to "REVIEW_NOT_FOUND"
                )
            ))
        } catch (e: ReviewNotAllowedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to e.message,
                    "code" to "VOTE_NOT_ALLOWED"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error voting on review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to vote on review",
                    "code" to "VOTE_FAILED"
                )
            ))
        }
    }

    // ==================== Reporting ====================

    /**
     * Report a review
     * POST /store/reviews/:reviewId/report
     *
     * Requires authentication
     */
    @Operation(summary = "Report a review")
    @PostMapping("/reviews/{reviewId}/report")
    @RateLimited(
        keyPrefix = "review:report",
        perIp = true,
        perEmail = true,
        limit = 20,
        windowSeconds = 3600,
        failClosed = true
    )
    fun reportReview(
        @PathVariable reviewId: String,
        @RequestBody request: StoreReportRequest
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required to report",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val customerId = userContext.customerId
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to "Customer account required to report",
                    "code" to "CUSTOMER_REQUIRED"
                )
            ))

        logger.info { "Reporting review $reviewId by customer $customerId, reason=${request.reason}" }

        return try {
            val reason = try {
                ReportReason.valueOf(request.reason.uppercase())
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().body(mapOf(
                    "error" to mapOf(
                        "message" to "Invalid report reason",
                        "code" to "INVALID_REASON"
                    )
                ))
            }

            reviewService.reportReview(
                reviewId = reviewId,
                customerId = customerId,
                reason = reason,
                description = request.description
            )

            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Report submitted successfully"
            ))
        } catch (e: ReviewNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Review not found",
                    "code" to "REVIEW_NOT_FOUND"
                )
            ))
        } catch (e: ReviewAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf(
                "error" to mapOf(
                    "message" to e.message,
                    "code" to "ALREADY_REPORTED"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error reporting review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to report review",
                    "code" to "REPORT_FAILED"
                )
            ))
        }
    }

    // ==================== Customer Reviews ====================

    /**
     * Get current customer's reviews
     * GET /store/customers/me/reviews
     *
     * Requires authentication
     */
    @Operation(summary = "Get current customer's reviews")
    @GetMapping("/customers/me/reviews")
    fun getMyReviews(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val customerId = userContext.customerId
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                "error" to mapOf(
                    "message" to "Customer account required",
                    "code" to "CUSTOMER_REQUIRED"
                )
            ))

        logger.debug { "Getting reviews for customer $customerId" }

        return try {
            val result = reviewService.getCustomerReviews(
                customerId = customerId,
                page = page,
                size = size.coerceIn(1, 50)
            )

            ResponseEntity.ok(mapOf(
                "reviews" to result.reviews.map { mapReviewResponse(it) },
                "page" to result.page,
                "size" to result.size,
                "total" to result.total
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting reviews for customer $customerId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to get your reviews",
                    "code" to "GET_REVIEWS_FAILED"
                )
            ))
        }
    }

    // ==================== Bulk Stats ====================

    /**
     * Get review statistics for multiple products
     * POST /store/reviews/stats/batch
     */
    @Operation(summary = "Get review statistics for multiple products")
    @PostMapping("/reviews/stats/batch")
    fun getBatchStats(
        @RequestBody request: StoreBatchStatsRequest
    ): ResponseEntity<Any> {
        if (request.productIds.isEmpty() || request.productIds.size > 50) {
            return ResponseEntity.badRequest().body(mapOf(
                "error" to mapOf(
                    "message" to "Must provide 1-50 product IDs",
                    "code" to "INVALID_REQUEST"
                )
            ))
        }

        logger.debug { "Getting batch stats for ${request.productIds.size} products" }

        return try {
            val statsMap = reviewService.getProductStatsForMultiple(request.productIds)
            ResponseEntity.ok(mapOf(
                "stats" to statsMap.mapValues { (_, stats) -> mapStatsResponse(stats) }
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting batch stats" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to get statistics",
                    "code" to "GET_STATS_FAILED"
                )
            ))
        }
    }

    // ==================== Helper Methods ====================

    private fun mapReviewResponse(review: ReviewResponse): Map<String, Any?> {
        return mapOf(
            "id" to review.id,
            "product_id" to review.productId,
            "customer_id" to review.customerId,
            "customer_name" to review.customerName,
            "customer_avatar" to review.customerAvatar,
            "rating" to review.rating,
            "title" to review.title,
            "content" to review.content,
            "pros" to review.pros,
            "cons" to review.cons,
            "images" to review.images?.map { img ->
                mapOf(
                    "url" to img.url,
                    "thumbnail_url" to img.thumbnailUrl,
                    "caption" to img.caption,
                    "sort_order" to img.sortOrder
                )
            },
            "verified_purchase" to review.verifiedPurchase,
            "variant_title" to review.variantTitle,
            "helpful_count" to review.helpfulCount,
            "not_helpful_count" to review.notHelpfulCount,
            "status" to review.status.name.lowercase(),
            "is_featured" to review.isFeatured,
            "is_edited" to review.isEdited,
            "admin_response" to review.adminResponse,
            "admin_response_at" to review.adminResponseAt,
            "created_at" to review.createdAt,
            "updated_at" to review.updatedAt
        )
    }

    private fun mapStatsResponse(stats: ReviewStatsResponse): Map<String, Any> {
        return mapOf(
            "product_id" to stats.productId,
            "average_rating" to stats.averageRating,
            "total_reviews" to stats.totalReviews,
            "verified_purchase_count" to stats.verifiedPurchaseCount,
            "with_images_count" to stats.withImagesCount,
            "recommendation_percent" to stats.recommendationPercent,
            "rating_distribution" to stats.ratingDistribution.map { dist ->
                mapOf(
                    "stars" to dist.stars,
                    "count" to dist.count,
                    "percent" to dist.percent
                )
            }
        )
    }
}

// ==================== Request DTOs ====================

data class StoreCreateReviewRequest(
    val rating: Int,
    val title: String,
    val content: String,
    val pros: List<String>? = null,
    val cons: List<String>? = null,
    val images: List<StoreReviewImageInput>? = null,
    @JsonProperty("variant_id")
    val variantId: String? = null
)

data class StoreUpdateReviewRequest(
    val rating: Int? = null,
    val title: String? = null,
    val content: String? = null,
    val pros: List<String>? = null,
    val cons: List<String>? = null,
    val images: List<StoreReviewImageInput>? = null
)

data class StoreReviewImageInput(
    val url: String,
    @JsonProperty("thumbnail_url")
    val thumbnailUrl: String? = null,
    val caption: String? = null
)

data class StoreVoteRequest(
    val helpful: Boolean
)

data class StoreReportRequest(
    val reason: String,
    val description: String? = null
)

data class StoreBatchStatsRequest(
    @JsonProperty("product_ids")
    val productIds: List<String>
)
