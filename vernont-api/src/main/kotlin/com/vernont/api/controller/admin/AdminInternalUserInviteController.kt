package com.vernont.api.controller.admin

import com.vernont.api.dto.InternalUserDto
import com.vernont.application.auth.InternalUserService
import com.vernont.infrastructure.email.EmailService
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.auth.GenerateResetPasswordTokenWorkflow
import com.vernont.workflow.flows.auth.dto.GenerateResetPasswordTokenInput
import com.vernont.workflow.flows.auth.dto.InviteInternalUserInput
import com.vernont.workflow.flows.auth.dto.InviteInternalUserOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/internal-users/invite")
@Tag(name = "Internal Users Invite", description = "Admin endpoints for inviting internal users")
class AdminInternalUserInviteController(
    private val workflowEngine: com.vernont.workflow.engine.WorkflowEngine,
    @Value("\${app.admin-url:http://localhost:3000}") private val adminUrl: String
) {
    private val logger = KotlinLogging.logger {}

    data class InviteRequest(
        @field:Email(message = "Valid email required")
        @field:NotBlank(message = "Email is required")
        val email: String,
        val firstName: String? = null,
        val lastName: String? = null,
        val roles: List<String> = listOf("ADMIN")
    )

    @Operation(summary = "Invite internal user", description = "Creates a user with a temporary password and emails a reset link")
    @PostMapping
    suspend fun invite(@Valid @RequestBody req: InviteRequest): ResponseEntity<Any> {
        logger.info { "Invite internal user: ${req.email} "}

        val input = InviteInternalUserInput(
            email = req.email,
            firstName = req.firstName,
            lastName = req.lastName,
            roles = req.roles,
            adminUrl = adminUrl
        )
        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.Auth.INVITE_INTERNAL_USER,
            input = input,
            inputType = InviteInternalUserInput::class,
            outputType = InviteInternalUserOutput::class,
            context = WorkflowContext()
        )
        return when (result) {
            is WorkflowResult.Success -> {
                ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
                    "id" to result.data.userId,
                    "email" to result.data.email
                ))
            }
            is WorkflowResult.Failure -> {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                    "error" to "INVITE_FAILED",
                    "message" to (result.error.message ?: "Invite failed")
                ))
            }
        }
    }

    private fun generateTempPassword(): String {
        // 16+ chars, alnum + specials
        val chars = (('a'..'z') + ('A'..'Z') + ('0'..'9') + "!@#${'$'}%^&*()-_+=?{}[]").toList()
        return (1..20).map { chars.random() }.joinToString("")
    }
}
