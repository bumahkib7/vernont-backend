package com.vernont.application.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "easypost")
data class EasyPostProperties(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val testApiKey: String = "",
    val useTestMode: Boolean = true,
    val defaultCarrier: String = "USPS",
    val defaultService: String = "Priority",
    val fromAddress: FromAddress = FromAddress()
) {
    data class FromAddress(
        val name: String = "",
        val company: String = "",
        val street1: String = "",
        val street2: String = "",
        val city: String = "",
        val state: String = "",
        val zip: String = "",
        val country: String = "GB",
        val phone: String = ""
    )

    fun getActiveApiKey(): String = if (useTestMode) testApiKey else apiKey

    fun isConfigured(): Boolean = enabled && getActiveApiKey().isNotBlank()
}
