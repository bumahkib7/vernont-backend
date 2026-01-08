package com.vernont.application.giftcard

import com.vernont.domain.giftcard.GiftCard
import com.vernont.domain.order.OrderGiftCard
import com.vernont.repository.giftcard.GiftCardRepository
import com.vernont.repository.order.OrderGiftCardRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Result of validating a gift card for order application
 */
data class GiftCardValidationResult(
    val valid: Boolean,
    val giftCard: GiftCard? = null,
    val errorMessage: String? = null,
    val availableBalance: Int = 0
)

/**
 * Result of applying a gift card to an order
 */
data class GiftCardRedemptionResult(
    val success: Boolean,
    val giftCardId: String? = null,
    val amountRedeemed: Int = 0,
    val remainingBalance: Int = 0,
    val errorMessage: String? = null
)

@Service
class GiftCardOrderService(
    private val giftCardRepository: GiftCardRepository,
    private val orderGiftCardRepository: OrderGiftCardRepository
) {

    /**
     * Validate a gift card code for use in an order
     */
    @Transactional(readOnly = true)
    fun validateGiftCard(code: String, currencyCode: String): GiftCardValidationResult {
        val normalizedCode = code.uppercase().replace("-", "").chunked(4).joinToString("-")

        logger.info { "Validating gift card: $normalizedCode" }

        val giftCard = giftCardRepository.findByCodeIgnoreCase(normalizedCode)
            ?: return GiftCardValidationResult(
                valid = false,
                errorMessage = "Gift card not found"
            )

        if (!giftCard.canRedeem()) {
            val reason = when {
                giftCard.isExpired() -> "Gift card has expired"
                giftCard.remainingAmount <= 0 -> "Gift card has no remaining balance"
                else -> "Gift card is not active"
            }
            return GiftCardValidationResult(
                valid = false,
                giftCard = giftCard,
                errorMessage = reason
            )
        }

        if (!giftCard.currencyCode.equals(currencyCode, ignoreCase = true)) {
            return GiftCardValidationResult(
                valid = false,
                giftCard = giftCard,
                errorMessage = "Gift card currency (${giftCard.currencyCode}) does not match order currency ($currencyCode)"
            )
        }

        return GiftCardValidationResult(
            valid = true,
            giftCard = giftCard,
            availableBalance = giftCard.remainingAmount
        )
    }

    /**
     * Apply a gift card to an order
     * @param code Gift card code
     * @param orderId Order ID
     * @param orderTotalCents Order total in minor currency units (cents/pence)
     * @param currencyCode Currency code
     * @param customerId Customer performing the redemption
     * @return Result containing amount redeemed and remaining balance
     */
    @Transactional
    fun applyGiftCardToOrder(
        code: String,
        orderId: String,
        orderTotalCents: Int,
        currencyCode: String,
        customerId: String
    ): GiftCardRedemptionResult {
        val normalizedCode = code.uppercase().replace("-", "").chunked(4).joinToString("-")

        logger.info { "Applying gift card $normalizedCode to order $orderId, orderTotal=$orderTotalCents cents" }

        // Validate first
        val validation = validateGiftCard(normalizedCode, currencyCode)
        if (!validation.valid) {
            return GiftCardRedemptionResult(
                success = false,
                errorMessage = validation.errorMessage
            )
        }

        val giftCard = validation.giftCard!!

        // Calculate amount to redeem (minimum of gift card balance and order total)
        val amountToRedeem = minOf(giftCard.remainingAmount, orderTotalCents)

        if (amountToRedeem <= 0) {
            return GiftCardRedemptionResult(
                success = false,
                errorMessage = "No amount to redeem"
            )
        }

        // Redeem from gift card
        val actualRedeemed = giftCard.redeem(amountToRedeem, customerId)
        giftCardRepository.save(giftCard)

        // Create order-gift card record
        val orderGiftCard = OrderGiftCard.create(
            orderId = orderId,
            giftCardId = giftCard.id,
            giftCardCode = giftCard.code,
            amountRedeemed = actualRedeemed,
            currencyCode = currencyCode,
            balanceAfter = giftCard.remainingAmount
        )
        orderGiftCardRepository.save(orderGiftCard)

        logger.info {
            "Gift card $normalizedCode redeemed: ${actualRedeemed} cents for order $orderId. " +
            "Remaining balance: ${giftCard.remainingAmount} cents"
        }

        return GiftCardRedemptionResult(
            success = true,
            giftCardId = giftCard.id,
            amountRedeemed = actualRedeemed,
            remainingBalance = giftCard.remainingAmount
        )
    }

    /**
     * Get gift cards applied to an order
     */
    @Transactional(readOnly = true)
    fun getGiftCardsForOrder(orderId: String): List<OrderGiftCard> {
        return orderGiftCardRepository.findByOrderId(orderId)
    }

    /**
     * Refund a gift card redemption (e.g., when order is cancelled)
     * Adds the redeemed amount back to the gift card balance
     */
    @Transactional
    fun refundGiftCardRedemption(orderId: String): List<GiftCardRedemptionResult> {
        val orderGiftCards = orderGiftCardRepository.findByOrderId(orderId)

        return orderGiftCards.map { orderGiftCard ->
            val giftCard = giftCardRepository.findById(orderGiftCard.giftCardId).orElse(null)

            if (giftCard == null) {
                logger.warn { "Gift card ${orderGiftCard.giftCardId} not found for refund" }
                GiftCardRedemptionResult(
                    success = false,
                    giftCardId = orderGiftCard.giftCardId,
                    errorMessage = "Gift card not found"
                )
            } else {
                // Add balance back
                giftCard.addBalance(orderGiftCard.amountRedeemed)
                giftCardRepository.save(giftCard)

                logger.info {
                    "Refunded ${orderGiftCard.amountRedeemed} cents to gift card ${giftCard.code}. " +
                    "New balance: ${giftCard.remainingAmount} cents"
                }

                GiftCardRedemptionResult(
                    success = true,
                    giftCardId = giftCard.id,
                    amountRedeemed = orderGiftCard.amountRedeemed,
                    remainingBalance = giftCard.remainingAmount
                )
            }
        }
    }
}
