package com.vernont.api.controller.admin

import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.auth.dto.GenerateResetPasswordTokenInput
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/internal-users")
class AdminInternalUserPasswordController(
    private val workflowEngine: WorkflowEngine
) {
    private val logger = KotlinLogging.logger {}

    data class ResetTokenRequest(
        @field:Email @field:NotBlank val email: String
    )

    data class ResetTokenResponse(val token: String)

    @PostMapping("/reset-password-token")
    suspend fun generateResetToken(@RequestBody req: ResetTokenRequest): ResponseEntity<Any> {
        logger.info { "Admin generating reset token for internal user: ${'$'}{req.email}" }
        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.Auth.GENERATE_RESET_PASSWORD_TOKEN,
            input = GenerateResetPasswordTokenInput(
                entityId = req.email,
                actorType = "user",
                provider = "emailpass"
            ),
            inputType = GenerateResetPasswordTokenInput::class,
            outputType = com.vernont.workflow.flows.auth.dto.GenerateResetPasswordTokenOutput::class,
            context = WorkflowContext()
        )
        return when (result) {
            is WorkflowResult.Success -> ResponseEntity.ok(ResetTokenResponse(result.data.token))
            is WorkflowResult.Failure -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf("error" to "RESET_TOKEN_FAILED", "message" to (result.error.message ?: "Failed to generate token"))
            )
        }
    }
}
