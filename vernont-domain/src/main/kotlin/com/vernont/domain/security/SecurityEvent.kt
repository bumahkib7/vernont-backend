package com.vernont.domain.security

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Security audit log for tracking blocked access attempts and security events.
 */
@Entity
@Table(
    name = "security_event",
    indexes = [
        Index(name = "idx_security_event_event_type", columnList = "event_type"),
        Index(name = "idx_security_event_severity", columnList = "severity"),
        Index(name = "idx_security_event_created_at", columnList = "created_at"),
        Index(name = "idx_security_event_resolved", columnList = "resolved"),
        Index(name = "idx_security_event_user_id", columnList = "user_id"),
        Index(name = "idx_security_event_ip_address", columnList = "ip_address")
    ]
)
class SecurityEvent : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: SecurityEventType = SecurityEventType.SESSION_CREATED

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var severity: EventSeverity = EventSeverity.MEDIUM

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null

    @Column(name = "user_id", length = 36)
    var userId: String? = null

    @Column(name = "user_email")
    var userEmail: String? = null

    @Column(name = "session_id", length = 36)
    var sessionId: String? = null

    @NotBlank
    @Column(nullable = false)
    var title: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "request_path", length = 500)
    var requestPath: String? = null

    @Column(name = "request_method", length = 10)
    var requestMethod: String? = null

    @Column(name = "user_agent", columnDefinition = "TEXT")
    var userAgent: String? = null

    @Column(name = "country_code", length = 2)
    var countryCode: String? = null

    @Column(length = 255)
    var city: String? = null

    @Column(name = "fraud_score")
    var fraudScore: Int? = null

    @Column(name = "is_vpn")
    var isVpn: Boolean? = null

    @Column(name = "is_proxy")
    var isProxy: Boolean? = null

    @Column(nullable = false)
    var resolved: Boolean = false

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    @Column(name = "resolved_by", length = 36)
    var resolvedBy: String? = null

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    var resolutionNotes: String? = null

    fun resolve(resolvedBy: String?, notes: String?) {
        this.resolved = true
        this.resolvedAt = Instant.now()
        this.resolvedBy = resolvedBy
        this.resolutionNotes = notes
    }

    companion object {
        fun createBlockedEvent(
            type: SecurityEventType,
            ipAddress: String,
            userId: String?,
            userEmail: String?,
            requestPath: String?,
            requestMethod: String?,
            userAgent: String?,
            ipIntelligence: IpIntelligenceCache?
        ): SecurityEvent {
            return SecurityEvent().apply {
                eventType = type
                severity = when (type) {
                    SecurityEventType.TOR_BLOCKED -> EventSeverity.CRITICAL
                    SecurityEventType.VPN_BLOCKED, SecurityEventType.PROXY_BLOCKED -> EventSeverity.HIGH
                    SecurityEventType.DATACENTER_BLOCKED, SecurityEventType.HIGH_FRAUD_SCORE -> EventSeverity.HIGH
                    SecurityEventType.BLOCKLIST_HIT -> EventSeverity.MEDIUM
                    else -> EventSeverity.LOW
                }
                this.ipAddress = ipAddress
                this.userId = userId
                this.userEmail = userEmail
                this.title = "${type.displayName} - Access Blocked"
                this.description = "Access blocked for IP $ipAddress"
                this.requestPath = requestPath
                this.requestMethod = requestMethod
                this.userAgent = userAgent

                ipIntelligence?.let {
                    this.countryCode = it.countryCode
                    this.city = it.city
                    this.fraudScore = it.fraudScore
                    this.isVpn = it.isVpn
                    this.isProxy = it.isProxy
                }
            }
        }
    }
}

enum class SecurityEventType(val displayName: String) {
    VPN_BLOCKED("VPN Detected"),
    PROXY_BLOCKED("Proxy Detected"),
    DATACENTER_BLOCKED("Datacenter IP"),
    TOR_BLOCKED("Tor Exit Node"),
    BOT_BLOCKED("Bot Detected"),
    HIGH_FRAUD_SCORE("High Fraud Score"),
    BLOCKLIST_HIT("Blocklisted IP"),
    ALLOWLIST_BYPASS("Allowlist Bypass"),
    SESSION_CREATED("Session Created"),
    SESSION_EXPIRED("Session Expired"),
    SESSION_REVOKED("Session Revoked"),
    LOGIN_SUCCESS("Login Success"),
    LOGIN_FAILED("Login Failed"),
    SUSPICIOUS_ACTIVITY("Suspicious Activity"),
    IP_LIST_ADDED("IP Added to List"),
    IP_LIST_REMOVED("IP Removed from List"),
    CONFIG_CHANGED("Config Changed")
}

enum class EventSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
