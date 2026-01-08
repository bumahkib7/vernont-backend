package com.vernont.api.controller.store

import com.vernont.api.dto.store.StoreSettingsPublicDto
import com.vernont.api.dto.store.StoreSettingsPublicResponse
import com.vernont.application.store.StoreSettingsNotFoundException
import com.vernont.application.store.StoreSettingsService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/store/settings")
@Tag(name = "Store Settings", description = "Public store settings for storefront")
class StoreSettingsController(
    private val storeSettingsService: StoreSettingsService
) {

    @Operation(summary = "Get public store settings for storefront")
    @GetMapping("/{storeId}")
    fun getPublicSettings(@PathVariable storeId: String): ResponseEntity<StoreSettingsPublicResponse> {
        logger.debug { "GET /store/settings/$storeId" }

        return try {
            val settings = storeSettingsService.getSettings(storeId)
            val publicSettings = StoreSettingsPublicDto.from(settings)
            ResponseEntity.ok(StoreSettingsPublicResponse(settings = publicSettings))
        } catch (e: StoreSettingsNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Get store features (for conditional UI rendering)")
    @GetMapping("/{storeId}/features")
    fun getFeatures(@PathVariable storeId: String): ResponseEntity<Map<String, Boolean>> {
        logger.debug { "GET /store/settings/$storeId/features" }

        return try {
            val settings = storeSettingsService.getSettings(storeId)
            ResponseEntity.ok(mapOf(
                "reviews" to settings.reviewsEnabled,
                "wishlist" to settings.wishlistEnabled,
                "gift_cards" to settings.giftCardsEnabled,
                "guest_checkout" to settings.guestCheckoutEnabled,
                "newsletter" to settings.newsletterEnabled,
                "product_comparison" to settings.productComparisonEnabled,
                "customer_tiers" to settings.customerTiersEnabled
            ))
        } catch (e: StoreSettingsNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Get store shipping info")
    @GetMapping("/{storeId}/shipping")
    fun getShippingInfo(@PathVariable storeId: String): ResponseEntity<Map<String, Any?>> {
        logger.debug { "GET /store/settings/$storeId/shipping" }

        return try {
            val settings = storeSettingsService.getSettings(storeId)
            val shippingSettings = settings.shippingSettings

            ResponseEntity.ok(mapOf(
                "free_shipping_threshold" to shippingSettings?.freeShippingThreshold,
                "international_shipping_enabled" to (shippingSettings?.internationalShippingEnabled ?: false),
                "estimated_delivery_days_min" to (shippingSettings?.estimatedDeliveryDaysMin ?: 3),
                "estimated_delivery_days_max" to (shippingSettings?.estimatedDeliveryDaysMax ?: 7)
            ))
        } catch (e: StoreSettingsNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Get store policies")
    @GetMapping("/{storeId}/policies")
    fun getPolicies(@PathVariable storeId: String): ResponseEntity<Map<String, Any?>> {
        logger.debug { "GET /store/settings/$storeId/policies" }

        return try {
            val settings = storeSettingsService.getSettings(storeId)
            val policies = settings.policies

            ResponseEntity.ok(mapOf(
                "return_policy_url" to policies?.returnPolicyUrl,
                "return_policy_summary" to policies?.returnPolicySummary,
                "shipping_policy_url" to policies?.shippingPolicyUrl,
                "terms_url" to policies?.termsAndConditionsUrl,
                "privacy_url" to policies?.privacyPolicyUrl,
                "return_window_days" to (policies?.returnWindowDays ?: 30)
            ))
        } catch (e: StoreSettingsNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }
}
