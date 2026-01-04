package com.vernont.domain.cart

import com.fasterxml.jackson.annotation.JsonIgnore
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

@Entity
@Table(
    name = "cart_line_item",
    indexes = [
        Index(name = "idx_cart_line_item_cart_id", columnList = "cart_id"),
        Index(name = "idx_cart_line_item_variant_id", columnList = "variant_id"),
        Index(name = "idx_cart_line_item_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "CartLineItem.full",
    attributeNodes = [
        NamedAttributeNode("cart")
    ]
)
class CartLineItem : BaseEntity() {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    var cart: Cart? = null

    @Column(name = "variant_id", nullable = false)
    var variantId: String = ""

    @NotBlank
    @Column(nullable = false)
    var title: String = ""

    @Column
    var description: String? = null

    @Column
    var thumbnail: String? = null

    @Column(name = "product_handle")
    var productHandle: String? = null

    @Column(name = "variant_title")
    var variantTitle: String? = null

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

    // Additional properties required by workflows
    @Column(precision = 19, scale = 4, nullable = false)
    var taxAmount: BigDecimal = BigDecimal.ZERO

    @Column(precision = 4, scale = 4, nullable = false)
    var taxRate: BigDecimal = BigDecimal.ZERO

    @Column(name = "tax_code")
    var taxCode: String? = null

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
        require(quantity > amount) { "Cannot decrease quantity to 0 or below, use remove instead" }
        quantity -= amount
        recalculateTotal()
    }

    fun updateQuantity(newQuantity: Int) {
        require(newQuantity > 0) { "Quantity must be greater than 0" }
        quantity = newQuantity
        recalculateTotal()
    }

    fun applyDiscount(discountAmount: BigDecimal) {
        require(allowDiscounts) { "Discounts are not allowed on this item" }
        require(discountAmount >= BigDecimal.ZERO) { "Discount amount must be non-negative" }
        this.discount = discountAmount
        recalculateTotal()
    }

    fun removeDiscount() {
        this.discount = null
        recalculateTotal()
    }

    fun hasDiscount(): Boolean = discount != null && discount!! > BigDecimal.ZERO

    fun getDiscountedPrice(): BigDecimal {
        return if (hasDiscount()) {
            total
        } else {
            unitPrice.multiply(BigDecimal(quantity))
        }
    }
}
