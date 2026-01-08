package com.vernont.application.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "shipengine")
data class ShipEngineProperties(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val sandboxApiKey: String = "",
    val useSandbox: Boolean = true,
    val defaultCarrierId: String = "",
    val defaultServiceCode: String = "usps_priority_mail",
    val labelFormat: String = "pdf",
    val fromAddress: FromAddress = FromAddress()
) {
    data class FromAddress(
        val name: String = "",
        val company: String = "",
        val street1: String = "",
        val street2: String = "",
        val city: String = "",
        val stateProvince: String = "",
        val postalCode: String = "",
        val countryCode: String = "GB",
        val phone: String = ""
    )

    fun getActiveApiKey(): String = if (useSandbox) sandboxApiKey else apiKey

    fun isConfigured(): Boolean = enabled && getActiveApiKey().isNotBlank()
}
