package com.vernont.api.controller.admin

import com.vernont.api.dto.admin.*
import com.vernont.application.store.*
import com.vernont.domain.store.CurrencyDisplayFormat
import com.vernont.domain.store.DateFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/stores/{storeId}/settings")
@Tag(name = "Admin Store Settings", description = "Store settings management endpoints")
class AdminStoreSettingsController(
    private val storeSettingsService: StoreSettingsService
) {

    // =========================================================================
    // Get Settings
    // =========================================================================

    @Operation(summary = "Get all store settings")
    @GetMapping
    fun getSettings(@PathVariable storeId: String): ResponseEntity<StoreSettingsResponse> {
        logger.info { "GET /admin/stores/$storeId/settings" }

        return try {
            val settings = storeSettingsService.getOrCreateSettings(storeId)
            ResponseEntity.ok(StoreSettingsResponse(
                storeSettings = StoreSettingsDetail.from(settings)
            ))
        } catch (e: StoreSettingsNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    // =========================================================================
    // Update Sections
    // =========================================================================

    @Operation(summary = "Update business information")
    @PutMapping("/business-info")
    @Transactional
    fun updateBusinessInfo(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateBusinessInfoRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings/business-info" }

        val serviceRequest = UpdateBusinessInfoRequest(
            description = request.description,
            logoUrl = request.logoUrl,
            faviconUrl = request.faviconUrl,
            contactEmail = request.contactEmail,
            contactPhone = request.contactPhone,
            legalBusinessName = request.legalBusinessName,
            taxId = request.taxId,
            socialLinks = request.socialLinks
        )
        val settings = storeSettingsService.updateBusinessInfo(storeId, serviceRequest)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    @Operation(summary = "Update localization settings")
    @PutMapping("/localization")
    @Transactional
    fun updateLocalization(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateLocalizationRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings/localization" }

        val serviceRequest = UpdateLocalizationRequest(
            timezone = request.timezone,
            defaultLocale = request.defaultLocale,
            dateFormat = request.dateFormat?.let { DateFormat.fromString(it) },
            currencyDisplayFormat = request.currencyDisplayFormat?.let { CurrencyDisplayFormat.fromString(it) }
        )
        val settings = storeSettingsService.updateLocalization(storeId, serviceRequest)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    @Operation(summary = "Update feature toggles")
    @PutMapping("/features")
    @Transactional
    fun updateFeatures(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateFeaturesRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings/features" }

        val serviceRequest = UpdateFeaturesRequest(
            reviewsEnabled = request.reviewsEnabled,
            wishlistEnabled = request.wishlistEnabled,
            giftCardsEnabled = request.giftCardsEnabled,
            customerTiersEnabled = request.customerTiersEnabled,
            guestCheckoutEnabled = request.guestCheckoutEnabled,
            newsletterEnabled = request.newsletterEnabled,
            productComparisonEnabled = request.productComparisonEnabled
        )
        val settings = storeSettingsService.updateFeatures(storeId, serviceRequest)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    @Operation(summary = "Update store policies")
    @PutMapping("/policies")
    @Transactional
    fun updatePolicies(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdatePoliciesRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings/policies" }

        val settings = storeSettingsService.updatePolicies(storeId, request.policies)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    @Operation(summary = "Update checkout settings")
    @PutMapping("/checkout")
    @Transactional
    fun updateCheckoutSettings(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateCheckoutSettingsRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings/checkout" }

        val settings = storeSettingsService.updateCheckoutSettings(storeId, request.checkoutSettings)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    @Operation(summary = "Update shipping settings")
    @PutMapping("/shipping")
    @Transactional
    fun updateShippingSettings(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateShippingSettingsRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings/shipping" }

        val settings = storeSettingsService.updateShippingSettings(storeId, request.shippingSettings)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    @Operation(summary = "Update SEO settings")
    @PutMapping("/seo")
    @Transactional
    fun updateSeoSettings(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateSeoSettingsRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings/seo" }

        val settings = storeSettingsService.updateSeoSettings(storeId, request.seoSettings)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    @Operation(summary = "Update theme settings")
    @PutMapping("/theme")
    @Transactional
    fun updateThemeSettings(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateThemeSettingsRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings/theme" }

        val settings = storeSettingsService.updateThemeSettings(storeId, request.themeSettings)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    // =========================================================================
    // Bulk Update
    // =========================================================================

    @Operation(summary = "Update all settings at once")
    @PutMapping
    @Transactional
    fun updateAllSettings(
        @PathVariable storeId: String,
        @RequestBody request: AdminUpdateAllSettingsRequest
    ): ResponseEntity<StoreSettingsResponse> {
        logger.info { "PUT /admin/stores/$storeId/settings (bulk update)" }

        var settings = storeSettingsService.getOrCreateSettings(storeId)

        request.businessInfo?.let {
            val serviceRequest = UpdateBusinessInfoRequest(
                description = it.description,
                logoUrl = it.logoUrl,
                faviconUrl = it.faviconUrl,
                contactEmail = it.contactEmail,
                contactPhone = it.contactPhone,
                legalBusinessName = it.legalBusinessName,
                taxId = it.taxId,
                socialLinks = it.socialLinks
            )
            settings = storeSettingsService.updateBusinessInfo(storeId, serviceRequest)
        }
        request.localization?.let {
            val serviceRequest = UpdateLocalizationRequest(
                timezone = it.timezone,
                defaultLocale = it.defaultLocale,
                dateFormat = it.dateFormat?.let { df -> DateFormat.fromString(df) },
                currencyDisplayFormat = it.currencyDisplayFormat?.let { cdf -> CurrencyDisplayFormat.fromString(cdf) }
            )
            settings = storeSettingsService.updateLocalization(storeId, serviceRequest)
        }
        request.features?.let {
            val serviceRequest = UpdateFeaturesRequest(
                reviewsEnabled = it.reviewsEnabled,
                wishlistEnabled = it.wishlistEnabled,
                giftCardsEnabled = it.giftCardsEnabled,
                customerTiersEnabled = it.customerTiersEnabled,
                guestCheckoutEnabled = it.guestCheckoutEnabled,
                newsletterEnabled = it.newsletterEnabled,
                productComparisonEnabled = it.productComparisonEnabled
            )
            settings = storeSettingsService.updateFeatures(storeId, serviceRequest)
        }
        request.policies?.let {
            settings = storeSettingsService.updatePolicies(storeId, it)
        }
        request.checkoutSettings?.let {
            settings = storeSettingsService.updateCheckoutSettings(storeId, it)
        }
        request.shippingSettings?.let {
            settings = storeSettingsService.updateShippingSettings(storeId, it)
        }
        request.seoSettings?.let {
            settings = storeSettingsService.updateSeoSettings(storeId, it)
        }

        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }

    // =========================================================================
    // Initialize Settings (if needed)
    // =========================================================================

    @Operation(summary = "Initialize settings for a store")
    @PostMapping("/initialize")
    @Transactional
    fun initializeSettings(@PathVariable storeId: String): ResponseEntity<StoreSettingsResponse> {
        logger.info { "POST /admin/stores/$storeId/settings/initialize" }

        val settings = storeSettingsService.getOrCreateSettings(storeId)
        return ResponseEntity.ok(StoreSettingsResponse(
            storeSettings = StoreSettingsDetail.from(settings)
        ))
    }
}
