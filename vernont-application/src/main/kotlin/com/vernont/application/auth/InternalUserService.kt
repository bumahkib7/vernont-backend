package com.vernont.application.auth

import com.vernont.infrastructure.config.Argon2PasswordEncoder
import com.vernont.domain.auth.Role
import com.vernont.domain.auth.User
import com.vernont.repository.auth.RoleRepository
import com.vernont.repository.auth.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class InternalUserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: Argon2PasswordEncoder
) {

    @Transactional(readOnly = true)
    fun getAllInternalUsers(): List<User> {
        logger.info { "Fetching all internal users" }
        return userRepository.findAllInternalUsers()
    }

    @Transactional(readOnly = true)
    fun getById(userId: String): User {
        logger.info { "Fetching user with id: $userId" }
        return userRepository.findByIdWithRoles(userId)
            ?: throw IllegalArgumentException("User with ID $userId not found")
    }

    /**
     * Validates that an email doesn't already exist in the system.
     * Used before sending invite emails to avoid sending to existing users.
     * @throws IllegalArgumentException if email already exists
     */
    @Transactional(readOnly = true)
    fun validateEmailNotExists(email: String) {
        val normalizedEmail = email.lowercase().trim()
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw IllegalArgumentException("User with email '$normalizedEmail' already exists")
        }
    }

    @Transactional
    fun createInternalUser(
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
        roleNames: List<String>
    ): User {
        val normalizedEmail = email.lowercase().trim()
        logger.info { "Creating internal user: $normalizedEmail" }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw IllegalArgumentException("User with email '$normalizedEmail' already exists")
        }

        // Validate roles
        if (roleNames.isEmpty()) {
            throw IllegalArgumentException("At least one role is required")
        }
        
        // Prevent creating users with ONLY 'CUSTOMER' or 'GUEST' roles via this internal API
        // (though techically allowed, this API is for internal staff)
        val nonCustomerRoles = roleNames.filter { it != Role.CUSTOMER && it != Role.GUEST }
        if (nonCustomerRoles.isEmpty()) {
            throw IllegalArgumentException("Internal users must have at least one administrative role (e.g. ADMIN, STAFF)")
        }

        val roles = roleRepository.findByNameIn(roleNames).toMutableSet()
        if (roles.size != roleNames.size) {
            val foundNames = roles.map { it.name }.toSet()
            val missing = roleNames.filter { !foundNames.contains(it) }
            throw IllegalArgumentException("Roles not found: $missing")
        }

        val user = User().apply {
            this.email = normalizedEmail
            this.passwordHash = passwordEncoder.encode(password)
            this.firstName = firstName
            this.lastName = lastName
            this.isActive = true
            this.emailVerified = true // Internal users usually pre-verified
            this.roles = roles
        }

        return userRepository.save(user)
    }

    /**
     * Creates an internal user via invite flow (sets PENDING status)
     */
    @Transactional
    fun createInvitedUser(
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
        roleNames: List<String>
    ): User {
        val normalizedEmail = email.lowercase().trim()
        logger.info { "Creating invited internal user: $normalizedEmail" }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw IllegalArgumentException("User with email '$normalizedEmail' already exists")
        }

        if (roleNames.isEmpty()) {
            throw IllegalArgumentException("At least one role is required")
        }

        val nonCustomerRoles = roleNames.filter { it != Role.CUSTOMER && it != Role.GUEST }
        if (nonCustomerRoles.isEmpty()) {
            throw IllegalArgumentException("Internal users must have at least one administrative role")
        }

        val roles = roleRepository.findByNameIn(roleNames).toMutableSet()
        if (roles.size != roleNames.size) {
            val foundNames = roles.map { it.name }.toSet()
            val missing = roleNames.filter { !foundNames.contains(it) }
            throw IllegalArgumentException("Roles not found: $missing")
        }

        val user = User().apply {
            this.email = normalizedEmail
            this.passwordHash = passwordEncoder.encode(password)
            this.firstName = firstName
            this.lastName = lastName
            this.isActive = false // Not active until they accept invite
            this.emailVerified = false
            this.roles = roles
            markAsInvited() // Sets inviteStatus = PENDING
        }

        return userRepository.save(user)
    }

    /**
     * Marks a user's invite as accepted (called when they set their password)
     */
    @Transactional
    fun acceptInvite(userId: String): User {
        logger.info { "Accepting invite for user: $userId" }
        val user = userRepository.findByIdWithRoles(userId)
            ?: throw IllegalArgumentException("User with ID $userId not found")

        user.acceptInvite()
        user.isActive = true
        user.emailVerified = true
        return userRepository.save(user)
    }

    @Transactional
    fun updateInternalUser(
        userId: String,
        email: String?,
        password: String?,
        firstName: String?,
        lastName: String?,
        isActive: Boolean?,
        roleNames: List<String>?
    ): User {
        logger.info { "Updating user: $userId" }

        val user = userRepository.findByIdWithRoles(userId)
            ?: throw IllegalArgumentException("User with ID $userId not found")

        if (email != null) {
            val normalizedEmail = email.lowercase().trim()
            if (normalizedEmail != user.email) {
                if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                    throw IllegalArgumentException("Email '$normalizedEmail' is already in use")
                }
                user.email = normalizedEmail
            }
        }

        if (password != null && password.isNotBlank()) {
            user.passwordHash = passwordEncoder.encode(password)
        }

        firstName?.let { user.firstName = it }
        lastName?.let { user.lastName = it }
        isActive?.let { user.isActive = it }

        if (roleNames != null) {
            if (roleNames.isEmpty()) {
                throw IllegalArgumentException("Cannot remove all roles from a user")
            }
            val roles = roleRepository.findByNameIn(roleNames).toMutableSet()
            if (roles.size != roleNames.distinct().size) {
                 val foundNames = roles.map { it.name }.toSet()
                 val missing = roleNames.filter { !foundNames.contains(it) }
                 throw IllegalArgumentException("Roles not found: $missing")
            }
            user.roles = roles
        }

        return userRepository.save(user)
    }

    /**
     * Archives (soft deletes) an internal user.
     * User can be restored later.
     */
    @Transactional
    fun archiveInternalUser(userId: String) {
        logger.info { "Archiving user: $userId" }
        val user = userRepository.findByIdWithRoles(userId)
            ?: throw IllegalArgumentException("User with ID $userId not found")

        user.softDelete(deletedBy = "ADMIN")
        userRepository.save(user)
    }

    /**
     * Alias for archiveInternalUser for backwards compatibility.
     */
    @Transactional
    fun deleteInternalUser(userId: String) {
        archiveInternalUser(userId)
    }

    /**
     * Permanently deletes an internal user and all associated data.
     * THIS CANNOT BE UNDONE.
     */
    @Transactional
    fun hardDeleteInternalUser(userId: String) {
        logger.warn { "HARD DELETING user: $userId - this cannot be undone!" }
        val user = userRepository.findByIdWithRoles(userId)
            ?: throw IllegalArgumentException("User with ID $userId not found")

        // Clear roles first (removes from user_role join table)
        user.roles.clear()
        userRepository.save(user)

        // Now delete the user permanently
        userRepository.delete(user)
        logger.info { "User $userId permanently deleted" }
    }

    /**
     * Restores a previously archived (soft-deleted) user.
     */
    @Transactional
    fun restoreInternalUser(userId: String): User {
        logger.info { "Restoring user: $userId" }
        // Need to find including deleted
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw IllegalArgumentException("User with ID $userId not found")

        if (user.deletedAt == null) {
            throw IllegalArgumentException("User $userId is not archived")
        }

        user.restore()
        return userRepository.save(user)
    }
}

