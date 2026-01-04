package com.vernont.api.config

import com.vernont.application.config.AwinProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(AwinProperties::class)
class AwinConfig {

    @Bean
    fun awinWebClient(props: AwinProperties): WebClient {
        return WebClient.builder()
            .baseUrl(props.baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${props.apiToken}")
            .build()
    }

    @Bean
    fun dummyJsonWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://dummyjson.com")
            .build()
    }
}
