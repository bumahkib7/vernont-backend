package com.vernont

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.web.config.EnableSpringDataWebSupport
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * NexusCommerce - Modern E-Commerce Backend
 *
 * A Kotlin + Spring Boot 4.x replacement for Medusa.js with:
 * - Multi-module architecture
 * - Workflow engine with compensation
 * - Event-driven design
 * - Named Entity Graphs for N+1 prevention
 * - Redis caching and sessions
 * - SendGrid email integration
 * - S3 storage
 * - Flyway migrations
 */
@SpringBootApplication(
        scanBasePackages =
                [
                        "com.vernont.api",
                        "com.vernont.application",
                        "com.vernont.domain",
                        "com.vernont.workflow",
                        "com.vernont.events",
                        "com.vernont.infrastructure",
                        "com.vernont.integration"]
)
@EnableConfigurationProperties(com.vernont.api.auth.CookieProperties::class)
@EnableJpaAuditing
@EnableJpaRepositories(
        basePackages = [
                "com.vernont.repository",
                "com.vernont.workflow.repository"
        ]
)
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableSpringDataWebSupport(
        pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO
)
@org.springframework.context.annotation.Import(
        com.vernont.infrastructure.cache.ManagedCacheAspect::class,
        com.vernont.infrastructure.cache.RedisCacheConfig::class
)
class VernontApplication

fun main(args: Array<String>) {
    runApplication<VernontApplication>(*args)
}
