package com.vernont.application.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "stripe")
data class StripeProperties(
    val secretKey: String = "",
    val publishableKey: String = "",
    val webhookSecret: String = "",
    val enabled: Boolean = true
) {
    fun isConfigured(): Boolean = secretKey.isNotBlank() && publishableKey.isNotBlank()
}
