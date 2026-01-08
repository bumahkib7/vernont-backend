package com.vernont.events

import java.time.Instant

data class PasswordResetEvent(
    val entityId: String,
    val actorType: String,
    val token: String,
    override val aggregateId: String = entityId,
) : DomainEvent(aggregateId = aggregateId)

data class EmailVerificationEvent(
    val userId: String,
    val email: String,
    val token: String,
    override val aggregateId: String = userId,
) : DomainEvent(aggregateId = aggregateId)

/**
 * Event published when an internal user is invited to the admin dashboard.
 */
data class InternalUserInvitedEvent(
    val userId: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val roles: List<String>,
    val invitedBy: String?,
    override val aggregateId: String = userId,
) : DomainEvent(aggregateId = aggregateId)

/**
 * Event published when a user accepts their invite by setting their password.
 */
data class InternalUserInviteAcceptedEvent(
    val userId: String,
    val email: String,
    override val aggregateId: String = userId,
) : DomainEvent(aggregateId = aggregateId)
