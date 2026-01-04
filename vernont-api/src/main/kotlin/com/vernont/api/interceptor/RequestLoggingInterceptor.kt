package com.vernont.api.interceptor

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.util.ContentCachingRequestWrapper
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

@Component
class RequestLoggingInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request is ContentCachingRequestWrapper) {
            val requestBody = String(request.contentAsByteArray, StandardCharsets.UTF_8)
            logger.info { "Incoming request: ${request.method} ${request.requestURI} - Body: $requestBody" }
        } else {
            logger.info { "Incoming request: ${request.method} ${request.requestURI}" }
        }
        return true
    }
}
