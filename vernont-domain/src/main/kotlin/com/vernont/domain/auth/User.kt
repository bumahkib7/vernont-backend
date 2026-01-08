package com.vernont.domain.auth

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * Invitation status for internal users
 */
enum class InviteStatus {
    /** User was created directly (not via invite) */
    NONE,
    /** Invitation sent, waiting for user to accept */
    PENDING,
    /** User accepted invite and set password */
    ACCEPTED,
    /** Invitation expired or was cancelled */
    EXPIRED
}

/**
 * User - Authentication and authorization entity
 *
 * Represents a user account for:
 * - Admin users (ADMIN role, no customer record)
 * - Staff users (CUSTOMER_SERVICE, WAREHOUSE_MANAGER roles, no customer record)
 * - Customer users (CUSTOMER role, has linked customer record for orders/addresses)
 *
 * Relationship with Customer:
 * - One User can have one Customer record (OneToOne)
 * - Customer record stores e-commerce data (addresses, orders, groups)
 * - User is source of truth for email, firstName, lastName
 * - Guest customers have no User record (checkout without account)
 */
@Entity
@Table(
    name = "app_user",
    indexes = [
        Index(name = "idx_user_email", columnList = "email", unique = true),
        Index(name = "idx_user_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "User.full",
    attributeNodes = [
        NamedAttributeNode("roles")
    ]
)
class User : BaseEntity() {

    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    var email: String = ""

    @Column(nullable = false)
    var passwordHash: String = ""

    @Column
    var firstName: String? = null

    @Column
    var lastName: String? = null

    @Column
    var avatarUrl: String? = null

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    var emailVerified: Boolean = false

    @Column
    var lastLoginAt: java.time.Instant? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var inviteStatus: InviteStatus = InviteStatus.NONE

    @Column
    var invitedAt: java.time.Instant? = null

    @Column
    var inviteAcceptedAt: java.time.Instant? = null

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_role",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf()

    fun getFullName(): String {
        return when {
            firstName != null && lastName != null -> "$firstName $lastName"
            firstName != null -> firstName!!
            lastName != null -> lastName!!
            else -> email
        }
    }

    fun addRole(role: Role) {
        roles.add(role)
    }

    fun removeRole(role: Role) {
        roles.remove(role)
    }

    fun hasRole(roleName: String): Boolean {
        return roles.any { it.name == roleName }
    }

    fun activate() {
        this.isActive = true
    }

    fun deactivate() {
        this.isActive = false
    }

    fun verifyEmail() {
        this.emailVerified = true
    }

    fun updateLastLogin() {
        this.lastLoginAt = java.time.Instant.now()
    }

    fun markAsInvited() {
        this.inviteStatus = InviteStatus.PENDING
        this.invitedAt = java.time.Instant.now()
    }

    fun acceptInvite() {
        this.inviteStatus = InviteStatus.ACCEPTED
        this.inviteAcceptedAt = java.time.Instant.now()
    }

    fun expireInvite() {
        this.inviteStatus = InviteStatus.EXPIRED
    }
}
