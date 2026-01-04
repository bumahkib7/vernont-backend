package com.vernont.api.rate

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.concurrent.TimeUnit

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
class RateLimitException(message: String) : RuntimeException(message)

@Aspect
@Configuration
@Order(1)
class RateLimitAspect(
    private val redisTemplate: StringRedisTemplate,
    @org.springframework.beans.factory.annotation.Value($$"${FORGOT_PW_LIMIT:5}") private val forgotPwLimit: Int,
    @org.springframework.beans.factory.annotation.Value($$"${FORGOT_PW_WINDOW:600}") private val forgotPwWindow: Long,
) {
    private val logger = KotlinLogging.logger {}

    @Around("@annotation(rateLimited)")
    fun around(joinPoint: ProceedingJoinPoint, rateLimited: RateLimited): Any? {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        // Extract IP from X-Forwarded-For, X-Real-IP, or remoteAddr
        val ip = request?.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request?.getHeader("X-Real-IP")
            ?: request?.remoteAddr

        val emailFromArgs = joinPoint.args.firstOrNull { it is Map<*, *> && it.containsKey("email") }
            ?.let { (it as Map<*, *>) ["email"] as? String }

        val keyParts = mutableListOf(rateLimited.keyPrefix)
        if (rateLimited.perIp) keyParts += (ip ?: "unknown-ip")
        if (rateLimited.perEmail) keyParts += (emailFromArgs ?: "no-email")
        val key = keyParts.joinToString(":")

        val isForgotPath = request?.requestURI?.contains("/api/forgot-password") == true
        val limit = if (isForgotPath) forgotPwLimit else rateLimited.limit
        val window = if (isForgotPath) forgotPwWindow else rateLimited.windowSeconds

        try {
            val ops = redisTemplate.opsForValue()
            val count = ops.increment(key) ?: 1
            if (count == 1L) {
                redisTemplate.expire(key, window, TimeUnit.SECONDS)
            }
            if (count > limit) {
                logger.warn { "SECURITY: Rate limit exceeded for key=$key, ip=$ip, email=$emailFromArgs, endpoint=${request?.requestURI}" }
                throw RateLimitException("Too many requests. Try again later.")
            }
        } catch (e: RateLimitException) {
            throw e  // Re-throw rate limit exceptions
        } catch (e: Exception) {
            // Changed behavior: Check failClosed flag
            if (rateLimited.failClosed) {
                logger.error(e) { "SECURITY: Rate limit check failed (fail-closed mode), blocking request. key=$key" }
                throw RateLimitException("Service temporarily unavailable. Please try again later.")
            } else {
                logger.warn(e) { "RateLimitAspect failure (fail-open mode), allowing request. key=$key" }
            }
        }

        return joinPoint.proceed()
    }
}
