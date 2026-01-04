package com.vernont.workflow.flows.auth.dto



data class InviteInternalUserInput(
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val roles: List<String> = listOf("ADMIN"),
    val adminUrl: String,
)

data class InviteInternalUserOutput(
    val userId: String,
    val email: String
)
