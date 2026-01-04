package com.vernont.application.cache

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Central place to derive cache names from a configurable prefix,
 * so we can rotate prefixes via configuration without code changes.
 */
@Component("cacheNameProvider")
class CacheNameProvider(
    @Value("\${cache.prefix:affiliate}") private val prefix: String
) {
    val catalog: String get() = "$prefix:catalog"
    val productDetail: String get() = "$prefix:productDetail"
    val brands: String get() = "$prefix:brands"
    val categories: String get() = "$prefix:categories"
    val sizes: String get() = "$prefix:sizes"
    val heroImages: String get() = "$prefix:heroImages"
    val newArrivals: String get() = "$prefix:newArrivals"
    val searchSuggestions: String get() = "$prefix:searchSuggestions"
    val networks: String get() = "$prefix:networks"
    val collections: String get() = "$prefix:collections"
    val similarProducts: String get() = "$prefix:similarProducts"
    val brandDetail: String get() = "$prefix:brandDetail"
    val brandProducts: String get() = "$prefix:brandProducts"
    val merchantDetail: String get() = "$prefix:merchantDetail"
    val merchantProducts: String get() = "$prefix:merchantProducts"
    val categoryDetail: String get() = "$prefix:categoryDetail"
    val categoryProducts: String get() = "$prefix:categoryProducts"
    val productById: String get() = "$prefix:productById"
}
