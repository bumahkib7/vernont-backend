package com.vernont.domain.cart.dto

import com.vernont.domain.cart.Cart
import com.vernont.domain.cart.CartLineItem
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant

/**
 * Request DTOs
 */

data class CreateCartRequest(
    val customerId: String? = null,
    @field:Email
    val email: String? = null,
    @field:NotBlank
    val regionId: String,
    @field:NotBlank
    val currencyCode: String
)

data class AddCartItemRequest(
    @field:NotBlank
    val variantId: String,
    @field:NotBlank
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    @field:Positive
    val quantity: Int = 1,
    val unitPrice: BigDecimal,
    @field:NotBlank
    val currencyCode: String,
    val isGiftcard: Boolean = false,
    val allowDiscounts: Boolean = true,
    val hasShipping: Boolean = true
)

data class UpdateCartItemQuantityRequest(
    @field:Positive
    val quantity: Int
)

data class ApplyCartItemDiscountRequest(
    @field:Positive
    val discountAmount: BigDecimal
)

data class UpdateCartRequest(
    val shippingMethodId: String? = null,
    val paymentMethodId: String? = null,
    val customerId: String? = null,
    @field:Email
    val email: String? = null,
    val note: String? = null
)

/**
 * Response DTOs
 */

data class CartResponse(
    val id: String,
    val customerId: String?,
    val email: String?,
    val regionId: String,
    val currencyCode: String,
    val subtotal: BigDecimal,
    val tax: BigDecimal,
    val shipping: BigDecimal,
    val discount: BigDecimal,
    val total: BigDecimal,
    val items: List<CartLineItemResponse>,
    val shippingMethodId: String?,
    val paymentMethodId: String?,
    val completedAt: Instant?,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(cart: Cart): CartResponse {
            return CartResponse(
                id = cart.id,
                customerId = cart.customerId,
                email = cart.email,
                regionId = cart.regionId,
                currencyCode = cart.currencyCode,
                subtotal = cart.subtotal,
                tax = cart.tax,
                shipping = cart.shipping,
                discount = cart.discount,
                total = cart.total,
                items = cart.items
                    .filter { it.deletedAt == null }
                    .map { CartLineItemResponse.from(it) },
                shippingMethodId = cart.shippingMethodId,
                paymentMethodId = cart.paymentMethodId,
                completedAt = cart.completedAt,
                note = cart.note,
                createdAt = cart.createdAt,
                updatedAt = cart.updatedAt
            )
        }
    }
}

data class CartLineItemResponse(
    val id: String,
    val variantId: String?,
    val title: String,
    val description: String?,
    val thumbnail: String?,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val total: BigDecimal,
    val currencyCode: String,
    val discount: BigDecimal?,
    val isGiftcard: Boolean,
    val allowDiscounts: Boolean,
    val hasShipping: Boolean
) {
    companion object {
        fun from(item: CartLineItem): CartLineItemResponse {
            return CartLineItemResponse(
                id = item.id,
                variantId = item.variantId,
                title = item.title,
                description = item.description,
                thumbnail = item.thumbnail,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                total = item.total,
                currencyCode = item.currencyCode,
                discount = item.discount,
                isGiftcard = item.isGiftcard,
                allowDiscounts = item.allowDiscounts,
                hasShipping = item.hasShipping
            )
        }
    }
}

data class CartSummaryResponse(
    val id: String,
    val customerId: String?,
    val email: String?,
    val itemCount: Int,
    val total: BigDecimal,
    val currencyCode: String,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(cart: Cart): CartSummaryResponse {
            return CartSummaryResponse(
                id = cart.id,
                customerId = cart.customerId,
                email = cart.email,
                itemCount = cart.getItemCount(),
                total = cart.total,
                currencyCode = cart.currencyCode,
                completedAt = cart.completedAt,
                createdAt = cart.createdAt,
                updatedAt = cart.updatedAt
            )
        }
    }
}
