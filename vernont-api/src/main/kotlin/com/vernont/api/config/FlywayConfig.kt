package com.vernont.api.config

import org.flywaydb.core.Flyway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import javax.sql.DataSource

@Configuration
class FlywayConfig {

    @Bean
    @DependsOn("entityManagerFactory") // Ensure JPA EntityManagerFactory is initialized first
    fun flyway(dataSource: DataSource): Flyway {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration") // Your migration scripts location
            .baselineOnMigrate(true) // Should already be in application.yml, but good to ensure
            .validateOnMigrate(true) // Should already be in application.yml, but good to ensure
            .load()
        flyway.migrate() // Manually trigger migration
        return flyway
    }
}
