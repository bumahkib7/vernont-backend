package com.vernont.workflow.engine

/**
 * Interface for customizing workflow execution without full replacement.
 *
 * Implement this interface to add before/after hooks to existing workflows.
 * Customizers are automatically discovered and applied by the WorkflowEngine.
 *
 * Example usage in a consumer project (e.g., gumite):
 * ```
 * @Component
 * class OrderLoyaltyCustomizer(
 *     private val loyaltyService: LoyaltyService
 * ) : WorkflowCustomizer<CreateOrderInput, Order> {
 *
 *     override fun getWorkflowName() = "order.create"
 *
 *     override suspend fun afterExecute(input: CreateOrderInput, result: Order, context: WorkflowContext): Order {
 *         loyaltyService.awardPoints(result.customerId, result.total)
 *         return result
 *     }
 * }
 * ```
 *
 * @param I The workflow input type
 * @param O The workflow output type
 */
interface WorkflowCustomizer<I, O> {

    /**
     * The name of the workflow this customizer applies to.
     * Must match the workflow's `name` property.
     */
    fun getWorkflowName(): String

    /**
     * The order in which this customizer runs relative to others.
     * Lower values run first. Default is 0.
     */
    fun getOrder(): Int = 0

    /**
     * Called before the workflow executes.
     * Use this to validate input, set up context, or perform pre-processing.
     *
     * @param input The workflow input
     * @param context The workflow context for sharing data between steps
     */
    suspend fun beforeExecute(input: I, context: WorkflowContext) { }

    /**
     * Called after the workflow executes successfully.
     * Use this to modify the result, trigger side effects, or perform post-processing.
     *
     * @param input The original workflow input
     * @param result The workflow result
     * @param context The workflow context
     * @return The (potentially modified) result
     */
    suspend fun afterExecute(input: I, result: O, context: WorkflowContext): O = result

    /**
     * Called when the workflow fails.
     * Use this to perform custom error handling or cleanup.
     *
     * @param input The original workflow input
     * @param error The exception that caused the failure
     * @param context The workflow context
     */
    suspend fun onError(input: I, error: Throwable, context: WorkflowContext) { }
}
