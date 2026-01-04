package com.vernont.infrastructure.cache

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * Manual, RedisTemplate-backed caching with explicit key/TTL control.
 * - `cacheName`: SpEL expression or literal cache bucket name.
 * - `key`: SpEL expression that must resolve to a stable key string.
 * - `ttlSeconds`: Optional TTL override; use `ttlProperty` to read from config instead.
 * - `ttlProperty`: Optional config property name (e.g. "cache.product-ttl") to drive TTL.
 * - `prependPrefix`: When true, prefix `cache.prefix` is added to `cacheName`.
 * - `cacheNulls`: When false, null results are not cached.
 * - `cacheEmpty`: When false, empty results (lists/maps/blank strings) are not cached.
 */
@Target(FUNCTION)
@Retention(RUNTIME)
annotation class ManagedCache(
    val cacheName: String,
    val key: String,
    val ttlSeconds: Long = -1,
    val ttlProperty: String = "",
    val prependPrefix: Boolean = true,
    val cacheNulls: Boolean = false,
    val cacheEmpty: Boolean = false
)
