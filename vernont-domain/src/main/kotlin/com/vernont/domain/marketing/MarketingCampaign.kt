package com.vernont.domain.marketing

import com.vernont.domain.common.BaseEntity
import com.vernont.domain.customer.CustomerGroup
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "marketing_campaign",
    indexes = [
        Index(name = "idx_campaign_type_status", columnList = "campaign_type, status, deleted_at"),
        Index(name = "idx_campaign_scheduled", columnList = "scheduled_at, status"),
        Index(name = "idx_campaign_created", columnList = "created_at")
    ]
)
@NamedEntityGraph(
    name = "MarketingCampaign.withGroup",
    attributeNodes = [
        NamedAttributeNode("targetCustomerGroup")
    ]
)
class MarketingCampaign : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false)
    var campaignType: CampaignType = CampaignType.MANUAL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CampaignStatus = CampaignStatus.DRAFT

    @Column(name = "scheduled_at")
    var scheduledAt: Instant? = null

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @NotBlank
    @Column(name = "email_subject", nullable = false, length = 500)
    var emailSubject: String = ""

    @Column(name = "email_template_id")
    var emailTemplateId: String? = null

    @Column(name = "email_preheader")
    var emailPreheader: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_customer_group_id")
    var targetCustomerGroup: CustomerGroup? = null

    @Column(name = "target_all_customers", nullable = false)
    var targetAllCustomers: Boolean = false

    @Column(name = "total_recipients", nullable = false)
    var totalRecipients: Long = 0

    @Column(name = "total_sent", nullable = false)
    var totalSent: Long = 0

    @Column(name = "total_failed", nullable = false)
    var totalFailed: Long = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var config: MutableMap<String, Any?>? = null

    fun schedule(scheduledAt: Instant) {
        this.scheduledAt = scheduledAt
        this.status = CampaignStatus.SCHEDULED
    }

    fun start() {
        this.status = CampaignStatus.RUNNING
        this.startedAt = Instant.now()
    }

    fun complete() {
        this.status = CampaignStatus.COMPLETED
        this.completedAt = Instant.now()
    }

    fun pause() {
        this.status = CampaignStatus.PAUSED
    }

    fun cancel() {
        this.status = CampaignStatus.CANCELLED
        this.completedAt = Instant.now()
    }

    fun incrementSent() {
        totalSent++
    }

    fun incrementFailed() {
        totalFailed++
    }
}

enum class CampaignType {
    PRICE_DROP,
    NEW_ARRIVALS,
    WIN_BACK,
    WEEKLY_DIGEST,
    MANUAL
}

enum class CampaignStatus {
    DRAFT,
    SCHEDULED,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED
}
