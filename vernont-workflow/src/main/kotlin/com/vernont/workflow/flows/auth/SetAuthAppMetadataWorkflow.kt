package com.vernont.workflow.flows.auth

import com.vernont.domain.auth.User
import com.vernont.repository.auth.UserRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.flows.auth.dto.SetAuthAppMetadataInput
import com.vernont.workflow.flows.auth.dto.UserDto
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
@WorkflowTypes(input = SetAuthAppMetadataInput::class, output = UserDto::class)
class SetAuthAppMetadataWorkflow(
    private val userRepository: UserRepository
) : Workflow<SetAuthAppMetadataInput, UserDto> {

    override val name: String = WorkflowConstants.AuthMetadata.SETUP

    @Transactional
    override suspend fun execute(
        input: SetAuthAppMetadataInput,
        context: WorkflowContext
    ): WorkflowResult<UserDto> {
        logger.info { "Starting $name workflow for userId=${input.userId}" }

        return try {
            // Step 1: Load User
            val loadUserStep = createStep<String, User>(
                name = "load-user",
                execute = { userId, ctx ->
                    val user = userRepository.findById(userId).orElseThrow {
                        IllegalArgumentException("User not found with id=$userId")
                    }

                    user.metadata?.toMutableMap()?.let {
                        ctx.addMetadata(
                            "originalUserMetadata",
                            it
                        )
                    }

                    StepResponse.of(user)
                },
                compensate = { userId, ctx ->
                    val user = userRepository.findById(userId).orElse(null)
                    if (user != null) {
                        @Suppress("UNCHECKED_CAST")
                        val originalMetadata =
                            ctx.getMetadata("originalUserMetadata") as? MutableMap<String, Any?>

                        user.metadata = originalMetadata
                        userRepository.save(user)
                        logger.info { "Compensated: restored original metadata for userId=$userId" }
                    }
                }
            )

            // Step 2: Update User's app metadata
            val updateMetadataStep = createStep<SetAuthAppMetadataInput, User>(
                name = "update-app-metadata",
                execute = { inp, ctx ->
                    val user = ctx.getMetadata("user") as User
                    val actorIdKey = "${inp.actorType}_id"

                    if (user.metadata == null) {
                        user.metadata = mutableMapOf()
                    }

                    if (inp.value == null) {
                        user.metadata?.remove(actorIdKey)
                        logger.info { "Removed key '$actorIdKey' from user ${user.id} metadata" }
                    } else {
                        user.metadata?.put(actorIdKey, inp.value)
                        logger.info {
                            "Set key '$actorIdKey' to '${inp.value}' in user ${user.id} metadata"
                        }
                    }

                    val updatedUser = userRepository.save(user)
                    StepResponse.Companion.of(updatedUser)
                }
            )

            // Step 3: Map User to UserDto
            val mapToDtoStep = createStep<User, UserDto>(
                name = "map-to-user-dto",
                execute = { user, _ ->
                    val dto = UserDto(
                        id = user.id,
                        email = user.email,
                        firstName = user.firstName,
                        lastName = user.lastName,
                        isActive = user.isActive,
                        emailVerified = user.emailVerified,
                        lastLoginAt = user.lastLoginAt,
                        createdAt = user.createdAt,
                        updatedAt = user.updatedAt,
                        metadata = user.metadata
                    )
                    StepResponse.Companion.of(dto)
                }
            )

            // Execute steps in sequence
            val loadedUser = loadUserStep.invoke(input.userId, context).data
            context.addMetadata("user", loadedUser)

            val updatedUser = updateMetadataStep.invoke(input, context).data
            val userDto = mapToDtoStep.invoke(updatedUser, context).data

            logger.info {
                "set-auth-app-metadata workflow completed successfully for userId=${input.userId}"
            }

            WorkflowResult.success(userDto)
        } catch (e: Exception) {
            logger.error(e) { "set-auth-app-metadata workflow failed: ${e.message}" }
            WorkflowResult.Companion.failure(e)
        }
    }

    override suspend fun compensate(context: WorkflowContext) {
        logger.warn { "Compensating set-auth-app-metadata workflow, executionId=${context.executionId}" }
        // Additional global compensation logic can go here if needed.
    }
}