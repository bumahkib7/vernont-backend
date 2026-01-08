package com.vernont.workflow.flows.customer

import com.vernont.domain.customer.CustomerActivityLog
import com.vernont.infrastructure.email.EmailService
import com.vernont.repository.auth.UserRepository
import com.vernont.repository.customer.CustomerActivityLogRepository
import com.vernont.repository.customer.CustomerRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Base64
import java.util.Date

private val logger = KotlinLogging.logger {}

/**
 * Workflow for initiating a password reset for a customer.
 * Generates a password reset token, sends email, and logs the activity.
 */
@Component
@WorkflowTypes(input = ResetCustomerPasswordInput::class, output = ResetCustomerPasswordOutput::class)
class ResetCustomerPasswordWorkflow(
    private val customerRepository: CustomerRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val activityLogRepository: CustomerActivityLogRepository,
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.expiration-ms:3600000}") private val jwtExpirationMs: Long,
    @Value("\${app.storefront.url:http://localhost:3000}") private val storefrontUrl: String
) : Workflow<ResetCustomerPasswordInput, ResetCustomerPasswordOutput> {

    override val name = WorkflowConstants.Customer.RESET_PASSWORD

    override suspend fun execute(
        input: ResetCustomerPasswordInput,
        context: WorkflowContext
    ): WorkflowResult<ResetCustomerPasswordOutput> {
        return try {
            logger.info { "Initiating password reset for customer ${input.customerId}" }

            // Find customer
            val customer = customerRepository.findByIdAndDeletedAtIsNull(input.customerId)
                ?: return WorkflowResult.failure(IllegalArgumentException("Customer not found: ${input.customerId}"))

            // Check if customer has an account
            if (!customer.hasAccount) {
                return WorkflowResult.failure(IllegalStateException("Customer does not have an account. Cannot reset password."))
            }

            // Find the user associated with this customer
            val user = customer.user
                ?: return WorkflowResult.failure(IllegalStateException("Customer has hasAccount=true but no linked user. Data inconsistency."))

            // Generate password reset token
            val key = buildSecretKey(jwtSecret)
            val now = Instant.now()
            val expiryDate = Date(now.toEpochMilli() + jwtExpirationMs)

            val token = Jwts.builder()
                .subject(input.email)
                .issuedAt(Date.from(now))
                .expiration(expiryDate)
                .claim("actorType", "customer")
                .claim("customerId", input.customerId)
                .signWith(key)
                .compact()

            // Build reset link
            val resetLink = "$storefrontUrl/reset-password?token=$token"

            // Send the email
            var emailSent = false
            try {
                emailService.sendTransactionalEmail(
                    to = input.email,
                    subject = "Reset your Vernont password",
                    htmlContent = buildResetPasswordEmail(customer.firstName, resetLink),
                    plainTextContent = buildResetPasswordEmailPlainText(customer.firstName, resetLink)
                )
                emailSent = true
            } catch (e: Exception) {
                logger.error(e) { "Failed to send password reset email to customer ${input.customerId}" }
            }

            // Log the activity
            val performedBy = context.getMetadata("performedBy") as? String ?: "system"
            val activityLog = CustomerActivityLog.passwordResetRequested(
                customerId = input.customerId,
                performedBy = performedBy
            )
            activityLogRepository.save(activityLog)

            logger.info { "Password reset initiated for customer ${input.customerId}, emailSent: $emailSent" }

            WorkflowResult.success(
                ResetCustomerPasswordOutput(
                    success = true,
                    resetTokenSent = emailSent
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to initiate password reset for customer ${input.customerId}: ${e.message}" }
            WorkflowResult.failure(e)
        }
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
            throw WeakKeyException("JWT secret too weak. Provide a key with at least $minBits bits.")
        }
        return Keys.hmacShaKeyFor(keyBytes)
    }

    private fun buildResetPasswordEmail(firstName: String?, resetLink: String): String {
        val greeting = firstName?.let { "Hi $it," } ?: "Hi,"

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="text-align: center; padding: 20px 0; border-bottom: 1px solid #eee; margin-bottom: 30px;">
                    <h1 style="color: #000; margin: 0;">Vernont</h1>
                </div>

                <div style="text-align: center;">
                    <h2 style="color: #333;">Reset Your Password</h2>
                    <p>$greeting</p>
                    <p>We received a request to reset your password for your Vernont account.</p>

                    <div style="margin: 30px 0;">
                        <a href="$resetLink" style="background-color: #000; color: #fff; padding: 14px 32px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: 500;">
                            Reset Password
                        </a>
                    </div>

                    <div style="background-color: #fef3c7; border-radius: 8px; padding: 16px; margin: 20px 0; text-align: left;">
                        <p style="margin: 0; color: #92400e; font-size: 14px;">
                            <strong>This link will expire in 1 hour.</strong><br>
                            If you didn't request a password reset, you can safely ignore this email.
                        </p>
                    </div>

                    <p style="font-size: 14px; color: #666;">
                        Or copy and paste this link into your browser:<br>
                        <span style="word-break: break-all; color: #2563eb;">$resetLink</span>
                    </p>
                </div>

                <div style="border-top: 1px solid #eee; margin-top: 30px; padding-top: 20px; text-align: center; font-size: 12px; color: #666;">
                    <p>&copy; Vernont. All rights reserved.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildResetPasswordEmailPlainText(firstName: String?, resetLink: String): String {
        val greeting = firstName?.let { "Hi $it," } ?: "Hi,"

        return """
            Vernont - Reset Your Password

            $greeting

            We received a request to reset your password for your Vernont account.

            Click the link below to reset your password:
            $resetLink

            This link will expire in 1 hour. If you didn't request a password reset, you can safely ignore this email.

            ---
            (c) Vernont. All rights reserved.
        """.trimIndent()
    }
}
