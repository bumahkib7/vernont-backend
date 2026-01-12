package com.vernont.domain.security

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

/**
 * Singleton table for security configuration settings.
 * Only one row should exist with id = 'default'.
 */
@Entity
@Table(name = "security_config")
class SecuritySettings : BaseEntity() {

    @Column(name = "block_vpn", nullable = false)
    var blockVpn: Boolean = true

    @Column(name = "block_proxy", nullable = false)
    var blockProxy: Boolean = true

    @Column(name = "block_datacenter", nullable = false)
    var blockDatacenter: Boolean = true

    @Column(name = "block_tor", nullable = false)
    var blockTor: Boolean = true

    @Column(name = "block_bots", nullable = false)
    var blockBots: Boolean = true

    @Column(name = "fraud_score_threshold", nullable = false)
    var fraudScoreThreshold: Int = 75

    @Column(name = "session_timeout_minutes", nullable = false)
    var sessionTimeoutMinutes: Int = 30

    @Column(name = "max_sessions_per_user", nullable = false)
    var maxSessionsPerUser: Int = 5

    @Column(name = "ipqs_enabled", nullable = false)
    var ipqsEnabled: Boolean = true

    @Column(name = "require_allowlist", nullable = false)
    var requireAllowlist: Boolean = false

    companion object {
        const val DEFAULT_ID = "default"

        fun createDefault(): SecuritySettings {
            return SecuritySettings().apply {
                id = DEFAULT_ID
            }
        }
    }
}
