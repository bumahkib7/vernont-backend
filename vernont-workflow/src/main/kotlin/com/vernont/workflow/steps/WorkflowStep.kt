package com.vernont.workflow.steps

import com.vernont.workflow.engine.WorkflowContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

private val logger = KotlinLogging.logger {}

/**
 * Base interface for workflow steps - inspired by Medusa's createStep
 */
interface WorkflowStep<I, O> {
    val name: String

    /**
     * Execute the step
     */
    suspend fun invoke(input: I, context: WorkflowContext): StepResponse<O>

    /**
     * Compensate/rollback the step
     */
    suspend fun compensate(input: I, context: WorkflowContext) {
        // Default: no compensation
    }
}

/**
 * Step response with optional compensation data
 */
data class StepResponse<T>(
    val data: T,
    val compensationData: Any? = null
) {
    companion object {
        fun <T> of(data: T, compensationData: Any? = null): StepResponse<T> {
            return StepResponse(data, compensationData)
        }
    }
}

/**
 * Helper to create workflow steps with automatic event publishing
 */
fun <I, O> createStep(
    name: String,
    execute: suspend (I, WorkflowContext) -> StepResponse<O>,
    compensate: (suspend (I, WorkflowContext) -> Unit)? = null
): WorkflowStep<I, O> {
    return object : WorkflowStep<I, O> {
        override val name: String = name

        override suspend fun invoke(input: I, context: WorkflowContext): StepResponse<O> {
            logger.info { "Executing step: $name" }
            val startTime = System.currentTimeMillis()

            // Publish step started event
            context.recordStepStart(name, input)

            return try {
                val result = execute(input, context)
                val durationMs = System.currentTimeMillis() - startTime

                // Publish step completed event
                context.recordStepComplete(name, result.data, durationMs)

                logger.info { "Step completed: $name in ${durationMs}ms" }
                result
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTime

                // Publish step failed event
                context.recordStepFailed(name, e, durationMs)

                logger.error(e) { "Step failed: $name after ${durationMs}ms" }
                throw e
            }
        }

        override suspend fun compensate(input: I, context: WorkflowContext) {
            if (compensate != null) {
                logger.info { "Compensating step: $name" }
                compensate.invoke(input, context)
            }
        }
    }
}

/**
 * Parallel step execution
 */
suspend fun <T> parallel(vararg steps: suspend () -> T): List<T> {
    return kotlinx.coroutines.coroutineScope {
        steps.map { async { it() } }.awaitAll()
    }
}
