package com.vernont.api.dto.admin

import com.vernont.domain.pricing.*
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// =============================================================================
// Workbench DTOs
// =============================================================================

/**
 * A single item in the pricing workbench grid
 */
data class WorkbenchItem(
    val variantId: String,
    val productId: String,
    val productTitle: String,
    val variantTitle: String,
    val sku: String?,
    val barcode: String?,
    val thumbnail: String?,
    val currentPrice: Int, // In cents (pence)
    val compareAtPrice: Int?, // In cents
    val currencyCode: String,
    val costPrice: Int?, // If available
    val margin: Int?, // In cents
    val marginPercentage: BigDecimal?, // As percentage
    val inventory: Int?,
    val lastUpdated: OffsetDateTime?,
    val lastUpdatedBy: String?
)

/**
 * Response for workbench data
 */
data class WorkbenchResponse(
    val items: List<WorkbenchItem>,
    val count: Long,
    val offset: Int,
    val limit: Int,
    val stats: WorkbenchStats
)

/**
 * Stats for the workbench header
 */
data class WorkbenchStats(
    val totalVariants: Long,
    val variantsWithDiscount: Long,
    val averageMargin: BigDecimal?,
    val activeRules: Int
)

// =============================================================================
// Bulk Update DTOs
// =============================================================================

/**
 * Request to update a single price
 */
data class PriceUpdate(
    val variantId: String,
    val amount: Int, // In cents
    val compareAtPrice: Int? = null // In cents
)

/**
 * Request for bulk price update
 */
data class BulkPriceUpdateRequest(
    val updates: List<PriceUpdate>
)

/**
 * Result of a bulk price update
 */
data class BulkPriceUpdateResult(
    val successCount: Int,
    val failureCount: Int,
    val errors: List<BulkUpdateError>,
    val changes: List<PriceChangeLogDto>
)

data class BulkUpdateError(
    val variantId: String,
    val message: String
)

// =============================================================================
// Simulation DTOs
// =============================================================================

/**
 * Request to simulate price changes
 */
data class PriceSimulationRequest(
    val variantIds: List<String>,
    val adjustmentType: AdjustmentType,
    val adjustmentValue: BigDecimal,
    val roundingStrategy: RoundingStrategy? = null
)

enum class RoundingStrategy {
    NONE,
    ROUND_TO_99, // 9.99, 19.99, etc.
    ROUND_TO_95, // 9.95, 19.95, etc.
    ROUND_TO_NEAREST_POUND, // 10.00, 20.00, etc.
    ROUND_TO_NEAREST_50P // 9.50, 10.00, etc.
}

/**
 * Result of price simulation
 */
data class PriceSimulationResult(
    val simulations: List<SimulatedPriceChange>,
    val summary: SimulationSummary
)

data class SimulatedPriceChange(
    val variantId: String,
    val productTitle: String,
    val variantTitle: String,
    val currentPrice: Int,
    val newPrice: Int,
    val priceDifference: Int,
    val percentageChange: BigDecimal
)

data class SimulationSummary(
    val totalVariants: Int,
    val totalCurrentValue: Int,
    val totalNewValue: Int,
    val averageChange: BigDecimal,
    val maxIncrease: SimulatedPriceChange?,
    val maxDecrease: SimulatedPriceChange?
)

// =============================================================================
// Pricing Rule DTOs
// =============================================================================

/**
 * Response DTO for pricing rule
 */
data class PricingRuleDto(
    val id: String,
    val name: String,
    val description: String?,
    val type: String,
    val typeDisplay: String,
    val status: String,
    val statusDisplay: String,
    val priority: Int,
    val config: Map<String, Any>,
    val targetType: String?,
    val targetTypeDisplay: String?,
    val targetIds: List<String>,
    val targetCount: Int,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val createdBy: String?
) {
    companion object {
        fun from(rule: PricingRule): PricingRuleDto {
            return PricingRuleDto(
                id = rule.id,
                name = rule.name,
                description = rule.description,
                type = rule.type.name,
                typeDisplay = rule.type.displayName,
                status = rule.status.name,
                statusDisplay = rule.status.displayName,
                priority = rule.priority,
                config = rule.config,
                targetType = rule.targetType?.name,
                targetTypeDisplay = rule.targetType?.displayName,
                targetIds = rule.targetIds,
                targetCount = rule.targetIds.size,
                startAt = rule.startAt?.atOffset(ZoneOffset.UTC),
                endAt = rule.endAt?.atOffset(ZoneOffset.UTC),
                isActive = rule.isActive(),
                createdAt = rule.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = rule.updatedAt.atOffset(ZoneOffset.UTC),
                createdBy = rule.createdBy
            )
        }
    }
}

/**
 * Request to create a pricing rule
 */
data class CreatePricingRuleRequest(
    val name: String,
    val description: String? = null,
    val type: String,
    val config: Map<String, Any>,
    val targetType: String? = null,
    val targetIds: List<String>? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val priority: Int = 0,
    val activateImmediately: Boolean = false
)

/**
 * Request to update a pricing rule
 */
data class UpdatePricingRuleRequest(
    val name: String? = null,
    val description: String? = null,
    val config: Map<String, Any>? = null,
    val targetType: String? = null,
    val targetIds: List<String>? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val priority: Int? = null
)

/**
 * Response for pricing rules list
 */
data class PricingRulesResponse(
    val rules: List<PricingRuleDto>,
    val count: Int
)

/**
 * Response for single pricing rule
 */
data class PricingRuleResponse(
    val rule: PricingRuleDto
)

/**
 * Request to apply a rule to products
 */
data class ApplyRuleRequest(
    val variantIds: List<String>? = null, // If null, applies to all matching targets
    val preview: Boolean = false
)

/**
 * Result of applying a rule
 */
data class ApplyRuleResult(
    val appliedCount: Int,
    val skippedCount: Int,
    val changes: List<PriceChangeLogDto>
)

// =============================================================================
// Price Change Log DTOs
// =============================================================================

/**
 * DTO for price change log entry
 */
data class PriceChangeLogDto(
    val id: String,
    val variantId: String,
    val productId: String,
    val productTitle: String?,
    val variantTitle: String?,
    val sku: String?,
    val previousPrice: Int?,
    val newPrice: Int,
    val priceDifference: Int,
    val percentageChange: BigDecimal?,
    val currencyCode: String,
    val changeType: String,
    val changeTypeDisplay: String,
    val changeSource: String?,
    val ruleId: String?,
    val ruleName: String?,
    val changedBy: String?,
    val changedByName: String?,
    val changedAt: OffsetDateTime
) {
    companion object {
        fun from(log: PriceChangeLog): PriceChangeLogDto {
            val prevAmountCents = log.previousAmount?.multiply(BigDecimal(100))?.toInt()
            val newAmountCents = log.newAmount.multiply(BigDecimal(100)).toInt()

            return PriceChangeLogDto(
                id = log.id,
                variantId = log.variantId,
                productId = log.productId,
                productTitle = log.productTitle,
                variantTitle = log.variantTitle,
                sku = log.sku,
                previousPrice = prevAmountCents,
                newPrice = newAmountCents,
                priceDifference = newAmountCents - (prevAmountCents ?: 0),
                percentageChange = log.getPercentageChange(),
                currencyCode = log.currencyCode,
                changeType = log.changeType.name,
                changeTypeDisplay = log.changeType.displayName,
                changeSource = log.changeSource,
                ruleId = log.ruleId,
                ruleName = log.ruleName,
                changedBy = log.changedBy,
                changedByName = log.changedByName,
                changedAt = log.changedAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

/**
 * Response for price history
 */
data class PriceHistoryResponse(
    val changes: List<PriceChangeLogDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

// =============================================================================
// Activity Feed DTOs
// =============================================================================

/**
 * Activity item for real-time feed
 */
data class PricingActivityItem(
    val id: String,
    val type: String, // PRICE_CHANGE, BULK_UPDATE, RULE_APPLIED, RULE_CREATED, etc.
    val message: String,
    val details: Map<String, Any>?,
    val timestamp: OffsetDateTime,
    val actor: String?
)

/**
 * Response for activity feed
 */
data class PricingActivityResponse(
    val activities: List<PricingActivityItem>,
    val count: Long
)
