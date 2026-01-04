package com.vernont.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun productMatchingApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("product-matching")
            .pathsToMatch(
                "/api/v1/internal/auth/**",
                "/api/admin/product-matching/**",
                "/api/admin/product-attributes/**",
                "/api/admin/affiliate-products/**",
                "/api/admin/affiliate-collections/**",
                "/api/admin/affiliate-categories/**",
                "/api/admin/affiliate-brands/**",
                "/api/admin/internal-users/**",
                "/api/admin/dev/seed/**"
            )
            .build()
    }

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Vernont API")
                    .version("1.0")
                    .description("API documentation for Vernont Backend")
            )
            .servers(
                listOf(
                    Server().url("http://localhost:8080").description("Local Development Server")
                )
            )
            .addSecurityItem(SecurityRequirement().addList("Bearer Authentication"))
            .components(
                Components()
                    .addSecuritySchemes(
                        "Bearer Authentication",
                        SecurityScheme()
                            .name("Bearer Authentication")
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
    }
}
