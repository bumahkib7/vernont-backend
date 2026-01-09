package com.vernont.api.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Explicit Flyway configuration to ensure migrations run before Hibernate.
 *
 * Flyway manages all database schema migrations via versioned SQL files
 * in src/main/resources/db/migration/
 */
@Configuration
class FlywayConfig {

    @Value("\${spring.flyway.locations:classpath:db/migration}")
    private lateinit var locations: String

    @Value("\${spring.flyway.baseline-on-migrate:true}")
    private var baselineOnMigrate: Boolean = true

    @Value("\${spring.flyway.validate-on-migrate:true}")
    private var validateOnMigrate: Boolean = true

    @Value("\${spring.flyway.out-of-order:false}")
    private var outOfOrder: Boolean = false

    @Value("\${spring.flyway.repair-on-migrate:false}")
    private var repairOnMigrate: Boolean = false

    @Bean
    fun flyway(dataSource: DataSource): Flyway {
        logger.info { "Configuring Flyway with locations: $locations" }

        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(locations)
            .baselineOnMigrate(baselineOnMigrate)
            .validateOnMigrate(validateOnMigrate)
            .outOfOrder(outOfOrder)
            .load()

        if (repairOnMigrate) {
            logger.warn { "Running Flyway repair before migration (FLYWAY_REPAIR_ON_MIGRATE=true)" }
            flyway.repair()
        }

        logger.info { "Running Flyway migrations" }
        flyway.migrate()

        logger.info { "Flyway migrations completed" }
        return flyway
    }
}
