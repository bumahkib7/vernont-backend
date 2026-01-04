package com.vernont.workflow.flows.auth

import com.vernont.domain.auth.User
import com.vernont.repository.auth.UserRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.auth.dto.VerifyEmailInput
import com.vernont.workflow.flows.auth.dto.VerifyEmailOutput
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Verify Email Workflow
 *
 * Steps:
 * 1. Validate and parse the verification token
 * 2. Find user by email from token
 * 3. Mark email as verified
 */
@Component
@WorkflowTypes(input = VerifyEmailInput::class, output = VerifyEmailOutput::class)
class VerifyEmailWorkflow(
    private val userRepository: UserRepository,
    @Value("\${app.jwt.secret}") private val jwtSecret: String
) : Workflow<VerifyEmailInput, VerifyEmailOutput> {

    override val name: String = WorkflowConstants.Auth.VERIFY_EMAIL

    @Transactional
    override suspend fun execute(
        input: VerifyEmailInput,
        context: WorkflowContext
    ): WorkflowResult<VerifyEmailOutput> {
        logger.info { "Starting verify email workflow" }

        return try {
            // Step 1: Validate and parse the verification token
            val parseTokenStep = createStep<String, Claims>(
                name = "parse-verification-token",
                execute = { token, ctx ->
                    try {
                        val key = buildSecretKey(jwtSecret)
                        val claims = Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .payload

                        // Validate purpose
                        val purpose = claims["purpose"] as? String
                        if (purpose != "email_verification") {
                            throw IllegalArgumentException("Invalid token purpose")
                        }

                        ctx.addMetadata("email", claims.subject)
                        logger.debug { "Token validated for email: ${claims.subject}" }
                        StepResponse.of(claims)
                    } catch (e: Exception) {
                        logger.error(e) { "Invalid or expired verification token" }
                        throw IllegalArgumentException("Invalid or expired verification token")
                    }
                }
            )

            // Step 2: Find user by email from token
            val findUserStep = createStep<Claims, User>(
                name = "find-user-by-email",
                execute = { claims, ctx ->
                    val email = claims.subject
                    val user = userRepository.findByEmail(email)
                        ?: throw IllegalArgumentException("User with email $email not found")

                    if (user.emailVerified) {
                        logger.info { "Email already verified for user: $email" }
                    }

                    ctx.addMetadata("user", user)
                    StepResponse.of(user)
                }
            )

            // Step 3: Mark email as verified
            val verifyEmailStep = createStep<User, User>(
                name = "mark-email-verified",
                execute = { user, ctx ->
                    if (!user.emailVerified) {
                        user.verifyEmail()
                        val savedUser = userRepository.save(user)
                        logger.info { "Email verified successfully for user: ${user.email}" }
                        StepResponse.of(savedUser)
                    } else {
                        logger.debug { "Email already verified, skipping update" }
                        StepResponse.of(user)
                    }
                }
            )

            // Execute steps
            val claims = parseTokenStep.invoke(input.token, context).data
            val user = findUserStep.invoke(claims, context).data
            verifyEmailStep.invoke(user, context)

            logger.info { "Email verification completed for user: ${user.email}" }
            WorkflowResult.success(
                VerifyEmailOutput(
                    email = user.email,
                    verified = true
                )
            )

        } catch (e: IllegalArgumentException) {
            logger.error { "Email verification failed: ${e.message}" }
            WorkflowResult.failure(e)
        } catch (e: Exception) {
            logger.error(e) { "Email verification workflow failed: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }

    private fun buildSecretKey(secret: String): javax.crypto.SecretKey {
        return try {
            val keyBytes = java.util.Base64.getDecoder().decode(secret)
            Keys.hmacShaKeyFor(keyBytes)
        } catch (e: Exception) {
            logger.warn { "JWT secret is not base64 encoded, using as-is" }
            val keyBytes = secret.toByteArray()
            require(keyBytes.size >= 32) { "JWT secret must be at least 256 bits (32 bytes)" }
            Keys.hmacShaKeyFor(keyBytes)
        }
    }
}
