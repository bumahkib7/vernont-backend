package com.vernont.workflow.engine

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Abstract base class for workflows that support extensibility.
 *
 * Extend this class to create workflows that can be customized by consumer projects
 * without full replacement. Customizers and step providers are automatically discovered
 * and applied by the workflow engine.
 *
 * Example:
 * ```
 * @Component
 * @ConditionalOnMissingBean(name = ["createOrderWorkflow"])
 * @WorkflowTypes(input = CreateOrderInput::class, output = Order::class)
 * class CreateOrderWorkflow(
 *     customizers: List<WorkflowCustomizer<CreateOrderInput, Order>>,
 *     stepProviders: List<WorkflowStepProvider>
 * ) : ExtensibleWorkflow<CreateOrderInput, Order>(customizers, stepProviders) {
 *     override val name = "order.create"
 *
 *     override suspend fun doExecute(input: CreateOrderInput, context: WorkflowContext): Order {
 *         // Core workflow logic
 *     }
 * }
 * ```
 *
 * @param I The workflow input type
 * @param O The workflow output type
 */
abstract class ExtensibleWorkflow<I : Any, O : Any>(
    private val allCustomizers: List<WorkflowCustomizer<*, *>> = emptyList(),
    private val allStepProviders: List<WorkflowStepProvider> = emptyList()
) : Workflow<I, O> {

    /**
     * Get customizers filtered and sorted for this workflow.
     */
    @Suppress("UNCHECKED_CAST")
    private val customizers: List<WorkflowCustomizer<I, O>> by lazy {
        allCustomizers
            .filter { it.getWorkflowName() == name }
            .sortedBy { it.getOrder() }
            .map { it as WorkflowCustomizer<I, O> }
    }

    /**
     * Get step providers filtered and sorted for this workflow.
     */
    private val stepProviders: List<WorkflowStepProvider> by lazy {
        allStepProviders
            .filter { it.getWorkflowName() == name && it.isEnabled() }
            .sortedBy { it.getOrder() }
    }

    /**
     * Main execute method that applies customizers and step providers.
     * Do not override this - override doExecute() instead.
     */
    final override suspend fun execute(input: I, context: WorkflowContext): WorkflowResult<O> {
        logger.debug { "Executing extensible workflow: $name with ${customizers.size} customizers and ${stepProviders.size} step providers" }

        return try {
            // 1. Run beforeExecute hooks
            customizers.forEach { customizer ->
                logger.debug { "Running beforeExecute hook: ${customizer::class.simpleName}" }
                customizer.beforeExecute(input, context)
            }

            // 2. Run BEFORE_ALL step providers
            stepProviders
                .filter { it.getPosition() is StepPosition.BeforeAll }
                .forEach { provider ->
                    logger.debug { "Running BEFORE_ALL step: ${provider.getStep().name}" }
                    @Suppress("UNCHECKED_CAST")
                    val step = provider.getStep() as com.vernont.workflow.steps.WorkflowStep<Any, Any>
                    step.invoke(input, context)
                }

            // 3. Execute core workflow logic
            var result = doExecute(input, context)

            // 4. Run AFTER_ALL step providers
            stepProviders
                .filter { it.getPosition() is StepPosition.AfterAll }
                .forEach { provider ->
                    logger.debug { "Running AFTER_ALL step: ${provider.getStep().name}" }
                    @Suppress("UNCHECKED_CAST")
                    val step = provider.getStep() as com.vernont.workflow.steps.WorkflowStep<Any, Any>
                    step.invoke(result, context)
                }

            // 5. Run afterExecute hooks (can modify result)
            customizers.forEach { customizer ->
                logger.debug { "Running afterExecute hook: ${customizer::class.simpleName}" }
                result = customizer.afterExecute(input, result, context)
            }

            WorkflowResult.success(result)

        } catch (e: Exception) {
            logger.error(e) { "Workflow $name failed: ${e.message}" }

            // Run onError hooks
            customizers.forEach { customizer ->
                try {
                    customizer.onError(input, e, context)
                } catch (hookError: Exception) {
                    logger.error(hookError) { "Error in onError hook: ${customizer::class.simpleName}" }
                }
            }

            WorkflowResult.failure(e)
        }
    }

    /**
     * Execute a step with BEFORE_STEP and AFTER_STEP providers applied.
     *
     * Call this method from doExecute() to allow step providers to inject
     * additional steps around your named steps.
     *
     * @param stepName The name of the step (must be unique within the workflow)
     * @param context The workflow context
     * @param stepLogic The actual step logic to execute
     * @return The result of the step
     */
    protected suspend fun <T> executeStep(
        stepName: String,
        input: Any,
        context: WorkflowContext,
        stepLogic: suspend () -> T
    ): T {
        // Run BEFORE_STEP providers
        stepProviders
            .filter { it.getPosition() is StepPosition.BeforeStep && (it.getPosition() as StepPosition.BeforeStep).stepName == stepName }
            .forEach { provider ->
                logger.debug { "Running BEFORE_STEP($stepName): ${provider.getStep().name}" }
                @Suppress("UNCHECKED_CAST")
                val step = provider.getStep() as com.vernont.workflow.steps.WorkflowStep<Any, Any>
                step.invoke(input, context)
            }

        // Execute the actual step
        val result = stepLogic()

        // Run AFTER_STEP providers
        stepProviders
            .filter { it.getPosition() is StepPosition.AfterStep && (it.getPosition() as StepPosition.AfterStep).stepName == stepName }
            .forEach { provider ->
                logger.debug { "Running AFTER_STEP($stepName): ${provider.getStep().name}" }
                @Suppress("UNCHECKED_CAST")
                val step = provider.getStep() as com.vernont.workflow.steps.WorkflowStep<Any, Any>
                step.invoke(result as Any, context)
            }

        return result
    }

    /**
     * Implement this method with your core workflow logic.
     *
     * Use executeStep() to wrap named steps and allow step providers to inject
     * additional steps around them.
     *
     * @param input The workflow input
     * @param context The workflow context
     * @return The workflow output
     */
    protected abstract suspend fun doExecute(input: I, context: WorkflowContext): O
}
