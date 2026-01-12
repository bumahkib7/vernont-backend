package com.vernont.domain.review

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

/**
 * Reason for reporting a review
 */
enum class ReportReason {
    INAPPROPRIATE,      // Inappropriate content
    SPAM,              // Spam or advertising
    FAKE,              // Fake/fraudulent review
    WRONG_PRODUCT,     // Review is for wrong product
    OFFENSIVE,         // Offensive language
    PERSONAL_INFO,     // Contains personal information
    COPYRIGHT,         // Copyright violation
    OTHER              // Other reason
}

/**
 * Report status
 */
enum class ReportStatus {
    PENDING,           // Awaiting review
    REVIEWED,          // Reviewed, no action taken
    ACTION_TAKEN,      // Action taken on the review
    DISMISSED          // Report dismissed as invalid
}

/**
 * ReviewReport - Customer reports on reviews
 *
 * Allows customers to flag inappropriate reviews for moderation
 */
@Entity
@Table(
    name = "review_report",
    indexes = [
        Index(name = "idx_review_report_review_id", columnList = "review_id"),
        Index(name = "idx_review_report_customer_id", columnList = "customer_id"),
        Index(name = "idx_review_report_status", columnList = "status"),
        Index(name = "idx_review_report_reason", columnList = "reason")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_review_report_review_customer",
            columnNames = ["review_id", "customer_id"]
        )
    ]
)
class ReviewReport : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    var review: Review? = null

    @Column(name = "review_id", insertable = false, updatable = false)
    var reviewId: String? = null

    @Column(name = "customer_id", nullable = false)
    var customerId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var reason: ReportReason = ReportReason.OTHER

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ReportStatus = ReportStatus.PENDING

    @Column(name = "reviewed_by")
    var reviewedBy: String? = null

    @Column(name = "review_note", columnDefinition = "TEXT")
    var reviewNote: String? = null
}
