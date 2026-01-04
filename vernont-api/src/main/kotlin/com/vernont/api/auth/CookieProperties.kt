package com.vernont.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for authentication cookies.
 *
 * For production (cross-subdomain setup):
 * - Set domain to your root domain (NO leading dot per RFC 6265 - enables all subdomains)
 * - Set secure to true (HTTPS only)
 * - SameSite=Lax allows cross-subdomain requests while protecting against CSRF
 *
 * For local development:
 * - Set domain to null (uses current host)
 * - Set secure to false (allows HTTP)
 */
@ConfigurationProperties(prefix = "auth.cookie")
data class CookieProperties(
        /** Whether cookies require HTTPS. Set to false for local dev. */
        val secure: Boolean = true,

        /** SameSite attribute: "Lax" (recommended), "Strict", or "None" */
        val sameSite: String = "Lax",

        /**
         * Cookie domain for cross-subdomain support (no leading dot per RFC 6265).
         * Null means the cookie is only valid for the exact host that set it.
         */
        val domain: String? = null,

        /** Access token cookie max age in seconds. Default: 1 hour */
        val accessTokenMaxAge: Int = 3600,

        /** Refresh token cookie max age in seconds. Default: 7 days */
        val refreshTokenMaxAge: Int = 604800
)
