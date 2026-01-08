package com.vernont.domain.giftcard

/**
 * Status of a gift card
 */
enum class GiftCardStatus(val displayName: String, val canRedeem: Boolean) {
    ACTIVE("Active", true),
    FULLY_REDEEMED("Fully Redeemed", false),
    EXPIRED("Expired", false),
    DISABLED("Disabled", false);

    companion object {
        fun fromString(value: String): GiftCardStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: ACTIVE
        }
    }
}
