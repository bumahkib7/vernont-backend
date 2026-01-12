package com.vernont.infrastructure.security

import com.vernont.domain.security.*
import com.vernont.repository.security.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for IP intelligence checking and access control.
 * Implements the following check order:
 * 1. Check allowlist → Allow immediately
 * 2. Check blocklist → Block immediately
 * 3. Check IPQS cache/API → Evaluate based on security config
 */
@Service
class IpIntelligenceService(
    private val ipQualityScoreClient: IpQualityScoreClient,
    private val ipListEntryRepository: IpListEntryRepository,
    private val ipIntelligenceCacheRepository: IpIntelligenceCacheRepository,
    private val securityEventRepository: SecurityEventRepository,
    private val securitySettingsRepository: SecuritySettingsRepository,
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        private const val REDIS_PREFIX = "ip:intelligence:"
        private const val REDIS_TTL_HOURS = 24L
    }

    /**
     * Check if an IP address should be allowed access.
     * Returns an IpCheckResult with the decision and details.
     */
    @Transactional
    fun checkIpAccess(
        ipAddress: String,
        userId: String? = null,
        userEmail: String? = null,
        requestPath: String? = null,
        requestMethod: String? = null,
        userAgent: String? = null
    ): IpCheckResult {
        logger.debug { "Checking IP access for $ipAddress" }

        val config = getSecurityConfig()

        // 1. Check allowlist first
        if (isAllowlisted(ipAddress)) {
            logger.debug { "IP $ipAddress is allowlisted, allowing access" }
            logSecurityEvent(
                SecurityEventType.ALLOWLIST_BYPASS,
                ipAddress, userId, userEmail, requestPath, requestMethod, userAgent, null
            )
            return IpCheckResult(allowed = true, reason = "Allowlisted", ipIntelligence = null)
        }

        // 2. Check blocklist
        if (isBlocklisted(ipAddress)) {
            logger.info { "IP $ipAddress is blocklisted, blocking access" }
            logSecurityEvent(
                SecurityEventType.BLOCKLIST_HIT,
                ipAddress, userId, userEmail, requestPath, requestMethod, userAgent, null
            )
            return IpCheckResult(allowed = false, reason = "IP is blocklisted", blockType = SecurityEventType.BLOCKLIST_HIT)
        }

        // 3. If require_allowlist is enabled and IP is not allowlisted, block
        if (config.requireAllowlist) {
            logger.info { "IP $ipAddress not in allowlist and require_allowlist is enabled" }
            return IpCheckResult(allowed = false, reason = "IP not in allowlist", blockType = SecurityEventType.BLOCKLIST_HIT)
        }

        // 4. Get IP intelligence (from cache or API)
        val ipIntelligence = getIpIntelligence(ipAddress)
        if (ipIntelligence == null) {
            // If IPQS is disabled or fails, allow by default
            logger.debug { "No IP intelligence available for $ipAddress, allowing access" }
            return IpCheckResult(allowed = true, reason = "No IP intelligence", ipIntelligence = null)
        }

        // 5. Evaluate based on security config
        return evaluateIpIntelligence(ipIntelligence, config, userId, userEmail, requestPath, requestMethod, userAgent)
    }

    /**
     * Get IP intelligence from cache or fetch from API.
     */
    fun getIpIntelligence(ipAddress: String): IpIntelligenceCache? {
        // Check Redis cache first
        val redisKey = "$REDIS_PREFIX$ipAddress"
        val cached = redisTemplate.opsForValue().get(redisKey)
        if (cached != null) {
            logger.debug { "IP intelligence cache hit for $ipAddress (Redis)" }
            return ipIntelligenceCacheRepository.findByIpAddressAndDeletedAtIsNull(ipAddress)
        }

        // Check database cache
        val dbCached = ipIntelligenceCacheRepository.findValidByIpAddress(ipAddress)
        if (dbCached != null) {
            logger.debug { "IP intelligence cache hit for $ipAddress (DB)" }
            // Warm Redis cache
            redisTemplate.opsForValue().set(redisKey, "1", Duration.ofHours(REDIS_TTL_HOURS))
            return dbCached
        }

        // Fetch from API
        logger.debug { "IP intelligence cache miss for $ipAddress, fetching from API" }
        val intelligence = runBlocking { ipQualityScoreClient.getIpIntelligence(ipAddress) }
        if (intelligence != null) {
            // Save to database
            val saved = ipIntelligenceCacheRepository.save(intelligence)
            // Warm Redis cache
            redisTemplate.opsForValue().set(redisKey, "1", Duration.ofHours(REDIS_TTL_HOURS))
            return saved
        }

        return null
    }

    private fun evaluateIpIntelligence(
        ipIntelligence: IpIntelligenceCache,
        config: SecuritySettings,
        userId: String?,
        userEmail: String?,
        requestPath: String?,
        requestMethod: String?,
        userAgent: String?
    ): IpCheckResult {
        val ipAddress = ipIntelligence.ipAddress

        // Check Tor
        if (config.blockTor && ipIntelligence.isTor) {
            logger.info { "IP $ipAddress is Tor exit node, blocking" }
            logSecurityEvent(SecurityEventType.TOR_BLOCKED, ipAddress, userId, userEmail, requestPath, requestMethod, userAgent, ipIntelligence)
            return IpCheckResult(allowed = false, reason = "Tor exit node detected", blockType = SecurityEventType.TOR_BLOCKED, ipIntelligence = ipIntelligence)
        }

        // Check VPN
        if (config.blockVpn && ipIntelligence.isVpn) {
            logger.info { "IP $ipAddress is VPN, blocking" }
            logSecurityEvent(SecurityEventType.VPN_BLOCKED, ipAddress, userId, userEmail, requestPath, requestMethod, userAgent, ipIntelligence)
            return IpCheckResult(allowed = false, reason = "VPN detected", blockType = SecurityEventType.VPN_BLOCKED, ipIntelligence = ipIntelligence)
        }

        // Check Proxy
        if (config.blockProxy && ipIntelligence.isProxy) {
            logger.info { "IP $ipAddress is proxy, blocking" }
            logSecurityEvent(SecurityEventType.PROXY_BLOCKED, ipAddress, userId, userEmail, requestPath, requestMethod, userAgent, ipIntelligence)
            return IpCheckResult(allowed = false, reason = "Proxy detected", blockType = SecurityEventType.PROXY_BLOCKED, ipIntelligence = ipIntelligence)
        }

        // Check Datacenter
        if (config.blockDatacenter && ipIntelligence.isDatacenter) {
            logger.info { "IP $ipAddress is datacenter IP, blocking" }
            logSecurityEvent(SecurityEventType.DATACENTER_BLOCKED, ipAddress, userId, userEmail, requestPath, requestMethod, userAgent, ipIntelligence)
            return IpCheckResult(allowed = false, reason = "Datacenter IP detected", blockType = SecurityEventType.DATACENTER_BLOCKED, ipIntelligence = ipIntelligence)
        }

        // Check Bot
        if (config.blockBots && ipIntelligence.isBot) {
            logger.info { "IP $ipAddress is bot, blocking" }
            logSecurityEvent(SecurityEventType.BOT_BLOCKED, ipAddress, userId, userEmail, requestPath, requestMethod, userAgent, ipIntelligence)
            return IpCheckResult(allowed = false, reason = "Bot detected", blockType = SecurityEventType.BOT_BLOCKED, ipIntelligence = ipIntelligence)
        }

        // Check Fraud Score
        if (ipIntelligence.fraudScore >= config.fraudScoreThreshold) {
            logger.info { "IP $ipAddress has high fraud score (${ipIntelligence.fraudScore}), blocking" }
            logSecurityEvent(SecurityEventType.HIGH_FRAUD_SCORE, ipAddress, userId, userEmail, requestPath, requestMethod, userAgent, ipIntelligence)
            return IpCheckResult(allowed = false, reason = "High fraud score: ${ipIntelligence.fraudScore}", blockType = SecurityEventType.HIGH_FRAUD_SCORE, ipIntelligence = ipIntelligence)
        }

        logger.debug { "IP $ipAddress passed all checks, allowing access" }
        return IpCheckResult(allowed = true, reason = "Passed all checks", ipIntelligence = ipIntelligence)
    }

    private fun isAllowlisted(ipAddress: String): Boolean {
        return ipListEntryRepository.existsActiveEntry(ipAddress, IpListType.ALLOWLIST)
    }

    private fun isBlocklisted(ipAddress: String): Boolean {
        return ipListEntryRepository.existsActiveEntry(ipAddress, IpListType.BLOCKLIST)
    }

    fun getSecurityConfig(): SecuritySettings {
        return securitySettingsRepository.findByIdAndDeletedAtIsNull(SecuritySettings.DEFAULT_ID)
            ?: SecuritySettings.createDefault()
    }

    private fun logSecurityEvent(
        eventType: SecurityEventType,
        ipAddress: String,
        userId: String?,
        userEmail: String?,
        requestPath: String?,
        requestMethod: String?,
        userAgent: String?,
        ipIntelligence: IpIntelligenceCache?
    ) {
        val event = SecurityEvent.createBlockedEvent(
            type = eventType,
            ipAddress = ipAddress,
            userId = userId,
            userEmail = userEmail,
            requestPath = requestPath,
            requestMethod = requestMethod,
            userAgent = userAgent,
            ipIntelligence = ipIntelligence
        )
        securityEventRepository.save(event)
    }

    /**
     * Invalidate cached IP intelligence for an IP address.
     */
    fun invalidateCache(ipAddress: String) {
        val redisKey = "$REDIS_PREFIX$ipAddress"
        redisTemplate.delete(redisKey)
        ipIntelligenceCacheRepository.findByIpAddressAndDeletedAtIsNull(ipAddress)?.let {
            it.softDelete()
            ipIntelligenceCacheRepository.save(it)
        }
        logger.info { "Invalidated IP intelligence cache for $ipAddress" }
    }
}

/**
 * Result of an IP access check.
 */
data class IpCheckResult(
    val allowed: Boolean,
    val reason: String,
    val blockType: SecurityEventType? = null,
    val ipIntelligence: IpIntelligenceCache? = null
) {
    val fraudScore: Int? get() = ipIntelligence?.fraudScore
    val isVpn: Boolean get() = ipIntelligence?.isVpn == true
    val isProxy: Boolean get() = ipIntelligence?.isProxy == true
    val isTor: Boolean get() = ipIntelligence?.isTor == true
    val isDatacenter: Boolean get() = ipIntelligence?.isDatacenter == true
    val countryCode: String? get() = ipIntelligence?.countryCode
    val city: String? get() = ipIntelligence?.city
}
