package com.vernont.api.controller

import com.vernont.infrastructure.email.EmailService
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.auth.dto.GenerateResetPasswordTokenInput
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/forgot-password")
class ForgotPasswordController(
    private val workflowEngine: WorkflowEngine,
    private val emailService: EmailService,
    @Value("\${app.admin-url:http://localhost:3000}") private val adminUrl: String
) {
    private val logger = KotlinLogging.logger {}

    data class ForgotPasswordRequest(val email: String)
    data class ForgotPasswordResponse(val ok: Boolean)

    @PostMapping
    @com.vernont.api.rate.RateLimited(keyPrefix = "forgot", perIp = true, perEmail = true, limit = 5, windowSeconds = 600)
    suspend fun forgotPassword(@RequestBody req: ForgotPasswordRequest): ResponseEntity<Any> {
        logger.info { "Forgot password for: ${'$'}{req.email}" }

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.Auth.GENERATE_RESET_PASSWORD_TOKEN,
            input = GenerateResetPasswordTokenInput(
                entityId = req.email,
                actorType = "customer",
                provider = "emailpass"
            ),
            inputType = GenerateResetPasswordTokenInput::class,
            outputType = com.vernont.workflow.flows.auth.dto.GenerateResetPasswordTokenOutput::class,
            context = WorkflowContext()
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val token = result.data.token
                val resetLink = "${'$'}adminUrl/forgot-password?token=${'$'}token"
                val subject = "Reset your password"
                val templateData = mapOf(
                    "brand" to "Vernont",
                    "preheader" to "Reset your password",
                    "title" to subject,
                    "heading" to "Password reset",
                    "body" to "<p>We received a request to reset your password.</p><p>Click the button below to reset it now.</p>",
                    "buttonText" to "Reset password",
                    "buttonUrl" to resetLink,
                    "footer" to "© Vernont",
                    "logoUrl" to ""
                )
                try {
                    emailService.sendTemplateEmail(
                        to = req.email,
                        templateId = "password-reset",
                        templateData = templateData,
                        subject = subject
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed sending forgot password email to ${'$'}{req.email}" }
                }
                ResponseEntity.ok(ForgotPasswordResponse(true))
            }
            is WorkflowResult.Failure -> {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    mapOf(
                        "error" to "RESET_TOKEN_FAILED",
                        "message" to (result.error.message ?: "Failed to generate token")
                    )
                )
            }
        }
    }
}
