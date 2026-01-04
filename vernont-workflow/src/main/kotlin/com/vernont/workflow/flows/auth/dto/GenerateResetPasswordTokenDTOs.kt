package com.vernont.workflow.flows.auth.dto

data class GenerateResetPasswordTokenInput(
    val entityId: String,       // User identifier (e.g., email)
    val actorType: String,      // e.g., "customer", "user"
    val provider: String        // e.g., "emailpass"
    // secret is handled by the application configuration for security
)

data class GenerateResetPasswordTokenOutput(
    val token: String
)
