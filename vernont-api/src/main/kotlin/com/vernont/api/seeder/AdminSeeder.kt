package com.vernont.api.seeder

import com.vernont.domain.auth.Role
import com.vernont.domain.auth.User
import com.vernont.repository.auth.RoleRepository
import com.vernont.repository.auth.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
class AdminSeeder(
        private val userRepository: UserRepository,
        private val roleRepository: RoleRepository, // <-- INJECT ROLE REPOSITORY
        private val passwordEncoder: PasswordEncoder,
        private val adminConfig: AdminConfiguration
) : CommandLineRunner {

    // Ensure the run method is transactional, as it performs database operations
    @Transactional
    override fun run(vararg args: String) {
        // Use the configured bootstrap properties
        val bootstrap = adminConfig.bootstrap

        // Ensure default roles are present, acting as a fallback if Flyway is stuck
        ensureRoleExists(Role.ADMIN)
        // Add calls for other default roles if needed, e.g.:
        ensureRoleExists(Role.CUSTOMER)
        ensureRoleExists(Role.GUEST)

        // Only run if auto-creation is enabled and credentials are provided
        if (bootstrap.enabled && bootstrap.email != null && bootstrap.password != null) {
            val adminEmail = bootstrap.email
            val adminPassword = bootstrap.password
            createBootstrapAdmin(adminEmail!!, adminPassword!!)
        } else {
            logger.info {
                "Admin bootstrap skipped: Auto-creation is disabled or credentials are not provided."
            }
        }
    }

    private fun ensureRoleExists(roleName: String): Role {
        return roleRepository.findByName(roleName)
                ?: run {
                    logger.info { "Role '$roleName' not found, creating it programmatically." }
                    val newRole =
                            Role().apply {
                                name = roleName
                                description = "Default $roleName role"
                            }
                    roleRepository.save(newRole)
                }
    }

    private fun createBootstrapAdmin(email: String, initialPassword: String) {

        // 1. Check if a user with this email already exists
        // Note: Assuming userRepository.findByEmail exists and returns
        // com.vernont.domain.auth.User
        if (userRepository.findByEmail(email) != null) {
            logger.info { "Admin bootstrap skipped: User with email $email already exists." }
            return
        }

        // 2. Hash the password
        val hashedPassword = passwordEncoder.encode(initialPassword)

        // 3. Fetch the Role entities from the database
        val requiredRoleNames = adminConfig.defaults.roles

        // Note: Assuming a method like findByNameIn exists on RoleRepository
        val roles: Set<Role> = roleRepository.findByNameIn(requiredRoleNames)

        if (roles.isEmpty()) {
            // We use warn here because the failure is usually due to missing seed data,
            // not necessarily a system crash.
            logger.warn {
                "Admin bootstrap failed. No valid Role entities found for names: $requiredRoleNames."
            }
            return
        }

        // 4. Create the new User entity
        val adminUser =
                User().apply {
                    this.email = email
                    this.passwordHash = hashedPassword // Correct property name from User entity
                    this.isActive = true
                    this.emailVerified = true // Bootstrap admin is trusted

                    // Map the fetched Role entities to the User's roles MutableSet
                    this.roles.addAll(roles)

                    // Optional: Set default names if available
                    this.firstName = "Bootstrap"
                    this.lastName = "Admin"
                }

        // 5. Save the new User
        userRepository.save(adminUser)

        logger.info { "Bootstrap Admin User Created Successfully" }
        logger.info { "Email: $email" }
        logger.info { "Initial Password: ***(Hashed)***" }
        logger.info { "Roles: ${roles.joinToString { it.name }}" }
    }
}
