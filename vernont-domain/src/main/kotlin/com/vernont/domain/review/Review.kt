package com.vernont.domain.review

import com.vernont.domain.common.BaseEntity
import com.vernont.domain.customer.Customer
import com.vernont.domain.product.Product
import jakarta.persistence.*
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import kotlin.math.sqrt

/**
 * Review status for moderation workflow
 */
enum class ReviewStatus {
    PENDING,      // Awaiting moderation
    APPROVED,     // Approved and visible
    REJECTED,     // Rejected by moderator
    FLAGGED,      // Flagged for review (by users or system)
    HIDDEN        // Hidden by admin but not rejected
}

/**
 * Review - Customer review for a product
 *
 * Features:
 * - 1-5 star rating
 * - Title and detailed content
 * - Pros and cons lists
 * - Verified purchase tracking
 * - Helpful/not helpful voting
 * - Image attachments
 * - Admin response capability
 * - Moderation workflow
 */
@Entity
@Table(
    name = "product_review",
    indexes = [
        Index(name = "idx_review_product_id", columnList = "product_id"),
        Index(name = "idx_review_customer_id", columnList = "customer_id"),
        Index(name = "idx_review_rating", columnList = "rating"),
        Index(name = "idx_review_status", columnList = "status"),
        Index(name = "idx_review_verified_purchase", columnList = "verified_purchase"),
        Index(name = "idx_review_helpful_count", columnList = "helpful_count"),
        Index(name = "idx_review_created_at", columnList = "created_at"),
        Index(name = "idx_review_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_review_product_status", columnList = "product_id, status"),
        Index(name = "idx_review_product_rating", columnList = "product_id, rating")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_review_product_customer",
            columnNames = ["product_id", "customer_id"]
        )
    ]
)
@NamedEntityGraph(
    name = "Review.withCustomer",
    attributeNodes = [
        NamedAttributeNode("customer")
    ]
)
class Review : BaseEntity() {

    // ==================== Relationships ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product? = null

    @Column(name = "product_id", insertable = false, updatable = false)
    var productId: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    var customer: Customer? = null

    @Column(name = "customer_id", insertable = false, updatable = false)
    var customerId: String? = null

    // For display without loading customer entity
    @Column(name = "customer_name", nullable = false)
    var customerName: String = ""

    @Column(name = "customer_avatar")
    var customerAvatar: String? = null

    // ==================== Rating ====================

    @Min(1)
    @Max(5)
    @Column(nullable = false)
    var rating: Int = 5

    // ==================== Content ====================

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    var title: String = ""

    @NotBlank
    @Size(max = 5000)
    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = ""

    // Pros and cons as JSON arrays
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var pros: MutableList<String>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var cons: MutableList<String>? = null

    // ==================== Images ====================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var images: MutableList<ReviewImage>? = null

    // ==================== Purchase Verification ====================

    @Column(name = "verified_purchase", nullable = false)
    var verifiedPurchase: Boolean = false

    @Column(name = "order_id")
    var orderId: String? = null

    @Column(name = "variant_id")
    var variantId: String? = null

    @Column(name = "variant_title")
    var variantTitle: String? = null

    // ==================== Voting ====================

    @Column(name = "helpful_count", nullable = false)
    var helpfulCount: Int = 0

    @Column(name = "not_helpful_count", nullable = false)
    var notHelpfulCount: Int = 0

    @Column(name = "report_count", nullable = false)
    var reportCount: Int = 0

    // ==================== Moderation ====================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ReviewStatus = ReviewStatus.PENDING

    @Column(name = "moderated_at")
    var moderatedAt: Instant? = null

    @Column(name = "moderated_by")
    var moderatedBy: String? = null

    @Column(name = "moderation_note", columnDefinition = "TEXT")
    var moderationNote: String? = null

    // ==================== Admin Response ====================

    @Column(name = "admin_response", columnDefinition = "TEXT")
    var adminResponse: String? = null

    @Column(name = "admin_response_at")
    var adminResponseAt: Instant? = null

    @Column(name = "admin_response_by")
    var adminResponseBy: String? = null

    // ==================== Flags ====================

    @Column(name = "is_featured", nullable = false)
    var isFeatured: Boolean = false

    @Column(name = "is_edited", nullable = false)
    var isEdited: Boolean = false

    @Column(name = "edited_at")
    var editedAt: Instant? = null

    // ==================== Computed ====================

    /**
     * Calculate helpfulness score (for sorting)
     * Wilson score for ranking
     */
    fun getHelpfulnessScore(): Double {
        val total = helpfulCount + notHelpfulCount
        if (total == 0) return 0.0

        val p = helpfulCount.toDouble() / total
        val z = 1.96 // 95% confidence
        val n = total.toDouble()

        // Wilson score lower bound
        return (p + z * z / (2 * n) - z * sqrt((p * (1 - p) + z * z / (4 * n)) / n)) / (1 + z * z / n)
    }
}

/**
 * Review image attachment
 */
data class ReviewImage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String,
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val sortOrder: Int = 0
)
