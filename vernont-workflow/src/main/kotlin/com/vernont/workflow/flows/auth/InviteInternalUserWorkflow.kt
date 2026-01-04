package com.vernont.workflow.flows.auth

import com.vernont.application.auth.InternalUserService
import com.vernont.infrastructure.email.EmailService
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.flows.auth.dto.InviteInternalUserInput
import com.vernont.workflow.flows.auth.dto.InviteInternalUserOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
@WorkflowTypes(input = InviteInternalUserInput::class, output = InviteInternalUserOutput::class)
class InviteInternalUserWorkflow(
    private val userService: InternalUserService,
    private val emailService: EmailService,
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.expiration-ms:3600000}") private val jwtExpirationMs: Long
) : Workflow<InviteInternalUserInput, InviteInternalUserOutput> {

    private val logger = KotlinLogging.logger {}
    override val name: String = WorkflowConstants.Auth.INVITE_INTERNAL_USER

    override suspend fun execute(
        input: InviteInternalUserInput,
        context: WorkflowContext
    ): WorkflowResult<InviteInternalUserOutput> {
        return try {
            // 1) Create user with strong temporary password
            val tempPassword = generateTempPassword()
            val user = userService.createInternalUser(
                email = input.email,
                password = tempPassword,
                firstName = input.firstName,
                lastName = input.lastName,
                roleNames = input.roles
            )

            // 2) Generate reset token directly (no need to look up the just-created user)
            val key = buildSecretKey(jwtSecret)
            val now = Instant.now()
            val expiryDate = Date(now.toEpochMilli() + jwtExpirationMs)

            val token = Jwts.builder()
                .subject(user.email)
                .issuedAt(Date.from(now))
                .expiration(expiryDate)
                .claim("actorType", "user")
                .signWith(key)
                .compact()

            // 3) Send email using branded template
            val resetLink = "${input.adminUrl}/forgot-password?token=$token"
            val subject = "You're invited to Vernont Admin"
            val templateData = mapOf(
                "brand" to "Vernont",
                "preheader" to "You've been invited to Vernont Admin",
                "title" to subject,
                "heading" to "Welcome to Vernont Admin",
                "body" to "<p>You've been invited to the Vernont Admin.</p><p>Click the button below to set your password and finish setting up your account.</p>",
                "buttonText" to "Set your password",
                "buttonUrl" to resetLink,
                "footer" to "© Vernont",
                "logoUrl" to ""
            )
            emailService.sendTemplateEmail(
                to = input.email,
                templateId = "admin-invite",
                templateData = templateData,
                subject = subject
            )

            WorkflowResult.success(InviteInternalUserOutput(user.id, user.email))
        } catch (e: Exception) {
            logger.error(e) { "InviteInternalUserWorkflow failed: ${'$'}{e.message}" }
            WorkflowResult.failure(e)
        }
    }

    private fun generateTempPassword(): String {
        val chars = (('a'..'z') + ('A'..'Z') + ('0'..'9') + "!@#$%^&*()-_+=?{}[]").toList()
        return (1..20).map { chars.random() }.joinToString("")
    }

    private fun buildSecretKey(secret: String): javax.crypto.SecretKey {
        val raw = secret.trim()
        val decoded = try {
            Base64.getDecoder().decode(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
        val keyBytes = (decoded?.takeIf { it.isNotEmpty() } ?: raw.toByteArray())
        val minBits = 256 // HS256 minimum
        if (keyBytes.size * 8 < minBits) {
            throw WeakKeyException("JWT secret too weak. Provide a key with at least $minBits bits (e.g. 'openssl rand -base64 64').")
        }
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
