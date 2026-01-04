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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * Admin User Management
 * 
 * Allows existing admins to create additional admin/staff users
 * Requires ADMIN role for access
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminManagementController(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: Argon2PasswordEncoder
) {

    data class CreateStaffRequest(
        @field:Email(message = "Valid email required")
        @field:NotBlank(message = "Email is required")
        val email: String,

        @field:NotBlank(message = "Password is required")
        @field:Size(min = 8, message = "Password must be at least 8 characters")
        val password: String,

        @field:NotBlank(message = "Role is required")
        val role: String, // ADMIN, CUSTOMER_SERVICE, WAREHOUSE_MANAGER, DEVELOPER

        val firstName: String? = null,
        val lastName: String? = null,
        val isActive: Boolean = true
    )

    /**
     * Create new admin/staff user
     * Only admins can create other admin/staff users
     */
    @PostMapping("/create")
    @Transactional
    fun createStaffUser(
        @Valid @RequestBody request: CreateStaffRequest,
        auth: Authentication
    ): ResponseEntity<Any> {
        val createdBy = auth.name
        logger.info { "Admin user creation request by $createdBy for email: ${request.email}" }

        // Validate role
        val validRoles = setOf(Role.ADMIN, Role.CUSTOMER_SERVICE, Role.WAREHOUSE_MANAGER, Role.DEVELOPER)
        if (request.role !in validRoles) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_ROLE",
                    "message" to "Invalid role. Must be one of: ${validRoles.joinToString()}"
                )
            )
        }

        // Check if email already exists
        if (userRepository.existsByEmailIgnoreCase(request.email)) {
            logger.warn { "User creation blocked - email already exists: ${request.email}" }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "EMAIL_EXISTS",
                    "message" to "Email already registered"
                )
            )
        }

        try {
            // Get the requested role
            val userRole = roleRepository.findByName(request.role)
                ?: throw RuntimeException("Role not found: ${request.role}")

            // Create user
            val newUser = User().apply {
                email = request.email
                passwordHash = passwordEncoder.encode(request.password)
                firstName = request.firstName ?: "Staff"
                lastName = request.lastName ?: "User"
                isActive = request.isActive
                emailVerified = true // Staff accounts are pre-verified
                addRole(userRole)
            }

            val savedUser = userRepository.save(newUser)

            logger.info { "Staff user created: ${savedUser.id} with role ${request.role} by admin $createdBy" }

            return ResponseEntity.status(HttpStatus.CREATED).body(
                mapOf(
                    "success" to true,
                    "message" to "User created successfully",
                    "user" to mapOf(
                        "id" to savedUser.id,
                        "email" to savedUser.email,
                        "firstName" to savedUser.firstName,
                        "lastName" to savedUser.lastName,
                        "role" to request.role,
                        "isActive" to savedUser.isActive,
                        "createdBy" to createdBy
                    )
                )
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to create staff user" }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "error" to "CREATION_FAILED",
                    "message" to "Failed to create user"
                )
            )
        }
    }

    /**
     * List all staff users (admin/staff roles only)
     * Supports Medusa-style pagination
     */
    @GetMapping
    fun listStaffUsers(
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) fields: String?,
        @RequestParam(required = false) order: String?
    ): ResponseEntity<Any> {
        val staffRoles = setOf(Role.ADMIN, Role.CUSTOMER_SERVICE, Role.WAREHOUSE_MANAGER, Role.DEVELOPER)

        val allStaffUsers = userRepository.findAllActive()
            .filter { user -> user.roles.any { it.name in staffRoles } }
            .map { user ->
                mapOf(
                    "id" to user.id,
                    "email" to user.email,
                    "firstName" to user.firstName,
                    "lastName" to user.lastName,
                    "roles" to user.roles.map { it.name },
                    "isActive" to user.isActive,
                    "lastLoginAt" to user.lastLoginAt,
                    "createdAt" to user.createdAt
                )
            }

        // Get total count
        val count = allStaffUsers.size

        // Apply pagination
        val paginatedUsers = allStaffUsers
            .drop(offset)
            .take(limit.coerceAtMost(100)) // Max 100 items per request

        // Return Medusa-compatible format
        return ResponseEntity.ok(
            mapOf(
                "users" to paginatedUsers,
                "limit" to limit,
                "offset" to offset,
                "count" to count
            )
        )
    }

    /**
     * Get a single user by ID
     */
    @GetMapping("/{userId}")
    fun getUserById(@PathVariable userId: String): ResponseEntity<Any> {
        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            mapOf(
                "id" to user.id,
                "email" to user.email,
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "roles" to user.roles.map { it.name },
                "isActive" to user.isActive,
                "emailVerified" to user.emailVerified,
                "lastLoginAt" to user.lastLoginAt,
                "createdAt" to user.createdAt,
                "updatedAt" to user.updatedAt
            )
        )
    }

    data class UpdateUserRequest(
        val firstName: String? = null,
        val lastName: String? = null,
        val roles: List<String>? = null,
        val isActive: Boolean? = null
    )

    /**
     * Update user details
     */
    @PutMapping("/{userId}")
    @Transactional
    fun updateUser(
        @PathVariable userId: String,
        @Valid @RequestBody request: UpdateUserRequest,
        auth: Authentication
    ): ResponseEntity<Any> {
        val updatedBy = auth.name
        logger.info { "User update request for $userId by $updatedBy" }

        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        try {
            // Update basic fields
            request.firstName?.let { user.firstName = it }
            request.lastName?.let { user.lastName = it }
            request.isActive?.let {
                if (it) user.activate() else user.deactivate()
            }

            // Update roles if provided
            request.roles?.let { requestedRoles ->
                val validRoles = setOf(Role.ADMIN, Role.CUSTOMER_SERVICE, Role.WAREHOUSE_MANAGER, Role.DEVELOPER)
                if (requestedRoles.any { it !in validRoles }) {
                    return ResponseEntity.badRequest().body(
                        mapOf(
                            "error" to "INVALID_ROLE",
                            "message" to "Invalid role. Must be one of: ${validRoles.joinToString()}"
                        )
                    )
                }

                // Clear existing roles and add new ones
                user.roles.clear()
                requestedRoles.forEach { roleName ->
                    val role = roleRepository.findByName(roleName)
                        ?: throw RuntimeException("Role not found: $roleName")
                    user.addRole(role)
                }
            }

            val savedUser = userRepository.save(user)

            logger.info { "User updated: $userId by $updatedBy" }

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "User updated successfully",
                    "user" to mapOf(
                        "id" to savedUser.id,
                        "email" to savedUser.email,
                        "firstName" to savedUser.firstName,
                        "lastName" to savedUser.lastName,
                        "roles" to savedUser.roles.map { it.name },
                        "isActive" to savedUser.isActive
                    )
                )
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to update user" }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "error" to "UPDATE_FAILED",
                    "message" to "Failed to update user"
                )
            )
        }
    }

    data class ResetPasswordRequest(
        @field:NotBlank(message = "Password is required")
        @field:Size(min = 8, message = "Password must be at least 8 characters")
        val newPassword: String
    )

    /**
     * Reset user password (admin action)
     */
    @PostMapping("/{userId}/reset-password")
    @Transactional
    fun resetPassword(
        @PathVariable userId: String,
        @Valid @RequestBody request: ResetPasswordRequest,
        auth: Authentication
    ): ResponseEntity<Any> {
        val resetBy = auth.name
        logger.info { "Password reset request for $userId by $resetBy" }

        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        try {
            user.passwordHash = passwordEncoder.encode(request.newPassword)
            userRepository.save(user)

            logger.info { "Password reset successful for user: $userId by $resetBy" }

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Password reset successfully"
                )
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to reset password" }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "error" to "RESET_FAILED",
                    "message" to "Failed to reset password"
                )
            )
        }
    }

    /**
     * Reactivate a deactivated user
     */
    @PostMapping("/{userId}/reactivate")
    @Transactional
    fun reactivateUser(
        @PathVariable userId: String,
        auth: Authentication
    ): ResponseEntity<Any> {
        val reactivatedBy = auth.name
        logger.info { "User reactivation request for $userId by $reactivatedBy" }

        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        user.activate()
        userRepository.save(user)

        logger.info { "User reactivated: $userId by $reactivatedBy" }

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "User reactivated successfully"
            )
        )
    }

    /**
     * Deactivate a user (soft delete)
     */
    @PostMapping("/{userId}/deactivate")
    @Transactional
    fun deactivateUser(
        @PathVariable userId: String,
        auth: Authentication
    ): ResponseEntity<Any> {
        val deactivatedBy = auth.name
        logger.info { "User deactivation request for $userId by $deactivatedBy" }

        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        // Prevent self-deactivation
        if (user.id == deactivatedBy) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "SELF_DEACTIVATION",
                    "message" to "Cannot deactivate your own account"
                )
            )
        }

        user.deactivate()
        userRepository.save(user)

        logger.info { "User deactivated: $userId by $deactivatedBy" }

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "User deactivated successfully"
            )
        )
    }

    /**
     * Get all available roles
     */
    @GetMapping("/roles")
    fun getAllRoles(): ResponseEntity<Any> {
        val roles = roleRepository.findAll().map { role ->
            mapOf(
                "id" to role.id,
                "name" to role.name,
                "description" to role.description
            )
        }

        return ResponseEntity.ok(
            mapOf(
                "roles" to roles,
                "count" to roles.size
            )
        )
    }
}