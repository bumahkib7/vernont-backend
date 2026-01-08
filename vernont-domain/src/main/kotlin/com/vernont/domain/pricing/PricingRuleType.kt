package com.vernont.domain.pricing

enum class PricingRuleType(val displayName: String, val description: String) {
    PERCENTAGE_DISCOUNT("Percentage Discount", "Apply a percentage discount to the price"),
    FIXED_DISCOUNT("Fixed Discount", "Subtract a fixed amount from the price"),
    MARKUP("Markup", "Apply a percentage markup to the price"),
    TIME_BASED("Time-Based", "Apply pricing based on time/date conditions"),
    QUANTITY_BASED("Quantity-Based", "Apply pricing based on quantity thresholds"),
    TIERED("Tiered Pricing", "Apply different prices at different quantity tiers")
}

enum class PricingRuleStatus(val displayName: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    SCHEDULED("Scheduled")
}

enum class TargetType(val displayName: String) {
    ALL("All Products"),
    PRODUCTS("Specific Products"),
    CATEGORIES("Categories"),
    COLLECTIONS("Collections"),
    BRANDS("Brands")
}

enum class AdjustmentType {
    PERCENTAGE,
    FIXED_AMOUNT,
    SET_PRICE
}
