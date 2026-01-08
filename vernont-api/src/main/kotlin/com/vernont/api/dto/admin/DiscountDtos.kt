package com.vernont.api.dto.admin

import com.vernont.domain.promotion.*
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// =============================================================================
// Promotion List & Detail DTOs
// =============================================================================

data class PromotionListItem(
    val id: String,
    val name: String?,
    val code: String,
    val type: String,
    val value: Double,
    val description: String?,
    val startsAt: OffsetDateTime?,
    val endsAt: OffsetDateTime?,
    val usageCount: Int,
    val usageLimit: Int?,
    val isActive: Boolean,
    val isDisabled: Boolean,
    val redemptionCount: Long,
    val totalDiscountGiven: BigDecimal,
    val status: String,
    val createdAt: OffsetDateTime
) {
    companion object {
        fun from(promotion: Promotion, redemptionCount: Long = 0, totalDiscountGiven: BigDecimal = BigDecimal.ZERO): PromotionListItem {
            val status = when {
                promotion.isDeleted() -> "DELETED"
                promotion.isDisabled -> "DISABLED"
                !promotion.isActive -> "INACTIVE"
                promotion.hasEnded() -> "EXPIRED"
                !promotion.hasStarted() -> "SCHEDULED"
                promotion.hasReachedUsageLimit() -> "LIMIT_REACHED"
                else -> "ACTIVE"
            }

            return PromotionListItem(
                id = promotion.id,
                name = promotion.name,
                code = promotion.code,
                type = promotion.type.name,
                value = promotion.value,
                description = promotion.description,
                startsAt = promotion.startsAt?.atOffset(ZoneOffset.UTC),
                endsAt = promotion.endsAt?.atOffset(ZoneOffset.UTC),
                usageCount = promotion.usageCount,
                usageLimit = promotion.usageLimit,
                isActive = promotion.isActive,
                isDisabled = promotion.isDisabled,
                redemptionCount = redemptionCount,
                totalDiscountGiven = totalDiscountGiven,
                status = status,
                createdAt = promotion.createdAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

data class PromotionDetail(
    val id: String,
    val name: String?,
    val code: String,
    val type: String,
    val value: Double,
    val description: String?,
    val startsAt: OffsetDateTime?,
    val endsAt: OffsetDateTime?,
    val usageLimit: Int?,
    val usageCount: Int,
    val customerUsageLimit: Int,
    val minimumAmount: BigDecimal?,
    val maximumDiscount: BigDecimal?,
    val isStackable: Boolean,
    val priority: Int,
    val isActive: Boolean,
    val isDisabled: Boolean,
    val buyQuantity: Int?,
    val getQuantity: Int?,
    val getDiscountValue: Double?,
    val rules: List<PromotionRuleDto>,
    val stats: PromotionStats,
    val status: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        fun from(
            promotion: Promotion,
            rules: List<PromotionRuleDto>,
            stats: PromotionStats
        ): PromotionDetail {
            val status = when {
                promotion.isDeleted() -> "DELETED"
                promotion.isDisabled -> "DISABLED"
                !promotion.isActive -> "INACTIVE"
                promotion.hasEnded() -> "EXPIRED"
                !promotion.hasStarted() -> "SCHEDULED"
                promotion.hasReachedUsageLimit() -> "LIMIT_REACHED"
                else -> "ACTIVE"
            }

            return PromotionDetail(
                id = promotion.id,
                name = promotion.name,
                code = promotion.code,
                type = promotion.type.name,
                value = promotion.value,
                description = promotion.description,
                startsAt = promotion.startsAt?.atOffset(ZoneOffset.UTC),
                endsAt = promotion.endsAt?.atOffset(ZoneOffset.UTC),
                usageLimit = promotion.usageLimit,
                usageCount = promotion.usageCount,
                customerUsageLimit = promotion.customerUsageLimit,
                minimumAmount = promotion.minimumAmount,
                maximumDiscount = promotion.maximumDiscount,
                isStackable = promotion.isStackable,
                priority = promotion.priority,
                isActive = promotion.isActive,
                isDisabled = promotion.isDisabled,
                buyQuantity = promotion.buyQuantity,
                getQuantity = promotion.getQuantity,
                getDiscountValue = promotion.getDiscountValue,
                rules = rules,
                stats = stats,
                status = status,
                createdAt = promotion.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = promotion.updatedAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

data class PromotionRuleDto(
    val id: String,
    val type: String,
    val value: String?,
    val description: String?,
    val attribute: String?,
    val operator: String?
) {
    companion object {
        fun from(rule: PromotionRule): PromotionRuleDto {
            return PromotionRuleDto(
                id = rule.id,
                type = rule.type.name,
                value = rule.value,
                description = rule.description,
                attribute = rule.attribute,
                operator = rule.operator
            )
        }
    }
}

data class PromotionStats(
    val redemptionCount: Long,
    val totalDiscountGiven: BigDecimal,
    val averageOrderValue: BigDecimal?,
    val redemptionsToday: Long,
    val redemptionsThisWeek: Long
)

// =============================================================================
// Response DTOs
// =============================================================================

data class PromotionsListResponse(
    val items: List<PromotionListItem>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class PromotionResponse(
    val promotion: PromotionDetail
)

data class DiscountStatsResponse(
    val totalPromotions: Long,
    val activePromotions: Long,
    val scheduledPromotions: Long,
    val expiredPromotions: Long,
    val disabledPromotions: Long,
    val totalRedemptions: Long,
    val totalDiscountGiven: BigDecimal,
    val redemptionsToday: Long,
    val redemptionsThisWeek: Long,
    val topPerformingCodes: List<TopPerformingCode>
)

data class TopPerformingCode(
    val promotionId: String,
    val code: String,
    val redemptionCount: Long,
    val totalDiscount: BigDecimal
)

// =============================================================================
// Request DTOs
// =============================================================================

data class CreatePromotionRequest(
    val name: String?,
    val code: String,
    val type: String,
    val value: Double,
    val description: String?,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val usageLimit: Int?,
    val customerUsageLimit: Int?,
    val minimumAmount: BigDecimal?,
    val maximumDiscount: BigDecimal?,
    val isStackable: Boolean?,
    val priority: Int?,
    val buyQuantity: Int?,
    val getQuantity: Int?,
    val getDiscountValue: Double?,
    val rules: List<CreatePromotionRuleRequest>?,
    val activateImmediately: Boolean?
)

data class CreatePromotionRuleRequest(
    val type: String,
    val value: String?,
    val description: String?,
    val attribute: String?,
    val operator: String?
)

data class UpdatePromotionRequest(
    val name: String?,
    val description: String?,
    val value: Double?,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val usageLimit: Int?,
    val customerUsageLimit: Int?,
    val minimumAmount: BigDecimal?,
    val maximumDiscount: BigDecimal?,
    val isStackable: Boolean?,
    val priority: Int?,
    val buyQuantity: Int?,
    val getQuantity: Int?,
    val getDiscountValue: Double?,
    val rules: List<CreatePromotionRuleRequest>?
)

data class BulkDiscountRequest(
    val ids: List<String>,
    val action: String // ACTIVATE, DEACTIVATE, DELETE, EXTEND
)

data class BulkDiscountResult(
    val successCount: Int,
    val failureCount: Int,
    val errors: List<BulkDiscountError>
)

data class BulkDiscountError(
    val promotionId: String,
    val message: String
)

// =============================================================================
// Redemption DTOs
// =============================================================================

data class RedemptionListItem(
    val id: String,
    val promotionId: String,
    val promotionCode: String,
    val customerId: String?,
    val orderId: String?,
    val discountAmount: BigDecimal,
    val orderSubtotal: BigDecimal?,
    val redeemedAt: OffsetDateTime
) {
    companion object {
        fun from(redemption: DiscountRedemption): RedemptionListItem {
            return RedemptionListItem(
                id = redemption.id,
                promotionId = redemption.promotion?.id ?: "",
                promotionCode = redemption.codeUsed ?: "",
                customerId = redemption.customerId,
                orderId = redemption.orderId,
                discountAmount = redemption.discountAmount,
                orderSubtotal = redemption.orderSubtotal,
                redeemedAt = redemption.redeemedAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

data class RedemptionsResponse(
    val items: List<RedemptionListItem>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

// =============================================================================
// Activity DTOs
// =============================================================================

data class DiscountActivityItem(
    val id: String,
    val promotionId: String?,
    val promotionCode: String?,
    val activityType: String,
    val description: String?,
    val actorId: String?,
    val actorName: String?,
    val timestamp: OffsetDateTime
) {
    companion object {
        fun from(activity: DiscountActivity): DiscountActivityItem {
            return DiscountActivityItem(
                id = activity.id,
                promotionId = activity.promotion?.id,
                promotionCode = activity.promotion?.code,
                activityType = activity.activityType.name,
                description = activity.description,
                actorId = activity.actorId,
                actorName = activity.actorName,
                timestamp = activity.createdAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

data class DiscountActivityResponse(
    val items: List<DiscountActivityItem>,
    val count: Long
)

// =============================================================================
// Code Generation
// =============================================================================

data class GeneratedCodeResponse(
    val code: String
)
