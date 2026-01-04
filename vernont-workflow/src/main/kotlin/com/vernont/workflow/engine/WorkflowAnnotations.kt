package com.vernont.workflow.engine

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class WorkflowTypes(
    val input: KClass<*>,
    val output: KClass<*>
)
