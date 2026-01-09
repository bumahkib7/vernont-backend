package com.vernont.workflow.config

import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowCustomizer
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowStepProvider
import com.vernont.workflow.engine.WorkflowTypes
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.aop.support.AopUtils
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

private val logger = KotlinLogging.logger {}

@Configuration
class WorkflowRegistrationConfig(
    private val workflowEngine: WorkflowEngine,
    private val applicationContext: ApplicationContext
) {

    @PostConstruct
    fun registerAllWorkflows() {
        // Log customizers and step providers
        val customizers = applicationContext.getBeansOfType(WorkflowCustomizer::class.java).values
        val stepProviders = applicationContext.getBeansOfType(WorkflowStepProvider::class.java).values

        if (customizers.isNotEmpty()) {
            logger.info { "Found ${customizers.size} workflow customizer(s):" }
            customizers.groupBy { it.getWorkflowName() }.forEach { (workflowName, custList) ->
                logger.info { "  - $workflowName: ${custList.size} customizer(s)" }
            }
        }

        if (stepProviders.isNotEmpty()) {
            logger.info { "Found ${stepProviders.size} workflow step provider(s):" }
            stepProviders.filter { it.isEnabled() }.groupBy { it.getWorkflowName() }.forEach { (workflowName, providerList) ->
                logger.info { "  - $workflowName: ${providerList.size} step provider(s)" }
            }
        }

        // Register workflows
        val workflowBeans = applicationContext.getBeansOfType(Workflow::class.java).values

        workflowBeans.forEach { workflow ->
            // Get the real class behind any Spring proxy (…$$SpringCGLIB$$0)
            val targetClass = AopUtils.getTargetClass(workflow)
            val kClass = targetClass.kotlin

            val annotation = kClass.findAnnotation<WorkflowTypes>()
            if (annotation == null) {
                logger.warn {
                    "Skipping workflow ${kClass.simpleName} (bean class: ${targetClass.name}) " +
                            "because it has no @WorkflowTypes annotation"
                }
                return@forEach
            }

            @Suppress("UNCHECKED_CAST")
            workflowEngine.registerWorkflow(
                workflow = workflow as Workflow<Any, Any>,
                inputType = annotation.input as KClass<Any>,
                outputType = annotation.output as KClass<Any>
            )

            val workflowCustomizers = customizers.count { it.getWorkflowName() == workflow.name }
            val workflowStepProviders = stepProviders.count { it.getWorkflowName() == workflow.name && it.isEnabled() }

            logger.info {
                "Registered workflow '${workflow.name}' " +
                        "with input=${annotation.input.simpleName}, output=${annotation.output.simpleName}" +
                        if (workflowCustomizers > 0 || workflowStepProviders > 0)
                            " [customizers=$workflowCustomizers, stepProviders=$workflowStepProviders]"
                        else ""
            }
        }

        logger.info { "Total workflows registered: ${workflowEngine.listWorkflows().size}" }
    }
}
