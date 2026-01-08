package com.vernont.api.dto.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.store.*
import java.math.BigDecimal

/**
 * Public-facing store settings DTO for storefront.
 * Excludes sensitive/internal settings like tax IDs, legal info.
 */
data class StoreSettingsPublicDto(
    val id: String,

    @JsonProperty("store_name")
    val storeName: String,

    val description: String?,

    @JsonProperty("logo_url")
    val logoUrl: String?,

    @JsonProperty("favicon_url")
    val faviconUrl: String?,

    @JsonProperty("contact_email")
    val contactEmail: String?,

    @JsonProperty("contact_phone")
    val contactPhone: String?,

    @JsonProperty("social_links")
    val socialLinks: SocialLinks?,

    // Localization
    val timezone: String,

    @JsonProperty("default_locale")
    val defaultLocale: String,

    @JsonProperty("date_format")
    val dateFormat: String,

    @JsonProperty("currency_display_format")
    val currencyDisplayFormat: String,

    // Features (public-facing)
    val features: StoreFeatures,

    // Policies (public URLs)
    val policies: PublicPolicies?,

    // Shipping info (public)
    @JsonProperty("shipping_info")
    val shippingInfo: PublicShippingInfo?,

    // SEO
    val seo: PublicSeoInfo?,

    // Theme
    val theme: PublicThemeInfo?
) {
    companion object {
        fun from(settings: StoreSettings): StoreSettingsPublicDto {
            return StoreSettingsPublicDto(
                id = settings.id,
                storeName = settings.store?.name ?: "",
                description = settings.description,
                logoUrl = settings.logoUrl,
                faviconUrl = settings.faviconUrl,
                contactEmail = settings.contactEmail,
                contactPhone = settings.contactPhone,
                socialLinks = settings.socialLinks,
                timezone = settings.timezone,
                defaultLocale = settings.defaultLocale,
                dateFormat = settings.dateFormat.pattern,
                currencyDisplayFormat = settings.currencyDisplayFormat.name,
                features = StoreFeatures(
                    reviewsEnabled = settings.reviewsEnabled,
                    wishlistEnabled = settings.wishlistEnabled,
                    giftCardsEnabled = settings.giftCardsEnabled,
                    guestCheckoutEnabled = settings.guestCheckoutEnabled,
                    newsletterEnabled = settings.newsletterEnabled,
                    productComparisonEnabled = settings.productComparisonEnabled
                ),
                policies = settings.policies?.let { PublicPolicies.from(it) },
                shippingInfo = settings.shippingSettings?.let { PublicShippingInfo.from(it) },
                seo = settings.seoSettings?.let { PublicSeoInfo.from(it) },
                theme = settings.themeSettings?.let { PublicThemeInfo.from(it) }
            )
        }
    }
}

data class StoreFeatures(
    @JsonProperty("reviews_enabled")
    val reviewsEnabled: Boolean,

    @JsonProperty("wishlist_enabled")
    val wishlistEnabled: Boolean,

    @JsonProperty("gift_cards_enabled")
    val giftCardsEnabled: Boolean,

    @JsonProperty("guest_checkout_enabled")
    val guestCheckoutEnabled: Boolean,

    @JsonProperty("newsletter_enabled")
    val newsletterEnabled: Boolean,

    @JsonProperty("product_comparison_enabled")
    val productComparisonEnabled: Boolean
)

data class PublicPolicies(
    @JsonProperty("return_policy_url")
    val returnPolicyUrl: String?,

    @JsonProperty("return_policy_summary")
    val returnPolicySummary: String?,

    @JsonProperty("shipping_policy_url")
    val shippingPolicyUrl: String?,

    @JsonProperty("terms_url")
    val termsUrl: String?,

    @JsonProperty("privacy_url")
    val privacyUrl: String?,

    @JsonProperty("return_window_days")
    val returnWindowDays: Int
) {
    companion object {
        fun from(policies: StorePolicies): PublicPolicies {
            return PublicPolicies(
                returnPolicyUrl = policies.returnPolicyUrl,
                returnPolicySummary = policies.returnPolicySummary,
                shippingPolicyUrl = policies.shippingPolicyUrl,
                termsUrl = policies.termsAndConditionsUrl,
                privacyUrl = policies.privacyPolicyUrl,
                returnWindowDays = policies.returnWindowDays
            )
        }
    }
}

data class PublicShippingInfo(
    @JsonProperty("free_shipping_threshold")
    val freeShippingThreshold: BigDecimal?,

    @JsonProperty("international_shipping_enabled")
    val internationalShippingEnabled: Boolean,

    @JsonProperty("estimated_delivery_days_min")
    val estimatedDeliveryDaysMin: Int,

    @JsonProperty("estimated_delivery_days_max")
    val estimatedDeliveryDaysMax: Int
) {
    companion object {
        fun from(settings: ShippingSettings): PublicShippingInfo {
            return PublicShippingInfo(
                freeShippingThreshold = settings.freeShippingThreshold,
                internationalShippingEnabled = settings.internationalShippingEnabled,
                estimatedDeliveryDaysMin = settings.estimatedDeliveryDaysMin,
                estimatedDeliveryDaysMax = settings.estimatedDeliveryDaysMax
            )
        }
    }
}

data class PublicSeoInfo(
    @JsonProperty("meta_title")
    val metaTitle: String?,

    @JsonProperty("meta_description")
    val metaDescription: String?,

    @JsonProperty("og_image")
    val ogImage: String?
) {
    companion object {
        fun from(settings: SeoSettings): PublicSeoInfo {
            return PublicSeoInfo(
                metaTitle = settings.metaTitle,
                metaDescription = settings.metaDescription,
                ogImage = settings.ogImage
            )
        }
    }
}

data class PublicThemeInfo(
    @JsonProperty("primary_color")
    val primaryColor: String,

    @JsonProperty("primary_foreground")
    val primaryForeground: String,

    @JsonProperty("secondary_color")
    val secondaryColor: String,

    @JsonProperty("secondary_foreground")
    val secondaryForeground: String,

    @JsonProperty("accent_color")
    val accentColor: String,

    @JsonProperty("accent_foreground")
    val accentForeground: String,

    @JsonProperty("background_color")
    val backgroundColor: String,

    @JsonProperty("foreground_color")
    val foregroundColor: String,

    @JsonProperty("card_color")
    val cardColor: String,

    @JsonProperty("card_foreground")
    val cardForeground: String,

    @JsonProperty("muted_color")
    val mutedColor: String,

    @JsonProperty("muted_foreground")
    val mutedForeground: String,

    @JsonProperty("border_color")
    val borderColor: String,

    @JsonProperty("input_color")
    val inputColor: String,

    @JsonProperty("ring_color")
    val ringColor: String,

    @JsonProperty("gold_color")
    val goldColor: String,

    @JsonProperty("champagne_color")
    val champagneColor: String,

    @JsonProperty("rose_gold_color")
    val roseGoldColor: String,

    @JsonProperty("destructive_color")
    val destructiveColor: String,

    @JsonProperty("heading_font")
    val headingFont: String,

    @JsonProperty("body_font")
    val bodyFont: String,

    @JsonProperty("accent_font")
    val accentFont: String,

    @JsonProperty("border_radius")
    val borderRadius: String
) {
    companion object {
        fun from(settings: ThemeSettings): PublicThemeInfo {
            return PublicThemeInfo(
                primaryColor = settings.primaryColor,
                primaryForeground = settings.primaryForeground,
                secondaryColor = settings.secondaryColor,
                secondaryForeground = settings.secondaryForeground,
                accentColor = settings.accentColor,
                accentForeground = settings.accentForeground,
                backgroundColor = settings.backgroundColor,
                foregroundColor = settings.foregroundColor,
                cardColor = settings.cardColor,
                cardForeground = settings.cardForeground,
                mutedColor = settings.mutedColor,
                mutedForeground = settings.mutedForeground,
                borderColor = settings.borderColor,
                inputColor = settings.inputColor,
                ringColor = settings.ringColor,
                goldColor = settings.goldColor,
                champagneColor = settings.champagneColor,
                roseGoldColor = settings.roseGoldColor,
                destructiveColor = settings.destructiveColor,
                headingFont = settings.headingFont,
                bodyFont = settings.bodyFont,
                accentFont = settings.accentFont,
                borderRadius = settings.borderRadius
            )
        }
    }
}

data class StoreSettingsPublicResponse(
    val settings: StoreSettingsPublicDto
)
