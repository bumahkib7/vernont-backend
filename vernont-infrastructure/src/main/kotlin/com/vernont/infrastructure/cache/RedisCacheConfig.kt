package com.vernont.infrastructure.cache

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis cache configuration for NexusCommerce.
 * Configures Spring Cache with Redis backend, including TTL settings and JSON serialization.
 */
@Configuration
@EnableCaching
class RedisCacheConfig(
    @Value("\${cache.default-ttl:3600}")
    private val defaultTtlSeconds: Long,
    @Value("\${cache.session-ttl:1800}")
    private val sessionTtlSeconds: Long,
    @Value("\${cache.product-ttl:7200}")
    private val productTtlSeconds: Long,
    @Value("\${cache.prefix:affiliate}")
    private val cachePrefix: String,
    private val objectMapper: ObjectMapper
) {

    /**
     * Configure the Redis cache manager with appropriate TTL settings per cache.
     *
     * Cache TTLs:
     * - default: 1 hour (3600 seconds)
     * - session: 30 minutes (1800 seconds)
     * - product: 2 hours (7200 seconds)
     *
     * @param connectionFactory The Redis connection factory
     * @return Configured RedisCacheManager
     */
    @Bean
    fun cacheManager(
        connectionFactory: RedisConnectionFactory
    ): CacheManager {
        val typedMapper = objectMapper.copy().apply {
            activateDefaultTyping(
                polymorphicTypeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
            )
        }
        val plainMapper = objectMapper.copy()
        val serializer = FlexibleRedisSerializer(typedMapper, plainMapper)

        // Default cache configuration
        val defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
            .disableCachingNullValues()

        // Session cache configuration (shorter TTL)
        val sessionCacheConfig = defaultCacheConfig
            .entryTtl(Duration.ofSeconds(sessionTtlSeconds))

        // Product cache configuration (longer TTL)
        val productCacheConfig = defaultCacheConfig
            .entryTtl(Duration.ofSeconds(productTtlSeconds))

        // Domain-specific cache names (configurable prefix so we can rotate via config)
        val catalogCacheName = "$cachePrefix:catalog"
        val productDetailCacheName = "$cachePrefix:productDetail"
        val brandsCacheName = "$cachePrefix:brands"
        val categoriesCacheName = "$cachePrefix:categories"

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withCacheConfiguration("sessions", sessionCacheConfig)
            .withCacheConfiguration("products", productCacheConfig)
            .withCacheConfiguration("users", defaultCacheConfig)
            .withCacheConfiguration("orders", defaultCacheConfig)
            .withCacheConfiguration("carts", sessionCacheConfig)
            .withCacheConfiguration("inventory", defaultCacheConfig)
            .withCacheConfiguration("pricing", productCacheConfig)
            // Affiliate caches
            .withCacheConfiguration(catalogCacheName, productCacheConfig)
            .withCacheConfiguration(productDetailCacheName, productCacheConfig)
            .withCacheConfiguration(brandsCacheName, defaultCacheConfig)
            .withCacheConfiguration(categoriesCacheName, defaultCacheConfig)
            .build()
    }

    /**
     * Swallow cache get/put errors (e.g., stale serialized shapes) by evicting the key
     * and letting method execution continue to repopulate with the new shape.
     */
    @Bean
    fun cacheErrorHandler(redisTemplate: org.springframework.data.redis.core.StringRedisTemplate): CacheErrorHandler {
        return object : CacheErrorHandler {
            override fun handleCacheGetError(exception: RuntimeException, cache: org.springframework.cache.Cache, key: Any) {
                cache.evict(key)
            }

            override fun handleCachePutError(exception: RuntimeException, cache: org.springframework.cache.Cache, key: Any, value: Any?) {
                cache.evict(key)
            }

            override fun handleCacheEvictError(exception: RuntimeException, cache: org.springframework.cache.Cache, key: Any) {
                // no-op
            }

            override fun handleCacheClearError(exception: RuntimeException, cache: org.springframework.cache.Cache) {
                // no-op
            }
        }
    }

    /**
     * Redis connection pooling properties.
     * Configure connection pool settings for optimal performance.
     */
    companion object {
        /**
         * Recommended Redis connection pool settings
         */
        const val MAX_POOL_SIZE = 30
        const val MIN_IDLE = 10
        const val MAX_WAIT_MILLIS = 1800000L
    }
}
