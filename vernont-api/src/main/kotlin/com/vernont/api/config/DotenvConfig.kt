package com.vernont.api.config

import io.github.cdimascio.dotenv.Dotenv
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class DotenvConfig : EnvironmentPostProcessor {

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication
    ) {
        try {
            val dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .ignoreIfMalformed()
                .load()

            val dotenvProperties = mutableMapOf<String, Any>()
            dotenv.entries().forEach { entry ->
                dotenvProperties[entry.key] = entry.value
            }

            if (dotenvProperties.isNotEmpty()) {
                environment.propertySources.addFirst(
                    MapPropertySource("dotenv", dotenvProperties)
                )
            }
        } catch (e: Exception) {
            // Ignore if .env file is not found
        }
    }
}
