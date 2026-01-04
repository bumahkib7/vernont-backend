package com.vernont.workflow.listener

import com.vernont.events.EmailVerificationEvent
import com.vernont.infrastructure.email.EmailService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class EmailVerificationEventListener(
    private val emailService: EmailService,
    @Value("\${app.frontend.url:http://localhost:3000}") private val frontendUrl: String
) {

    @EventListener
    fun handleEmailVerificationEvent(event: EmailVerificationEvent) = runBlocking {
        logger.info { "Handling EmailVerificationEvent for email: ${event.email}" }

        try {
            sendVerificationEmail(event.email, event.token)
            logger.info { "Verification email sent successfully to: ${event.email}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send verification email to: ${event.email}" }
        }
    }

    private suspend fun sendVerificationEmail(email: String, token: String) {
        // Use API URL for verification (will redirect to frontend after verification)
        val apiUrl = System.getenv("API_URL") ?: "http://localhost:8080"
        val verificationUrl = "$apiUrl/store/auth/verify-email?token=$token"

        val subject = "Verify Your Email - Vernont"

        val templateData = mapOf(
            "brand" to "Vernont",
            "preheader" to "Please verify your email address to activate your account",
            "heading" to "Verify Your Email",
            "body" to """
                <p>Welcome to Vernont! We're excited to have you on board.</p>
                <p>Please click the button below to verify your email address and activate your account.</p>
                <p style="margin-top: 24px; color: #6b7280; font-size: 14px;">
                    This link will expire in 24 hours. If you didn't create an account with Vernont, you can safely ignore this email.
                </p>
            """.trimIndent(),
            "buttonText" to "Verify Email Address",
            "buttonUrl" to verificationUrl,
            "footer" to "© Vernont · <a href=\"$frontendUrl\">Visit Our Website</a>",
            "logoUrl" to ""
        )

        emailService.sendTemplateEmail(
            to = email,
            templateId = "email-verification",
            templateData = templateData,
            subject = subject
        )

        logger.info { "Sent verification email to $email with verification URL: $verificationUrl" }
    }
}
