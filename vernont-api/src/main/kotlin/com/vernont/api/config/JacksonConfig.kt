package com.vernont.api.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

private val logger = KotlinLogging.logger {}

/**
 * Jackson ObjectMapper configuration with Kotlin module for proper
 * deserialization of Kotlin data classes.
 */
@Configuration
class JacksonConfig : WebMvcConfigurer {

    @PostConstruct
    fun init() {
        logger.info { "JacksonConfig initialized - Kotlin module will be registered" }
    }

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        logger.info { "Creating @Primary ObjectMapper with KotlinModule" }
        return ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .also { logger.info { "ObjectMapper created with modules: ${it.registeredModuleIds}" } }
    }

    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        logger.info { "Configuring HttpMessageConverters to use Kotlin-aware ObjectMapper" }
        val mapper = objectMapper()
        // Replace any existing Jackson converter with our Kotlin-aware one
        converters.removeIf { it is MappingJackson2HttpMessageConverter }
        converters.add(0, MappingJackson2HttpMessageConverter(mapper))
        logger.info { "MappingJackson2HttpMessageConverter configured with KotlinModule" }
    }
}
