package com.vernont.workflow.config

import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowEngine
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

            logger.info {
                "Registered workflow '${workflow.name}' " +
                        "with input=${annotation.input.simpleName}, output=${annotation.output.simpleName}"
            }
        }

        logger.info { "Total workflows registered: ${workflowEngine.listWorkflows().size}" }
    }
}
