package com.vernont.infrastructure.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Component

/**
 * Lightweight cleanup to remove legacy cache keys (e.g. catalogV3/productDetailV3)
 * so type changes don't blow up deserialization. Enabled by default and runs once at startup.
 */
@Component
class CacheCleanupRunner(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${cache.cleanup.enabled:true}") private val enabled: Boolean,
    @Value("\${cache.cleanup.legacy-patterns:catalogV*,productDetailV*,brandsV*,categoriesV*}")
    private val legacyPatterns: List<String>,
    @Value("\${cache.prefix:affiliate}") private val cachePrefix: String,
    @Value("\${cache.cleanup.purge-current:true}") private val purgeCurrent: Boolean
) : ApplicationRunner {

    private val log = KotlinLogging.logger {}

    override fun run(args: ApplicationArguments) {
        if (!enabled) return
        val connection = redisTemplate.connectionFactory?.connection ?: return

        var deleted = 0L
        val patterns = legacyPatterns.toMutableList()
        if (purgeCurrent) {
            patterns += listOf(
                "$cachePrefix:catalog*",
                "$cachePrefix:productDetail*",
                "$cachePrefix:brands*",
                "$cachePrefix:categories*"
            )
        }

        patterns.forEach { pattern ->
            val options = ScanOptions.scanOptions().match(pattern).count(500).build()
            connection.scan(options).use { cursor ->
                cursor.forEachRemaining { key ->
                    val keyStr = String(key, Charsets.UTF_8)
                    redisTemplate.delete(keyStr)
                    deleted++
                }
            }
        }
        if (deleted > 0) {
            log.info { "CacheCleanupRunner removed $deleted legacy cache keys matching $legacyPatterns" }
        }
    }
}
