package com.vernont.infrastructure.cache

import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

/**
 * Service for managing cache operations, including clearing problematic cache entries
 * after serialization format changes.
 */
@Service
class CacheManagementService(
    private val cacheManager: CacheManager
) {

    /**
     * Clear all product-related caches to resolve serialization issues.
     * Should be called after Redis serializer updates.
     */
    fun clearProductCaches() {
        val cachesToClear = listOf("productDetail", "products", "brands", "categories")
        
        cachesToClear.forEach { cacheName ->
            cacheManager.getCache(cacheName)?.clear()
        }
    }

    /**
     * Clear a specific cache by name.
     */
    fun clearCache(cacheName: String) {
        cacheManager.getCache(cacheName)?.clear()
    }

    /**
     * Clear all caches.
     */
    fun clearAllCaches() {
        cacheManager.cacheNames.forEach { cacheName ->
            cacheManager.getCache(cacheName)?.clear()
        }
    }

    /**
     * Get cache statistics (if available).
     */
    fun getCacheNames(): Collection<String> = cacheManager.cacheNames
}