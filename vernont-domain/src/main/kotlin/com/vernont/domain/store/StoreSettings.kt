package com.vernont.domain.store

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.Email
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Store settings entity for multi-store configuration.
 * Each store has one settings record that controls storefront behavior.
 */
@Entity
@Table(
    name = "store_settings",
    indexes = [
        Index(name = "idx_store_settings_store_id", columnList = "store_id"),
        Index(name = "idx_store_settings_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "StoreSettings.full",
    attributeNodes = [
        NamedAttributeNode("store")
    ]
)
class StoreSettings : BaseEntity() {

    // =========================================================================
    // Relationship to Store (OneToOne)
    // =========================================================================

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    var store: Store? = null

    // =========================================================================
    // Business Information
    // =========================================================================

    @Column(length = 500)
    var description: String? = null

    @Column(name = "logo_url", length = 2048)
    var logoUrl: String? = null

    @Column(name = "favicon_url", length = 2048)
    var faviconUrl: String? = null

    @Email
    @Column(name = "contact_email")
    var contactEmail: String? = null

    @Column(name = "contact_phone", length = 50)
    var contactPhone: String? = null

    @Column(name = "legal_business_name")
    var legalBusinessName: String? = null

    @Column(name = "tax_id", length = 100)
    var taxId: String? = null

    // =========================================================================
    // Localization Settings
    // =========================================================================

    @Column(name = "timezone", length = 50, nullable = false)
    var timezone: String = "UTC"

    @Column(name = "default_locale", length = 10, nullable = false)
    var defaultLocale: String = "en-GB"

    @Enumerated(EnumType.STRING)
    @Column(name = "date_format", length = 20, nullable = false)
    var dateFormat: DateFormat = DateFormat.DD_MM_YYYY

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_display_format", length = 20, nullable = false)
    var currencyDisplayFormat: CurrencyDisplayFormat = CurrencyDisplayFormat.SYMBOL_BEFORE

    // =========================================================================
    // Feature Toggles
    // =========================================================================

    @Column(name = "reviews_enabled", nullable = false)
    var reviewsEnabled: Boolean = false

    @Column(name = "wishlist_enabled", nullable = false)
    var wishlistEnabled: Boolean = true

    @Column(name = "gift_cards_enabled", nullable = false)
    var giftCardsEnabled: Boolean = false

    @Column(name = "customer_tiers_enabled", nullable = false)
    var customerTiersEnabled: Boolean = false

    @Column(name = "guest_checkout_enabled", nullable = false)
    var guestCheckoutEnabled: Boolean = true

    @Column(name = "newsletter_enabled", nullable = false)
    var newsletterEnabled: Boolean = true

    @Column(name = "product_comparison_enabled", nullable = false)
    var productComparisonEnabled: Boolean = false

    // =========================================================================
    // JSONB Settings Blocks
    // =========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "social_links", columnDefinition = "jsonb")
    var socialLinks: SocialLinks? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policies", columnDefinition = "jsonb")
    var policies: StorePolicies? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checkout_settings", columnDefinition = "jsonb")
    var checkoutSettings: CheckoutSettings? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_settings", columnDefinition = "jsonb")
    var shippingSettings: ShippingSettings? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seo_settings", columnDefinition = "jsonb")
    var seoSettings: SeoSettings? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "theme_settings", columnDefinition = "jsonb")
    var themeSettings: ThemeSettings? = null

    // =========================================================================
    // Business Logic Methods
    // =========================================================================

    /**
     * Check if a specific feature is enabled
     */
    fun isFeatureEnabled(feature: StoreFeature): Boolean {
        return when (feature) {
            StoreFeature.REVIEWS -> reviewsEnabled
            StoreFeature.WISHLIST -> wishlistEnabled
            StoreFeature.GIFT_CARDS -> giftCardsEnabled
            StoreFeature.CUSTOMER_TIERS -> customerTiersEnabled
            StoreFeature.GUEST_CHECKOUT -> guestCheckoutEnabled
            StoreFeature.NEWSLETTER -> newsletterEnabled
            StoreFeature.PRODUCT_COMPARISON -> productComparisonEnabled
        }
    }

    /**
     * Get the free shipping threshold amount
     */
    fun getFreeShippingThreshold(): java.math.BigDecimal? {
        return shippingSettings?.freeShippingThreshold
    }

    /**
     * Check if international shipping is enabled
     */
    fun isInternationalShippingEnabled(): Boolean {
        return shippingSettings?.internationalShippingEnabled ?: false
    }

    /**
     * Get list of accepted payment methods
     */
    fun getAcceptedPaymentMethods(): List<String> {
        return checkoutSettings?.acceptedPaymentMethods ?: listOf("stripe")
    }

    /**
     * Get the checkout flow type
     */
    fun getCheckoutFlow(): CheckoutFlow {
        return checkoutSettings?.checkoutFlow ?: CheckoutFlow.MULTI_STEP
    }

    companion object {
        /**
         * Create default settings for a store
         */
        fun createDefault(store: Store): StoreSettings {
            return StoreSettings().apply {
                this.store = store
                this.socialLinks = SocialLinks()
                this.policies = StorePolicies()
                this.checkoutSettings = CheckoutSettings()
                this.shippingSettings = ShippingSettings()
                this.seoSettings = SeoSettings()
                this.themeSettings = ThemeSettings()
            }
        }
    }
}
