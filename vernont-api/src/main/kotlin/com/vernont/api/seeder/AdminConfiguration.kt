package com.vernont.api.seeder

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

// Bootstrap properties (app.admin.bootstrap)
data class AdminBootstrapProperties(
    var enabled: Boolean = true,
    var email: String? = null,
    var password: String? = null
)

// Defaults (app.admin.defaults)
data class AdminDefaultsProperties(
    val roles: List<String> = listOf("ADMIN")
)

/**
 * Maps the 'app.admin' section of the application configuration.
 */
@Component
@ConfigurationProperties(prefix = "app.admin")
data class AdminConfiguration(
    val bootstrap: AdminBootstrapProperties = AdminBootstrapProperties(),
    val defaults: AdminDefaultsProperties = AdminDefaultsProperties()
)