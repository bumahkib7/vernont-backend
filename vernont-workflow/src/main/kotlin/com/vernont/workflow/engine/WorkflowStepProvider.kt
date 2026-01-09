package com.vernont.workflow.engine

import com.vernont.workflow.steps.WorkflowStep

/**
 * Defines where an additional step should be inserted in a workflow.
 */
sealed class StepPosition {
    /** Insert step at the very beginning, before all other steps */
    data object BeforeAll : StepPosition()

    /** Insert step at the very end, after all other steps */
    data object AfterAll : StepPosition()

    /** Insert step before a specific named step */
    data class BeforeStep(val stepName: String) : StepPosition()

    /** Insert step after a specific named step */
    data class AfterStep(val stepName: String) : StepPosition()
}

/**
 * Interface for injecting additional steps into existing workflows.
 *
 * Implement this interface to add custom steps at specific positions
 * in a workflow without replacing the entire workflow.
 *
 * Example usage in a consumer project (e.g., gumite):
 * ```
 * @Component
 * class FraudCheckStepProvider(
 *     private val fraudService: FraudService
 * ) : WorkflowStepProvider {
 *
 *     override fun getWorkflowName() = "order.create"
 *
 *     override fun getPosition() = StepPosition.BeforeStep("reserve-inventory")
 *
 *     override fun getStep() = createStep<CreateOrderInput, Unit>(
 *         name = "fraud-check",
 *         execute = { input, ctx ->
 *             fraudService.checkOrder(input)
 *             StepResponse.of(Unit)
 *         }
 *     )
 * }
 * ```
 */
interface WorkflowStepProvider {

    /**
     * The name of the workflow this step provider applies to.
     * Must match the workflow's `name` property.
     */
    fun getWorkflowName(): String

    /**
     * The position where the step should be inserted.
     */
    fun getPosition(): StepPosition

    /**
     * The order in which this provider runs relative to others at the same position.
     * Lower values run first. Default is 0.
     */
    fun getOrder(): Int = 0

    /**
     * The step to inject into the workflow.
     * The step's input type should be compatible with the data available at the insertion point.
     */
    fun getStep(): WorkflowStep<*, *>

    /**
     * Whether this step is enabled. Can be used for feature flags.
     * Default is true.
     */
    fun isEnabled(): Boolean = true
}
