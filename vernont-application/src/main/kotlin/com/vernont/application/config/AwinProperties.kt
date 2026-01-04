package com.vernont.application.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "awin")
data class AwinProperties(
    val baseUrl: String = "https://api.awin.com",
    val publisherId: String = "",
    val apiToken: String = "",
    val defaultLocale: String = "en_GB",
    val advertisers: List<Long> = emptyList()
)
