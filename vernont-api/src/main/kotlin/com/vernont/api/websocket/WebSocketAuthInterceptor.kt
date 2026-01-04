package com.vernont.api.websocket

import com.vernont.api.auth.JwtTokenProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

private val logger = KotlinLogging.logger {}

@Component
class WebSocketAuthInterceptor(private val jwtTokenProvider: JwtTokenProvider) :
        HandshakeInterceptor {

    override fun beforeHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            attributes: MutableMap<String, Any>
    ): Boolean {
        // Extract token from query parameter or header
        val token = extractToken(request)

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            logger.warn {
                "WebSocket handshake rejected: Invalid or missing token from ${request.remoteAddress}"
            }
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }

        val tokenType = jwtTokenProvider.getTokenType(token)
        if (tokenType != "access") {
            logger.warn { "WebSocket handshake rejected: Invalid token type $tokenType" }
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }

        // Extract user context and store in session attributes
        val userContext = jwtTokenProvider.getUserContextFromToken(token)
        if (userContext == null) {
            logger.warn { "WebSocket handshake rejected: Could not extract user context" }
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }

        // Store user context for use in channel interceptor
        attributes["userContext"] = userContext
        attributes["userId"] = userContext.userId
        attributes["roles"] = userContext.roles

        logger.info {
            "WebSocket handshake accepted for user: ${userContext.email} (${userContext.userId})"
        }
        return true
    }

    override fun afterHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            exception: Exception?
    ) {
        // No-op
    }

    private fun extractToken(request: ServerHttpRequest): String? {
        // 1. Try cookie first (new secure method - matches JwtAuthenticationFilter)
        val cookieHeader = request.headers.getFirst("Cookie")
        if (cookieHeader != null) {
            val cookies = cookieHeader.split(";").map { it.trim() }
            for (cookie in cookies) {
                val parts = cookie.split("=", limit = 2)
                if (parts.size == 2 && parts[0] == "access_token") {
                    return parts[1]
                }
            }
        }

        // 2. Try query parameter (for WebSocket clients that can't set headers)
        val uri = request.uri
        val query = uri.query
        if (query != null) {
            val params = query.split("&")
            for (param in params) {
                val parts = param.split("=", limit = 2)
                if (parts.size == 2 && parts[0] == "token") {
                    return parts[1]
                }
            }
        }

        // 3. Try Authorization header (backward compatibility)
        val authHeader = request.headers.getFirst("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        return null
    }
}
