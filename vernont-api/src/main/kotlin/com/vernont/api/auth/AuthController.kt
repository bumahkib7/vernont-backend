package com.vernont.api.auth

import com.vernont.api.rate.RateLimited
import com.vernont.application.customer.CustomerService
import com.vernont.domain.auth.Role
import com.vernont.domain.auth.User
import com.vernont.infrastructure.config.Argon2PasswordEncoder
import com.vernont.repository.auth.RoleRepository
import com.vernont.repository.auth.UserRepository
import com.vernont.repository.customer.CustomerRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowOptions
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.customer.CreateCustomerAccountInput
import com.vernont.workflow.flows.customer.CustomerAccountAlreadyExistsException
import com.vernont.workflow.flows.customer.WeakPasswordException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController("apiAuthController")
@RequestMapping("/auth")
class AuthController(
        private val userRepository: UserRepository,
        private val roleRepository: RoleRepository,
        private val customerService: CustomerService,
        private val passwordEncoder: PasswordEncoder,
        private val argon2PasswordEncoder: Argon2PasswordEncoder,
        private val jwtTokenProvider: JwtTokenProvider,
        private val workflowEngine: WorkflowEngine,
        private val customerRepository: CustomerRepository,
        private val googleOAuthService: GoogleOAuthService,
        private val cookieProperties: CookieProperties,
        private val orderService: com.vernont.application.order.OrderService
) {

        companion object {
                const val ACCESS_TOKEN_COOKIE = "access_token"
                const val REFRESH_TOKEN_COOKIE = "refresh_token"
        }

        data class RegisterRequest(
                @field:Email(message = "Please provide a valid email address")
                @field:NotBlank(message = "Email is required")
                val email: String,
                @field:NotBlank(message = "Password is required")
                @field:Size(min = 8, message = "Password must be at least 8 characters long")
                val password: String,
                val firstName: String? = null,
                val lastName: String? = null
        )

        data class LoginRequest(
                @field:Email(message = "Please provide a valid email address")
                @field:NotBlank(message = "Email is required")
                val email: String,
                @field:NotBlank(message = "Password is required") val password: String
        )

        data class RefreshTokenRequest(
                @field:NotBlank(message = "Refresh token is required") val refreshToken: String
        )

        data class GoogleLoginRequest(
                @field:NotBlank(message = "Google ID token is required") val idToken: String
        )

        /** Legacy response with tokens in body (for backward compatibility) */
        data class AuthResponse(
                val accessToken: String,
                val refreshToken: String,
                val user: UserInfo
        )

        /** New response with tokens in HTTP-only cookies (secure) */
        data class CookieAuthResponse(val user: UserInfo)

        data class UserInfo(
                val id: String,
                val email: String,
                val firstName: String?,
                val lastName: String?,
                val roles: List<String>,
                val emailVerified: Boolean,
                val customerId: String? = null
        )

        /** Helper function to get customerId for a user if they have CUSTOMER role */
        private fun getCustomerIdForUser(user: User): String? {
                return if (user.hasRole(com.vernont.domain.auth.Role.CUSTOMER)) {
                        customerRepository.findByUserIdAndDeletedAtIsNull(user.id)?.id
                } else {
                        null
                }
        }

        /**
         * Sets HTTP-only authentication cookies on the response. Access token: Short-lived, sent
         * with all requests (path="/") Refresh token: Long-lived, only sent to auth endpoints
         * (path="/auth")
         */
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
                                path = "/auth" // Only sent to auth endpoints
                                maxAge = cookieProperties.refreshTokenMaxAge
                                cookieProperties.domain?.let { domain = it }
                                setAttribute("SameSite", cookieProperties.sameSite)
                        }
                response.addCookie(refreshCookie)
        }

        /** Clears authentication cookies by setting them to empty with maxAge=0 */
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
                                path = "/auth"
                                maxAge = 0
                                cookieProperties.domain?.let { domain = it }
                                setAttribute("SameSite", cookieProperties.sameSite)
                        }
                response.addCookie(refreshCookie)
        }

        @Throws(RuntimeException::class)
        @PostMapping("/customer/emailpass/register")
        @Transactional
        @RateLimited(
                keyPrefix = "auth:register",
                perIp = true,
                perEmail = true,
                limit = 3,
                windowSeconds = 3600, // 1 hour
                failClosed = true
        )
        suspend fun register(
                @Valid @RequestBody request: RegisterRequest,
                response: HttpServletResponse
        ): ResponseEntity<Any> {
                logger.info { "Registration attempt for email: ${request.email}" }

                try {
                        val input =
                                CreateCustomerAccountInput(
                                        email = request.email,
                                        password = request.password,
                                        firstName = request.firstName ?: "",
                                        lastName = request.lastName ?: "",
                                        phone = null, // Not provided in RegisterRequest, workflow
                                        // can handle
                                        // null
                                        metadata = null // Not provided in RegisterRequest
                                )

                        val result =
                                workflowEngine.execute(
                                        workflowName = WorkflowConstants.CreateCustomerAccount.NAME,
                                        input = input,
                                        inputType = CreateCustomerAccountInput::class,
                                        outputType =
                                                com.vernont.domain.customer.Customer::class,
                                        options =
                                                WorkflowOptions(
                                                        correlationId =
                                                                UUID.randomUUID()
                                                                        .toString(), // Generate new
                                                        // correlation
                                                        // ID for workflow
                                                        timeoutSeconds = 30
                                                )
                                )

                        return when (result) {
                                is WorkflowResult.Success -> {
                                        val customer =
                                                result.data // This is the Customer entity returned
                                        // by the workflow
                                        val user =
                                                customer.user
                                                        ?: throw RuntimeException(
                                                                "Customer must be linked to a User after creation"
                                                        )

                                        // Link any guest orders (orders placed before registration) to this customer
                                        try {
                                                val linkedOrders =
                                                    withContext(Dispatchers.IO) {
                                                        orderService.linkGuestOrdersToCustomer(
                                                            customerId = customer.id,
                                                            email = customer.email
                                                        )
                                                    }
                                                if (linkedOrders > 0) {
                                                        logger.info { "Linked $linkedOrders guest orders to newly registered customer: ${customer.id}" }
                                                }
                                        } catch (e: Exception) {
                                                // Don't fail registration if order linking fails
                                                logger.warn(e) { "Failed to link guest orders for customer: ${customer.id}" }
                                        }

                                        // Generate tokens with customerId included
                                        val accessToken =
                                                jwtTokenProvider.generateAccessToken(
                                                        user,
                                                        customer.id
                                                )
                                        val refreshToken =
                                                jwtTokenProvider.generateRefreshToken(user)

                                        logger.info {
                                                "User and Customer registered successfully: userId=${user.id}, customerId=${customer.id}"
                                        }

                                        // Set HTTP-only cookies (for new secure frontend)
                                        setAuthCookies(response, accessToken, refreshToken)

                                        ResponseEntity.status(HttpStatus.CREATED)
                                                .body(
                                                        AuthResponse(
                                                                accessToken = accessToken,
                                                                refreshToken = refreshToken,
                                                                user =
                                                                        UserInfo(
                                                                                id = user.id,
                                                                                email = user.email,
                                                                                firstName =
                                                                                        user.firstName,
                                                                                lastName =
                                                                                        user.lastName,
                                                                                roles =
                                                                                        user.roles
                                                                                                .map {
                                                                                                        it.name
                                                                                                },
                                                                                emailVerified =
                                                                                        user.emailVerified,
                                                                                customerId =
                                                                                        customer.id
                                                                        )
                                                        )
                                                )
                                }
                                is WorkflowResult.Failure -> {
                                        val error = result.error
                                        logger.warn {
                                                "Registration workflow failed for email: ${request.email}, error=${error.message}"
                                        }

                                        val statusCode =
                                                when (error) {
                                                        is CustomerAccountAlreadyExistsException ->
                                                                HttpStatus.CONFLICT
                                                        is WeakPasswordException ->
                                                                HttpStatus.BAD_REQUEST
                                                        is IllegalArgumentException ->
                                                                HttpStatus.BAD_REQUEST
                                                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                                                }

                                        ResponseEntity.status(statusCode)
                                                .body(
                                                        mapOf(
                                                                "error" to error::class.simpleName,
                                                                "message" to
                                                                        (error.message
                                                                                ?: "Registration failed")
                                                        )
                                                )
                                }
                        }
                } catch (e: Exception) {
                        logger.error(e) {
                                "Registration processing failed for email: ${request.email}"
                        }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "REGISTRATION_FAILED",
                                                "message" to
                                                        "Registration failed. Please try again later."
                                        )
                                )
                }
        }
        @PostMapping("/customer/emailpass")
        @RateLimited(
                keyPrefix = "auth:login",
                perIp = true,
                perEmail = true,
                limit = 5,
                windowSeconds = 900, // 15 minutes
                failClosed = true
        )
        fun login(
                @Valid @RequestBody request: LoginRequest,
                response: HttpServletResponse
        ): ResponseEntity<Any> {
                val normalizedEmail = request.email.lowercase().trim()
                logger.info { "Login attempt for email: $normalizedEmail" }

                val user = userRepository.findByEmailWithRoles(normalizedEmail)

                if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
                        // Check if this is an OAuth-only account (created with Google, no password
                        // set)
                        if (user != null) {
                                val hasOAuthProvider =
                                        user.metadata?.containsKey("googleSub") == true
                                if (hasOAuthProvider &&
                                                !passwordEncoder.matches(
                                                        request.password,
                                                        user.passwordHash
                                                )
                                ) {
                                        logger.warn {
                                                "Login failed - OAuth account attempted password login: $normalizedEmail"
                                        }
                                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                .body(
                                                        mapOf(
                                                                "error" to "OAUTH_ACCOUNT",
                                                                "message" to
                                                                        "This account was created with Google. Please sign in with Google or reset your password to use email/password login."
                                                        )
                                                )
                                }
                        }

                        logger.warn {
                                "Login failed - invalid credentials for email: $normalizedEmail"
                        }
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(
                                        mapOf(
                                                "error" to "INVALID_CREDENTIALS",
                                                "message" to "Invalid email or password"
                                        )
                                )
                }

                if (!user.isActive) {
                        logger.warn {
                                "Login failed - account deactivated for email: $normalizedEmail"
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
                        // Check if password hash needs upgrading (e.g., from BCrypt to Argon2 or
                        // weaker Argon2
                        // params)
                        val upgradedHash =
                                argon2PasswordEncoder.upgradePassword(
                                        request.password,
                                        user.passwordHash
                                )
                        if (upgradedHash != null) {
                                logger.info { "Upgrading password hash for user: ${user.id}" }
                                user.passwordHash = upgradedHash
                        }

                        // Auto-assign CUSTOMER role to Admins if they don't have it (needed for
                        // Aggregator
                        // access)
                        if (user.roles.any { it.name == Role.ADMIN } && !user.hasRole(Role.CUSTOMER)
                        ) {
                                roleRepository.findByName(Role.CUSTOMER)?.let { customerRole ->
                                        logger.info {
                                                "Auto-assigning CUSTOMER role to Admin user: ${user.email}"
                                        }
                                        user.addRole(customerRole)
                                }
                        }

                        // Normalize email to lowercase for consistency
                        if (user.email != normalizedEmail) {
                                user.email = normalizedEmail
                        }

                        // Update last login
                        user.updateLastLogin()
                        userRepository.save(user)

                        // Get customerId if user has CUSTOMER role
                        val customerId = getCustomerIdForUser(user)

                        // Generate tokens with customerId if applicable
                        val accessToken = jwtTokenProvider.generateAccessToken(user, customerId)
                        val refreshToken = jwtTokenProvider.generateRefreshToken(user)

                        logger.info {
                                "User logged in successfully: userId=${user.id}, role=${user.roles.map { it.name }}, customerId=$customerId"
                        }

                        // Set HTTP-only cookies (for new secure frontend)
                        setAuthCookies(response, accessToken, refreshToken)

                        // Return tokens in body for backward compatibility with old frontend
                        return ResponseEntity.ok(
                                AuthResponse(
                                        accessToken = accessToken,
                                        refreshToken = refreshToken,
                                        user =
                                                UserInfo(
                                                        id = user.id,
                                                        email = user.email,
                                                        firstName = user.firstName,
                                                        lastName = user.lastName,
                                                        roles = user.roles.map { it.name },
                                                        emailVerified = user.emailVerified,
                                                        customerId = customerId
                                                )
                                )
                        )
                } catch (e: Exception) {
                        logger.error(e) { "Login processing failed for email: ${request.email}" }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "LOGIN_FAILED",
                                                "message" to "Login failed. Please try again later."
                                        )
                                )
                }
        }

        @PostMapping("/token/refresh")
        @RateLimited(
                keyPrefix = "auth:refresh",
                perIp = true,
                perEmail = false,
                limit = 10,
                windowSeconds = 3600, // 1 hour
                failClosed = false // Fail open to avoid locking out users
        )
        fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<Any> {
                logger.info { "Token refresh attempt" }

                try {
                        if (!jwtTokenProvider.validateToken(request.refreshToken)) {
                                logger.warn { "Token refresh failed - invalid token" }
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "INVALID_REFRESH_TOKEN",
                                                        "message" to
                                                                "Invalid or expired refresh token"
                                                )
                                        )
                        }

                        val tokenType = jwtTokenProvider.getTokenType(request.refreshToken)
                        if (tokenType != "refresh") {
                                logger.warn {
                                        "Token refresh failed - wrong token type: $tokenType"
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

                        val userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken)
                        val user = userRepository.findByIdWithRoles(userId)

                        if (user == null || !user.isActive) {
                                logger.warn {
                                        "Token refresh failed - user not found or inactive: $userId"
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

                        // Get customerId if user has CUSTOMER role
                        val customerId = getCustomerIdForUser(user)

                        // Generate new tokens with customerId if applicable
                        val accessToken = jwtTokenProvider.generateAccessToken(user, customerId)
                        val newRefreshToken = jwtTokenProvider.generateRefreshToken(user)

                        logger.info {
                                "Token refreshed successfully for user: $userId, customerId=$customerId"
                        }

                        return ResponseEntity.ok(
                                AuthResponse(
                                        accessToken = accessToken,
                                        refreshToken = newRefreshToken,
                                        user =
                                                UserInfo(
                                                        id = user.id,
                                                        email = user.email,
                                                        firstName = user.firstName,
                                                        lastName = user.lastName,
                                                        roles = user.roles.map { it.name },
                                                        emailVerified = user.emailVerified,
                                                        customerId = customerId
                                                )
                                )
                        )
                } catch (e: Exception) {
                        logger.error(e) { "Token refresh processing failed" }
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

        /**
         * Cookie-based token refresh endpoint.
         * Reads refresh token from HTTP-only cookie and returns new tokens in cookies.
         * This is the preferred method for browser-based clients.
         */
        @PostMapping("/refresh")
        @RateLimited(
                keyPrefix = "auth:cookie-refresh",
                perIp = true,
                perEmail = false,
                limit = 30,
                windowSeconds = 3600, // 1 hour
                failClosed = false
        )
        fun refreshTokenFromCookie(
                servletRequest: HttpServletRequest,
                response: HttpServletResponse
        ): ResponseEntity<Any> {
                logger.info { "Cookie-based token refresh attempt" }

                val token = servletRequest.cookies
                        ?.find { it.name == REFRESH_TOKEN_COOKIE }
                        ?.value

                if (token.isNullOrBlank()) {
                        logger.warn { "Cookie refresh failed - no token in cookie" }
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(
                                        mapOf(
                                                "error" to "MISSING_TOKEN",
                                                "message" to "No refresh token found"
                                        )
                                )
                }

                try {
                        if (!jwtTokenProvider.validateToken(token)) {
                                logger.warn { "Cookie refresh failed - invalid token" }
                                clearAuthCookies(response)
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "INVALID_REFRESH_TOKEN",
                                                        "message" to "Invalid or expired refresh token"
                                                )
                                        )
                        }

                        val tokenType = jwtTokenProvider.getTokenType(token)
                        if (tokenType != "refresh") {
                                logger.warn { "Cookie refresh failed - wrong token type: $tokenType" }
                                clearAuthCookies(response)
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "INVALID_TOKEN_TYPE",
                                                        "message" to "Invalid token type for refresh"
                                                )
                                        )
                        }

                        val userId = jwtTokenProvider.getUserIdFromToken(token)
                        val user = userRepository.findByIdWithRoles(userId)

                        if (user == null || !user.isActive) {
                                logger.warn { "Cookie refresh failed - user not found or inactive: $userId" }
                                clearAuthCookies(response)
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "USER_NOT_FOUND",
                                                        "message" to "User not found or account deactivated"
                                                )
                                        )
                        }

                        // Get customerId if user has CUSTOMER role
                        val customerId = getCustomerIdForUser(user)

                        // Generate new tokens
                        val accessToken = jwtTokenProvider.generateAccessToken(user, customerId)
                        val newRefreshToken = jwtTokenProvider.generateRefreshToken(user)

                        // Set tokens in HTTP-only cookies
                        setAuthCookies(response, accessToken, newRefreshToken)

                        // Get customer info if applicable
                        val customer = customerId?.let { customerRepository.findByIdAndDeletedAtIsNull(it) }

                        logger.info { "Cookie refresh successful for user: $userId, customerId=$customerId" }

                        val userInfo = UserInfo(
                                id = user.id,
                                email = user.email,
                                firstName = user.firstName,
                                lastName = user.lastName,
                                roles = user.roles.map { it.name },
                                emailVerified = user.emailVerified,
                                customerId = customerId
                        )

                        val responseBody = mutableMapOf<String, Any>(
                                "user" to userInfo
                        )

                        customer?.let { c ->
                                responseBody["customer"] = mapOf(
                                        "id" to c.id,
                                        "email" to c.email,
                                        "firstName" to c.firstName,
                                        "lastName" to c.lastName,
                                        "phone" to c.phone
                                )
                        }

                        return ResponseEntity.ok(responseBody)
                } catch (e: Exception) {
                        logger.error(e) { "Cookie refresh processing failed" }
                        clearAuthCookies(response)
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "REFRESH_FAILED",
                                                "message" to "Token refresh failed. Please try again later."
                                        )
                                )
                }
        }

        @GetMapping("/customer/me")
        fun getCurrentUser(authentication: Authentication?): ResponseEntity<Any> {
                val userContext =
                        authentication?.principal as? com.vernont.domain.auth.UserContext
                                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "NOT_AUTHENTICATED",
                                                        "message" to "No valid session"
                                                )
                                        )

                val userInfo = UserInfo(
                        id = userContext.userId,
                        email = userContext.email,
                        firstName = userContext.firstName,
                        lastName = userContext.lastName,
                        roles = userContext.roles,
                        emailVerified = userContext.emailVerified,
                        customerId = userContext.customerId
                )

                // Build response in expected format { user: UserInfo, customer?: CustomerInfo }
                val responseBody = mutableMapOf<String, Any>("user" to userInfo)

                // Get customer info if user has customerId
                userContext.customerId?.let { customerId ->
                        customerRepository.findByIdAndDeletedAtIsNull(customerId)?.let { customer ->
                                responseBody["customer"] = mapOf(
                                        "id" to customer.id,
                                        "email" to customer.email,
                                        "firstName" to customer.firstName,
                                        "lastName" to customer.lastName,
                                        "phone" to customer.phone,
                                        "hasAccount" to customer.hasAccount
                                )
                        }
                }

                return ResponseEntity.ok(responseBody)
        }

        @PostMapping("/customer/google")
        @Transactional
        @RateLimited(
                keyPrefix = "auth:google",
                perIp = true,
                perEmail = false,
                limit = 10,
                windowSeconds = 3600, // 1 hour
                failClosed = true
        )
        fun loginWithGoogle(
                @Valid @RequestBody request: GoogleLoginRequest,
                response: HttpServletResponse
        ): ResponseEntity<Any> {
                logger.info { "Google login attempt" }

                val tokenInfo =
                        googleOAuthService.verifyIdToken(request.idToken)
                                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(
                                                mapOf(
                                                        "error" to "INVALID_GOOGLE_TOKEN",
                                                        "message" to
                                                                "Google token is invalid or expired"
                                                )
                                        )

                val email = tokenInfo.email!!.lowercase()
                val googleSub = tokenInfo.sub!!

                try {
                        val customerRole =
                                roleRepository.findByName(Role.CUSTOMER)
                                        ?: return ResponseEntity.status(
                                                        HttpStatus.INTERNAL_SERVER_ERROR
                                                )
                                                .body(
                                                        mapOf(
                                                                "error" to "ROLE_NOT_FOUND",
                                                                "message" to
                                                                        "Customer role is not configured"
                                                        )
                                                )

                        var user = userRepository.findByEmailWithRoles(email)
                        val isNewUser = user == null

                        if (user == null) {
                                user =
                                        User().apply {
                                                this.email = email
                                                firstName = tokenInfo.givenName
                                                lastName = tokenInfo.familyName
                                                avatarUrl = tokenInfo.picture
                                                isActive = true
                                                emailVerified = tokenInfo.emailVerified
                                                passwordHash =
                                                        passwordEncoder.encode(
                                                                UUID.randomUUID().toString()
                                                        )
                                                addRole(customerRole)
                                                metadata =
                                                        mutableMapOf(
                                                                "googleSub" to googleSub,
                                                                "googlePicture" to tokenInfo.picture
                                                        )
                                        }
                        } else {
                                if (!user.isActive) {
                                        logger.warn {
                                                "Google login blocked - user deactivated for email: $email"
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

                                // Normalize email to lowercase for consistency (account merging)
                                user.email = email
                                tokenInfo.givenName?.let { user.firstName = it }
                                tokenInfo.familyName?.let { user.lastName = it }
                                tokenInfo.picture?.let { user.avatarUrl = it }
                                user.emailVerified = user.emailVerified || tokenInfo.emailVerified

                                val metadata = user.metadata ?: mutableMapOf()
                                metadata["googleSub"] = googleSub
                                metadata["googlePicture"] = tokenInfo.picture
                                user.metadata = metadata

                                if (!user.hasRole(Role.CUSTOMER)) {
                                        user.addRole(customerRole)
                                }
                        }

                        user.updateLastLogin()
                        val savedUser = userRepository.save(user)

                        var customer =
                                customerRepository.findByUserIdAndDeletedAtIsNull(savedUser.id)
                                        ?: customerRepository.findByEmailAndDeletedAtIsNull(email)

                        if (customer == null) {
                                customer =
                                        com.vernont.domain.customer.Customer().apply {
                                                this.email = email
                                                firstName = tokenInfo.givenName
                                                lastName = tokenInfo.familyName
                                                hasAccount = true
                                                linkToUser(savedUser)
                                        }
                        } else {
                                // Normalize customer email to match user email (account merging)
                                customer.email = email
                                customer.linkToUser(savedUser)
                                customer.syncFromUser()
                        }

                        val savedCustomer = customerRepository.save(customer)

                        // Link any guest orders to this customer (for new registrations or account merging)
                        if (isNewUser) {
                                try {
                                        val linkedOrders = orderService.linkGuestOrdersToCustomer(
                                                customerId = savedCustomer.id,
                                                email = email
                                        )
                                        if (linkedOrders > 0) {
                                                logger.info { "Linked $linkedOrders guest orders to Google customer: ${savedCustomer.id}" }
                                        }
                                } catch (e: Exception) {
                                        logger.warn(e) { "Failed to link guest orders for Google customer: ${savedCustomer.id}" }
                                }
                        }

                        val accessToken =
                                jwtTokenProvider.generateAccessToken(savedUser, savedCustomer.id)
                        val refreshToken = jwtTokenProvider.generateRefreshToken(savedUser)

                        logger.info {
                                "Google login success for email=$email, newUser=$isNewUser, customerId=${savedCustomer.id}"
                        }

                        // Set HTTP-only cookies (for new secure frontend)
                        setAuthCookies(response, accessToken, refreshToken)

                        return ResponseEntity.ok(
                                AuthResponse(
                                        accessToken = accessToken,
                                        refreshToken = refreshToken,
                                        user =
                                                UserInfo(
                                                        id = savedUser.id,
                                                        email = savedUser.email,
                                                        firstName = savedUser.firstName,
                                                        lastName = savedUser.lastName,
                                                        roles = savedUser.roles.map { it.name },
                                                        emailVerified = savedUser.emailVerified,
                                                        customerId = savedCustomer.id
                                                )
                                )
                        )
                } catch (ex: Exception) {
                        logger.error(ex) { "Google login processing failed for email=$email" }
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                        mapOf(
                                                "error" to "GOOGLE_LOGIN_FAILED",
                                                "message" to
                                                        "We could not sign you in with Google right now. Please try again."
                                        )
                                )
                }
        }

        /**
         * Logout endpoint that clears authentication cookies. For cookie-based auth, this is
         * essential since the frontend can't delete HttpOnly cookies.
         */
        @PostMapping("/logout")
        fun logout(response: HttpServletResponse): ResponseEntity<Any> {
                clearAuthCookies(response)
                logger.info { "User logged out - cookies cleared" }
                return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
        }
}
