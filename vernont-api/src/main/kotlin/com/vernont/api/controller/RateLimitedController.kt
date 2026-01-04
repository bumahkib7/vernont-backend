package com.vernont.api.controller

import com.vernont.application.security.RedisRateLimiter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/ratelimited")
class RateLimitedController(
    private val redisRateLimiter: RedisRateLimiter
) {

    @GetMapping("/test")
    fun testRateLimit(
        request: HttpServletRequest,
        @RequestParam(defaultValue = "10") limit: Long,
        @RequestParam(defaultValue = "60") window: Long
    ): ResponseEntity<String> {
        val ipAddress = request.remoteAddr
        val key = "ratelimit:test:$ipAddress"

        if (redisRateLimiter.isAllowed(key, limit, window)) {
            logger.info { "Request from $ipAddress allowed. Current count: ${redisRateLimiter.isAllowed(key, limit, window)}" } // Note: this call will increment again
            return ResponseEntity.ok("Request allowed! Try again.")
        } else {
            logger.warn { "Request from $ipAddress rate-limited." }
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Too many requests. Please try again later.")
        }
    }

    @GetMapping("/reset")
    fun resetRateLimit(request: HttpServletRequest): ResponseEntity<String> {
        val ipAddress = request.remoteAddr
        val key = "ratelimit:test:$ipAddress"
        redisRateLimiter.reset(key)
        return ResponseEntity.ok("Rate limit for $ipAddress reset.")
    }
}
