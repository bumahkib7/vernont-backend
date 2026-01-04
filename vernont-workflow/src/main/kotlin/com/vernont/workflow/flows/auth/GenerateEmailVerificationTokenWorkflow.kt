package com.vernont.workflow.flows.auth

import com.vernont.domain.auth.User
import com.vernont.events.EmailVerificationEvent
import com.vernont.events.EventPublisher
import com.vernont.repository.auth.UserRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.auth.dto.GenerateEmailVerificationTokenInput
import com.vernont.workflow.flows.auth.dto.GenerateEmailVerificationTokenOutput
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Generate Email Verification Token Workflow
 *
 * Steps:
 * 1. Find user by email
 * 2. Generate verification token (JWT with 24 hour expiry)
 * 3. Publish EmailVerificationEvent
 */
@Component
@WorkflowTypes(input = GenerateEmailVerificationTokenInput::class, output = GenerateEmailVerificationTokenOutput::class)
class GenerateEmailVerificationTokenWorkflow(
    private val userRepository: UserRepository,
    private val eventPublisher: EventPublisher,
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.email-verification.expiration-ms:86400000}") private val tokenExpirationMs: Long // 24 hours default
) : Workflow<GenerateEmailVerificationTokenInput, GenerateEmailVerificationTokenOutput> {

    override val name: String = WorkflowConstants.Auth.GENERATE_EMAIL_VERIFICATION_TOKEN

    override suspend fun execute(
        input: GenerateEmailVerificationTokenInput,
        context: WorkflowContext
    ): WorkflowResult<GenerateEmailVerificationTokenOutput> {
        logger.info { "Starting generate email verification token workflow for user: ${input.userId}" }

        return try {
            // Step 1: Find user by email
            val findUserStep = createStep<GenerateEmailVerificationTokenInput, User>(
                name = "find-user-by-email",
                execute = { inp, ctx ->
                    val user = userRepository.findById(inp.userId).orElse(null)
                        ?: throw IllegalArgumentException("User with ID ${inp.userId} not found")
                    ctx.addMetadata("user", user)
                    logger.debug { "Found user: ${user.email}" }
                    StepResponse.of(user)
                }
            )

            // Step 2: Generate verification token
            val generateTokenStep = createStep<User, String>(
                name = "generate-email-verification-token",
                execute = { user, ctx ->
                    val key = buildSecretKey(jwtSecret)
                    val now = Instant.now()
                    val expiryDate = Date(now.toEpochMilli() + tokenExpirationMs)

                    val token = Jwts.builder()
                        .subject(user.email)
                        .issuedAt(Date.from(now))
                        .expiration(expiryDate)
                        .claim("userId", user.id)
                        .claim("purpose", "email_verification")
                        .signWith(key)
                        .compact()

                    ctx.addMetadata("token", token)
                    logger.debug { "Generated email verification token for user: ${user.email}" }
                    StepResponse.of(token)
                }
            )

            // Step 3: Publish EmailVerificationEvent
            val publishEventStep = createStep<GenerateEmailVerificationTokenInput, Unit>(
                name = "publish-email-verification-event",
                execute = { _, ctx ->
                    val user = ctx.getMetadata("user") as User
                    val token = ctx.getMetadata("token") as String

                    val event = EmailVerificationEvent(
                        userId = user.id,
                        email = user.email,
                        token = token,
                        aggregateId = user.id
                    )

                    eventPublisher.publish(event)
                    logger.info { "Published EmailVerificationEvent for user: ${user.email}" }
                    StepResponse.of(Unit)
                }
            )

            // Execute steps
            val user = findUserStep.invoke(input, context).data
            val token = generateTokenStep.invoke(user, context).data
            publishEventStep.invoke(input, context)

            logger.info { "Email verification token generated successfully for user: ${user.email}" }
            WorkflowResult.success(GenerateEmailVerificationTokenOutput(token = token))

        } catch (e: Exception) {
            logger.error(e) { "Failed to generate email verification token: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }

    private fun buildSecretKey(secret: String): javax.crypto.SecretKey {
        return try {
            val keyBytes = java.util.Base64.getDecoder().decode(secret)
            Keys.hmacShaKeyFor(keyBytes)
        } catch (e: Exception) {
            logger.warn { "JWT secret is not base64 encoded, using as-is" }
            // Fallback: use secret directly (ensure it's long enough for HS256)
            val keyBytes = secret.toByteArray()
            require(keyBytes.size >= 32) { "JWT secret must be at least 256 bits (32 bytes)" }
            Keys.hmacShaKeyFor(keyBytes)
        }
    }
}
