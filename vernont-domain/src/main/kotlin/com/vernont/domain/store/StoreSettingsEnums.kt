package com.vernont.domain.store

/**
 * Date format options for store display
 */
enum class DateFormat(val pattern: String, val displayName: String) {
    DD_MM_YYYY("dd/MM/yyyy", "DD/MM/YYYY"),
    MM_DD_YYYY("MM/dd/yyyy", "MM/DD/YYYY"),
    YYYY_MM_DD("yyyy-MM-dd", "YYYY-MM-DD"),
    DD_MON_YYYY("dd MMM yyyy", "DD Mon YYYY");

    companion object {
        fun fromString(value: String): DateFormat {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: DD_MM_YYYY
        }
    }
}

/**
 * Currency display format options
 */
enum class CurrencyDisplayFormat(val displayName: String) {
    SYMBOL_BEFORE("Symbol Before (e.g., £100)"),
    SYMBOL_AFTER("Symbol After (e.g., 100£)"),
    CODE_BEFORE("Code Before (e.g., GBP 100)"),
    CODE_AFTER("Code After (e.g., 100 GBP)");

    companion object {
        fun fromString(value: String): CurrencyDisplayFormat {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: SYMBOL_BEFORE
        }
    }
}

/**
 * Checkout flow options
 */
enum class CheckoutFlow(val displayName: String) {
    SINGLE_PAGE("Single Page Checkout"),
    MULTI_STEP("Multi-Step Checkout"),
    EXPRESS("Express Checkout");

    companion object {
        fun fromString(value: String): CheckoutFlow {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: MULTI_STEP
        }
    }
}

/**
 * Store feature flags
 */
enum class StoreFeature(val displayName: String) {
    REVIEWS("Product Reviews"),
    WISHLIST("Wishlist"),
    GIFT_CARDS("Gift Cards"),
    CUSTOMER_TIERS("Customer Tiers"),
    GUEST_CHECKOUT("Guest Checkout"),
    NEWSLETTER("Newsletter"),
    PRODUCT_COMPARISON("Product Comparison")
}
