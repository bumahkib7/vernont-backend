package com.vernont.domain.promotion

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "discount_redemption",
    indexes = [
        Index(name = "idx_discount_redemption_promotion", columnList = "promotion_id"),
        Index(name = "idx_discount_redemption_customer", columnList = "customer_id"),
        Index(name = "idx_discount_redemption_order", columnList = "order_id"),
        Index(name = "idx_discount_redemption_date", columnList = "redeemed_at")
    ]
)
class DiscountRedemption : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    var promotion: Promotion? = null

    @Column(name = "discount_id")
    var discountId: String? = null

    @Column(name = "customer_id")
    var customerId: String? = null

    @Column(name = "order_id")
    var orderId: String? = null

    @Column(name = "code_used")
    var codeUsed: String? = null

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: BigDecimal = BigDecimal.ZERO

    @Column(name = "order_subtotal")
    var orderSubtotal: BigDecimal? = null

    @Column(name = "redeemed_at", nullable = false)
    var redeemedAt: Instant = Instant.now()



    companion object {
        fun create(
            promotion: Promotion,
            customerId: String?,
            orderId: String?,
            discountAmount: BigDecimal,
            orderSubtotal: BigDecimal?
        ): DiscountRedemption {
            return DiscountRedemption().apply {
                this.promotion = promotion
                this.codeUsed = promotion.code
                this.customerId = customerId
                this.orderId = orderId
                this.discountAmount = discountAmount
                this.orderSubtotal = orderSubtotal
                this.redeemedAt = Instant.now()
            }
        }
    }
}
