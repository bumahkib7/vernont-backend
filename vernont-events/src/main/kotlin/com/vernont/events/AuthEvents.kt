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
