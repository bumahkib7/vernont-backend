package com.vernont.infrastructure.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder as SpringArgon2PasswordEncoder
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Argon2id Password Encoder (wrapper over Spring's implementation).
 *
 * Extends Spring's Argon2PasswordEncoder to keep all internals (BouncyCastle, encoding
 * format, upgradeEncoding, etc.) and adds:
 *  - Config via application properties
 *  - Extra logging
 *  - needsRehashing(...) + upgradePassword(...)
 */
@Component
class Argon2PasswordEncoder(

    @Value("\${app.security.argon2.salt-length:16}")
    private val saltLength: Int,

    @Value("\${app.security.argon2.hash-length:32}")
    private val hashLength: Int,

    @Value("\${app.security.argon2.parallelism:1}")
    private val parallelism: Int,

    // default same as Spring: 1 << 14 (16 MB)
    @Value("\${app.security.argon2.memory:16384}")
    private val memory: Int,

    // default same as Spring: 2 iterations
    @Value("\${app.security.argon2.iterations:2}")
    private val iterations: Int
) : SpringArgon2PasswordEncoder(
    saltLength,
    hashLength,
    parallelism,
    memory,
    iterations
) {

    init {
        logger.info {
            "Argon2PasswordEncoder (Spring-based) initialized with params: " +
                    "saltLength=${saltLength}B, hashLength=${hashLength}B, " +
                    "memory=${memory}KB, iterations=$iterations, parallelism=$parallelism"
        }
    }

    /**
     * Encode with extra logging, still using Spring's implementation.
     */
    override fun encode(rawPassword: CharSequence): String {
        return try {
            val hash = super.encode(rawPassword)
            logger.debug { "Password encoded successfully (Argon2)" }
            hash
        } catch (e: Exception) {
            logger.error(e) { "Failed to encode password with Argon2" }
            throw RuntimeException("Password encoding failed", e)
        }
    }

    /**
     * Verify with extra logging, still using Spring's implementation.
     */
    override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean {
        return try {
            val matches = super.matches(rawPassword, encodedPassword)
            logger.debug { "Password verification: ${if (matches) "success" else "failed"}" }
            matches
        } catch (e: Exception) {
            logger.warn(e) { "Password verification error (Argon2)" }
            false
        }
    }

    /**
     * Check if the encoded password needs rehashing with updated parameters.
     *
     * Just wraps Spring's upgradeEncoding(...).
     */
    fun needsRehashing(encodedPassword: String): Boolean {
        return try {
            val needsUpgrade = super.upgradeEncoding(encodedPassword)
            if (needsUpgrade) {
                logger.info { "Encoded password requires Argon2 rehash with stronger parameters" }
            }
            needsUpgrade
        } catch (e: Exception) {
            logger.warn(e) { "Failed to evaluate Argon2 hash for upgrade" }
            true // be conservative: rehash if unsure
        }
    }

    /**
     * Upgrade password hash if needed during login.
     *
     * Usage:
     *   if (passwordEncoder.matches(raw, user.password)) {
     *       (passwordEncoder as Argon2PasswordEncoder)
     *           .upgradePassword(raw, user.password)
     *           ?.let { newHash ->
     *               user.password = newHash
     *               userRepo.save(user)
     *           }
     *   }
     */
    fun upgradePassword(rawPassword: CharSequence, currentHash: String): String? {
        return if (needsRehashing(currentHash)) {
            logger.info { "Upgrading password hash with stronger Argon2 parameters (Spring-based)" }
            encode(rawPassword)
        } else {
            null
        }
    }
}
