package com.vernont.api.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(private val jwtTokenProvider: JwtTokenProvider) :
        OncePerRequestFilter() {

    override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain
    ) {
        try {
            val jwt = getJwtFromRequest(request)

            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                val tokenType = jwtTokenProvider.getTokenType(jwt)

                if (tokenType == "access") {
                    val userContext = jwtTokenProvider.getUserContextFromToken(jwt)

                    if (userContext != null) {
                        val authentication =
                                UsernamePasswordAuthenticationToken(
                                        userContext,
                                        null,
                                        userContext.authorities
                                )
                        authentication.details =
                                WebAuthenticationDetailsSource().buildDetails(request)

                        SecurityContextHolder.getContext().authentication = authentication
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("Could not set user authentication in security context", ex)
        }

        filterChain.doFilter(request, response)
    }

    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val path = request.requestURI

        val cookieName = when {
            path.startsWith("/admin") || path.startsWith("/api/v1/internal") || path.startsWith("/api/admin") ->
                com.vernont.api.controller.InternalAuthController.ACCESS_TOKEN_COOKIE
            else ->
                AuthController.ACCESS_TOKEN_COOKIE
        }

        request.cookies?.find { it.name == cookieName }?.value?.let {
            return it
        }

        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else null
    }
}
