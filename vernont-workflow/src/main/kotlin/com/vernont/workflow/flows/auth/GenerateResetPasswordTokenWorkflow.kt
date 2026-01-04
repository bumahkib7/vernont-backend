package com.vernont.workflow.flows.auth

import com.vernont.domain.auth.User
import com.vernont.events.EventPublisher
import com.vernont.events.PasswordResetEvent
import com.vernont.repository.auth.UserRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.auth.dto.GenerateResetPasswordTokenInput
import com.vernont.workflow.flows.auth.dto.GenerateResetPasswordTokenOutput
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import java.time.Instant
import java.util.Base64

private val logger = KotlinLogging.logger {}

@Component
@WorkflowTypes(input = GenerateResetPasswordTokenInput::class, output = GenerateResetPasswordTokenOutput::class)
class GenerateResetPasswordTokenWorkflow(
    private val userRepository: UserRepository,
    private val eventPublisher: EventPublisher,
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.expiration-ms:3600000}") private val jwtExpirationMs: Long // 1 hour default
) : Workflow<GenerateResetPasswordTokenInput, GenerateResetPasswordTokenOutput> {

    override val name: String = WorkflowConstants.Auth.GENERATE_RESET_PASSWORD_TOKEN

    override suspend fun execute(
        input: GenerateResetPasswordTokenInput,
        context: WorkflowContext
    ): WorkflowResult<GenerateResetPasswordTokenOutput> {
        logger.info { "Starting generate reset password token workflow for entity: ${input.entityId}" }

        return try {
            val findUserStep = createStep<GenerateResetPasswordTokenInput, User>(
                name = "find-user-by-email",
                execute = { inp, ctx ->
                    val user = userRepository.findByEmail(inp.entityId)
                        ?: throw IllegalArgumentException("User with email ${inp.entityId} not found")
                    ctx.addMetadata("user", user)
                    StepResponse.of<User>(user)
                }
            )

            val generateTokenStep = createStep<User, String>(
                name = "generate-password-reset-token",
                execute = { user, ctx ->
                    val key = buildSecretKey(jwtSecret)
                    val now = Instant.now()
                    val expiryDate = Date(now.toEpochMilli() + jwtExpirationMs)

                    val token = Jwts.builder()
                        .subject(user.email)
                        .issuedAt(Date.from(now))
                        .expiration(expiryDate)
                        .claim("actorType", input.actorType)
                        .signWith(key)
                        .compact()
                    
                    ctx.addMetadata("token", token)
                    StepResponse.of<String>(token)
                }
            )

            val emitEventStep = createStep<GenerateResetPasswordTokenInput, Unit>(
                name = "emit-password-reset-event",
                execute = { _, ctx ->
                    val user = ctx.getMetadata("user") as User
                    val token = ctx.getMetadata("token") as String

                    val event = PasswordResetEvent(
                        entityId = user.email,
                        actorType = input.actorType,
                        token = token
                    )
                    eventPublisher.publish(event)
                    StepResponse.of<Unit>(Unit)
                }
            )
            
            val user = findUserStep.invoke(input, context).data
            val token = generateTokenStep.invoke(user, context).data
            emitEventStep.invoke(input, context)

            val output = GenerateResetPasswordTokenOutput(token)
            WorkflowResult.success(output)

        } catch (e: Exception) {
            logger.error(e) { "Generate reset password token workflow failed: ${e.message}" }
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
            throw WeakKeyException("JWT secret too weak. Provide a key with at least $minBits bits (e.g. 'openssl rand -base64 64').")
        }
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
