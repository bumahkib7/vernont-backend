package com.vernont.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    var secret: String = "69df2472bc8e9bf6d554dfc62bcf12dec4d0168a4abba489e7adf8deccfe7f22b3710eea5d92001fe5adb4337db15face3afd65dba4509f5237297102d382c3a",
    var expirationMs: Long = 86400000, // 24 hours
    var refreshExpirationMs: Long = 604800000 // 7 days
)