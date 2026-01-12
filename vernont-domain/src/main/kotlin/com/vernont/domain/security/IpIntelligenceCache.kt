package com.vernont.domain.security

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Cached IP intelligence data from IPQualityScore API.
 * Cache entries expire after 24 hours by default.
 */
@Entity
@Table(
    name = "ip_intelligence_cache",
    indexes = [
        Index(name = "idx_ip_intelligence_expires_at", columnList = "expires_at"),
        Index(name = "idx_ip_intelligence_fraud_score", columnList = "fraud_score")
    ]
)
class IpIntelligenceCache : BaseEntity() {

    @NotBlank
    @Column(name = "ip_address", nullable = false, length = 45)
    var ipAddress: String = ""

    @Column(name = "fraud_score", nullable = false)
    var fraudScore: Int = 0

    @Column(name = "is_vpn", nullable = false)
    var isVpn: Boolean = false

    @Column(name = "is_proxy", nullable = false)
    var isProxy: Boolean = false

    @Column(name = "is_tor", nullable = false)
    var isTor: Boolean = false

    @Column(name = "is_datacenter", nullable = false)
    var isDatacenter: Boolean = false

    @Column(name = "is_bot", nullable = false)
    var isBot: Boolean = false

    @Column(name = "is_crawler", nullable = false)
    var isCrawler: Boolean = false

    @Column(name = "country_code", length = 2)
    var countryCode: String? = null

    @Column(length = 255)
    var city: String? = null

    @Column(length = 255)
    var region: String? = null

    @Column(length = 255)
    var isp: String? = null

    @Column(length = 255)
    var organization: String? = null

    @Column
    var asn: Int? = null

    @Column
    var latitude: Double? = null

    @Column
    var longitude: Double? = null

    @Column(length = 100)
    var timezone: String? = null

    @Column(nullable = false)
    var mobile: Boolean = false

    @Column(length = 255)
    var host: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    var rawResponse: Map<String, Any?>? = null

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now().plusSeconds(24 * 60 * 60)

    fun isExpired(): Boolean = expiresAt.isBefore(Instant.now())

    fun isSuspicious(fraudThreshold: Int = 75): Boolean {
        return fraudScore >= fraudThreshold || isVpn || isProxy || isTor || isDatacenter
    }
}
