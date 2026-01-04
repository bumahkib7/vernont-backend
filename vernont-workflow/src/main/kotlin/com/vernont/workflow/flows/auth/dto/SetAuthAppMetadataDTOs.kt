package com.vernont.workflow.flows.auth.dto

import java.time.Instant

// Input DTO for SetAuthAppMetadataWorkflow
data class SetAuthAppMetadataInput(
    val userId: String,          // The ID of the User entity (our AuthIdentity equivalent)
    val actorType: String,       // e.g., "user", "customer", "manager", "vendor"
    val value: String?           // The ID of the actor, or null to remove the association
)

// Output DTO representing the updated User entity
data class UserDto(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val isActive: Boolean,
    val emailVerified: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metadata: Map<String, Any?>? // The updated app_metadata
)
