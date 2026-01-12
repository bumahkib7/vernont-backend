package com.vernont.domain.review

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

/**
 * Vote type for review helpfulness
 */
enum class VoteType {
    HELPFUL,
    NOT_HELPFUL
}

/**
 * ReviewVote - Tracks individual votes on reviews
 *
 * Ensures each customer can only vote once per review
 * and allows changing vote
 */
@Entity
@Table(
    name = "review_vote",
    indexes = [
        Index(name = "idx_review_vote_review_id", columnList = "review_id"),
        Index(name = "idx_review_vote_customer_id", columnList = "customer_id"),
        Index(name = "idx_review_vote_type", columnList = "vote_type")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_review_vote_review_customer",
            columnNames = ["review_id", "customer_id"]
        )
    ]
)
class ReviewVote : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    var review: Review? = null

    @Column(name = "review_id", insertable = false, updatable = false)
    var reviewId: String? = null

    @Column(name = "customer_id", nullable = false)
    var customerId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false)
    var voteType: VoteType = VoteType.HELPFUL
}
