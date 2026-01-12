package com.vernont.repository.review

import com.vernont.domain.review.ReviewVote
import com.vernont.domain.review.VoteType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReviewVoteRepository : JpaRepository<ReviewVote, String> {

    fun findByReviewIdAndCustomerId(reviewId: String, customerId: String): ReviewVote?

    fun existsByReviewIdAndCustomerId(reviewId: String, customerId: String): Boolean

    fun deleteByReviewIdAndCustomerId(reviewId: String, customerId: String)

    fun countByReviewIdAndVoteType(reviewId: String, voteType: VoteType): Long
}
