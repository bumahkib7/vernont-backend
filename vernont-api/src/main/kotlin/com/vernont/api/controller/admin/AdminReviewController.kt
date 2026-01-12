package com.vernont.api.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.application.review.*
import com.vernont.domain.auth.getCurrentUserContext
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * Admin Review Controller
 *
 * Administrative endpoints for review moderation including:
 * - View pending/flagged reviews
 * - Approve/reject reviews
 * - Add admin responses
 * - Feature reviews
 * - Delete reviews
 */
@RestController
@RequestMapping("/admin/reviews")
@Tag(name = "Admin Reviews", description = "Review moderation endpoints for admin")
class AdminReviewController(
    private val reviewService: ReviewService
) {

    // ==================== Moderation Queue ====================

    /**
     * Get pending reviews for moderation
     * GET /admin/reviews/pending
     */
    @Operation(summary = "Get pending reviews")
    @GetMapping("/pending")
    fun getPendingReviews(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        logger.info { "Getting pending reviews, page=$page, size=$size" }

        return try {
            val reviews = reviewService.getPendingReviews(page, size.coerceIn(1, 100))
            ResponseEntity.ok(mapOf(
                "reviews" to reviews.content.map { mapReviewResponse(it) },
                "page" to page,
                "size" to size,
                "total" to reviews.totalElements
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting pending reviews" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to get pending reviews",
                    "code" to "GET_PENDING_FAILED"
                )
            ))
        }
    }

    /**
     * Get flagged reviews for moderation
     * GET /admin/reviews/flagged
     */
    @Operation(summary = "Get flagged reviews")
    @GetMapping("/flagged")
    fun getFlaggedReviews(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        logger.info { "Getting flagged reviews, page=$page, size=$size" }

        return try {
            val reviews = reviewService.getFlaggedReviews(page, size.coerceIn(1, 100))
            ResponseEntity.ok(mapOf(
                "reviews" to reviews.content.map { mapReviewResponse(it) },
                "page" to page,
                "size" to size,
                "total" to reviews.totalElements
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting flagged reviews" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to get flagged reviews",
                    "code" to "GET_FLAGGED_FAILED"
                )
            ))
        }
    }

    // ==================== Moderation Actions ====================

    /**
     * Moderate a review (approve/reject)
     * POST /admin/reviews/:reviewId/moderate
     */
    @Operation(summary = "Moderate a review")
    @PostMapping("/{reviewId}/moderate")
    fun moderateReview(
        @PathVariable reviewId: String,
        @RequestBody request: AdminModerateRequest
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val adminId = userContext.userId

        logger.info { "Moderating review $reviewId, approved=${request.approved} by admin $adminId" }

        return try {
            val review = reviewService.moderateReview(
                reviewId = reviewId,
                adminId = adminId,
                approved = request.approved,
                note = request.note
            )

            ResponseEntity.ok(mapOf("review" to mapReviewResponse(review)))
        } catch (e: ReviewNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Review not found",
                    "code" to "REVIEW_NOT_FOUND"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error moderating review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to moderate review",
                    "code" to "MODERATE_FAILED"
                )
            ))
        }
    }

    /**
     * Add admin response to a review
     * POST /admin/reviews/:reviewId/response
     */
    @Operation(summary = "Add admin response to a review")
    @PostMapping("/{reviewId}/response")
    fun addAdminResponse(
        @PathVariable reviewId: String,
        @RequestBody request: AdminResponseRequest
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val adminId = userContext.userId

        logger.info { "Adding admin response to review $reviewId by admin $adminId" }

        return try {
            val review = reviewService.addAdminResponse(
                reviewId = reviewId,
                adminId = adminId,
                response = request.response
            )

            ResponseEntity.ok(mapOf("review" to mapReviewResponse(review)))
        } catch (e: ReviewNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Review not found",
                    "code" to "REVIEW_NOT_FOUND"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error adding admin response to review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to add response",
                    "code" to "RESPONSE_FAILED"
                )
            ))
        }
    }

    /**
     * Set featured status of a review
     * POST /admin/reviews/:reviewId/featured
     */
    @Operation(summary = "Set featured status of a review")
    @PostMapping("/{reviewId}/featured")
    fun setFeatured(
        @PathVariable reviewId: String,
        @RequestBody request: AdminSetFeaturedRequest
    ): ResponseEntity<Any> {
        logger.info { "Setting featured status of review $reviewId to ${request.featured}" }

        return try {
            val review = reviewService.setFeatured(reviewId, request.featured)
            ResponseEntity.ok(mapOf("review" to mapReviewResponse(review)))
        } catch (e: ReviewNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Review not found",
                    "code" to "REVIEW_NOT_FOUND"
                )
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error setting featured status of review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to set featured status",
                    "code" to "SET_FEATURED_FAILED"
                )
            ))
        }
    }

    /**
     * Delete a review (admin)
     * DELETE /admin/reviews/:reviewId
     */
    @Operation(summary = "Delete a review (admin)")
    @DeleteMapping("/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: String,
        @RequestBody(required = false) request: AdminDeleteRequest?
    ): ResponseEntity<Any> {
        val userContext = getCurrentUserContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf(
                "error" to mapOf(
                    "message" to "Authentication required",
                    "code" to "UNAUTHORIZED"
                )
            ))

        val adminId = userContext.userId

        logger.info { "Admin $adminId deleting review $reviewId" }

        return try {
            reviewService.adminDeleteReview(
                reviewId = reviewId,
                adminId = adminId,
                reason = request?.reason
            )

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
        } catch (e: Exception) {
            logger.error(e) { "Error deleting review $reviewId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Failed to delete review",
                    "code" to "DELETE_FAILED"
                )
            ))
        }
    }

    /**
     * Get a single review (admin view with more details)
     * GET /admin/reviews/:reviewId
     */
    @Operation(summary = "Get a review (admin view)")
    @GetMapping("/{reviewId}")
    fun getReview(
        @PathVariable reviewId: String
    ): ResponseEntity<Any> {
        logger.debug { "Getting review $reviewId (admin view)" }

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
}

// ==================== Request DTOs ====================

data class AdminModerateRequest(
    val approved: Boolean,
    val note: String? = null
)

data class AdminResponseRequest(
    val response: String
)

data class AdminSetFeaturedRequest(
    val featured: Boolean
)

data class AdminDeleteRequest(
    val reason: String? = null
)
