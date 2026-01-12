package com.vernont.domain.security

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Active admin user sessions for live tracking and security monitoring.
 */
@Entity
@Table(
    name = "admin_session",
    indexes = [
        Index(name = "idx_admin_session_user_id", columnList = "user_id"),
        Index(name = "idx_admin_session_status", columnList = "status"),
        Index(name = "idx_admin_session_last_activity", columnList = "last_activity_at"),
        Index(name = "idx_admin_session_token_hash", columnList = "session_token_hash"),
        Index(name = "idx_admin_session_ip_address", columnList = "ip_address")
    ]
)
class AdminSession : BaseEntity() {

    @NotBlank
    @Column(name = "user_id", nullable = false, length = 36)
    var userId: String = ""

    @NotBlank
    @Column(name = "session_token_hash", nullable = false, length = 64)
    var sessionTokenHash: String = ""

    @NotBlank
    @Column(name = "ip_address", nullable = false, length = 45)
    var ipAddress: String = ""

    @Column(name = "user_agent", columnDefinition = "TEXT")
    var userAgent: String? = null

    @Column(name = "device_type", length = 50)
    var deviceType: String? = null

    @Column(length = 100)
    var browser: String? = null

    @Column(name = "browser_version", length = 50)
    var browserVersion: String? = null

    @Column(length = 100)
    var os: String? = null

    @Column(name = "os_version", length = 50)
    var osVersion: String? = null

    @Column(name = "country_code", length = 2)
    var countryCode: String? = null

    @Column(length = 255)
    var city: String? = null

    @Column(length = 255)
    var region: String? = null

    @Column
    var latitude: Double? = null

    @Column
    var longitude: Double? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SessionStatus = SessionStatus.ACTIVE

    @Column(name = "last_activity_at", nullable = false)
    var lastActivityAt: Instant = Instant.now()

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now().plusSeconds(30 * 60)

    @Column(name = "flagged_vpn", nullable = false)
    var flaggedVpn: Boolean = false

    @Column(name = "flagged_proxy", nullable = false)
    var flaggedProxy: Boolean = false

    @Column(name = "fraud_score")
    var fraudScore: Int? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "revoked_by", length = 36)
    var revokedBy: String? = null

    @Column(name = "revoke_reason", columnDefinition = "TEXT")
    var revokeReason: String? = null

    fun isActive(): Boolean = status == SessionStatus.ACTIVE && !isExpired() && !isDeleted()

    fun isExpired(): Boolean = expiresAt.isBefore(Instant.now())

    fun updateActivity(timeoutMinutes: Int = 30) {
        lastActivityAt = Instant.now()
        expiresAt = Instant.now().plusSeconds(timeoutMinutes * 60L)
    }

    fun revoke(revokedBy: String?, reason: String?) {
        this.status = SessionStatus.REVOKED
        this.revokedAt = Instant.now()
        this.revokedBy = revokedBy
        this.revokeReason = reason
    }

    fun expire() {
        this.status = SessionStatus.EXPIRED
    }
}

enum class SessionStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
