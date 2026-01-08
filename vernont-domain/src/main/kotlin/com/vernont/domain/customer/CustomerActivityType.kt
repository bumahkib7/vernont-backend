package com.vernont.domain.customer

/**
 * Types of customer activity events for the activity log.
 */
enum class CustomerActivityType(val displayName: String, val category: String) {
    // Account events
    ACCOUNT_CREATED("Account Created", "account"),
    ACCOUNT_ACTIVATED("Account Activated", "account"),
    ACCOUNT_SUSPENDED("Account Suspended", "account"),
    ACCOUNT_BANNED("Account Banned", "account"),
    PROFILE_UPDATED("Profile Updated", "account"),
    PASSWORD_RESET_REQUESTED("Password Reset Requested", "account"),
    PASSWORD_CHANGED("Password Changed", "account"),
    LOGIN("Login", "account"),
    LOGIN_FAILED("Login Failed", "account"),

    // Tier events
    TIER_UPGRADED("Tier Upgraded", "tier"),
    TIER_CHANGED_MANUALLY("Tier Changed Manually", "tier"),

    // Communication events
    EMAIL_SENT("Email Sent", "communication"),
    GIFT_CARD_SENT("Gift Card Sent", "communication"),

    // Order events
    ORDER_PLACED("Order Placed", "order"),
    ORDER_CANCELLED("Order Cancelled", "order"),
    ORDER_REFUNDED("Order Refunded", "order"),

    // Address events
    ADDRESS_ADDED("Address Added", "address"),
    ADDRESS_UPDATED("Address Updated", "address"),
    ADDRESS_REMOVED("Address Removed", "address"),
    BILLING_ADDRESS_SET("Billing Address Set", "address"),

    // Group events
    ADDED_TO_GROUP("Added to Group", "group"),
    REMOVED_FROM_GROUP("Removed from Group", "group"),

    // Wishlist/Favorites
    PRODUCT_FAVORITED("Product Favorited", "wishlist"),
    PRODUCT_UNFAVORITED("Product Unfavorited", "wishlist"),

    // Notes
    NOTE_ADDED("Internal Note Added", "note");

    companion object {
        fun fromString(value: String): CustomerActivityType? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
