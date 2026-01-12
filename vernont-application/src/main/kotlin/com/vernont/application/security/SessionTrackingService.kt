package com.vernont.application.security

import com.vernont.domain.security.*
import com.vernont.infrastructure.security.IpCheckResult
import com.vernont.infrastructure.security.IpIntelligenceService
import com.vernont.infrastructure.security.SessionWebSocketPublisher
import com.vernont.repository.security.AdminSessionRepository
import com.vernont.repository.security.SecurityEventRepository
import com.vernont.repository.security.SecuritySettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for tracking admin user sessions.
 * Handles session creation, heartbeat, revocation, and expiration.
 */
@Service
@Transactional
class SessionTrackingService(
    private val adminSessionRepository: AdminSessionRepository,
    private val securityEventRepository: SecurityEventRepository,
    private val securitySettingsRepository: SecuritySettingsRepository,
    private val ipIntelligenceService: IpIntelligenceService,
    private val sessionWebSocketPublisher: SessionWebSocketPublisher
) {
    /**
     * Track a new session for an admin user.
     */
    fun trackSession(
        userId: String,
        sessionToken: String,
        ipAddress: String,
        userAgent: String?
    ): AdminSession {
        val config = getSecurityConfig()
        val tokenHash = hashToken(sessionToken)

        // Parse user agent
        val parsedAgent = parseUserAgent(userAgent)

        // Get IP intelligence
        val ipCheckResult = ipIntelligenceService.checkIpAccess(ipAddress, userId)
        val ipIntelligence = ipCheckResult.ipIntelligence

        // Enforce max sessions per user
        val activeSessions = adminSessionRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, SessionStatus.ACTIVE)
        if (activeSessions.size >= config.maxSessionsPerUser) {
            // Revoke oldest sessions
            val toRevoke = activeSessions.sortedBy { it.createdAt }.take(activeSessions.size - config.maxSessionsPerUser + 1)
            toRevoke.forEach { session ->
                session.revoke(null, "Max sessions exceeded")
                adminSessionRepository.save(session)
                sessionWebSocketPublisher.publishSessionRevoked(session)
                logger.info { "Revoked session ${session.id} for user $userId (max sessions exceeded)" }
            }
        }

        val session = AdminSession().apply {
            this.userId = userId
            this.sessionTokenHash = tokenHash
            this.ipAddress = ipAddress
            this.userAgent = userAgent
            this.deviceType = parsedAgent.deviceType
            this.browser = parsedAgent.browser
            this.browserVersion = parsedAgent.browserVersion
            this.os = parsedAgent.os
            this.osVersion = parsedAgent.osVersion
            this.countryCode = ipIntelligence?.countryCode
            this.city = ipIntelligence?.city
            this.region = ipIntelligence?.region
            this.latitude = ipIntelligence?.latitude
            this.longitude = ipIntelligence?.longitude
            this.status = SessionStatus.ACTIVE
            this.lastActivityAt = Instant.now()
            this.expiresAt = Instant.now().plusSeconds(config.sessionTimeoutMinutes * 60L)
            this.flaggedVpn = ipIntelligence?.isVpn == true
            this.flaggedProxy = ipIntelligence?.isProxy == true
            this.fraudScore = ipIntelligence?.fraudScore
        }

        val saved = adminSessionRepository.save(session)
        logger.info { "Created session ${saved.id} for user $userId from IP $ipAddress" }

        // Log security event
        val event = SecurityEvent().apply {
            eventType = SecurityEventType.SESSION_CREATED
            severity = if (saved.flaggedVpn || saved.flaggedProxy) EventSeverity.MEDIUM else EventSeverity.LOW
            this.ipAddress = ipAddress
            this.userId = userId
            this.sessionId = saved.id
            this.title = "Session created"
            this.description = "New admin session created from ${saved.city ?: ipAddress}"
            this.userAgent = userAgent
            this.countryCode = saved.countryCode
            this.city = saved.city
            this.fraudScore = saved.fraudScore
            this.isVpn = saved.flaggedVpn
            this.isProxy = saved.flaggedProxy
        }
        securityEventRepository.save(event)

        sessionWebSocketPublisher.publishSessionCreated(saved)
        sessionWebSocketPublisher.publishSecurityEvent(event)

        return saved
    }

    /**
     * Update session activity (heartbeat).
     */
    fun heartbeat(sessionToken: String): AdminSession? {
        val tokenHash = hashToken(sessionToken)
        val session = adminSessionRepository.findBySessionTokenHashAndDeletedAtIsNull(tokenHash)

        if (session == null) {
            logger.debug { "Session not found for heartbeat" }
            return null
        }

        if (!session.isActive()) {
            logger.debug { "Session ${session.id} is not active, skipping heartbeat" }
            return null
        }

        val config = getSecurityConfig()
        session.updateActivity(config.sessionTimeoutMinutes)
        val saved = adminSessionRepository.save(session)

        sessionWebSocketPublisher.publishSessionUpdated(saved)
        return saved
    }

    /**
     * Get session by token.
     */
    @Transactional(readOnly = true)
    fun getSessionByToken(sessionToken: String): AdminSession? {
        val tokenHash = hashToken(sessionToken)
        return adminSessionRepository.findBySessionTokenHashAndDeletedAtIsNull(tokenHash)
    }

    /**
     * Revoke a session.
     */
    fun revokeSession(sessionId: String, revokedBy: String?, reason: String?): AdminSession? {
        val session = adminSessionRepository.findByIdAndDeletedAtIsNull(sessionId)
            ?: return null

        session.revoke(revokedBy, reason)
        val saved = adminSessionRepository.save(session)

        logger.info { "Revoked session ${session.id} by $revokedBy: $reason" }

        // Log security event
        val event = SecurityEvent().apply {
            eventType = SecurityEventType.SESSION_REVOKED
            severity = EventSeverity.LOW
            ipAddress = session.ipAddress
            userId = session.userId
            this.sessionId = session.id
            title = "Session revoked"
            description = reason ?: "Session manually revoked"
        }
        securityEventRepository.save(event)

        sessionWebSocketPublisher.publishSessionRevoked(saved)
        sessionWebSocketPublisher.publishSecurityEvent(event)

        return saved
    }

    /**
     * Revoke all sessions for a user.
     */
    fun revokeAllUserSessions(userId: String, revokedBy: String?, reason: String?): Int {
        val sessions = adminSessionRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, SessionStatus.ACTIVE)
        sessions.forEach { session ->
            session.revoke(revokedBy, reason)
            adminSessionRepository.save(session)
            sessionWebSocketPublisher.publishSessionRevoked(session)
        }
        logger.info { "Revoked ${sessions.size} sessions for user $userId" }
        return sessions.size
    }

    /**
     * Get all active sessions.
     */
    @Transactional(readOnly = true)
    fun getActiveSessions(): List<AdminSession> {
        return adminSessionRepository.findByStatusAndDeletedAtIsNullOrderByLastActivityAtDesc(SessionStatus.ACTIVE)
    }

    /**
     * Get sessions for a specific user.
     */
    @Transactional(readOnly = true)
    fun getUserSessions(userId: String): List<AdminSession> {
        return adminSessionRepository.findByUserIdAndDeletedAtIsNull(userId)
    }

    /**
     * Expire old sessions.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    fun expireOldSessions() {
        val expiredSessions = adminSessionRepository.findExpiredActiveSessions()
        if (expiredSessions.isEmpty()) return

        logger.info { "Expiring ${expiredSessions.size} sessions" }
        expiredSessions.forEach { session ->
            session.expire()
            adminSessionRepository.save(session)
            sessionWebSocketPublisher.publishSessionExpired(session)
        }
    }

    /**
     * Get security dashboard statistics.
     */
    @Transactional(readOnly = true)
    fun getSecurityStats(): SecurityStats {
        val now = Instant.now()
        val last24Hours = now.minusSeconds(24 * 60 * 60)

        return SecurityStats(
            activeSessions = adminSessionRepository.countActiveSessions(),
            blockedAttempts24h = securityEventRepository.countBlockedAttemptsSince(
                listOf(
                    SecurityEventType.VPN_BLOCKED,
                    SecurityEventType.PROXY_BLOCKED,
                    SecurityEventType.DATACENTER_BLOCKED,
                    SecurityEventType.TOR_BLOCKED,
                    SecurityEventType.BOT_BLOCKED,
                    SecurityEventType.HIGH_FRAUD_SCORE,
                    SecurityEventType.BLOCKLIST_HIT
                ),
                last24Hours
            ),
            unresolvedEvents = securityEventRepository.countUnresolved(),
            vpnFlagged24h = adminSessionRepository.countVpnFlaggedSessions(last24Hours),
            proxyFlagged24h = adminSessionRepository.countProxyFlaggedSessions(last24Hours)
        )
    }

    private fun getSecurityConfig(): SecuritySettings {
        return securitySettingsRepository.findByIdAndDeletedAtIsNull(SecuritySettings.DEFAULT_ID)
            ?: SecuritySettings.createDefault()
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(token.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun parseUserAgent(userAgent: String?): ParsedUserAgent {
        if (userAgent.isNullOrBlank()) {
            return ParsedUserAgent()
        }

        val ua = userAgent.lowercase()

        // Detect device type
        val deviceType = when {
            ua.contains("mobile") || ua.contains("android") && !ua.contains("tablet") -> "Mobile"
            ua.contains("tablet") || ua.contains("ipad") -> "Tablet"
            else -> "Desktop"
        }

        // Detect browser
        val (browser, browserVersion) = when {
            ua.contains("edg/") -> {
                val version = Regex("edg/([\\d.]+)").find(ua)?.groupValues?.get(1) ?: ""
                "Edge" to version
            }
            ua.contains("chrome/") && !ua.contains("edg/") -> {
                val version = Regex("chrome/([\\d.]+)").find(ua)?.groupValues?.get(1) ?: ""
                "Chrome" to version
            }
            ua.contains("firefox/") -> {
                val version = Regex("firefox/([\\d.]+)").find(ua)?.groupValues?.get(1) ?: ""
                "Firefox" to version
            }
            ua.contains("safari/") && !ua.contains("chrome/") -> {
                val version = Regex("version/([\\d.]+)").find(ua)?.groupValues?.get(1) ?: ""
                "Safari" to version
            }
            else -> "Unknown" to ""
        }

        // Detect OS
        val (os, osVersion) = when {
            ua.contains("windows nt 10") -> "Windows" to "10"
            ua.contains("windows nt 11") -> "Windows" to "11"
            ua.contains("windows") -> "Windows" to ""
            ua.contains("mac os x") -> {
                val version = Regex("mac os x ([\\d_]+)").find(ua)?.groupValues?.get(1)?.replace("_", ".") ?: ""
                "macOS" to version
            }
            ua.contains("iphone") || ua.contains("ipad") -> {
                val version = Regex("os ([\\d_]+)").find(ua)?.groupValues?.get(1)?.replace("_", ".") ?: ""
                "iOS" to version
            }
            ua.contains("android") -> {
                val version = Regex("android ([\\d.]+)").find(ua)?.groupValues?.get(1) ?: ""
                "Android" to version
            }
            ua.contains("linux") -> "Linux" to ""
            else -> "Unknown" to ""
        }

        return ParsedUserAgent(
            deviceType = deviceType,
            browser = browser,
            browserVersion = browserVersion,
            os = os,
            osVersion = osVersion
        )
    }
}

data class ParsedUserAgent(
    val deviceType: String? = null,
    val browser: String? = null,
    val browserVersion: String? = null,
    val os: String? = null,
    val osVersion: String? = null
)

data class SecurityStats(
    val activeSessions: Long,
    val blockedAttempts24h: Long,
    val unresolvedEvents: Long,
    val vpnFlagged24h: Long,
    val proxyFlagged24h: Long
)
