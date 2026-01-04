package com.vernont.domain.marketing

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "campaign_execution",
    indexes = [
        Index(name = "idx_campaign_execution_campaign", columnList = "campaign_id"),
        Index(name = "idx_campaign_execution_status", columnList = "execution_status, started_at")
    ]
)
class CampaignExecution : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    lateinit var campaign: MarketingCampaign

    @Column(name = "workflow_execution_id")
    var workflowExecutionId: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false)
    var executionStatus: ExecutionStatus = ExecutionStatus.PENDING

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @Column(name = "emails_sent", nullable = false)
    var emailsSent: Int = 0

    @Column(name = "emails_failed", nullable = false)
    var emailsFailed: Int = 0

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null

    fun start() {
        this.executionStatus = ExecutionStatus.RUNNING
        this.startedAt = Instant.now()
    }

    fun complete() {
        this.executionStatus = ExecutionStatus.COMPLETED
        this.completedAt = Instant.now()
    }

    fun fail(error: String) {
        this.executionStatus = ExecutionStatus.FAILED
        this.completedAt = Instant.now()
        this.errorMessage = error
    }

    fun incrementSent() {
        emailsSent++
    }

    fun incrementFailed() {
        emailsFailed++
    }
}

enum class ExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
