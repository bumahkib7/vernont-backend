package com.vernont.api.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Service for managing cart sessions via HTTP-only cookies.
 *
 * This provides secure, server-controlled cart session management:
 * - Cart ID is stored in an HTTP-only cookie (not accessible via JavaScript)
 * - Cookie is Secure (HTTPS only in production)
 * - Cookie has SameSite=Lax for CSRF protection
 * - Server controls when cookie is set/cleared
 */
@Service
class CartSessionService(
    @Value("\${auth.cookie.secure:false}")
    private val secureCookie: Boolean,

    @Value("\${auth.cookie.same-site:Lax}")
    private val sameSite: String,

    @Value("\${auth.cookie.domain:}")
    private val cookieDomain: String,

    @Value("\${cart.session.max-age-seconds:604800}") // 7 days default
    private val maxAgeSeconds: Int
) {
    companion object {
        const val CART_SESSION_COOKIE_NAME = "vernont_cart_session"
    }

    /**
     * Get cart ID from the session cookie
     */
    fun getCartId(request: HttpServletRequest): String? {
        val cookies = request.cookies ?: return null
        val cartCookie = cookies.find { it.name == CART_SESSION_COOKIE_NAME }
        return cartCookie?.value?.takeIf { it.isNotBlank() }
    }

    /**
     * Set cart ID in HTTP-only cookie
     */
    fun setCartId(response: HttpServletResponse, cartId: String) {
        val cookie = createCookie(cartId, maxAgeSeconds)
        response.addCookie(cookie)
        logger.debug { "Cart session cookie set: cartId=$cartId" }
    }

    /**
     * Clear cart session cookie (expire immediately)
     */
    fun clearCartSession(response: HttpServletResponse) {
        val cookie = createCookie("", 0)
        response.addCookie(cookie)
        logger.debug { "Cart session cookie cleared" }
    }

    /**
     * Create a cookie with proper security settings
     */
    private fun createCookie(value: String, maxAge: Int): Cookie {
        return Cookie(CART_SESSION_COOKIE_NAME, value).apply {
            isHttpOnly = true
            secure = secureCookie
            path = "/"
            this.maxAge = maxAge

            // Set domain if configured (empty means use request domain)
            if (cookieDomain.isNotBlank()) {
                domain = cookieDomain
            }

            // SameSite attribute - set via response header since Cookie class doesn't support it directly
            setAttribute("SameSite", sameSite)
        }
    }
}
