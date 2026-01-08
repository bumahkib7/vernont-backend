package com.vernont.api.dto.admin

import com.vernont.domain.giftcard.GiftCard
import com.vernont.domain.giftcard.GiftCardStatus
import java.math.BigDecimal
import java.time.Instant

// ============================================================================
// List Response
// ============================================================================

data class GiftCardsListResponse(
    val items: List<GiftCardListItem>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class GiftCardListItem(
    val id: String,
    val code: String,
    val status: String,
    val statusDisplay: String,
    val initialAmount: Int,
    val remainingAmount: Int,
    val formattedInitialAmount: String,
    val formattedBalance: String,
    val currencyCode: String,
    val recipientName: String?,
    val recipientEmail: String?,
    val expiresAt: Instant?,
    val isExpired: Boolean,
    val canRedeem: Boolean,
    val createdAt: Instant?
) {
    companion object {
        fun from(giftCard: GiftCard): GiftCardListItem {
            return GiftCardListItem(
                id = giftCard.id,
                code = giftCard.code,
                status = giftCard.status.name,
                statusDisplay = giftCard.status.displayName,
                initialAmount = giftCard.initialAmount,
                remainingAmount = giftCard.remainingAmount,
                formattedInitialAmount = giftCard.getFormattedInitialAmount(),
                formattedBalance = giftCard.getFormattedBalance(),
                currencyCode = giftCard.currencyCode,
                recipientName = giftCard.recipientName,
                recipientEmail = giftCard.recipientEmail,
                expiresAt = giftCard.expiresAt,
                isExpired = giftCard.isExpired(),
                canRedeem = giftCard.canRedeem(),
                createdAt = giftCard.createdAt
            )
        }
    }
}

// ============================================================================
// Detail Response
// ============================================================================

data class GiftCardResponse(
    val giftCard: GiftCardDetail
)

data class GiftCardDetail(
    val id: String,
    val code: String,
    val status: String,
    val statusDisplay: String,
    val initialAmount: Int,
    val remainingAmount: Int,
    val formattedInitialAmount: String,
    val formattedBalance: String,
    val currencyCode: String,
    val recipientName: String?,
    val recipientEmail: String?,
    val message: String?,
    val expiresAt: Instant?,
    val isExpired: Boolean,
    val canRedeem: Boolean,
    val issuedToCustomerId: String?,
    val issuedByUserId: String?,
    val redeemedByCustomerId: String?,
    val firstRedeemedAt: Instant?,
    val fullyRedeemedAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?
) {
    companion object {
        fun from(giftCard: GiftCard): GiftCardDetail {
            return GiftCardDetail(
                id = giftCard.id,
                code = giftCard.code,
                status = giftCard.status.name,
                statusDisplay = giftCard.status.displayName,
                initialAmount = giftCard.initialAmount,
                remainingAmount = giftCard.remainingAmount,
                formattedInitialAmount = giftCard.getFormattedInitialAmount(),
                formattedBalance = giftCard.getFormattedBalance(),
                currencyCode = giftCard.currencyCode,
                recipientName = giftCard.recipientName,
                recipientEmail = giftCard.recipientEmail,
                message = giftCard.message,
                expiresAt = giftCard.expiresAt,
                isExpired = giftCard.isExpired(),
                canRedeem = giftCard.canRedeem(),
                issuedToCustomerId = giftCard.issuedToCustomerId,
                issuedByUserId = giftCard.issuedByUserId,
                redeemedByCustomerId = giftCard.redeemedByCustomerId,
                firstRedeemedAt = giftCard.firstRedeemedAt,
                fullyRedeemedAt = giftCard.fullyRedeemedAt,
                createdAt = giftCard.createdAt,
                updatedAt = giftCard.updatedAt
            )
        }
    }
}

// ============================================================================
// Create/Update Requests
// ============================================================================

data class CreateGiftCardRequest(
    val amount: Int, // Amount in minor units (cents/pence)
    val currencyCode: String = "GBP",
    val recipientName: String?,
    val recipientEmail: String?,
    val message: String?,
    val expiresInDays: Int? = null, // null = never expires
    val sendEmail: Boolean = true
)

data class UpdateGiftCardRequest(
    val recipientName: String?,
    val recipientEmail: String?,
    val message: String?,
    val expiresAt: Instant?
)

data class AdjustBalanceRequest(
    val amount: Int, // Positive to add, negative to deduct
    val reason: String?
)

// ============================================================================
// Stats Response
// ============================================================================

data class GiftCardStatsResponse(
    val totalGiftCards: Long,
    val activeGiftCards: Long,
    val fullyRedeemedGiftCards: Long,
    val expiredGiftCards: Long,
    val disabledGiftCards: Long,
    val totalIssuedValue: Long, // In minor units
    val totalRemainingValue: Long, // In minor units
    val totalRedeemedValue: Long, // In minor units
    val formattedIssuedValue: String,
    val formattedRemainingValue: String,
    val formattedRedeemedValue: String,
    val expiringThisWeek: Int,
    val issuedThisWeek: Int
)

// ============================================================================
// Bulk Operations
// ============================================================================

data class BulkGiftCardRequest(
    val ids: List<String>,
    val action: String // DISABLE, ENABLE, DELETE
)

data class BulkGiftCardResult(
    val successCount: Int,
    val failureCount: Int,
    val errors: List<BulkGiftCardError>
)

data class BulkGiftCardError(
    val giftCardId: String,
    val error: String
)
