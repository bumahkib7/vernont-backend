package com.vernont.api.admin

import com.vernont.infrastructure.config.Argon2PasswordEncoder
import com.vernont.domain.auth.Role
import com.vernont.domain.auth.User
import com.vernont.repository.auth.RoleRepository
import com.vernont.repository.auth.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * Admin Bootstrap Service
 * 
 * Provides secure methods for creating the first admin user:
 * 1. One-time bootstrap endpoint (disabled after first admin creation)
 * 2. Environment variable-based auto-creation
 * 3. Command-line tool for production deployment
 */
@RestController
@RequestMapping("/bootstrap")
class AdminBootstrapController(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: Argon2PasswordEncoder
) {

    @Value("\${app.admin.bootstrap.enabled:true}")
    private var bootstrapEnabled: Boolean = true

    @Value("\${app.admin.bootstrap.email:}")
    private var bootstrapEmail: String = ""

    @Value("\${app.admin.bootstrap.password:}")
    private var bootstrapPassword: String = ""

    @Value("\${app.admin.bootstrap.secret-key:}")
    private var bootstrapSecretKey: String = ""

    data class CreateAdminRequest(
        @field:Email(message = "Valid email required")
        @field:NotBlank(message = "Email is required")
        val email: String,

        @field:NotBlank(message = "Password is required")
        @field:Size(min = 12, message = "Admin password must be at least 12 characters")
        val password: String,

        @field:NotBlank(message = "Secret key is required")
        val secretKey: String,

        val firstName: String? = null,
        val lastName: String? = null
    )

    /**
     * Bootstrap endpoint - creates first admin user
     * Disabled automatically after first admin is created
     * Requires secret key for security
     */
    @PostMapping("/create-admin")
    @Transactional
    fun createBootstrapAdmin(@Valid @RequestBody request: CreateAdminRequest): ResponseEntity<Any> {
        logger.info { "Bootstrap admin creation attempt for email: ${request.email}" }

        // Check if bootstrap is still enabled
        if (!bootstrapEnabled) {
            logger.warn { "Bootstrap admin creation disabled - admin already exists" }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                mapOf(
                    "error" to "BOOTSTRAP_DISABLED",
                    "message" to "Admin bootstrap is disabled. First admin already created."
                )
            )
        }

        // Verify secret key
        if (bootstrapSecretKey.isBlank() || request.secretKey != bootstrapSecretKey) {
            logger.warn { "Invalid bootstrap secret key attempt" }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf(
                    "error" to "INVALID_SECRET",
                    "message" to "Invalid bootstrap secret key"
                )
            )
        }

        // Check if any admin already exists
        val existingAdmins = userRepository.findAllActive()
            .filter { it.hasRole(Role.ADMIN) }

        if (existingAdmins.isNotEmpty()) {
            logger.warn { "Admin creation blocked - admin users already exist" }
            bootstrapEnabled = false
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "ADMIN_EXISTS",
                    "message" to "Admin user already exists"
                )
            )
        }

        // Check if email already used
        if (userRepository.existsByEmailIgnoreCase(request.email)) {
            logger.warn { "Admin creation blocked - email already exists: ${request.email}" }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "EMAIL_EXISTS",
                    "message" to "Email already registered"
                )
            )
        }

        try {
            // Get admin role
            val adminRole = roleRepository.findByName(Role.ADMIN)
                ?: throw RuntimeException("Admin role not found in database")

            // Create admin user
            val adminUser = User().apply {
                email = request.email
                passwordHash = passwordEncoder.encode(request.password)
                firstName = request.firstName ?: "Admin"
                lastName = request.lastName ?: "User"
                isActive = true
                emailVerified = true // Admin accounts are pre-verified
                addRole(adminRole)
            }

            val savedAdmin = userRepository.save(adminUser)

            // Disable bootstrap after first admin creation
            bootstrapEnabled = false

            logger.info { "Bootstrap admin created successfully: ${savedAdmin.id}" }

            return ResponseEntity.status(HttpStatus.CREATED).body(
                mapOf(
                    "success" to true,
                    "message" to "Admin user created successfully",
                    "admin" to mapOf(
                        "id" to savedAdmin.id,
                        "email" to savedAdmin.email,
                        "firstName" to savedAdmin.firstName,
                        "lastName" to savedAdmin.lastName
                    )
                )
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to create bootstrap admin" }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "error" to "CREATION_FAILED",
                    "message" to "Failed to create admin user"
                )
            )
        }
    }

    /**
     * Check if bootstrap is available
     */
    @GetMapping("/status")
    fun getBootstrapStatus(): ResponseEntity<Any> {
        val adminCount = userRepository.findAllActive()
            .count { it.hasRole(Role.ADMIN) }

        return ResponseEntity.ok(
            mapOf(
                "bootstrapEnabled" to bootstrapEnabled,
                "adminExists" to (adminCount > 0),
                "adminCount" to adminCount,
                "message" to if (bootstrapEnabled) {
                    "Bootstrap available - no admin users exist"
                } else {
                    "Bootstrap disabled - admin user already created"
                }
            )
        )
    }

    /**
     * Auto-create admin on startup if environment variables are set
     * Useful for Docker deployments and CI/CD
     */
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun autoCreateAdminOnStartup() {
        if (bootstrapEmail.isNotBlank() && bootstrapPassword.isNotBlank()) {
            logger.info { "Auto-creating admin from environment variables" }

            try {
                // Check if admin already exists
                val adminExists = userRepository.findAllActive()
                    .any { it.hasRole(Role.ADMIN) }

                if (adminExists) {
                    logger.info { "Admin already exists, skipping auto-creation" }
                    return
                }

                val adminRole = roleRepository.findByName(Role.ADMIN)
                    ?: throw RuntimeException("Admin role not found")

                val adminUser = User().apply {
                    email = bootstrapEmail
                    passwordHash = passwordEncoder.encode(bootstrapPassword)
                    firstName = "System"
                    lastName = "Admin"
                    isActive = true
                    emailVerified = true
                    addRole(adminRole)
                }

                val savedAdmin = userRepository.save(adminUser)
                bootstrapEnabled = false

                logger.info { "Auto-created admin user: ${savedAdmin.email}" }

            } catch (e: Exception) {
                logger.error(e) { "Failed to auto-create admin user" }
            }
        }
    }
}