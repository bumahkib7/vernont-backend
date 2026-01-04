package com.vernont.api.rate

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimited(
    val keyPrefix: String,
    val perIp: Boolean = true,
    val perEmail: Boolean = false,
    val limit: Int = 5,
    val windowSeconds: Long = 600,
    val failClosed: Boolean = false  // Whether to block requests when Redis is down (fail-closed) or allow them (fail-open)
)
