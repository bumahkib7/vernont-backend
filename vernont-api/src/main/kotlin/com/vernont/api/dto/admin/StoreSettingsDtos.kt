package com.vernont.api.dto.admin

import com.vernont.domain.store.*
import java.math.BigDecimal
import java.time.Instant

// ============================================================================
// Response DTOs
// ============================================================================

data class StoreSettingsResponse(
    val storeSettings: StoreSettingsDetail
)

data class StoreSettingsDetail(
    val id: String,
    val storeId: String,
    val storeName: String,

    // Business Info
    val description: String?,
    val logoUrl: String?,
    val faviconUrl: String?,
    val contactEmail: String?,
    val contactPhone: String?,
    val legalBusinessName: String?,
    val taxId: String?,
    val socialLinks: SocialLinks?,

    // Localization
    val timezone: String,
    val defaultLocale: String,
    val dateFormat: String,
    val currencyDisplayFormat: String,

    // Features
    val reviewsEnabled: Boolean,
    val wishlistEnabled: Boolean,
    val giftCardsEnabled: Boolean,
    val customerTiersEnabled: Boolean,
    val guestCheckoutEnabled: Boolean,
    val newsletterEnabled: Boolean,
    val productComparisonEnabled: Boolean,

    // JSONB blocks
    val policies: StorePolicies?,
    val checkoutSettings: CheckoutSettings?,
    val shippingSettings: ShippingSettings?,
    val seoSettings: SeoSettings?,
    val themeSettings: ThemeSettings?,

    // Timestamps
    val createdAt: Instant?,
    val updatedAt: Instant?
) {
    companion object {
        fun from(settings: StoreSettings): StoreSettingsDetail {
            return StoreSettingsDetail(
                id = settings.id,
                storeId = settings.store?.id ?: "",
                storeName = settings.store?.name ?: "",
                description = settings.description,
                logoUrl = settings.logoUrl,
                faviconUrl = settings.faviconUrl,
                contactEmail = settings.contactEmail,
                contactPhone = settings.contactPhone,
                legalBusinessName = settings.legalBusinessName,
                taxId = settings.taxId,
                socialLinks = settings.socialLinks,
                timezone = settings.timezone,
                defaultLocale = settings.defaultLocale,
                dateFormat = settings.dateFormat.name,
                currencyDisplayFormat = settings.currencyDisplayFormat.name,
                reviewsEnabled = settings.reviewsEnabled,
                wishlistEnabled = settings.wishlistEnabled,
                giftCardsEnabled = settings.giftCardsEnabled,
                customerTiersEnabled = settings.customerTiersEnabled,
                guestCheckoutEnabled = settings.guestCheckoutEnabled,
                newsletterEnabled = settings.newsletterEnabled,
                productComparisonEnabled = settings.productComparisonEnabled,
                policies = settings.policies,
                checkoutSettings = settings.checkoutSettings,
                shippingSettings = settings.shippingSettings,
                seoSettings = settings.seoSettings,
                themeSettings = settings.themeSettings,
                createdAt = settings.createdAt,
                updatedAt = settings.updatedAt
            )
        }
    }
}

// ============================================================================
// Request DTOs
// ============================================================================

data class AdminUpdateBusinessInfoRequest(
    val description: String? = null,
    val logoUrl: String? = null,
    val faviconUrl: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val legalBusinessName: String? = null,
    val taxId: String? = null,
    val socialLinks: SocialLinks? = null
)

data class AdminUpdateLocalizationRequest(
    val timezone: String? = null,
    val defaultLocale: String? = null,
    val dateFormat: String? = null,
    val currencyDisplayFormat: String? = null
)

data class AdminUpdateFeaturesRequest(
    val reviewsEnabled: Boolean? = null,
    val wishlistEnabled: Boolean? = null,
    val giftCardsEnabled: Boolean? = null,
    val customerTiersEnabled: Boolean? = null,
    val guestCheckoutEnabled: Boolean? = null,
    val newsletterEnabled: Boolean? = null,
    val productComparisonEnabled: Boolean? = null
)

data class AdminUpdatePoliciesRequest(
    val policies: StorePolicies
)

data class AdminUpdateCheckoutSettingsRequest(
    val checkoutSettings: CheckoutSettings
)

data class AdminUpdateShippingSettingsRequest(
    val shippingSettings: ShippingSettings
)

data class AdminUpdateSeoSettingsRequest(
    val seoSettings: SeoSettings
)

data class AdminUpdateThemeSettingsRequest(
    val themeSettings: ThemeSettings
)

/**
 * Bulk update request for all settings at once
 */
data class AdminUpdateAllSettingsRequest(
    val businessInfo: AdminUpdateBusinessInfoRequest? = null,
    val localization: AdminUpdateLocalizationRequest? = null,
    val features: AdminUpdateFeaturesRequest? = null,
    val policies: StorePolicies? = null,
    val checkoutSettings: CheckoutSettings? = null,
    val shippingSettings: ShippingSettings? = null,
    val seoSettings: SeoSettings? = null,
    val themeSettings: ThemeSettings? = null
)
