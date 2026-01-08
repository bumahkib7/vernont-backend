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
 * Helper to create workflow steps with automatic event publishing and compensation registration.
 *
 * When a step completes successfully and has a compensate function defined,
 * the compensation is automatically pushed onto the context's compensation stack.
 * This enables saga-pattern rollback on workflow failure.
 *
 * @param name Step name for logging and tracking
 * @param execute Step execution function returning StepResponse with optional compensationData
 * @param compensate Compensation function receiving (input, output, compensationData, context).
 *                   For simple compensations that don't need output/compensationData, just ignore those params:
 *                   `compensate = { input, _, _, ctx -> ... }`
 */
fun <I, O> createStep(
    name: String,
    execute: suspend (I, WorkflowContext) -> StepResponse<O>,
    compensate: (suspend (I, O, Any?, WorkflowContext) -> Unit)? = null
): WorkflowStep<I, O> {
    return object : WorkflowStep<I, O> {
        override val name: String = name

        override suspend fun invoke(input: I, context: WorkflowContext): StepResponse<O> {
            logger.info { "Executing step: $name" }
            val startTime = System.currentTimeMillis()

            // Publish step started event and capture index for threading through
            val stepIndex = context.recordStepStart(name, input)

            return try {
                val result = execute(input, context)
                val durationMs = System.currentTimeMillis() - startTime

                // Publish step completed event with the same index
                context.recordStepComplete(name, stepIndex, result.data, durationMs)

                // Register compensation if step succeeded and has a compensate function
                if (compensate != null) {
                    context.pushCompensation(name) {
                        compensate.invoke(input, result.data, result.compensationData, context)
                    }
                }

                logger.info { "Step completed: $name in ${durationMs}ms" }
                result
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTime

                // Publish step failed event with the same index
                context.recordStepFailed(name, stepIndex, e, durationMs)

                logger.error(e) { "Step failed: $name after ${durationMs}ms" }
                throw e
            }
        }

        override suspend fun compensate(input: I, context: WorkflowContext) {
            // Note: This method is rarely called directly now that we have automatic
            // compensation registration. It's kept for manual compensation scenarios.
            logger.warn { "Manual compensate() called for step: $name - prefer automatic compensation via context stack" }
        }
    }
}

/**
 * Legacy overload for backwards compatibility.
 * Creates a workflow step with the simpler (input, context) compensation signature.
 *
 * Prefer the enhanced version above for new code, which provides output and compensationData to compensate.
 */
@JvmName("createStepLegacy")
fun <I, O> createStep(
    name: String,
    execute: suspend (I, WorkflowContext) -> StepResponse<O>,
    compensate: (suspend (I, WorkflowContext) -> Unit)?
): WorkflowStep<I, O> {
    // Wrap legacy compensate in the new signature
    val wrappedCompensate: (suspend (I, O, Any?, WorkflowContext) -> Unit)? = if (compensate != null) {
        { input, _, _, ctx -> compensate(input, ctx) }
    } else null

    return createStep(name, execute, wrappedCompensate)
}

/**
 * Parallel step execution helper.
 *
 * WARNING: Use with caution! When running workflow steps in parallel:
 * - Step indices will be assigned in start order, not completion order
 * - Compensation order may not match logical dependency order
 * - If one parallel step fails, others may still complete and push compensations
 *
 * Safe to use for:
 * - Read-only operations (fetching data, computing values)
 * - Independent external service calls with no side effects
 *
 * NOT safe for:
 * - Steps with side effects that depend on each other
 * - Steps where compensation order matters
 *
 * If you need parallel execution with proper compensation semantics,
 * consider grouping parallel operations into a single step.
 */
suspend fun <T> parallel(vararg steps: suspend () -> T): List<T> {
    return kotlinx.coroutines.coroutineScope {
        steps.map { async { it() } }.awaitAll()
    }
}
