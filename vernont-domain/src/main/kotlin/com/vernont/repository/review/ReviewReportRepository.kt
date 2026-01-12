package com.vernont.repository.review

import com.vernont.domain.review.ReviewReport
import com.vernont.domain.review.ReportStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReviewReportRepository : JpaRepository<ReviewReport, String> {

    fun findByReviewIdAndCustomerId(reviewId: String, customerId: String): ReviewReport?

    fun existsByReviewIdAndCustomerId(reviewId: String, customerId: String): Boolean

    fun findByStatus(status: ReportStatus, pageable: Pageable): Page<ReviewReport>

    fun findByReviewId(reviewId: String): List<ReviewReport>

    fun countByReviewId(reviewId: String): Long

    fun countByStatus(status: ReportStatus): Long
}
