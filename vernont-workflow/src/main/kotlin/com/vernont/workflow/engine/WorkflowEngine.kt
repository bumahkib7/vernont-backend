package com.vernont.workflow.engine

import com.vernont.workflow.domain.WorkflowExecution
import com.vernont.workflow.events.WorkflowEventPublisher
import com.vernont.workflow.service.WorkflowExecutionService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.*
import kotlinx.coroutines.TimeoutCancellationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

/**
 * Base interface for all workflows
 */
interface Workflow<I, O> {
    val name: String
    suspend fun execute(input: I, context: WorkflowContext): WorkflowResult<O>
    suspend fun compensate(context: WorkflowContext) {
        // Default: no compensation
    }
}

/**
 * Workflow execution context for passing data between steps
 */
class WorkflowContext {
    // Execution metadata
    var executionId: String? = null
    var workflowName: String? = null
    var correlationId: String? = null

    // Event publisher for step-level events (injected by WorkflowEngine)
    internal var eventPublisher: WorkflowEventPublisher? = null

    private val metadata = ConcurrentHashMap<String, Any>()
    private val executedSteps = mutableListOf<String>()
    private var stepIndex = 0

    fun addMetadata(key: String, value: Any) {
        metadata[key] = value
    }

    fun getMetadata(key: String): Any? = metadata[key]

    /**
     * Record a step execution and publish step events
     */
    fun recordStep(stepName: String) {
        executedSteps.add(stepName)
    }

    /**
     * Record step start with event publishing
     */
    fun recordStepStart(stepName: String, input: Any?, totalSteps: Int = 0) {
        stepIndex = executedSteps.size
        eventPublisher?.publishStepStarted(
            executionId = executionId ?: "",
            workflowName = workflowName ?: "",
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            input = input,
            correlationId = correlationId
        )
    }

    /**
     * Record step completion with event publishing
     */
    fun recordStepComplete(stepName: String, output: Any?, durationMs: Long, totalSteps: Int = 0) {
        executedSteps.add(stepName)
        eventPublisher?.publishStepCompleted(
            executionId = executionId ?: "",
            workflowName = workflowName ?: "",
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            output = output,
            durationMs = durationMs,
            correlationId = correlationId
        )
    }

    /**
     * Record step failure with event publishing
     */
    fun recordStepFailed(stepName: String, error: Throwable, durationMs: Long, totalSteps: Int = 0) {
        eventPublisher?.publishStepFailed(
            executionId = executionId ?: "",
            workflowName = workflowName ?: "",
            stepName = stepName,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            error = error,
            durationMs = durationMs,
            correlationId = correlationId
        )
    }

    fun getExecutedSteps(): List<String> = executedSteps.toList()

    fun getCurrentStepIndex(): Int = stepIndex
}

/**
 * Workflow execution result
 */
sealed class WorkflowResult<out T> {
    data class Success<T>(val data: T) : WorkflowResult<T>()
    data class Failure(val error: Throwable) : WorkflowResult<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error
    }

    companion object {
        fun <T> success(data: T): WorkflowResult<T> = Success(data)
        fun <T> failure(error: Throwable): WorkflowResult<T> = Failure(error)
    }
}

/**
 * Production-ready Workflow Engine - Inspired by Medusa's workflow system
 * 
 * Features:
 * - Persistent execution tracking with PostgreSQL
 * - Distributed locking for business entity concurrency protection
 * - Manual retry mechanisms with failure tracking
 * - Timeout handling with compensation
 * - Comprehensive metrics and monitoring
 * - Type-safe workflow registration and execution
 * - Saga compensation/rollback support for failures and timeouts
 */
@Component
class WorkflowEngine(
    private val workflowExecutionService: WorkflowExecutionService,
    private val redisTemplate: StringRedisTemplate,
    private val meterRegistry: MeterRegistry,
    private val workflowEventPublisher: WorkflowEventPublisher,
    @Value("\${app.workflow.default-timeout-seconds:300000}")
    private val defaultTimeoutSeconds: Long,
    @Value("\${app.workflow.lock-timeout-seconds:60}")
    private val lockTimeoutSeconds: Long,
    @Value("\${app.workflow.max-retries:3}")
    private val defaultMaxRetries: Int
) {

    private val workflows = ConcurrentHashMap<String, WorkflowMetadata<*, *>>()

    /**
     * Register a workflow with type safety
     */
    fun <I : Any, O : Any> registerWorkflow(
        workflow: Workflow<I, O>,
        inputType: KClass<I>,
        outputType: KClass<O>
    ) {
        val metadata = WorkflowMetadata(
            workflow = workflow,
            inputType = inputType,
            outputType = outputType
        )
        workflows[workflow.name] = metadata
        logger.info { 
            "Registered workflow: ${workflow.name} " +
            "with input type: ${inputType.simpleName}, " +
            "output type: ${outputType.simpleName}" 
        }
    }
    

    /**
     * Execute a workflow by name with type safety
     */
    suspend fun <I : Any, O : Any> execute(
        workflowName: String,
        input: I,
        inputType: KClass<I>,
        outputType: KClass<O>,
        context: WorkflowContext = WorkflowContext(),
        options: WorkflowOptions = WorkflowOptions()
    ): WorkflowResult<O> {
        val metadata = workflows[workflowName] 
            ?: throw WorkflowNotFoundException("Workflow not found: $workflowName")
        
        // Type safety validation
        if (metadata.inputType != inputType || metadata.outputType != outputType) {
            throw WorkflowTypeException(
                "Type mismatch for workflow '$workflowName'. " +
                "Expected: ${metadata.inputType.simpleName} -> ${metadata.outputType.simpleName}, " +
                "but got: ${inputType.simpleName} -> ${outputType.simpleName}"
            )
        }

        @Suppress("UNCHECKED_CAST")
        val workflow = metadata.workflow as Workflow<I, O>
        
        return executeWorkflow(workflow, input, context, options)
    }
    

    /**
     * Execute a workflow instance with full production features
     */
    suspend fun <I : Any, O : Any> executeWorkflow(
        workflow: Workflow<I, O>,
        input: I,
        context: WorkflowContext = WorkflowContext(),
        options: WorkflowOptions = WorkflowOptions()
    ): WorkflowResult<O> {
        
        // Create execution record
        val execution = workflowExecutionService.createExecution(
            workflowName = workflow.name,
            inputData = input,
            parentExecutionId = options.parentExecutionId,
            correlationId = options.correlationId,
            maxRetries = options.maxRetries ?: defaultMaxRetries,
            timeoutSeconds = options.timeoutSeconds ?: defaultTimeoutSeconds
        )
        
        val executionId = execution.id
        val effectiveLockKey = options.lockKey ?: "workflow:lock:${workflow.name}:$executionId"
        
        // Enrich context with execution info
        context.executionId = executionId
        context.workflowName = workflow.name
        context.correlationId = options.correlationId
        context.eventPublisher = workflowEventPublisher

        logger.info {
            "Starting workflow: ${workflow.name} (execution: $executionId) " +
            "with lockKey=$effectiveLockKey, correlationId=${options.correlationId}"
        }

        // Metrics
        val timer = Timer.start(meterRegistry)
        val startTimeMs = System.currentTimeMillis()
        meterRegistry.counter("workflow.executions.started", "workflow", workflow.name).increment()

        // Publish workflow started event
        workflowEventPublisher.publishWorkflowStarted(
            executionId = executionId,
            workflowName = workflow.name,
            input = input,
            correlationId = options.correlationId,
            parentExecutionId = options.parentExecutionId
        )

        return try {
            // Distributed locking for concurrent safety
            val acquired = redisTemplate.opsForValue().setIfAbsent(effectiveLockKey, executionId, lockTimeoutSeconds, TimeUnit.SECONDS)

            if (acquired != true) {
                throw WorkflowLockException("Could not acquire lock for workflow execution: $executionId")
            }

            try {
                // Execute with timeout
                val result = withTimeout(Duration.ofSeconds(execution.timeoutSeconds ?: defaultTimeoutSeconds).toMillis()) {
                    workflow.execute(input, context)
                }
                
                // Handle the result properly
                when {
                    result.isSuccess() -> {
                        val durationMs = System.currentTimeMillis() - startTimeMs

                        // Update execution record as completed
                        workflowExecutionService.completeExecution(executionId, result.getOrNull())

                        // Publish workflow completed event
                        workflowEventPublisher.publishWorkflowCompleted(
                            executionId = executionId,
                            workflowName = workflow.name,
                            output = result.getOrNull(),
                            durationMs = durationMs,
                            correlationId = options.correlationId
                        )

                        // Metrics for success
                        timer.stop(Timer.builder("workflow.execution.duration")
                            .tag("workflow", workflow.name)
                            .tag("status", "completed")
                            .register(meterRegistry))
                        meterRegistry.counter("workflow.executions.completed", "workflow", workflow.name).increment()

                        logger.info { "Workflow completed successfully: ${workflow.name} (execution: $executionId) in ${durationMs}ms" }
                        result
                    }
                    result.isFailure() -> {
                        val durationMs = System.currentTimeMillis() - startTimeMs

                        // Handle WorkflowResult.Failure - this is a business logic failure, not an exception
                        val failure = result as WorkflowResult.Failure
                        workflowExecutionService.failExecution(executionId, failure.error)

                        // Publish workflow failed event
                        workflowEventPublisher.publishWorkflowFailed(
                            executionId = executionId,
                            workflowName = workflow.name,
                            error = failure.error,
                            durationMs = durationMs,
                            correlationId = options.correlationId
                        )

                        // Attempt compensation for business failures too
                        try {
                            workflow.compensate(context)
                            workflowExecutionService.compensateExecution(executionId)
                            logger.info { "Compensation completed for failed workflow: ${workflow.name} (execution: $executionId)" }
                        } catch (compensationError: Exception) {
                            logger.error(compensationError) {
                                "Compensation failed for workflow: ${workflow.name} (execution: $executionId)"
                            }
                        }

                        // Metrics for business failure
                        timer.stop(Timer.builder("workflow.execution.duration")
                            .tag("workflow", workflow.name)
                            .tag("status", "failed")
                            .register(meterRegistry))
                        meterRegistry.counter("workflow.executions.failed", "workflow", workflow.name).increment()

                        logger.warn { "Workflow failed with business logic error: ${workflow.name} (execution: $executionId) - ${failure.error.message}" }
                        result
                    }
                    else -> {
                        // This should never happen, but handle it gracefully
                        val unknownError = IllegalStateException("Workflow returned unexpected result state")
                        workflowExecutionService.failExecution(executionId, unknownError)
                        WorkflowResult.failure(unknownError)
                    }
                }
                
            } finally {
                redisTemplate.delete(effectiveLockKey)
            }
            
        } catch (e: TimeoutCancellationException) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            val timeoutError = WorkflowTimeoutException("Workflow execution timed out: $executionId")
            workflowExecutionService.failExecution(executionId, timeoutError)

            // Publish workflow failed event for timeout
            workflowEventPublisher.publishWorkflowFailed(
                executionId = executionId,
                workflowName = workflow.name,
                error = timeoutError,
                durationMs = durationMs,
                correlationId = options.correlationId
            )

            // Attempt compensation on timeout (partial state may need cleanup)
            try {
                workflow.compensate(context)
                workflowExecutionService.compensateExecution(executionId)
                logger.info { "Compensation completed after timeout for workflow: ${workflow.name} (execution: $executionId)" }
            } catch (compensationError: Exception) {
                logger.error(compensationError) {
                    "Compensation failed after timeout for workflow: ${workflow.name} (execution: $executionId)"
                }
            }

            timer.stop(Timer.builder("workflow.execution.duration")
                .tag("workflow", workflow.name)
                .tag("status", "timeout")
                .register(meterRegistry))
            meterRegistry.counter("workflow.executions.timeout", "workflow", workflow.name).increment()

            logger.error(timeoutError) { "Workflow timed out: ${workflow.name} (execution: $executionId)" }
            WorkflowResult.failure(timeoutError)
            
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            logger.error(e) { "Workflow failed: ${workflow.name} (execution: $executionId)" }
            workflowExecutionService.failExecution(executionId, e)

            // Publish workflow failed event
            workflowEventPublisher.publishWorkflowFailed(
                executionId = executionId,
                workflowName = workflow.name,
                error = e,
                durationMs = durationMs,
                correlationId = options.correlationId
            )

            // Attempt compensation
            try {
                workflow.compensate(context)
                workflowExecutionService.compensateExecution(executionId)
                logger.info { "Compensation completed for workflow: ${workflow.name} (execution: $executionId)" }
            } catch (compensationError: Exception) {
                logger.error(compensationError) {
                    "Compensation failed for workflow: ${workflow.name} (execution: $executionId)"
                }
            }

            // Metrics
            timer.stop(Timer.builder("workflow.execution.duration")
                .tag("workflow", workflow.name)
                .tag("status", "failed")
                .register(meterRegistry))
            meterRegistry.counter("workflow.executions.failed", "workflow", workflow.name).increment()

            WorkflowResult.failure(e)
        }
    }

    /**
     * Retry a failed workflow execution
     */
    suspend fun <I : Any, O : Any> retryExecution(
        executionId: String,
        inputType: KClass<I>,
        outputType: KClass<O>
    ): WorkflowResult<O> {
        val execution = workflowExecutionService.getExecution(executionId)
        
        if (!execution.canRetry()) {
            throw IllegalStateException("Execution $executionId cannot be retried")
        }
        
        val metadata = workflows[execution.workflowName]
            ?: throw WorkflowNotFoundException("Workflow not found: ${execution.workflowName}")
        
        @Suppress("UNCHECKED_CAST")
        val workflow = metadata.workflow as Workflow<I, O>
        
        // Deserialize input
        val input = workflowExecutionService.deserializeData(execution.inputData, inputType.java)
            ?: throw IllegalArgumentException("Could not deserialize input for execution: $executionId")
        
        // Update retry count
        workflowExecutionService.retryExecution(executionId)

        return executeWorkflow(workflow, input, WorkflowContext())
    }

    /**
     * Retry a failed workflow execution (type-erased version for admin API)
     * Returns the new execution ID
     */
    fun retryExecution(executionId: String): String {
        val execution = workflowExecutionService.getExecution(executionId)

        if (!execution.canRetry()) {
            throw IllegalStateException("Execution $executionId cannot be retried")
        }

        val metadata = workflows[execution.workflowName]
            ?: throw WorkflowNotFoundException("Workflow not found: ${execution.workflowName}")

        // Run the retry in a new coroutine scope
        val newExecutionId = runBlocking {
            retryExecutionInternal(execution, metadata)
        }

        return newExecutionId
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <I : Any, O : Any> retryExecutionInternal(
        execution: WorkflowExecution,
        metadata: WorkflowMetadata<I, O>
    ): String {
        val workflow = metadata.workflow
        val inputType = metadata.inputType

        // Deserialize input
        val input = workflowExecutionService.deserializeData(execution.inputData, inputType.java)
            ?: throw IllegalArgumentException("Could not deserialize input for execution: ${execution.id}")

        // Update retry count on original execution
        workflowExecutionService.retryExecution(execution.id)

        // Execute as a new workflow instance with new execution ID
        val context = WorkflowContext().apply {
            correlationId = execution.correlationId
        }
        executeWorkflow(workflow, input, context)

        // Return the new execution ID from the context
        return context.executionId ?: execution.id
    }

    /**
     * Pause a running workflow execution
     */
    fun pauseExecution(executionId: String) {
        workflowExecutionService.pauseExecution(executionId)
        logger.info { "Paused workflow execution: $executionId" }
    }
    
    /**
     * Resume a paused workflow execution
     */
    suspend fun <I : Any, O : Any> resumeExecution(
        executionId: String,
        inputType: KClass<I>,
        outputType: KClass<O>
    ): WorkflowResult<O> {
        val execution = workflowExecutionService.resumeExecution(executionId)
        
        val metadata = workflows[execution.workflowName]
            ?: throw WorkflowNotFoundException("Workflow not found: ${execution.workflowName}")
        
        @Suppress("UNCHECKED_CAST")
        val workflow = metadata.workflow as Workflow<I, O>
        
        val input = workflowExecutionService.deserializeData(execution.inputData, inputType.java)
            ?: throw IllegalArgumentException("Could not deserialize input for execution: $executionId")
        
        return executeWorkflow(workflow, input, WorkflowContext())
    }
    
    /**
     * Cancel a workflow execution
     */
    fun cancelExecution(executionId: String) {
        workflowExecutionService.cancelExecution(executionId)
        logger.info { "Cancelled workflow execution: $executionId" }
    }
    
    /**
     * Get workflow execution by ID
     */
    fun getExecution(executionId: String): WorkflowExecution {
        return workflowExecutionService.getExecution(executionId)
    }

    /**
     * Get all executions for a workflow
     */
    fun getWorkflowExecutions(workflowName: String, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<WorkflowExecution> {
        return workflowExecutionService.findExecutionsByWorkflow(workflowName, pageable)
    }
    
    /**
     * Get workflow statistics
     */
    fun getWorkflowStatistics(workflowName: String, since: java.time.Instant): List<com.vernont.workflow.repository.WorkflowExecutionStats> {
        return workflowExecutionService.getWorkflowStatistics(workflowName, since)
    }
    
    /**
     * List all registered workflows
     */
    fun listWorkflows(): List<WorkflowInfo> {
        return workflows.values.map { metadata ->
            WorkflowInfo(
                name = metadata.workflow.name,
                inputType = metadata.inputType.simpleName ?: "Unknown",
                outputType = metadata.outputType.simpleName ?: "Unknown"
            )
        }
    }
    
    /**
     * Health check method
     */
    fun isHealthy(): Boolean {
        return try {
            // Check Redis connectivity
            redisTemplate.execute { connection -> connection.ping() }
            true
        } catch (e: Exception) {
            logger.error(e) { "Workflow engine health check failed" }
            false
        }
    }
}

/**
 * Workflow metadata for type-safe registration
 */
internal data class WorkflowMetadata<I : Any, O : Any>(
    val workflow: Workflow<I, O>,
    val inputType: KClass<I>,
    val outputType: KClass<O>
)

/**
 * Workflow execution options
 */
data class WorkflowOptions(
    val parentExecutionId: String? = null,
    val correlationId: String? = null,
    val maxRetries: Int? = null,
    val timeoutSeconds: Long? = null,
    val lockKey: String? = null  // Business entity lock key for concurrency protection
)

/**
 * Workflow information for listing
 */
data class WorkflowInfo(
    val name: String,
    val inputType: String,
    val outputType: String
)

// Exceptions
class WorkflowNotFoundException(message: String) : Exception(message)
class WorkflowTypeException(message: String) : Exception(message) 
class WorkflowLockException(message: String) : Exception(message)
class WorkflowTimeoutException(message: String) : Exception(message)
