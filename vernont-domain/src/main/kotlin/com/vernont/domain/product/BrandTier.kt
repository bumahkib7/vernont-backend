package com.vernont.domain.product

/**
 * Classification of brand positioning in the market.
 * Used to segment and prioritize brands in catalog displays.
 */
enum class BrandTier {
    /**
     * Luxury/haute couture brands (e.g., Dior, Gucci, Prada)
     * - Heritage fashion houses
     * - Premium pricing
     * - Exclusive positioning
     */
    LUXURY,

    /**
     * Premium/contemporary brands (e.g., Calvin Klein, Hugo Boss)
     * - High quality
     * - Mid-to-high pricing
     * - Aspirational positioning
     */
    PREMIUM,

    /**
     * Standard/mainstream brands
     * - Accessible pricing
     * - Mass market appeal
     */
    STANDARD
}
