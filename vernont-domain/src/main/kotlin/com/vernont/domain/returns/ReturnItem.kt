package com.vernont.domain.returns

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * Represents an item being returned as part of a Return request.
 */
@Entity
@Table(
    name = "return_item",
    indexes = [
        Index(name = "idx_return_item_return_id", columnList = "return_id"),
        Index(name = "idx_return_item_order_line_item_id", columnList = "order_line_item_id"),
        Index(name = "idx_return_item_variant_id", columnList = "variant_id")
    ]
)
class ReturnItem : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    var returnRequest: Return? = null

    @Column(name = "order_line_item_id", nullable = false)
    var orderLineItemId: String = ""

    @Column(name = "variant_id")
    var variantId: String? = null

    @Column(nullable = false)
    var title: String = ""

    @Column
    var description: String? = null

    @Column
    var thumbnail: String? = null

    @Column(nullable = false)
    var quantity: Int = 1

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    var unitPrice: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false)
    var total: BigDecimal = BigDecimal.ZERO

    /**
     * Recalculate the total based on quantity and unit price
     */
    fun recalculateTotal() {
        total = unitPrice.multiply(BigDecimal(quantity))
    }

    companion object {
        fun create(
            orderLineItemId: String,
            variantId: String?,
            title: String,
            description: String?,
            thumbnail: String?,
            quantity: Int,
            unitPrice: BigDecimal
        ): ReturnItem {
            return ReturnItem().apply {
                this.orderLineItemId = orderLineItemId
                this.variantId = variantId
                this.title = title
                this.description = description
                this.thumbnail = thumbnail
                this.quantity = quantity
                this.unitPrice = unitPrice
                this.recalculateTotal()
            }
        }
    }
}
