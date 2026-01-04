package com.vernont.workflow.listener

import com.vernont.events.CustomerAccountCreated
import com.vernont.repository.auth.UserRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.auth.dto.GenerateEmailVerificationTokenInput
import com.vernont.workflow.flows.auth.dto.GenerateEmailVerificationTokenOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CustomerAccountCreatedListener(
    private val workflowEngine: WorkflowEngine,
    private val userRepository: UserRepository
) {

    @EventListener
    fun handleCustomerAccountCreated(event: CustomerAccountCreated) = runBlocking {
        logger.info { "Handling CustomerAccountCreated event for customer: ${event.customerId}" }

        // Only send verification email if user has an account
        val userId = event.userId
        if (userId == null) {
            logger.debug { "Customer ${event.customerId} has no user account, skipping email verification" }
            return@runBlocking
        }

        // Check if user is already verified (e.g., from OAuth)
        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            logger.warn { "User $userId not found for customer ${event.customerId}" }
            return@runBlocking
        }

        if (user.emailVerified) {
            logger.info { "User $userId already verified (likely from OAuth), skipping email verification" }
            return@runBlocking
        }

        try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.Auth.GENERATE_EMAIL_VERIFICATION_TOKEN,
                input = GenerateEmailVerificationTokenInput(
                    email = event.email,
                    userId = userId
                ),
                inputType = GenerateEmailVerificationTokenInput::class,
                outputType = GenerateEmailVerificationTokenOutput::class,
                context = WorkflowContext()
            )

            when (result) {
                is WorkflowResult.Success -> {
                    logger.info { "Email verification token generated for customer: ${event.customerId}" }
                }
                is WorkflowResult.Failure -> {
                    logger.error(result.error) { "Failed to generate email verification token for customer: ${event.customerId}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to trigger email verification workflow for customer: ${event.customerId}" }
        }
    }
}
