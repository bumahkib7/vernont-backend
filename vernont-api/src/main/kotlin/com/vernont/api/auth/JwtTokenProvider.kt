package com.vernont.api.auth

import com.vernont.domain.auth.User
import com.vernont.domain.auth.UserContext
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import java.util.*
import java.util.Base64
import javax.crypto.SecretKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider {

    @Value("\${app.jwt.secret:69df2472bc8e9bf6d554dfc62bcf12dec4d0168a4abba489e7adf8deccfe7f22b3710eea5d92001fe5adb4337db15face3afd65dba4509f5237297102d382c3a}")
    private lateinit var jwtSecret: String

    @Value("\${app.jwt.expiration-ms:86400000}") // 24 hours
    private var jwtExpirationMs: Long = 86400000

    @Value("\${app.jwt.refresh-expiration-ms:604800000}") // 7 days
    private var jwtRefreshExpirationMs: Long = 604800000

    fun generateAccessToken(user: User, customerId: String? = null): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        val roles = user.roles.joinToString(",") { it.name }

        val builder =
                Jwts.builder()
                        .subject(user.id)
                        .claim("email", user.email)
                        .claim("firstName", user.firstName)
                        .claim("lastName", user.lastName)
                        .claim("emailVerified", user.emailVerified)
                        .claim("roles", roles)
                        .claim("type", "access")
                        .issuedAt(now)
                        .expiration(expiryDate)

        // Include customerId if provided
        if (customerId != null) {
            builder.claim("customerId", customerId)
        }

        return builder.signWith(secretKey).compact()
    }

    fun generateRefreshToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtRefreshExpirationMs)

        return Jwts.builder()
                .subject(user.id)
                .claim("email", user.email)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact()
    }

    private val secretKey: SecretKey by lazy { buildSecretKey(jwtSecret) }

    fun getUserIdFromToken(token: String): String {
        val claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

        return claims.subject
    }

    fun getEmailFromToken(token: String): String {
        val claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

        return claims["email"] as String
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)
            true
        } catch (ex: Exception) {
            false
        }
    }

    fun getTokenType(token: String): String? {
        return try {
            val claims =
                    Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

            claims["type"] as? String
        } catch (ex: Exception) {
            null
        }
    }

    fun getRolesFromToken(token: String): List<String> {
        return try {
            val claims =
                    Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

            val rolesString = claims["roles"] as? String
            rolesString?.split(",") ?: emptyList()
        } catch (ex: Exception) {
            emptyList()
        }
    }

    /**
     * Extract full user context from JWT token Returns UserContext with all user information
     * embedded in the token
     */
    fun getUserContextFromToken(token: String): UserContext? {
        return try {
            val claims =
                    Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

            val userId = claims.subject
            val email = claims["email"] as? String ?: return null
            val firstName = claims["firstName"] as? String
            val lastName = claims["lastName"] as? String
            val emailVerified = claims["emailVerified"] as? Boolean ?: true
            val rolesString = claims["roles"] as? String
            val roles = rolesString?.split(",") ?: emptyList()
            val customerId = claims["customerId"] as? String

            UserContext(
                    userId = userId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    roles = roles,
                    customerId = customerId,
                    emailVerified = emailVerified
            )
        } catch (ex: Exception) {
            null
        }
    }

    fun getFirstNameFromToken(token: String): String? {
        return try {
            val claims =
                    Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

            claims["firstName"] as? String
        } catch (ex: Exception) {
            null
        }
    }

    fun getLastNameFromToken(token: String): String? {
        return try {
            val claims =
                    Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

            claims["lastName"] as? String
        } catch (ex: Exception) {
            null
        }
    }

    fun getCustomerIdFromToken(token: String): String? {
        return try {
            val claims =
                    Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload

            claims["customerId"] as? String
        } catch (ex: Exception) {
            null
        }
    }

    private fun buildSecretKey(secret: String): SecretKey {
        val raw = secret.trim()
        val decoded =
                try {
                    Base64.getDecoder().decode(raw)
                } catch (_: IllegalArgumentException) {
                    null
                }
        val keyBytes = (decoded?.takeIf { it.isNotEmpty() } ?: raw.toByteArray())
        // HS512 requires at least 512 bits (64 bytes)
        val minBits = 512
        if (keyBytes.size * 8 < minBits) {
            throw WeakKeyException(
                    "JWT secret too weak for HS512. Provide a key with at least $minBits bits (e.g. 'openssl rand -base64 64')."
            )
        }
        return Keys.hmacShaKeyFor(keyBytes)
    }
}

class UnauthorizedException(message: String) : RuntimeException(message)
