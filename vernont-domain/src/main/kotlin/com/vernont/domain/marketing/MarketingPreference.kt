package com.vernont.domain.marketing

import com.vernont.domain.common.BaseEntity
import com.vernont.domain.customer.Customer
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "marketing_preference",
    indexes = [
        Index(name = "idx_marketing_pref_customer", columnList = "customer_id", unique = true),
        Index(name = "idx_marketing_pref_enabled", columnList = "marketing_emails_enabled")
    ]
)
class MarketingPreference : BaseEntity() {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    lateinit var customer: Customer

    @Column(name = "marketing_emails_enabled", nullable = false)
    var marketingEmailsEnabled: Boolean = true

    @Column(name = "price_drop_alerts_enabled", nullable = false)
    var priceDropAlertsEnabled: Boolean = true

    @Column(name = "new_arrivals_enabled", nullable = false)
    var newArrivalsEnabled: Boolean = true

    @Column(name = "weekly_digest_enabled", nullable = false)
    var weeklyDigestEnabled: Boolean = true

    @Column(name = "promotional_enabled", nullable = false)
    var promotionalEnabled: Boolean = true

    @Enumerated(EnumType.STRING)
    @Column(name = "email_frequency", nullable = false)
    var emailFrequency: EmailFrequency = EmailFrequency.NORMAL

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var preferredCategories: MutableList<String>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var preferredBrands: MutableList<String>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var excludedCategories: MutableList<String>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var excludedBrands: MutableList<String>? = null

    @Column(name = "unsubscribed_at")
    var unsubscribedAt: Instant? = null

    @Column(name = "unsubscribe_reason")
    var unsubscribeReason: String? = null

    fun unsubscribeAll(reason: String? = null) {
        this.marketingEmailsEnabled = false
        this.priceDropAlertsEnabled = false
        this.newArrivalsEnabled = false
        this.weeklyDigestEnabled = false
        this.promotionalEnabled = false
        this.unsubscribedAt = Instant.now()
        this.unsubscribeReason = reason
    }

    fun resubscribe() {
        this.marketingEmailsEnabled = true
        this.unsubscribedAt = null
        this.unsubscribeReason = null
    }

    fun canReceiveEmail(emailType: CampaignType): Boolean {
        if (!marketingEmailsEnabled) return false

        return when (emailType) {
            CampaignType.PRICE_DROP -> priceDropAlertsEnabled
            CampaignType.NEW_ARRIVALS -> newArrivalsEnabled
            CampaignType.WEEKLY_DIGEST -> weeklyDigestEnabled
            CampaignType.MANUAL -> promotionalEnabled
            else -> promotionalEnabled
        }
    }
}

enum class EmailFrequency {
    DAILY,
    NORMAL,
    WEEKLY,
    MONTHLY
}
