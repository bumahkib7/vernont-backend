package com.vernont.application.store

import com.vernont.domain.store.*
import com.vernont.infrastructure.cache.ManagedCache
import com.vernont.repository.store.StoreRepository
import com.vernont.repository.store.StoreSettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class StoreSettingsService(
    private val storeSettingsRepository: StoreSettingsRepository,
    private val storeRepository: StoreRepository
) {

    /**
     * Get settings for a store (cached with 60s TTL)
     */
    @Transactional(readOnly = true)
    @ManagedCache(
        cacheName = "'store-settings'",
        key = "'storeId'",
        ttlSeconds = 60
    )
    fun getSettings(storeId: String): StoreSettings {
        logger.debug { "Fetching settings for store: $storeId" }
        return storeSettingsRepository.findByStoreIdAndDeletedAtIsNull(storeId)
            ?: throw StoreSettingsNotFoundException("Settings not found for store: $storeId")
    }

    /**
     * Get settings or create defaults if none exist
     */
    fun getOrCreateSettings(storeId: String): StoreSettings {
        return storeSettingsRepository.findByStoreIdAndDeletedAtIsNull(storeId)
            ?: initializeSettings(storeId)
    }

    /**
     * Initialize settings for a store
     */
    fun initializeSettings(storeId: String): StoreSettings {
        logger.info { "Initializing settings for store: $storeId" }

        if (storeSettingsRepository.existsByStoreIdAndDeletedAtIsNull(storeId)) {
            throw IllegalStateException("Settings already exist for store: $storeId")
        }

        val store = storeRepository.findByIdAndDeletedAtIsNull(storeId)
            ?: throw IllegalArgumentException("Store not found: $storeId")

        val settings = StoreSettings.createDefault(store)
        return storeSettingsRepository.save(settings)
    }

    /**
     * Update business information
     */
    
    fun updateBusinessInfo(storeId: String, request: UpdateBusinessInfoRequest): StoreSettings {
        logger.info { "Updating business info for store: $storeId" }

        val settings = getSettings(storeId)

        request.description?.let { settings.description = it }
        request.logoUrl?.let { settings.logoUrl = it }
        request.faviconUrl?.let { settings.faviconUrl = it }
        request.contactEmail?.let { settings.contactEmail = it }
        request.contactPhone?.let { settings.contactPhone = it }
        request.legalBusinessName?.let { settings.legalBusinessName = it }
        request.taxId?.let { settings.taxId = it }
        request.socialLinks?.let { settings.socialLinks = it }

        return storeSettingsRepository.save(settings)
    }

    /**
     * Update localization settings
     */
    
    fun updateLocalization(storeId: String, request: UpdateLocalizationRequest): StoreSettings {
        logger.info { "Updating localization for store: $storeId" }

        val settings = getSettings(storeId)

        request.timezone?.let { settings.timezone = it }
        request.defaultLocale?.let { settings.defaultLocale = it }
        request.dateFormat?.let { settings.dateFormat = it }
        request.currencyDisplayFormat?.let { settings.currencyDisplayFormat = it }

        return storeSettingsRepository.save(settings)
    }

    /**
     * Update feature toggles
     */
    
    fun updateFeatures(storeId: String, request: UpdateFeaturesRequest): StoreSettings {
        logger.info { "Updating features for store: $storeId" }

        val settings = getSettings(storeId)

        request.reviewsEnabled?.let { settings.reviewsEnabled = it }
        request.wishlistEnabled?.let { settings.wishlistEnabled = it }
        request.giftCardsEnabled?.let { settings.giftCardsEnabled = it }
        request.customerTiersEnabled?.let { settings.customerTiersEnabled = it }
        request.guestCheckoutEnabled?.let { settings.guestCheckoutEnabled = it }
        request.newsletterEnabled?.let { settings.newsletterEnabled = it }
        request.productComparisonEnabled?.let { settings.productComparisonEnabled = it }

        return storeSettingsRepository.save(settings)
    }

    /**
     * Update policies
     */
    
    fun updatePolicies(storeId: String, policies: StorePolicies): StoreSettings {
        logger.info { "Updating policies for store: $storeId" }

        val settings = getSettings(storeId)
        settings.policies = policies
        return storeSettingsRepository.save(settings)
    }

    /**
     * Update checkout settings
     */
    
    fun updateCheckoutSettings(storeId: String, checkoutSettings: CheckoutSettings): StoreSettings {
        logger.info { "Updating checkout settings for store: $storeId" }

        val settings = getSettings(storeId)
        settings.checkoutSettings = checkoutSettings
        return storeSettingsRepository.save(settings)
    }

    /**
     * Update shipping settings
     */
    
    fun updateShippingSettings(storeId: String, shippingSettings: ShippingSettings): StoreSettings {
        logger.info { "Updating shipping settings for store: $storeId" }

        val settings = getSettings(storeId)
        settings.shippingSettings = shippingSettings
        return storeSettingsRepository.save(settings)
    }

    /**
     * Update SEO settings
     */
    
    fun updateSeoSettings(storeId: String, seoSettings: SeoSettings): StoreSettings {
        logger.info { "Updating SEO settings for store: $storeId" }

        val settings = getSettings(storeId)
        settings.seoSettings = seoSettings
        return storeSettingsRepository.save(settings)
    }

    /**
     * Update theme settings
     */
    
    fun updateThemeSettings(storeId: String, themeSettings: ThemeSettings): StoreSettings {
        logger.info { "Updating theme settings for store: $storeId" }

        val settings = getSettings(storeId)
        settings.themeSettings = themeSettings
        return storeSettingsRepository.save(settings)
    }

    /**
     * Check if a feature is enabled for a store
     */
    @Transactional(readOnly = true)
    fun isFeatureEnabled(storeId: String, feature: StoreFeature): Boolean {
        return try {
            getSettings(storeId).isFeatureEnabled(feature)
        } catch (e: StoreSettingsNotFoundException) {
            false
        }
    }
}

// ============================================================================
// Request DTOs (used by service)
// ============================================================================

data class UpdateBusinessInfoRequest(
    val description: String? = null,
    val logoUrl: String? = null,
    val faviconUrl: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val legalBusinessName: String? = null,
    val taxId: String? = null,
    val socialLinks: SocialLinks? = null
)

data class UpdateLocalizationRequest(
    val timezone: String? = null,
    val defaultLocale: String? = null,
    val dateFormat: DateFormat? = null,
    val currencyDisplayFormat: CurrencyDisplayFormat? = null
)

data class UpdateFeaturesRequest(
    val reviewsEnabled: Boolean? = null,
    val wishlistEnabled: Boolean? = null,
    val giftCardsEnabled: Boolean? = null,
    val customerTiersEnabled: Boolean? = null,
    val guestCheckoutEnabled: Boolean? = null,
    val newsletterEnabled: Boolean? = null,
    val productComparisonEnabled: Boolean? = null
)

// ============================================================================
// Exceptions
// ============================================================================

class StoreSettingsNotFoundException(message: String) : RuntimeException(message)
