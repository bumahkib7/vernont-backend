package com.vernont.workflow.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Configuration for the workflow engine
 */
@Configuration
@EnableScheduling
@EnableAsync
class WorkflowConfiguration
