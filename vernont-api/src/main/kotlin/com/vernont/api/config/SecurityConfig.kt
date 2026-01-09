package com.vernont.api.config

import com.vernont.api.auth.JwtAuthenticationFilter
import com.vernont.domain.auth.Role
import com.vernont.infrastructure.config.Argon2PasswordEncoder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
        private val jwtAuthenticationFilter: JwtAuthenticationFilter,
        private val argon2PasswordEncoder: Argon2PasswordEncoder,
        @Value(
                "\${spring.security.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:8080,http://127.0.0.1:3000,http://127.0.0.1:3001,http://127.0.0.1:8080}"
        )
        private val corsAllowedOrigins: List<String>,
        @Value(
                "\${spring.security.cors.allowed-origin-patterns:https://*.vernont.com,https://vernont.com,https://*.vercel.app}"
        )
        private val corsAllowedOriginPatterns: List<String>
) {

    @Bean fun passwordEncoder(): PasswordEncoder = argon2PasswordEncoder

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsAllowedOrigins
        configuration.allowedOriginPatterns = corsAllowedOriginPatterns
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.exposedHeaders = listOf("Authorization")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .exceptionHandling { exc ->
                    exc.authenticationEntryPoint { _, resp, _ ->
                        resp.status = 401
                        resp.contentType = "application/json"
                        resp.writer.write(
                                """{"error":"Unauthorized","message":"Invalid or missing token"}"""
                        )
                    }
                    exc.accessDeniedHandler { _, resp, _ ->
                        resp.status = 403
                        resp.contentType = "application/json"
                        resp.writer.write(
                                """{"error":"Forbidden","message":"Insufficient permissions"}"""
                        )
                    }
                }
                .headers { headers ->
                    headers
                            // Clickjacking protection
                            .frameOptions { it.deny() }

                            // HSTS - enforce HTTPS for 1 year + subdomains + preload
                            .httpStrictTransportSecurity { hsts ->
                                hsts.maxAgeInSeconds(31536000).includeSubDomains(true).preload(true)
                            }

                            // Prevent MIME-type sniffing
                            .contentTypeOptions {} // Enables X-Content-Type-Options: nosniff

                            // Privacy-preserving referrer policy
                            .referrerPolicy { referrer ->
                                referrer.policy(
                                        ReferrerPolicyHeaderWriter.ReferrerPolicy
                                                .STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                                )
                            }

                            // Permissions-Policy - disable unnecessary/dangerous browser features
                            .permissionsPolicyHeader { policy ->
                                policy.policy(
                                        "geolocation=()," +
                                                "microphone=()," +
                                                "camera=()," +
                                                "payment=()," +
                                                "usb=()," +
                                                "accelerometer=()," +
                                                "gyroscope=()," +
                                                "magnetometer=()," +
                                                "fullscreen=(self)," +
                                                "autoplay=()," +
                                                "sync-xhr=()"
                                )
                            }

                            // Cross-Origin-Opener-Policy (COOP) - process isolation
                            .crossOriginOpenerPolicy { coop ->
                                coop.policy(
                                        CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy
                                                .SAME_ORIGIN
                                )
                            }

                            // Cross-Origin-Embedder-Policy (COEP) - requires CORP on cross-origin
                            // resources
                            .crossOriginEmbedderPolicy { coep ->
                                coep.policy(
                                        CrossOriginEmbedderPolicyHeaderWriter
                                                .CrossOriginEmbedderPolicy.REQUIRE_CORP
                                )
                            }

                            // Cross-Origin-Resource-Policy (CORP) - prevents cross-origin loading
                            // unless explicitly allowed
                            .crossOriginResourcePolicy { corp ->
                                corp.policy(
                                        CrossOriginResourcePolicyHeaderWriter
                                                .CrossOriginResourcePolicy.SAME_ORIGIN
                                )
                            }

                            // Content-Security-Policy
                            .contentSecurityPolicy { csp ->
                                csp.policyDirectives(
                                        "default-src 'self';" +
                                                "script-src 'self' https://www.googletagmanager.com https://www.google-analytics.com;" +
                                                "style-src 'self' 'unsafe-inline';" +
                                                "img-src 'self' data: https: blob:;" +
                                                "font-src 'self' data:;" +
                                                "connect-src 'self' https://www.google-analytics.com https://accounts.google.com;" +
                                                "frame-src 'self' https://accounts.google.com;" +
                                                "object-src 'none';" +
                                                "frame-ancestors 'none';" +
                                                "base-uri 'self';" +
                                                "form-action 'self';" +
                                                "upgrade-insecure-requests;" +
                                                "block-all-mixed-content;"
                                )
                            }
                }
                .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
                .csrf { csrf -> csrf.disable() }
                .sessionManagement { session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                }
                .authorizeHttpRequests { authorize ->
                    authorize
                            .requestMatchers(HttpMethod.OPTIONS, "/**")
                            .permitAll()
                            // Public storefront endpoints - accessible to everyone including guests
                            .requestMatchers("/storefront/**")
                            .permitAll()
                            .requestMatchers("/store/auth/register")
                            .permitAll()
                            .requestMatchers("/store/auth/login")
                            .permitAll()
                            .requestMatchers("/store/auth/refresh")
                            .permitAll()
                            .requestMatchers("/store/auth/forgot-password")
                            .permitAll()
                            .requestMatchers("/store/auth/reset-password")
                            .permitAll()
                            // Order tracking - public access
                            .requestMatchers("/store/orders/track")
                            .permitAll()

                            // Internal/Admin authentication endpoints
                            .requestMatchers("/api/v1/internal/auth/login")
                            .permitAll()
                            .requestMatchers("/api/v1/internal/auth/refresh")
                            .permitAll()
                            // /me only requires a valid token; role is embedded in the token claims
                            .requestMatchers("/api/v1/internal/auth/me")
                            .authenticated()

                            // Admin uploads (used by admin UI) - require admin or customer service
                            // role
                            .requestMatchers("/admin/uploads/**")
                            .hasAnyRole(Role.ADMIN, Role.CUSTOMER_SERVICE)

                            // Affiliate endpoints - MUST be first to avoid conflicts
                            .requestMatchers("/api/affiliate/**")
                            .permitAll()
                            .requestMatchers("/affiliate/**")
                            .permitAll()

                            // Chatbot endpoints - public access for AI shopping assistant
                            .requestMatchers("/api/chatbot/**")
                            .permitAll()

                            // Meta endpoints - public analytics and tracking
                            .requestMatchers("/api/meta/**")
                            .permitAll()

                            // Health checks and monitoring
                            .requestMatchers("/actuator/health", "/actuator/health/**")
                            .permitAll()
                            .requestMatchers("/actuator/info")
                            .permitAll()
                            .requestMatchers("/actuator/metrics")
                            .permitAll()
                            .requestMatchers("/actuator/prometheus")
                            .permitAll()
                            // All other actuator endpoints require ADMIN role
                            .requestMatchers("/actuator/**")
                            .hasRole(Role.ADMIN)
                            .requestMatchers("/sitemap.xml", "/sitemap-*.xml")
                            .permitAll()

                        // WebSocket endpoints - authentication handled by WebSocketAuthInterceptor
                        .requestMatchers("/ws/**").permitAll()

                        // Stripe webhooks - must be public (verified by signature)
                        .requestMatchers("/webhooks/**").permitAll()

                        // OpenAPI/Swagger UI
                            .requestMatchers("/swagger-ui/**")
                            .permitAll()
                            .requestMatchers("/v3/api-docs/**")
                            .permitAll()
                            .requestMatchers("/swagger-ui.html")
                            .permitAll()

                            // Public aggregator endpoints
                            .requestMatchers("/api/aggregator/**")
                            .permitAll()

                            // Search endpoints - public access for admin panel testing
                            .requestMatchers("/api/v1/search/**")
                            .permitAll()

                            // Guest cart operations (session-based)
                            .requestMatchers("/store/carts")
                            .permitAll()
                            .requestMatchers("/store/carts/{cartId}")
                            .permitAll()
                            .requestMatchers("/store/carts/{cartId}/line-items")
                            .permitAll()

                            // Customer-only endpoints - require authentication (MUST come before
                            // /store/**)
                            .requestMatchers("/store/customers/**")
                            .hasRole(Role.CUSTOMER)
                            .requestMatchers("/store/orders/**")
                            .hasRole(Role.CUSTOMER)
                            .requestMatchers("/api/carts/**")
                            .hasAnyRole(Role.CUSTOMER, Role.ADMIN)

                            // Other store endpoints - accessible to everyone
                            .requestMatchers("/store/**")
                            .permitAll()
                            .requestMatchers("/auth/**")
                            .permitAll()

                            // Admin endpoints - granular rules first, then general rules
                            // General admin areas
                            .requestMatchers("/admin/**")
                            .hasAnyRole(
                                    Role.ADMIN,
                                    Role.CUSTOMER_SERVICE,
                                    Role.WAREHOUSE_MANAGER,
                                    Role.DEVELOPER
                            )
                            .requestMatchers("/api/admin/**")
                            .hasAnyRole(Role.ADMIN, Role.CUSTOMER_SERVICE)
                            // FlexOffers and CJ admin APIs - secured at controller level with
                            // @PreAuthorize
                            .requestMatchers("/api/v1/admin/flexoffers/**")
                            .hasRole(Role.ADMIN)
                            .requestMatchers("/api/v1/admin/cj/**")
                            .hasAnyRole(
                                    Role.ADMIN,
                                    Role.CUSTOMER_SERVICE,
                                    Role.WAREHOUSE_MANAGER,
                                    Role.DEVELOPER
                            )

                            // Rate-limited endpoints
                            .requestMatchers("/rate-limited/**")
                            .permitAll()
                        .requestMatchers("/admin/products")
                        .hasRole(Role.ADMIN)
                            // Default: require authentication for any other endpoint
                            .anyRequest()
                            .authenticated()
                }
                .httpBasic { it.disable() }
                .formLogin { it.disable() }
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter::class.java
                )

        return http.build()
    }
}
