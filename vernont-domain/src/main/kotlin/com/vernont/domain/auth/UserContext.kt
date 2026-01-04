package com.vernont.domain.auth // Updated package

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder // Added import
import org.springframework.security.core.userdetails.UserDetails

// Placeholder for UnauthorizedException
class UnauthorizedException(message: String) : RuntimeException(message)

/**
 * UserContext - Elegant container for authenticated user information
 *
 * This class represents the authenticated user's context throughout the application. It's stored in
 * the Spring Security context and provides easy access to user details.
 *
 * Similar to the fleet_copilots pattern, but adapted for nexus-commerce.
 */
data class UserContext(
        val userId: String,
        val email: String,
        val firstName: String?,
        val lastName: String?,
        val roles: List<String>,
        val customerId: String? = null,
        val emailVerified: Boolean = true
) : UserDetails {

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        return roles.map { SimpleGrantedAuthority("ROLE_$it") }.toMutableList()
    }

    override fun getPassword(): String? = null

    override fun getUsername(): String = email

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true

    /** Get full name of the user */
    fun getFullName(): String {
        return when {
            firstName != null && lastName != null -> "$firstName $lastName"
            firstName != null -> firstName
            lastName != null -> lastName
            else -> email
        }
    }

    /** Check if user has a specific role */
    fun hasRole(role: String): Boolean {
        return roles.contains(role)
    }

    /** Check if user has any of the specified roles */
    fun hasAnyRole(vararg rolesToCheck: String): Boolean {
        return roles.any { it in rolesToCheck }
    }

    /** Check if user is a customer (has customerId) */
    fun isCustomer(): Boolean = customerId != null

    /** Check if user is an admin */
    fun isAdmin(): Boolean = hasRole("ADMIN")
}

/** Extension function to get UserContext from Spring Security context */
fun getCurrentUserContext(): UserContext? {
    val authentication = SecurityContextHolder.getContext().authentication
    return if (authentication?.principal is UserContext) {
        authentication.principal as UserContext
    } else {
        null
    }
}

/** Extension function to require UserContext (throws exception if not authenticated) */
fun requireUserContext(): UserContext {
    return getCurrentUserContext() ?: throw UnauthorizedException("User must be authenticated")
}
