package com.vernont.domain.order

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/**
 * Tracks gift cards used in an order.
 * Supports partial redemption (a gift card can be used across multiple orders).
 */
@Entity
@Table(
    name = "order_gift_card",
    indexes = [
        Index(name = "idx_order_gift_card_order_id", columnList = "order_id"),
        Index(name = "idx_order_gift_card_gift_card_id", columnList = "gift_card_id")
    ]
)
class OrderGiftCard : BaseEntity() {

    @Column(name = "order_id", nullable = false, length = 36)
    var orderId: String = ""

    @Column(name = "gift_card_id", nullable = false, length = 36)
    var giftCardId: String = ""

    /** Gift card code (for display purposes) */
    @Column(name = "gift_card_code", nullable = false, length = 19)
    var giftCardCode: String = ""

    /** Amount redeemed from this gift card for this order (in minor currency units, e.g. pence) */
    @Column(name = "amount_redeemed", nullable = false)
    var amountRedeemed: Int = 0

    /** Currency code */
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = "GBP"

    /** Balance remaining on the gift card after this redemption */
    @Column(name = "balance_after", nullable = false)
    var balanceAfter: Int = 0

    /** When the redemption occurred */
    @Column(name = "redeemed_at", nullable = false)
    var redeemedAt: Instant = Instant.now()

    companion object {
        fun create(
            orderId: String,
            giftCardId: String,
            giftCardCode: String,
            amountRedeemed: Int,
            currencyCode: String,
            balanceAfter: Int
        ): OrderGiftCard {
            return OrderGiftCard().apply {
                this.orderId = orderId
                this.giftCardId = giftCardId
                this.giftCardCode = giftCardCode
                this.amountRedeemed = amountRedeemed
                this.currencyCode = currencyCode
                this.balanceAfter = balanceAfter
                this.redeemedAt = Instant.now()
            }
        }
    }

    /**
     * Get the redeemed amount as BigDecimal (in currency units, e.g. pounds)
     */
    fun getAmountRedeemedDecimal(): BigDecimal {
        return BigDecimal(amountRedeemed).divide(BigDecimal(100))
    }
}
