package com.vernont.api.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestSanitizationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI ?: ""
        val query = request.queryString ?: ""
        if (isSuspicious(uri, query)) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun isSuspicious(uri: String, query: String): Boolean {
        val target = "${uri}?${query}".lowercase()
        if (target.contains("\\") || target.contains("%5c")) return true
        if (target.contains("\u0000") || target.contains("%00")) return true
        if (target.contains("/.env") || target.contains("wp-admin") || target.contains("wp-login")) return true
        if (target.contains("index.php") && target.contains("think")) return true
        return false
    }
}
