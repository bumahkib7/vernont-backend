package com.vernont.api.auth

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

private val logger = KotlinLogging.logger {}

data class GoogleTokenInfo(
    val aud: String? = null,
    val iss: String? = null,
    val sub: String? = null,
    val email: String? = null,
    @JsonProperty("email_verified")
    private val emailVerifiedField: Any? = null,
    @JsonProperty("given_name")
    val givenName: String? = null,
    @JsonProperty("family_name")
    val familyName: String? = null,
    val picture: String? = null,
    @JsonProperty("exp")
    private val expField: Any? = null
) {
    val emailVerified: Boolean
        get() = when (emailVerifiedField) {
            is Boolean -> emailVerifiedField
            is String -> emailVerifiedField.equals("true", ignoreCase = true) || emailVerifiedField == "1"
            else -> false
        }

    val expiresAtEpochSeconds: Long?
        get() = when (expField) {
            is Number -> expField.toLong()
            is String -> expField.toLongOrNull()
            else -> null
        }
}

@Service
class GoogleOAuthService(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${spring.security.oauth2.client.registration.google.client-id}")
    private val googleClientId: String
) {

    private val allowedIssuers = setOf("https://accounts.google.com", "accounts.google.com")

    fun verifyIdToken(idToken: String): GoogleTokenInfo? {
        return try {
            val tokenInfo = webClientBuilder.build()
                .get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                .retrieve()
                .bodyToMono(GoogleTokenInfo::class.java)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume {
                    logger.warn(it) { "Failed to fetch Google token info" }
                    Mono.empty()
                }
                .block()
                ?: return null

            if (tokenInfo.aud != googleClientId) {
                logger.warn { "Google token rejected due to audience mismatch. aud=${tokenInfo.aud}" }
                return null
            }

            if (!allowedIssuers.contains(tokenInfo.iss)) {
                logger.warn { "Google token rejected due to invalid issuer: ${tokenInfo.iss}" }
                return null
            }

            val expiresAtMs = tokenInfo.expiresAtEpochSeconds?.times(1000)
            if (expiresAtMs != null && expiresAtMs < System.currentTimeMillis()) {
                logger.warn { "Google token rejected because it is expired (exp=${tokenInfo.expiresAtEpochSeconds})" }
                return null
            }

            if (tokenInfo.email.isNullOrBlank() || tokenInfo.sub.isNullOrBlank()) {
                logger.warn { "Google token missing required fields (email/sub)" }
                return null
            }

            tokenInfo
        } catch (ex: Exception) {
            logger.error(ex) { "Unexpected error while validating Google ID token" }
            null
        }
    }
}
