package com.vernont.workflow.events

import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.vernont.infrastructure.messaging.MessagingService
import com.vernont.workflow.domain.StepEventStatus
import com.vernont.workflow.domain.WorkflowStepEvent
import com.vernont.workflow.repository.WorkflowStepEventRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Mixin to add @JsonIdentityInfo to any class, preventing circular reference issues
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator::class, property = "@id")
private abstract class CircularReferenceMixin

/**
 * Publishes workflow execution events to Kafka (persistence) and WebSocket (real-time UI).
 * Also persists step events to the database for historical tracking.
 */
@Component
class WorkflowEventPublisher(
    private val messagingService: MessagingService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
    private val stepEventRepository: WorkflowStepEventRepository
) {
    private lateinit var eventObjectMapper: ObjectMapper

    // Track in-progress step events by (executionId, stepIndex) for updating on completion
    private val inProgressSteps = ConcurrentHashMap<String, WorkflowStepEvent>()

    @PostConstruct
    fun init() {
        // Create a dedicated ObjectMapper for event serialization that handles circular references
        eventObjectMapper = objectMapper.copy().apply {
            // Don't fail on empty beans (common with lazy-loaded proxies)
            configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            // Add mixin for common entities that have circular references
            addMixIn(Any::class.java, CircularReferenceMixin::class.java)
        }
    }

    companion object {
        const val KAFKA_TOPIC = "workflow-events"
        const val WEBSOCKET_TOPIC = "/topic/workflows"

        private fun stepKey(executionId: String, stepIndex: Int) = "$executionId:$stepIndex"
    }

    /**
     * Publish workflow/step event to both Kafka and WebSocket
     */
    fun publish(event: WorkflowExecutionEvent) {
        try {
            // Publish to Kafka for persistence
            publishToKafka(event)

            // Broadcast via WebSocket for real-time UI
            publishToWebSocket(event)

            logger.debug {
                "Published workflow event: ${event.eventType} " +
                "for ${event.workflowName}${event.stepName?.let { " step=$it" } ?: ""} " +
                "(execution=${event.executionId})"
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish workflow event: ${event.eventType}" }
            // Don't throw - event publishing should not break workflow execution
        }
    }

    /**
     * Serialize input/output data to JSON string for event payload.
     * Uses a dedicated ObjectMapper configured to handle circular references in JPA entities.
     */
    fun serializeData(data: Any?): String? {
        if (data == null) return null
        return try {
            eventObjectMapper.writeValueAsString(data)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to serialize data for workflow event" }
            // Return a placeholder if serialization fails
            "{\"error\": \"serialization_failed\", \"type\": \"${data::class.simpleName}\"}"
        }
    }

    private fun publishToKafka(event: WorkflowExecutionEvent) {
        try {
            messagingService.publish(KAFKA_TOPIC, event.executionId, event)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish workflow event to Kafka" }
        }
    }

    private fun publishToWebSocket(event: WorkflowExecutionEvent) {
        try {
            messagingTemplate.convertAndSend(WEBSOCKET_TOPIC, event)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish workflow event to WebSocket" }
        }
    }

    // Convenience methods for common events

    fun publishWorkflowStarted(
        executionId: String,
        workflowName: String,
        input: Any?,
        correlationId: String? = null,
        parentExecutionId: String? = null
    ) {
        publish(WorkflowExecutionEvent.workflowStarted(
            executionId = executionId,
            workflowName = workflowName,
            input = serializeData(input),
            correlationId = correlationId,
            parentExecutionId = parentExecutionId
        ))
    }

    fun publishWorkflowCompleted(
        executionId: String,
        workflowName: String,
        output: Any?,
        durationMs: Long,
        correlationId: String? = null
    ) {
        publish(WorkflowExecutionEvent.workflowCompleted(
            executionId = executionId,
            workflowName = workflowName,
            output = serializeData(output),
            durationMs = durationMs,
            correlationId = correlationId
        ))
    }

    fun publishWorkflowFailed(
        executionId: String,
        workflowName: String,
        error: Throwable,
        durationMs: Long,
        correlationId: String? = null
    ) {
        publish(WorkflowExecutionEvent.workflowFailed(
            executionId = executionId,
            workflowName = workflowName,
            error = error.message,
            errorType = error::class.simpleName,
            durationMs = durationMs,
            correlationId = correlationId
        ))
    }

    fun publishStepStarted(
        executionId: String,
        workflowName: String,
        stepName: String,
        stepIndex: Int,
        totalSteps: Int,
        input: Any?,
        correlationId: String? = null
    ) {
        val serializedInput = serializeData(input)
        val key = stepKey(executionId, stepIndex)

        // Persist step event to database (idempotent - handles duplicate inserts)
        try {
            val stepEvent = WorkflowStepEvent.create(
                executionId = executionId,
                workflowName = workflowName,
                stepName = stepName,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                inputData = serializedInput
            )
            stepEventRepository.save(stepEvent)
            inProgressSteps[key] = stepEvent
            logger.debug { "Persisted step started event: $workflowName/$stepName (execution=$executionId)" }
        } catch (e: org.springframework.dao.DataIntegrityViolationException) {
            // Check if this is specifically our unique constraint violation
            val isUniqueConstraintViolation = e.message?.contains("uq_workflow_step_event_execution_step") == true
                    || e.cause?.message?.contains("uq_workflow_step_event_execution_step") == true
                    || e.rootCause?.message?.contains("uq_workflow_step_event_execution_step") == true

            if (isUniqueConstraintViolation) {
                // Idempotent: row already exists (e.g., retry scenario or multi-node race)
                val existingEvent = stepEventRepository.findByExecutionIdAndStepIndex(executionId, stepIndex)
                if (existingEvent != null) {
                    inProgressSteps[key] = existingEvent
                    logger.info { "Step event already exists (idempotent): $workflowName/$stepName (execution=$executionId)" }
                } else {
                    logger.warn(e) { "Unique constraint violation but row not found: $key" }
                }
            } else {
                // Different integrity violation - don't swallow it
                logger.error(e) { "Unexpected data integrity violation for step event: $key" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to persist step started event to database" }
        }

        // Publish to Kafka/WebSocket
        publish(WorkflowExecutionEvent.stepStarted(
            executionId = executionId,
            workflowName = workflowName,
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            input = serializedInput,
            correlationId = correlationId
        ))
    }

    fun publishStepCompleted(
        executionId: String,
        workflowName: String,
        stepName: String,
        stepIndex: Int,
        totalSteps: Int,
        output: Any?,
        durationMs: Long,
        correlationId: String? = null
    ) {
        val serializedOutput = serializeData(output)

        // Update persisted step event
        try {
            val key = stepKey(executionId, stepIndex)
            var stepEvent = inProgressSteps.remove(key)

            // Fallback: if in-memory tracking missed (e.g., after restart), lookup from DB
            if (stepEvent == null) {
                stepEvent = stepEventRepository.findByExecutionIdAndStepIndex(executionId, stepIndex)
                if (stepEvent != null) {
                    logger.info { "Fallback DB lookup for step completion: $workflowName/$stepName (execution=$executionId)" }
                }
            }

            if (stepEvent != null) {
                // Idempotent: first terminal state wins
                when (stepEvent.status) {
                    StepEventStatus.RUNNING -> {
                        stepEvent.markCompleted(serializedOutput, durationMs)
                        try {
                            stepEventRepository.saveAndFlush(stepEvent)
                            logger.debug { "Persisted step completed event: $workflowName/$stepName (execution=$executionId)" }
                        } catch (e: ObjectOptimisticLockingFailureException) {
                            // Lost race to another writer - first terminal state wins
                            val latest = stepEventRepository.findByExecutionIdAndStepIndex(executionId, stepIndex)
                            when (latest?.status) {
                                StepEventStatus.COMPLETED -> logger.info { "Step already completed (optimistic race): $workflowName/$stepName (execution=$executionId)" }
                                StepEventStatus.FAILED -> logger.warn { "COMPLETED lost to FAILED (optimistic race): $workflowName/$stepName (execution=$executionId)" }
                                else -> logger.warn(e) { "Optimistic lock on completion but latest state unknown: $key" }
                            }
                        }
                    }
                    StepEventStatus.COMPLETED -> {
                        logger.info { "Step already completed (idempotent): $workflowName/$stepName (execution=$executionId)" }
                    }
                    StepEventStatus.FAILED -> {
                        logger.warn { "Received COMPLETED for already FAILED step (anomaly): $workflowName/$stepName (execution=$executionId)" }
                    }
                }
            } else {
                logger.warn { "No step event found for completion (in-memory or DB): $key" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to persist step completed event to database" }
        }

        // Publish to Kafka/WebSocket
        publish(WorkflowExecutionEvent.stepCompleted(
            executionId = executionId,
            workflowName = workflowName,
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            output = serializedOutput,
            durationMs = durationMs,
            correlationId = correlationId
        ))
    }

    fun publishStepFailed(
        executionId: String,
        workflowName: String,
        stepName: String,
        stepIndex: Int,
        totalSteps: Int,
        error: Throwable,
        durationMs: Long,
        correlationId: String? = null
    ) {
        // Update persisted step event
        try {
            val key = stepKey(executionId, stepIndex)
            var stepEvent = inProgressSteps.remove(key)

            // Fallback: if in-memory tracking missed (e.g., after restart), lookup from DB
            if (stepEvent == null) {
                stepEvent = stepEventRepository.findByExecutionIdAndStepIndex(executionId, stepIndex)
                if (stepEvent != null) {
                    logger.info { "Fallback DB lookup for step failure: $workflowName/$stepName (execution=$executionId)" }
                }
            }

            if (stepEvent != null) {
                // Idempotent: first terminal state wins
                when (stepEvent.status) {
                    StepEventStatus.RUNNING -> {
                        stepEvent.markFailed(error.message, error::class.simpleName, durationMs)
                        try {
                            stepEventRepository.saveAndFlush(stepEvent)
                            logger.debug { "Persisted step failed event: $workflowName/$stepName (execution=$executionId)" }
                        } catch (e: ObjectOptimisticLockingFailureException) {
                            // Lost race to another writer - first terminal state wins
                            val latest = stepEventRepository.findByExecutionIdAndStepIndex(executionId, stepIndex)
                            when (latest?.status) {
                                StepEventStatus.FAILED -> logger.info { "Step already failed (optimistic race): $workflowName/$stepName (execution=$executionId)" }
                                StepEventStatus.COMPLETED -> logger.warn { "FAILED lost to COMPLETED (optimistic race): $workflowName/$stepName (execution=$executionId)" }
                                else -> logger.warn(e) { "Optimistic lock on failure but latest state unknown: $key" }
                            }
                        }
                    }
                    StepEventStatus.FAILED -> {
                        logger.info { "Step already failed (idempotent): $workflowName/$stepName (execution=$executionId)" }
                    }
                    StepEventStatus.COMPLETED -> {
                        logger.warn { "Received FAILED for already COMPLETED step (anomaly): $workflowName/$stepName (execution=$executionId)" }
                    }
                }
            } else {
                logger.warn { "No step event found for failure (in-memory or DB): $key" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to persist step failed event to database" }
        }

        // Publish to Kafka/WebSocket
        publish(WorkflowExecutionEvent.stepFailed(
            executionId = executionId,
            workflowName = workflowName,
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            error = error.message,
            errorType = error::class.simpleName,
            durationMs = durationMs,
            correlationId = correlationId
        ))
    }

    /**
     * Publish step progress event (e.g., for image uploads).
     * Only publishes to WebSocket for real-time UI updates, not to Kafka (no persistence needed).
     */
    fun publishStepProgress(
        executionId: String,
        workflowName: String,
        stepName: String,
        stepIndex: Int,
        totalSteps: Int,
        progressCurrent: Int,
        progressTotal: Int,
        progressMessage: String? = null,
        correlationId: String? = null
    ) {
        val event = WorkflowExecutionEvent.stepProgress(
            executionId = executionId,
            workflowName = workflowName,
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            progressCurrent = progressCurrent,
            progressTotal = progressTotal,
            progressMessage = progressMessage,
            correlationId = correlationId
        )

        // Only publish to WebSocket for real-time updates (no persistence for progress events)
        publishToWebSocket(event)

        logger.debug {
            "Published step progress: $workflowName/$stepName " +
            "$progressCurrent/$progressTotal (${event.progressPercent}%) " +
            "(execution=$executionId)"
        }
    }
}
