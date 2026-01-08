package com.vernont.workflow.shipping

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Registry for shipping label providers.
 *
 * Selects the appropriate provider based on provider ID or configuration.
 */
@Component
class ShippingProviderRegistry(
    private val providers: List<ShippingLabelProvider>
) {

    private val providerMap: Map<String, ShippingLabelProvider> by lazy {
        providers.associateBy { it.name.lowercase() }
    }

    /**
     * Get provider by name/ID
     */
    fun getProvider(providerId: String): ShippingLabelProvider? {
        val normalized = normalizeProviderId(providerId)
        return providerMap[normalized]
    }

    /**
     * Get provider by name/ID, or throw if not found
     */
    fun getProviderOrThrow(providerId: String): ShippingLabelProvider {
        return getProvider(providerId)
            ?: throw IllegalArgumentException("Unknown shipping provider: $providerId")
    }

    /**
     * Get available providers
     */
    fun getAvailableProviders(): List<ShippingLabelProvider> {
        return providers.filter { it.isAvailable() }
    }

    /**
     * Check if a provider is available
     */
    fun isProviderAvailable(providerId: String): Boolean {
        return getProvider(providerId)?.isAvailable() == true
    }

    /**
     * Get the default provider (ShipEngine)
     */
    fun getDefaultProvider(): ShippingLabelProvider {
        return getProvider("shipengine") ?: getProvider("manual")!!
    }

    /**
     * Normalize provider ID to match our provider names
     */
    private fun normalizeProviderId(providerId: String): String {
        val lower = providerId.lowercase()
        return when {
            lower.contains("shipengine") -> "shipengine"
            lower.contains("manual") -> "manual"
            // Map legacy provider IDs to shipengine
            lower.contains("shippo") -> "shipengine"
            lower.contains("shipstation") -> "shipengine"
            lower.contains("easypost") -> "shipengine"
            else -> "shipengine" // Default to shipengine
        }
    }

    init {
        logger.info { "Registered ${providers.size} shipping providers: ${providers.map { it.name }}" }
        logger.info { "Available providers: ${getAvailableProviders().map { it.name }}" }
    }
}
