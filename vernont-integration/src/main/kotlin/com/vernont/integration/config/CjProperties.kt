package com.vernont.integration.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cj")
data class CjProperties(
    val baseUrl: String = "https://ads.api.cj.com/query",
    val apiKey: String = "",
    val defaultCompanyId: String = "",
    val defaultPid: String = "",
    val defaultServiceableArea: String = "GB",
    val defaultCurrency: String = "GBP",
    val rateLimit: RateLimitConfig = RateLimitConfig(),
    val sync: SyncConfig = SyncConfig()
) {
    data class RateLimitConfig(
        val requestsPerSecond: Int = 3,
        val timeout: Duration = Duration.ofSeconds(20)
    )

    data class SyncConfig(
        val enableScheduling: Boolean = false,
        val cron: String = "0 0 3 * * ?",
        val advertisers: List<Long> = emptyList(),
        val keywords: List<String> = emptyList(),
        val pageSize: Int = 100,
        val maxPages: Int = 100
    )
}
