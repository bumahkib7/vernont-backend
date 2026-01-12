package com.vernont.api.controller

import com.vernont.api.auth.CookieProperties
import com.vernont.api.auth.JwtTokenProvider
import com.vernont.application.security.SessionTrackingService
import com.vernont.domain.auth.Role
import com.vernont.infrastructure.security.SessionDto
import com.vernont.infrastructure.config.Argon2PasswordEncoder
import com.vernont.repository.auth.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/internal/auth")
@Tag(name = "Authentication", description = "Internal authentication endpoints for admin users")
class InternalAuthController(
        private val userRepository: UserRepository,
        private val passwordEncoder: PasswordEncoder,
        private val argon2PasswordEncoder: Argon2PasswordEncoder,
        private val jwtTokenProvider: JwtTokenProvider,
        private val cookieProperties: CookieProperties,
        private val sessionTrackingService: SessionTrackingService
) {

        companion object {
                // Use different cookie names to avoid conflict with storefront cookies
                const val ACCESS_TOKEN_COOKIE = "admin_access_token"
                const val REFRESH_TOKEN_COOKIE = "admin_refresh_token"
        }

        /** Set HTTP-only auth cookies */
        private fun setAuthCookies(
                response: HttpServletResponse,
                accessToken: String,
                refreshToken: String
        ) {
                val accessCookie =
                        Cookie(ACCESS_TOKEN_COOKIE, accessToken).apply {
                                isHttpOnly = true
                                secure = cookieProperties.secure
                                path = "/"
                                maxAge = cookieProperties.accessTokenMaxAge
                                cookieProperties.domain?.let { domain = it }
                                setAttribute("SameSite", cookieProperties.sameSite)
                        }
                response.addCookie(accessCookie)

                val refreshCookie =
                        Cookie(REFRESH_TOKEN_COOKIE, refreshToken).apply {
                                isHttpOnly = true
                                secure = cookieProperties.secure
                                path = "/api/v1/internal/auth"
                                maxAge = cookieProperties.refreshTokenMaxAge
                                cookieProperties.domain?.let { domain = it }
                                setAttribute("SameSite", cookieProperties.sameSite)
                        }
                response.addCookie(refreshCookie)
        }

        /** Clear auth cookies on logout */
        private fun clearAuthCookies(response: HttpServletResponse) {
                val accessCookie =
                        Cookie(ACCESS_TOKEN_COOKIE, "").apply {
                                isHttpOnly = true
                                secure = cookieProperties.secure
                                path = "/"
                                maxAge = 0
                                cookieProperties.domain?.let { domain = it }
                                setAttribute("SameSite", cookieProperties.sameSite)
                        }
                response.addCookie(accessCookie)

                val refreshCookie =
                        Cookie(REFRESH_TOKEN_COOKIE, "").apply {
                                isHttpOnly = true
                                secure = cookieProperties.secure
                                path = "/api/v1/internal/auth"
                                maxAge = 0
                                cookieProperties.domain?.let { domain = it }
                                setAttribute("SameSite", cookieProperties.sameSite)
                        }
                response.addCookie(refreshCookie)
        }

        /** Get client IP address from request */
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

        data class InternalLoginRequest(
                @field:Email(message = "Please provide a valid email address")
                @field:NotBlank(message = "Email is required")
                val email: String,
                @field:NotBlank(message = "Password is required") val password: String,
                val rememberMe: Boolean? = false
        )

        data class InternalRefreshRequest(val refreshToken: String? = null)

        data class InternalAuthResponse(val user: InternalUserInfo)

        data class InternalUserInfo(
                val id: String,
                val email: String,
                val firstName: String?,
                val lastName: String?,
                val role: String
        )

        @Operation(
                summary = "Admin Login",
                description = "Authenticate admin users and receive JWT tokens"
        )
        @ApiResponses(
                value =
                        [
                                ApiResponse(responseCode = "200", description = "Login successful"),
                                ApiResponse(
                                        responseCode = "401",
                                        description = "Invalid credentials"
                                ),
                                ApiResponse(
                                        responseCode = "403",
                                        description = "Insufficient privileges"
                                )]
        )
        @PostMapping("/login")
        fun login(
                @Valid @RequestBody request: InternalLoginRequest,
                servletRequest: HttpServletRequest,
                response: HttpServletResponse
        ): ResponseEntity<Any> {
                val normalizedEmail = request.email.lowercase().trim()
                logger.info { "Internal login attempt for email: $normalizedEmail" }

                val user = userRepository.findByEmailWithRoles(normalizedEmail)

                if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
                        logger.warn {
                                "Internal login failed - invalid credentials for email: ${request.email}"
                        }
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(
                                        mapOf(
                                                "error" to "INVALID_CREDENTIALS",
                                                "message" to "Invalid email or password"
                                        )
                                )
                }

                // Check if a user has an admin-related role
                val hasAdminRole =
                        user.roles.any {
                                it.name in
                                        listOf(
                                                Role.ADMIN,
                                                Role.CUSTOMER_SERVICE,
                                                Role.WAREHOUSE_MANAGER,
                                                Role.DEVELOPER
                                        )
                        }

                if (!hasAdminRole) {
                        logger.warn {
                                "Internal login failed - user ${request.email} does not have admin privileges"
                        }
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(
                                        mapOf(
                                                "error" to "INSUFFICIENT_PRIVILEGES",
                                                "message" to
                                                        "Access denied. Admin privileges required."
                                        )
                                )
                }

                if (!user.isActive) {
                        logger.warn {
                                "Internal login failed - account deactivated for email: ${request.email}"
                        }
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(
                                        mapOf(
                                                "error" to "ACCOUNT_DEACTIVATED",
                                                "message" to
                                                        "Your account has been deactivated. Please contact support."
                                        )
                                )
                }

                try {
                        // Check if password hash needs upgrading
                        val upgradedHash =
                                argon2PasswordEncoder.upgradePassword(
                                        request.password,
                                        user.passwordHash
                                )
                        if (upgradedHash != null) {
                                logger.info { "Upgrading password hash for user: ${user.id}" }
                                user.passwordHash = upgradedHash
                        }

                        // Update last login
                        user.updateLastLogin()
                        userRepository.save(user)

                        // Generate tokens (no customerId for admin users)
                        val accessToken = jwtTokenProvider.generateAccessToken(user, null)
                        val refreshToken = jwtTokenProvider.generateRefreshToken(user)

                        // Get primary role (highest privilege)
                        val primaryRole =
                                user.roles.firstOrNull { it.name == Role.ADMIN }?.name
                                        ?: user.roles
                                                .firstOrNull { it.name == Role.DEVELOPER }
                                                ?.name
                                                ?: user.roles
                                                .firstOrNull { it.name == Role.CUSTOMER_SERVICE }
                                                ?.name
                                                ?: user.roles.firstOrNull()?.name ?: "USER"

                        logger.info {
                                "Internal user logged in successfully: userId=${user.id}, role=$primaryRole"
                        }

                        // Set HTTP-only cookies
                        setAuthCookies(response, accessToken, refreshToken)

                        // Track admin session
                        try {
                                val ipAddress = getClientIpAddress(servletRequest)
                                val userAgent = servletRequest.getHeader("User-Agent")
                                sessionTrackingService.trackSession(
                                        userId = user.id,
                                        sessionToken = accessToken,
                                        ipAddress = ipAddress,
                                        userAgent = userAgent
                                )
                        } catch (e: Exception) {
                                logger.warn(e) { "Failed to track session for user ${user.id}" }
                        }

                        return ResponseEntity.ok(
                                InternalAuthResponse(
                                        user =
                                                InternalUserInfo(
                                                        id = user.id,
                                                        email = user.email,
                                                        firstName = user.firstName,
                                                        lastName = user.lastName,
                                                        role = primaryRole
                                                )
                                )
                        )
                } catch (e: Exception) {
                        logger.error(e) {
                                "Internal login processing failed for email: ${request.email}"
                        }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "LOGIN_FAILED",
                                                "message" to "Login failed. Please try again later."
                                        )
                                )
                }
        }

        @Operation(
                summary = "Refresh Token",
                description = "Refresh access token using refresh token"
        )
        @ApiResponses(
                value =
                        [
                                ApiResponse(
                                        responseCode = "200",
                                        description = "Token refreshed successfully"
                                ),
                                ApiResponse(
                                        responseCode = "401",
                                        description = "Invalid or expired refresh token"
                                )]
        )
        @PostMapping("/refresh")
        fun refreshToken(
                @RequestBody(required = false) request: InternalRefreshRequest?,
                servletRequest: HttpServletRequest,
                response: HttpServletResponse
        ): ResponseEntity<Any> {
                logger.info { "Internal token refresh attempt" }

                val token =
                        request?.refreshToken
                                ?: servletRequest.cookies
                                        ?.find { it.name == REFRESH_TOKEN_COOKIE }
                                        ?.value

                if (token.isNullOrBlank()) {
                        logger.warn { "Internal token refresh failed - no token provided" }
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(
                                        mapOf(
                                                "error" to "MISSING_TOKEN",
                                                "message" to "Refresh token is required"
                                        )
                                )
                }

                try {
                        if (!jwtTokenProvider.validateToken(token)) {
                                logger.warn { "Internal token refresh failed - invalid token" }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "INVALID_REFRESH_TOKEN",
                                                        "message" to
                                                                "Invalid or expired refresh token"
                                                )
                                        )
                        }

                        val tokenType = jwtTokenProvider.getTokenType(token)
                        if (tokenType != "refresh") {
                                logger.warn {
                                        "Internal token refresh failed - wrong token type: $tokenType"
                                }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "INVALID_TOKEN_TYPE",
                                                        "message" to
                                                                "Invalid token type for refresh"
                                                )
                                        )
                        }

                        val userId = jwtTokenProvider.getUserIdFromToken(token)
                        val user = userRepository.findByIdWithRoles(userId)

                        if (user == null || !user.isActive) {
                                logger.warn {
                                        "Internal token refresh failed - user not found or inactive: $userId"
                                }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "USER_NOT_FOUND",
                                                        "message" to
                                                                "User not found or account deactivated"
                                                )
                                        )
                        }

                        // Verify user still has admin privileges
                        val hasAdminRole =
                                user.roles.any {
                                        it.name in
                                                listOf(
                                                        Role.ADMIN,
                                                        Role.CUSTOMER_SERVICE,
                                                        Role.WAREHOUSE_MANAGER,
                                                        Role.DEVELOPER
                                                )
                                }

                        if (!hasAdminRole) {
                                logger.warn {
                                        "Internal token refresh failed - user $userId lost admin privileges"
                                }
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(
                                                mapOf(
                                                        "error" to "INSUFFICIENT_PRIVILEGES",
                                                        "message" to
                                                                "Access denied. Admin privileges required."
                                                )
                                        )
                        }

                        // Generate new tokens
                        val accessToken = jwtTokenProvider.generateAccessToken(user, null)
                        val newRefreshToken = jwtTokenProvider.generateRefreshToken(user)

                        // Get primary role
                        val primaryRole =
                                user.roles.firstOrNull { it.name == Role.ADMIN }?.name
                                        ?: user.roles
                                                .firstOrNull { it.name == Role.DEVELOPER }
                                                ?.name
                                                ?: user.roles
                                                .firstOrNull { it.name == Role.CUSTOMER_SERVICE }
                                                ?.name
                                                ?: user.roles.firstOrNull()?.name ?: "USER"

                        logger.info { "Internal token refreshed successfully for user: $userId" }

                        // Set HTTP-only cookies
                        setAuthCookies(response, accessToken, newRefreshToken)

                        return ResponseEntity.ok(
                                InternalAuthResponse(
                                        user =
                                                InternalUserInfo(
                                                        id = user.id,
                                                        email = user.email,
                                                        firstName = user.firstName,
                                                        lastName = user.lastName,
                                                        role = primaryRole
                                                )
                                )
                        )
                } catch (e: Exception) {
                        logger.error(e) { "Internal token refresh processing failed" }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "REFRESH_FAILED",
                                                "message" to
                                                        "Token refresh failed. Please try again later."
                                        )
                                )
                }
        }

        @Operation(summary = "Admin Logout", description = "Logout admin user and clear cookies")
        @PostMapping("/logout")
        fun logout(response: HttpServletResponse): ResponseEntity<Any> {
                clearAuthCookies(response)
                logger.info { "Internal user logged out" }
                return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
        }

        data class HeartbeatResponse(
                val session: SessionDto?,
                val status: String
        )

        @Operation(
                summary = "Session Heartbeat",
                description = "Keep session alive and update last activity"
        )
        @ApiResponses(
                value =
                        [
                                ApiResponse(responseCode = "200", description = "Heartbeat successful"),
                                ApiResponse(responseCode = "401", description = "Session not found or expired")]
        )
        @PostMapping("/heartbeat")
        fun heartbeat(servletRequest: HttpServletRequest): ResponseEntity<Any> {
                val accessToken = servletRequest.cookies?.find { it.name == ACCESS_TOKEN_COOKIE }?.value

                if (accessToken.isNullOrBlank()) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(
                                        mapOf(
                                                "error" to "NO_SESSION",
                                                "message" to "No active session"
                                        )
                                )
                }

                val session = sessionTrackingService.heartbeat(accessToken)

                return if (session != null) {
                        ResponseEntity.ok(
                                HeartbeatResponse(
                                        session = SessionDto.from(session),
                                        status = "active"
                                )
                        )
                } else {
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(
                                        mapOf(
                                                "error" to "SESSION_EXPIRED",
                                                "message" to "Session expired or not found"
                                        )
                                )
                }
        }

        data class InternalMeResponse(
                val id: String,
                val email: String,
                val firstName: String?,
                val lastName: String?,
                val role: String
        )

        @Operation(
                summary = "Get Current User",
                description = "Get currently authenticated user information"
        )
        @ApiResponses(
                value =
                        [
                                ApiResponse(
                                        responseCode = "200",
                                        description = "User information retrieved"
                                ),
                                ApiResponse(
                                        responseCode = "401",
                                        description = "Not authenticated"
                                )]
        )
        @GetMapping("/me")
        fun getMe(): ResponseEntity<Any> {
                logger.info { "Internal /me endpoint called" }
                try {
                        val authentication = SecurityContextHolder.getContext().authentication

                        if (authentication == null ||
                                        !authentication.isAuthenticated ||
                                        "anonymousUser" == authentication.principal
                        ) {
                                logger.warn { "Internal /me failed - no authenticated user" }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "UNAUTHENTICATED",
                                                        "message" to "No authenticated user found."
                                                )
                                        )
                        }

                        val principal = authentication.principal
                        val userId =
                                when (principal) {
                                        is com.vernont.domain.auth.UserContext ->
                                                principal.userId
                                        else -> authentication.name
                                }

                        // Access tokens encode email as username; fall back to email lookup if ID
                        // lookup fails
                        val user =
                                userRepository.findByIdWithRoles(userId)
                                        ?: userRepository.findByEmailWithRoles(authentication.name)

                        if (user == null || !user.isActive) {
                                logger.warn {
                                        "Internal /me failed - user not found or inactive: $userId"
                                }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "USER_NOT_FOUND",
                                                        "message" to
                                                                "User not found or account deactivated"
                                                )
                                        )
                        }

                        val primaryRole =
                                user.roles.firstOrNull { it.name == Role.ADMIN }?.name
                                        ?: user.roles
                                                .firstOrNull { it.name == Role.DEVELOPER }
                                                ?.name
                                                ?: user.roles
                                                .firstOrNull { it.name == Role.CUSTOMER_SERVICE }
                                                ?.name
                                                ?: user.roles.firstOrNull()?.name ?: "USER"

                        return ResponseEntity.ok(
                                InternalMeResponse(
                                        id = user.id,
                                        email = user.email,
                                        firstName = user.firstName,
                                        lastName = user.lastName,
                                        role = primaryRole
                                )
                        )
                } catch (e: Exception) {
                        logger.error(e) { "Internal /me endpoint processing failed" }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "ME_FAILED",
                                                "message" to "Failed to retrieve user information."
                                        )
                                )
                }
        }

        data class UpdateProfileRequest(
                val firstName: String? = null,
                val lastName: String? = null
        )

        @Operation(
                summary = "Update Current User Profile",
                description = "Update the authenticated user's profile information"
        )
        @ApiResponses(
                value =
                        [
                                ApiResponse(
                                        responseCode = "200",
                                        description = "Profile updated successfully"
                                ),
                                ApiResponse(
                                        responseCode = "401",
                                        description = "Not authenticated"
                                )]
        )
        @PutMapping("/me")
        fun updateMe(@Valid @RequestBody request: UpdateProfileRequest): ResponseEntity<Any> {
                logger.info { "Internal /me PUT endpoint called" }
                try {
                        val authentication = SecurityContextHolder.getContext().authentication

                        if (authentication == null ||
                                        !authentication.isAuthenticated ||
                                        "anonymousUser" == authentication.principal
                        ) {
                                logger.warn { "Internal /me PUT failed - no authenticated user" }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "UNAUTHENTICATED",
                                                        "message" to "No authenticated user found."
                                                )
                                        )
                        }

                        val principal = authentication.principal
                        val userId =
                                when (principal) {
                                        is com.vernont.domain.auth.UserContext ->
                                                principal.userId
                                        else -> authentication.name
                                }

                        val user =
                                userRepository.findByIdWithRoles(userId)
                                        ?: userRepository.findByEmailWithRoles(authentication.name)

                        if (user == null || !user.isActive) {
                                logger.warn {
                                        "Internal /me PUT failed - user not found or inactive: $userId"
                                }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "USER_NOT_FOUND",
                                                        "message" to
                                                                "User not found or account deactivated"
                                                )
                                        )
                        }

                        // Update profile fields
                        request.firstName?.let { user.firstName = it }
                        request.lastName?.let { user.lastName = it }

                        userRepository.save(user)

                        val primaryRole =
                                user.roles.firstOrNull { it.name == Role.ADMIN }?.name
                                        ?: user.roles
                                                .firstOrNull { it.name == Role.DEVELOPER }
                                                ?.name
                                                ?: user.roles
                                                .firstOrNull { it.name == Role.CUSTOMER_SERVICE }
                                                ?.name
                                                ?: user.roles.firstOrNull()?.name ?: "USER"

                        logger.info { "Profile updated for user: ${user.id}" }

                        return ResponseEntity.ok(
                                InternalMeResponse(
                                        id = user.id,
                                        email = user.email,
                                        firstName = user.firstName,
                                        lastName = user.lastName,
                                        role = primaryRole
                                )
                        )
                } catch (e: Exception) {
                        logger.error(e) { "Internal /me PUT endpoint processing failed" }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "UPDATE_FAILED",
                                                "message" to "Failed to update profile."
                                        )
                                )
                }
        }

        data class ChangePasswordRequest(
                @field:NotBlank(message = "Current password is required")
                val currentPassword: String,
                @field:NotBlank(message = "New password is required")
                val newPassword: String
        )

        @Operation(
                summary = "Change Password",
                description = "Change the authenticated user's password"
        )
        @ApiResponses(
                value =
                        [
                                ApiResponse(
                                        responseCode = "200",
                                        description = "Password changed successfully"
                                ),
                                ApiResponse(
                                        responseCode = "400",
                                        description = "Invalid current password"
                                ),
                                ApiResponse(
                                        responseCode = "401",
                                        description = "Not authenticated"
                                )]
        )
        @PutMapping("/me/password")
        fun changePassword(@Valid @RequestBody request: ChangePasswordRequest): ResponseEntity<Any> {
                logger.info { "Internal /me/password PUT endpoint called" }
                try {
                        val authentication = SecurityContextHolder.getContext().authentication

                        if (authentication == null ||
                                        !authentication.isAuthenticated ||
                                        "anonymousUser" == authentication.principal
                        ) {
                                logger.warn { "Internal /me/password PUT failed - no authenticated user" }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "UNAUTHENTICATED",
                                                        "message" to "No authenticated user found."
                                                )
                                        )
                        }

                        val principal = authentication.principal
                        val userId =
                                when (principal) {
                                        is com.vernont.domain.auth.UserContext ->
                                                principal.userId
                                        else -> authentication.name
                                }

                        val user =
                                userRepository.findByIdWithRoles(userId)
                                        ?: userRepository.findByEmailWithRoles(authentication.name)

                        if (user == null || !user.isActive) {
                                logger.warn {
                                        "Internal /me/password PUT failed - user not found or inactive: $userId"
                                }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "USER_NOT_FOUND",
                                                        "message" to
                                                                "User not found or account deactivated"
                                                )
                                        )
                        }

                        // Verify current password
                        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
                                logger.warn { "Password change failed - invalid current password for user: ${user.id}" }
                                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(
                                                mapOf(
                                                        "error" to "INVALID_PASSWORD",
                                                        "message" to "Current password is incorrect"
                                                )
                                        )
                        }

                        // Validate new password length
                        if (request.newPassword.length < 8) {
                                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(
                                                mapOf(
                                                        "error" to "WEAK_PASSWORD",
                                                        "message" to "Password must be at least 8 characters"
                                                )
                                        )
                        }

                        // Hash new password with Argon2
                        user.passwordHash = argon2PasswordEncoder.encode(request.newPassword)
                        userRepository.save(user)

                        logger.info { "Password changed for user: ${user.id}" }

                        return ResponseEntity.ok(
                                mapOf("message" to "Password changed successfully")
                        )
                } catch (e: Exception) {
                        logger.error(e) { "Internal /me/password PUT endpoint processing failed" }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "PASSWORD_CHANGE_FAILED",
                                                "message" to "Failed to change password."
                                        )
                                )
                }
        }

        data class WsTokenResponse(val token: String)

        @Operation(
                summary = "Get WebSocket Token",
                description = "Get a short-lived token for WebSocket authentication"
        )
        @ApiResponses(
                value =
                        [
                                ApiResponse(
                                        responseCode = "200",
                                        description = "WebSocket token generated"
                                ),
                                ApiResponse(
                                        responseCode = "401",
                                        description = "Not authenticated"
                                )]
        )
        @PostMapping("/ws-token")
        fun getWsToken(): ResponseEntity<Any> {
                try {
                        val authentication = SecurityContextHolder.getContext().authentication

                        if (authentication == null ||
                                !authentication.isAuthenticated ||
                                "anonymousUser" == authentication.principal
                        ) {
                                logger.warn { "WS token request failed - no authenticated user" }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "UNAUTHENTICATED",
                                                        "message" to "No authenticated user found."
                                                )
                                        )
                        }

                    val userId =
                                when (val principal = authentication.principal) {
                                        is com.vernont.domain.auth.UserContext ->
                                                principal.userId

                                        else -> authentication.name
                                }

                        val user =
                                userRepository.findByIdWithRoles(userId)
                                        ?: userRepository.findByEmailWithRoles(authentication.name)

                        if (user == null || !user.isActive) {
                                logger.warn { "WS token request failed - user not found: $userId" }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "USER_NOT_FOUND",
                                                        "message" to "User not found or account deactivated"
                                                )
                                        )
                        }

                        // Generate a short-lived access token for WebSocket
                        val wsToken = jwtTokenProvider.generateAccessToken(user, null)

                        logger.debug { "WS token generated for user: ${user.id}" }

                        return ResponseEntity.ok(WsTokenResponse(token = wsToken))
                } catch (e: Exception) {
                        logger.error(e) { "WS token generation failed" }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "WS_TOKEN_FAILED",
                                                "message" to "Failed to generate WebSocket token."
                                        )
                                )
                }
        }
}
