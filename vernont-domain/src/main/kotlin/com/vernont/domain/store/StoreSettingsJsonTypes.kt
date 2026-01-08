package com.vernont.domain.store

import java.io.Serializable
import java.math.BigDecimal

/**
 * Social media links for the store
 */
data class SocialLinks(
    var facebook: String? = null,
    var instagram: String? = null,
    var twitter: String? = null,
    var tiktok: String? = null,
    var youtube: String? = null,
    var pinterest: String? = null,
    var linkedin: String? = null
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Store policies configuration
 */
data class StorePolicies(
    var returnPolicyUrl: String? = null,
    var returnPolicySummary: String? = null,
    var shippingPolicyUrl: String? = null,
    var shippingPolicySummary: String? = null,
    var termsAndConditionsUrl: String? = null,
    var privacyPolicyUrl: String? = null,
    var cookiePolicyUrl: String? = null,
    var refundPolicyUrl: String? = null,
    var returnWindowDays: Int = 30,
    var exchangeWindowDays: Int = 14
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Checkout configuration settings
 */
data class CheckoutSettings(
    var acceptedPaymentMethods: List<String> = listOf("stripe"),
    var checkoutFlow: CheckoutFlow = CheckoutFlow.MULTI_STEP,
    var requirePhone: Boolean = false,
    var requireCompany: Boolean = false,
    var showOrderNotes: Boolean = true,
    var autoCapture: Boolean = true,
    var minimumOrderAmount: BigDecimal? = null,
    var maximumOrderAmount: BigDecimal? = null
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Shipping configuration settings
 */
data class ShippingSettings(
    var freeShippingThreshold: BigDecimal? = null,
    var internationalShippingEnabled: Boolean = false,
    var defaultShippingMethodId: String? = null,
    var estimatedDeliveryDaysMin: Int = 3,
    var estimatedDeliveryDaysMax: Int = 7,
    var internationalDeliveryDaysMin: Int = 7,
    var internationalDeliveryDaysMax: Int = 21,
    var allowedCountries: List<String>? = null,
    var blockedCountries: List<String> = emptyList()
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * SEO configuration settings
 */
data class SeoSettings(
    var metaTitle: String? = null,
    var metaDescription: String? = null,
    var ogImage: String? = null,
    var googleAnalyticsId: String? = null,
    var facebookPixelId: String? = null,
    var enableStructuredData: Boolean = true
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Theme configuration for storefront appearance.
 * Colors are stored as CSS-compatible hex values (e.g., "#D4AF37").
 */
data class ThemeSettings(
    // Core colors
    var primaryColor: String = "#1A1A1A",
    var primaryForeground: String = "#FDFBF7",
    var secondaryColor: String = "#F5F0E8",
    var secondaryForeground: String = "#1A1A1A",
    var accentColor: String = "#D4AF37",
    var accentForeground: String = "#1A1A1A",

    // Background colors
    var backgroundColor: String = "#FDFBF7",
    var foregroundColor: String = "#1A1A1A",
    var cardColor: String = "#FFFFFF",
    var cardForeground: String = "#1A1A1A",

    // Muted colors
    var mutedColor: String = "#F5F0E8",
    var mutedForeground: String = "#6B6B6B",

    // Border and input
    var borderColor: String = "#E5E0D8",
    var inputColor: String = "#E5E0D8",
    var ringColor: String = "#D4AF37",

    // Brand accent colors
    var goldColor: String = "#D4AF37",
    var champagneColor: String = "#F7E7CE",
    var roseGoldColor: String = "#B76E79",

    // Destructive
    var destructiveColor: String = "#DC2626",

    // Typography
    var headingFont: String = "Playfair Display",
    var bodyFont: String = "Crimson Pro",
    var accentFont: String = "Cormorant Garamond",

    // Border radius
    var borderRadius: String = "0.5rem"
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
