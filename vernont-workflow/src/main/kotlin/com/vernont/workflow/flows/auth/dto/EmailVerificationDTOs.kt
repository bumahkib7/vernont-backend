package com.vernont.workflow.flows.auth.dto

data class GenerateEmailVerificationTokenInput(
    val email: String,
    val userId: String
)

data class GenerateEmailVerificationTokenOutput(
    val token: String
)

data class VerifyEmailInput(
    val token: String
)

data class VerifyEmailOutput(
    val email: String,
    val verified: Boolean
)
