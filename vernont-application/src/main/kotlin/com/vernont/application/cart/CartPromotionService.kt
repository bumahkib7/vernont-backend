package com.vernont.application.cart

import com.vernont.domain.cart.Cart
import com.vernont.domain.promotion.Discount
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.promotion.DiscountRepository
import com.vernont.repository.promotion.PromotionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Result of applying promo codes to a cart
 */
data class ApplyPromoCodesResult(
    val totalDiscount: BigDecimal,
    val appliedCodes: List<String>,
    val invalidCodes: List<String>
)

/**
 * Service for handling cart promotion/discount operations.
 * Uses proper transaction management for pessimistic locking operations.
 */
@Service
class CartPromotionService(
    private val cartRepository: CartRepository,
    private val promotionRepository: PromotionRepository,
    private val discountRepository: DiscountRepository
) {

    /**
     * Apply promo codes to a cart.
     * This method handles the transactional logic for applying promo codes,
     * including pessimistic locking to prevent race conditions.
     *
     * @param cartId The cart ID to apply promo codes to
     * @param promoCodes The list of promo codes to apply
     * @return Result containing total discount and which codes were applied/invalid
     */
    @Transactional
    fun applyPromoCodes(cartId: String, promoCodes: List<String>): ApplyPromoCodesResult {
        logger.info { "Applying ${promoCodes.size} promo codes to cart: $cartId" }

        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        // Remove existing discounts first - use atomic decrement
        val existingDiscounts = discountRepository.findByCartIdAndDeletedAtIsNull(cart.id)
        existingDiscounts.forEach { discount ->
            discount.promotion?.let { promotion ->
                // Use atomic decrement to prevent race conditions
                promotionRepository.atomicDecrementUsage(promotion.id)
            }
            discountRepository.delete(discount)
        }

        var totalDiscount = BigDecimal.ZERO
        val appliedCodes = mutableListOf<String>()
        val invalidCodes = mutableListOf<String>()

        promoCodes.forEach { code ->
            // Use pessimistic lock to prevent concurrent applications
            val promotion = promotionRepository.findByCodeForApplication(code)

            if (promotion != null && promotion.isValid() && promotion.appliesToRegion(cart.regionId)) {
                // Atomic increment - returns 0 if limit reached
                val incrementResult = promotionRepository.atomicIncrementUsage(promotion.id)
                if (incrementResult == 0) {
                    logger.warn { "Promo code $code has reached usage limit" }
                    invalidCodes.add(code)
                    return@forEach
                }

                // Create and apply discount
                val discount = Discount.fromPromotion(promotion).apply {
                    this.cartId = cart.id
                    this.isValid = true
                    this.isApplied = false
                }
                discount.updateAmount(cart.subtotal)
                val savedDiscount = discountRepository.save(discount)
                savedDiscount.apply()
                discountRepository.save(savedDiscount)

                totalDiscount = totalDiscount.add(savedDiscount.amount)
                appliedCodes.add(code)
                logger.info { "Applied promo code: $code, discount: ${savedDiscount.amount}" }
            } else {
                logger.warn { "Invalid or inapplicable promo code: $code" }
                invalidCodes.add(code)
            }
        }

        // Update cart discount totals
        cart.discount = totalDiscount
        cart.discountTotal = totalDiscount
        cartRepository.save(cart)

        logger.info { "Total discount applied: $totalDiscount, applied: $appliedCodes, invalid: $invalidCodes" }

        return ApplyPromoCodesResult(
            totalDiscount = totalDiscount,
            appliedCodes = appliedCodes,
            invalidCodes = invalidCodes
        )
    }

    /**
     * Remove all promo codes from a cart.
     */
    @Transactional
    fun removePromoCodes(cartId: String) {
        logger.info { "Removing all promo codes from cart: $cartId" }

        val cart = cartRepository.findByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        val existingDiscounts = discountRepository.findByCartIdAndDeletedAtIsNull(cart.id)
        existingDiscounts.forEach { discount ->
            discount.promotion?.let { promotion ->
                promotionRepository.atomicDecrementUsage(promotion.id)
            }
            discountRepository.delete(discount)
        }

        cart.discount = BigDecimal.ZERO
        cart.discountTotal = BigDecimal.ZERO
        cartRepository.save(cart)

        logger.info { "Removed all promo codes from cart: $cartId" }
    }

    /**
     * Get the current discount amount for a cart.
     */
    @Transactional(readOnly = true)
    fun getCartDiscount(cartId: String): BigDecimal {
        val cart = cartRepository.findByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")
        return cart.discount
    }
}
