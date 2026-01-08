package com.vernont.domain.promotion

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

@Entity
@Table(
    name = "discount",
    indexes = [
        Index(name = "idx_discount_order_id", columnList = "order_id"),
        Index(name = "idx_discount_cart_id", columnList = "cart_id"),
        Index(name = "idx_discount_promotion_id", columnList = "promotion_id"),
        Index(name = "idx_discount_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Discount.full",
    attributeNodes = [
        NamedAttributeNode("promotion")
    ]
)
@NamedEntityGraph(
    name = "Discount.withPromotion",
    attributeNodes = [
        NamedAttributeNode("promotion")
    ]
)
class Discount : BaseEntity() {

    @Column(name = "order_id")
    var orderId: String? = null

    @Column(name = "cart_id")
    var cartId: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    var promotion: Promotion? = null

    @NotBlank
    @Column(nullable = false)
    var code: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: DiscountType = DiscountType.PERCENTAGE

    @Column(nullable = false)
    var value: Double = 0.0

    @Column(nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "is_applied", nullable = false)
    var isApplied: Boolean = false

    @Column(name = "is_valid", nullable = false)
    var isValid: Boolean = true

    @Column(columnDefinition = "TEXT")
    var reason: String? = null

    fun apply() {
        require(isValid) { "Cannot apply invalid discount" }
        this.isApplied = true
    }

    fun invalidate(reason: String? = null) {
        this.isValid = false
        this.isApplied = false
        this.reason = reason
    }

    fun calculateAmount(subtotal: BigDecimal): BigDecimal {
        return when (type) {
            DiscountType.PERCENTAGE -> subtotal.multiply(BigDecimal.valueOf(value / 100.0))
            DiscountType.FIXED -> BigDecimal.valueOf(value)
            DiscountType.FREE_SHIPPING -> BigDecimal.ZERO
        }
    }

    fun updateAmount(subtotal: BigDecimal) {
        this.amount = calculateAmount(subtotal)
    }

    fun isPercentage(): Boolean {
        return type == DiscountType.PERCENTAGE
    }

    fun isFixed(): Boolean {
        return type == DiscountType.FIXED
    }

    fun isFreeShipping(): Boolean {
        return type == DiscountType.FREE_SHIPPING
    }

    companion object {
        fun fromPromotion(promotion: Promotion): Discount {
            return Discount().apply {
                this.promotion = promotion
                this.code = promotion.code
                this.type = when (promotion.type) {
                    PromotionType.PERCENTAGE -> DiscountType.PERCENTAGE
                    PromotionType.FIXED -> DiscountType.FIXED
                    PromotionType.FREE_SHIPPING -> DiscountType.FREE_SHIPPING
                    else -> {
                        throw IllegalArgumentException("Unsupported promotion type: ${promotion.type}")
                    }
                }
                this.value = promotion.value
            }
        }
    }
}

enum class DiscountType {
    PERCENTAGE,
    FIXED,
    FREE_SHIPPING
}
