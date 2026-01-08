package com.vernont.api.dto

import com.vernont.domain.auth.InviteStatus
import com.vernont.domain.auth.User
import java.time.Instant

/**
 * DTO for internal user responses (admin, staff, etc.).
 * Excludes sensitive data like password hash.
 */
data class InternalUserDto(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val avatarUrl: String?,
    val isActive: Boolean,
    val emailVerified: Boolean,
    val lastLoginAt: Instant?,
    val inviteStatus: String,
    val invitedAt: Instant?,
    val inviteAcceptedAt: Instant?,
    val roles: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Request DTO for creating a new internal user.
 */
data class CreateInternalUserRequest(
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val roles: List<String>
)

/**
 * Request DTO for updating an internal user.
 */
data class UpdateInternalUserRequest(
    val email: String? = null, // Email updates might be restricted, but included for completeness
    val password: String? = null, // Optional password reset
    val firstName: String? = null,
    val lastName: String? = null,
    val isActive: Boolean? = null,
    val roles: List<String>? = null // Optional role update
)

/**
 * Mapper extension for InternalUserDto.
 */
fun User.toInternalUserDto(): InternalUserDto {
    return InternalUserDto(
        id = this.id,
        email = this.email,
        firstName = this.firstName,
        lastName = this.lastName,
        avatarUrl = this.avatarUrl,
        isActive = this.isActive,
        emailVerified = this.emailVerified,
        lastLoginAt = this.lastLoginAt,
        inviteStatus = this.inviteStatus.name,
        invitedAt = this.invitedAt,
        inviteAcceptedAt = this.inviteAcceptedAt,
        roles = this.roles.map { it.name }.sorted(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

fun List<User>.toInternalUserDtos(): List<InternalUserDto> {
    return this.map { it.toInternalUserDto() }
}

/**
 * Response wrapper for list of internal users
 */
data class InternalUsersResponse(
    val users: List<InternalUserDto>,
    val count: Int,
    val offset: Int,
    val limit: Int
)
