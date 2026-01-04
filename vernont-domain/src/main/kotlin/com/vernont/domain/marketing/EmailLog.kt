package com.vernont.domain.marketing

import com.vernont.domain.common.BaseEntity
import com.vernont.domain.customer.Customer
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "email_log",
    indexes = [
        Index(name = "idx_email_log_customer", columnList = "customer_id, created_at"),
        Index(name = "idx_email_log_campaign", columnList = "campaign_id, status"),
        Index(name = "idx_email_log_status", columnList = "status, sent_at"),
        Index(name = "idx_email_log_mailersend", columnList = "mailersend_message_id")
    ]
)
class EmailLog : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    var campaign: MarketingCampaign? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_execution_id")
    var campaignExecution: CampaignExecution? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    lateinit var customer: Customer

    @Column(name = "recipient_email", nullable = false)
    var recipientEmail: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false)
    var emailType: CampaignType = CampaignType.MANUAL

    @Column(nullable = false, length = 500)
    var subject: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EmailStatus = EmailStatus.PENDING

    @Column(name = "sent_at")
    var sentAt: Instant? = null

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null

    @Column(name = "opened_at")
    var openedAt: Instant? = null

    @Column(name = "clicked_at")
    var clickedAt: Instant? = null

    @Column(name = "bounced_at")
    var bouncedAt: Instant? = null

    @Column(name = "failed_at")
    var failedAt: Instant? = null

    @Column(name = "open_count", nullable = false)
    var openCount: Int = 0

    @Column(name = "click_count", nullable = false)
    var clickCount: Int = 0

    @Column(name = "mailersend_message_id")
    var mailersendMessageId: String? = null

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null

    fun markSent(messageId: String) {
        this.status = EmailStatus.SENT
        this.sentAt = Instant.now()
        this.mailersendMessageId = messageId
    }

    fun markDelivered() {
        this.status = EmailStatus.DELIVERED
        this.deliveredAt = Instant.now()
    }

    fun markOpened() {
        if (this.openedAt == null) {
            this.openedAt = Instant.now()
        }
        this.openCount++
        this.status = EmailStatus.OPENED
    }

    fun markClicked() {
        if (this.clickedAt == null) {
            this.clickedAt = Instant.now()
        }
        this.clickCount++
        this.status = EmailStatus.CLICKED
    }

    fun markBounced() {
        this.status = EmailStatus.BOUNCED
        this.bouncedAt = Instant.now()
    }

    fun markFailed(error: String) {
        this.status = EmailStatus.FAILED
        this.failedAt = Instant.now()
        this.errorMessage = error
    }
}

enum class EmailStatus {
    PENDING,
    SENT,
    DELIVERED,
    OPENED,
    CLICKED,
    BOUNCED,
    FAILED,
    UNSUBSCRIBED
}
