package com.vernont.infrastructure.security

import com.vernont.domain.security.IpIntelligenceCache
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Client for IPQualityScore API.
 * Provides VPN/proxy detection, fraud scoring, and IP geolocation.
 *
 * API Documentation: https://www.ipqualityscore.com/documentation/overview
 */
@Component
class IpQualityScoreClient(
    webClientBuilder: WebClient.Builder,
    @Value("\${app.ipqs.api-key:}") private val apiKey: String,
    @Value("\${app.ipqs.enabled:true}") private val enabled: Boolean,
    @Value("\${app.ipqs.timeout-ms:5000}") private val timeoutMs: Long,
    @Value("\${app.ipqs.cache-ttl-hours:24}") private val cacheTtlHours: Long
) {
    private val webClient = webClientBuilder
        .baseUrl("https://ipqualityscore.com")
        .build()

    /**
     * Get IP intelligence from IPQualityScore API.
     * Returns null if the API is disabled or if the request fails.
     */
    suspend fun getIpIntelligence(ipAddress: String): IpIntelligenceCache? {
        if (!enabled) {
            logger.debug { "IPQS disabled, skipping lookup for $ipAddress" }
            return null
        }

        if (apiKey.isBlank()) {
            logger.warn { "IPQS API key not configured" }
            return null
        }

        return try {
            logger.debug { "Fetching IP intelligence for $ipAddress" }

            val response = webClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/json/ip/{apiKey}/{ipAddress}")
                        .queryParam("strictness", 1)
                        .queryParam("user_agent", "")
                        .queryParam("user_language", "")
                        .queryParam("fast", true)
                        .queryParam("mobile", true)
                        .queryParam("allow_public_access_points", true)
                        .queryParam("lighter_penalties", false)
                        .build(apiKey, ipAddress)
                }
                .retrieve()
                .awaitBodyOrNull<IpqsResponse>()

            if (response == null) {
                logger.warn { "IPQS returned null response for $ipAddress" }
                return null
            }

            if (!response.success) {
                logger.warn { "IPQS request failed for $ipAddress: ${response.message}" }
                return null
            }

            logger.info { "IPQS response for $ipAddress: fraud_score=${response.fraud_score}, vpn=${response.vpn}, proxy=${response.proxy}, tor=${response.tor}" }

            IpIntelligenceCache().apply {
                this.ipAddress = ipAddress
                this.fraudScore = response.fraud_score ?: 0
                this.isVpn = response.vpn ?: false
                this.isProxy = response.proxy ?: false
                this.isTor = response.tor ?: false
                this.isDatacenter = response.is_datacenter ?: false
                this.isBot = response.bot_status ?: false
                this.isCrawler = response.is_crawler ?: false
                this.countryCode = response.country_code
                this.city = response.city
                this.region = response.region
                this.isp = response.ISP
                this.organization = response.organization
                this.asn = response.ASN
                this.latitude = response.latitude
                this.longitude = response.longitude
                this.timezone = response.timezone
                this.mobile = response.mobile ?: false
                this.host = response.host
                this.rawResponse = mapOf(
                    "fraud_score" to response.fraud_score,
                    "vpn" to response.vpn,
                    "proxy" to response.proxy,
                    "tor" to response.tor,
                    "is_datacenter" to response.is_datacenter,
                    "bot_status" to response.bot_status,
                    "recent_abuse" to response.recent_abuse,
                    "request_id" to response.request_id
                )
                this.expiresAt = Instant.now().plusSeconds(cacheTtlHours * 60 * 60)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch IP intelligence for $ipAddress" }
            null
        }
    }

    fun isEnabled(): Boolean = enabled && apiKey.isNotBlank()
}

/**
 * IPQualityScore API response DTO.
 */
data class IpqsResponse(
    val success: Boolean = false,
    val message: String? = null,
    val fraud_score: Int? = null,
    val country_code: String? = null,
    val region: String? = null,
    val city: String? = null,
    val ISP: String? = null,
    val ASN: Int? = null,
    val organization: String? = null,
    val host: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val mobile: Boolean? = null,
    val proxy: Boolean? = null,
    val vpn: Boolean? = null,
    val tor: Boolean? = null,
    val active_vpn: Boolean? = null,
    val active_tor: Boolean? = null,
    val is_datacenter: Boolean? = null,
    val recent_abuse: Boolean? = null,
    val bot_status: Boolean? = null,
    val is_crawler: Boolean? = null,
    val connection_type: String? = null,
    val abuse_velocity: String? = null,
    val request_id: String? = null
)
