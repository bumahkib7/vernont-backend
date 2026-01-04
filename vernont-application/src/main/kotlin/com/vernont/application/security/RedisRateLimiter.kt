package com.vernont.application.security

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class RedisRateLimiter(
    private val redisTemplate: StringRedisTemplate
) {

    /**
     * Checks if a request is allowed based on a rate limit.
     * Implements a fixed window counter algorithm.
     *
     * @param key The unique key for the rate limit (e.g., user ID, IP address, API endpoint).
     * @param limit The maximum number of requests allowed within the time window.
     * @param timeWindowSeconds The duration of the time window in seconds.
     * @return `true` if the request is allowed, `false` otherwise.
     */
    fun isAllowed(key: String, limit: Long, timeWindowSeconds: Long): Boolean {
        val ops = redisTemplate.opsForValue()
        val currentCount = ops.increment(key, 1) // Increment and get current count

        if (currentCount == null) {
            // Should not happen if ops.increment is used correctly, but handle defensively
            logger.warn { "Redis increment operation returned null for key: $key" }
            return false // Deny request if Redis state is uncertain
        }

        if (currentCount == 1L) {
            // If it's the first request in the window, set expiry
            redisTemplate.expire(key, Duration.ofSeconds(timeWindowSeconds))
        }

        return currentCount <= limit
    }

    /**
     * Resets the rate limit for a given key by deleting it from Redis.
     *
     * @param key The unique key for the rate limit.
     */
    fun reset(key: String) {
        redisTemplate.delete(key)
        logger.info { "Rate limit for key '$key' has been reset." }
    }
}
