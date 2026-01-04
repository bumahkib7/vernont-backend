package com.vernont.domain.order

import com.fasterxml.jackson.annotation.JsonIgnore
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

@Entity
@Table(
    name = "order_line_item",
    indexes = [
        Index(name = "idx_order_line_item_order_id", columnList = "order_id"),
        Index(name = "idx_order_line_item_variant_id", columnList = "variant_id"),
        Index(name = "idx_order_line_item_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "OrderLineItem.full",
    attributeNodes = [
        NamedAttributeNode("order")
    ]
)
class OrderLineItem : BaseEntity() {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null

    @Column(name = "variant_id")
    var variantId: String? = null

    @NotBlank
    @Column(nullable = false)
    var title: String = ""

    @Column
    var description: String? = null

    @Column
    var thumbnail: String? = null

    @Column(nullable = false)
    var quantity: Int = 1

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(precision = 19, scale = 4, nullable = false)
    var unitPrice: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false)
    var total: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4)
    var discount: BigDecimal? = null

    @Column
    var isGiftcard: Boolean = false

    @Column
    var shouldMerge: Boolean = true

    @Column(nullable = false)
    var allowDiscounts: Boolean = true

    @Column
    var hasShipping: Boolean = true

    @Column(nullable = false)
    var fulfilledQuantity: Int = 0

    @Column(nullable = false)
    var returnedQuantity: Int = 0

    @Column(nullable = false)
    var shippedQuantity: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderLineItemStatus = OrderLineItemStatus.PENDING

    fun recalculateTotal() {
        total = unitPrice.multiply(BigDecimal(quantity))
        discount?.let { total = total.subtract(it) }
    }

    fun increaseQuantity(amount: Int = 1) {
        require(amount > 0) { "Amount must be positive" }
        quantity += amount
        recalculateTotal()
    }

    fun decreaseQuantity(amount: Int = 1) {
        require(amount > 0) { "Amount must be positive" }
        require(quantity >= amount) { "Cannot decrease quantity below 0" }
        quantity -= amount
        recalculateTotal()
    }

    fun fulfill(amount: Int) {
        require(amount > 0) { "Fulfill amount must be positive" }
        require(fulfilledQuantity + amount <= quantity) { "Cannot fulfill more than ordered" }
        fulfilledQuantity += amount
    }

    fun ship(amount: Int) {
        require(amount > 0) { "Ship amount must be positive" }
        require(shippedQuantity + amount <= fulfilledQuantity) { "Cannot ship more than fulfilled" }
        shippedQuantity += amount
    }

    fun returnItem(amount: Int) {
        require(amount > 0) { "Return amount must be positive" }
        require(returnedQuantity + amount <= shippedQuantity) { "Cannot return more than shipped" }
        returnedQuantity += amount
    }

    fun isFullyFulfilled(): Boolean = fulfilledQuantity == quantity

    fun isPartiallyFulfilled(): Boolean = fulfilledQuantity > 0 && fulfilledQuantity < quantity

    fun isFullyShipped(): Boolean = shippedQuantity == quantity

    fun getRemainingQuantity(): Int = quantity - fulfilledQuantity
}

/**
 * Order line item status enumeration
 */
enum class OrderLineItemStatus {
    PENDING,
    FULFILLED,
    SHIPPED,
    PARTIALLY_SHIPPED,
    RETURNED,
    CANCELED
}
