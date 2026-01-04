package com.vernont.api.controller

import com.vernont.infrastructure.config.Argon2PasswordEncoder
import com.vernont.repository.auth.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import javax.crypto.SecretKey
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/reset-password")
class ResetPasswordController(
    private val userRepository: UserRepository,
    private val passwordEncoder: Argon2PasswordEncoder,
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
) {
    private val logger = KotlinLogging.logger {}

    data class ResetPasswordRequest(
        @field:NotBlank val token: String,
        @field:NotBlank val newPassword: String
    )
    data class ResetPasswordResponse(val ok: Boolean)

    @PostMapping
    fun resetPassword(@RequestBody req: ResetPasswordRequest): ResponseEntity<Any> {
        return try {
            val secretKey = buildSecretKey(jwtSecret)
            val claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(req.token)
                .payload
            val email = claims.subject ?: return bad("INVALID_TOKEN", "Missing subject")
            val exp = claims.expiration ?: Date(0)
            if (Date().after(exp)) return bad("TOKEN_EXPIRED", "Reset token has expired")

            val user = userRepository.findByEmail(email)
                ?: return bad("USER_NOT_FOUND", "No user found for token")

            user.passwordHash = passwordEncoder.encode(req.newPassword)
            userRepository.save(user)

            ResponseEntity.ok(ResetPasswordResponse(true))
        } catch (e: WeakKeyException) {
            logger.error(e) { "Weak JWT key for reset token" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to "WEAK_KEY",
                "message" to "Server configuration error"
            ))
        } catch (e: Exception) {
            logger.warn(e) { "Failed to reset password: ${'$'}{e.message}" }
            bad("INVALID_TOKEN", "Invalid or expired token")
        }
    }

    private fun bad(code: String, msg: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to code, "message" to msg))

    private fun buildSecretKey(secret: String): SecretKey {
        val raw = secret.trim()
        val decoded = try {
            Base64.getDecoder().decode(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
        val keyBytes = (decoded?.takeIf { it.isNotEmpty() } ?: raw.toByteArray())
        val minBits = 256 // HS256 minimum
        if (keyBytes.size * 8 < minBits) {
            throw WeakKeyException("JWT secret too weak. Provide a key with at least $minBits bits.")
        }
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
