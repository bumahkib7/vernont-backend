package com.vernont.domain.customer

import java.math.BigDecimal

/**
 * Customer loyalty tiers based on total spend.
 * Tiers auto-upgrade when spend threshold is reached.
 * Tiers never auto-downgrade (loyalty preservation).
 */
enum class CustomerTier(
    val displayName: String,
    val spendThreshold: BigDecimal,
    val discountPercent: Int,
    val freeShipping: Boolean
) {
    BRONZE("Bronze", BigDecimal.ZERO, 0, false),
    SILVER("Silver", BigDecimal("500.00"), 5, false),
    GOLD("Gold", BigDecimal("2000.00"), 10, false),
    PLATINUM("Platinum", BigDecimal("5000.00"), 15, true);

    companion object {
        /**
         * Calculate the appropriate tier based on total spend.
         * Returns the highest tier the customer qualifies for.
         */
        fun forSpend(totalSpent: BigDecimal): CustomerTier {
            return entries
                .sortedByDescending { it.spendThreshold }
                .firstOrNull { totalSpent >= it.spendThreshold }
                ?: BRONZE
        }

        /**
         * Get the next tier above the current one, if any.
         */
        fun nextTier(current: CustomerTier): CustomerTier? {
            val currentOrdinal = current.ordinal
            return entries.getOrNull(currentOrdinal + 1)
        }

        /**
         * Calculate spend needed to reach the next tier.
         */
        fun spendToNextTier(currentSpend: BigDecimal): BigDecimal? {
            val currentTier = forSpend(currentSpend)
            val nextTier = nextTier(currentTier) ?: return null
            return nextTier.spendThreshold - currentSpend
        }
    }
}
