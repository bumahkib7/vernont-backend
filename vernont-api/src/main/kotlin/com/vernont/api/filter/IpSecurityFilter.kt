package com.vernont.api.filter

import com.vernont.domain.auth.UserContext
import com.vernont.infrastructure.security.IpIntelligenceService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private val logger = KotlinLogging.logger {}

/**
 * IP Security Filter for admin panel protection.
 * Checks IP intelligence and blocks suspicious requests (VPN, proxy, high fraud score).
 *
 * Filter order: After JwtAuthenticationFilter
 * Protected paths: admin panel and internal API
 * Excluded paths: login, logout, refresh endpoints
 */
@Component
class IpSecurityFilter(
    private val ipIntelligenceService: IpIntelligenceService,
    @Value("\${app.ip-security.enabled:true}") private val enabled: Boolean
) : OncePerRequestFilter() {

    companion object {
        private val PROTECTED_PATHS = listOf("/admin/", "/api/v1/internal/")
        private val EXCLUDED_PATHS = listOf(
            "/api/v1/internal/auth/login",
            "/api/v1/internal/auth/logout",
            "/api/v1/internal/auth/refresh"
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI

        if (!enabled) {
            return true
        }

        if (EXCLUDED_PATHS.any { path.startsWith(it) }) {
            return true
        }

        return !PROTECTED_PATHS.any { path.startsWith(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ipAddress = getClientIpAddress(request)
        val path = request.requestURI
        val method = request.method
        val userAgent = request.getHeader("User-Agent")

        val authentication = SecurityContextHolder.getContext().authentication
        val userContext = authentication?.principal as? UserContext
        val userId = userContext?.userId
        val userEmail = userContext?.email

        logger.debug { "IP Security check for $method $path from $ipAddress (user: $userEmail)" }

        try {
            val result = ipIntelligenceService.checkIpAccess(
                ipAddress = ipAddress,
                userId = userId,
                userEmail = userEmail,
                requestPath = path,
                requestMethod = method,
                userAgent = userAgent
            )

            if (!result.allowed) {
                logger.info { "Blocking request from $ipAddress to $path: ${result.reason}" }
                writeBlockedResponse(response, result.reason, result.blockType?.displayName)
                return
            }

            request.setAttribute("ipCheckResult", result)

        } catch (e: Exception) {
            logger.error { "Error checking IP security for $ipAddress: ${e.message}" }
        }

        filterChain.doFilter(request, response)
    }

    private fun getClientIpAddress(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            return xForwardedFor.split(",").first().trim()
        }

        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp.trim()
        }

        val cfConnectingIp = request.getHeader("CF-Connecting-IP")
        if (!cfConnectingIp.isNullOrBlank()) {
            return cfConnectingIp.trim()
        }

        return request.remoteAddr ?: "unknown"
    }

    private fun writeBlockedResponse(response: HttpServletResponse, reason: String, blockType: String?) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        val code = blockType ?: "IP_BLOCKED"
        response.writer.write("""{"error":"Access Denied","message":"$reason","code":"$code","status":403}""")
    }
}
