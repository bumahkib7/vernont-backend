package com.vernont.workflow.flows.cart.dto

import com.vernont.domain.cart.Cart
import com.vernont.domain.cart.CartLineItem
import java.math.BigDecimal
import java.time.Instant

/**
 * DTOs for Cart Workflows - API-ready responses
 * These DTOs match Medusa's cart API format for consistency
 */

data class CartResponse(
    val cart: CartDto,
    val correlationId: String? = null
)

data class CartDto(
    val id: String,
    val customerId: String? = null,
    val email: String? = null,
    val regionId: String,
    val currencyCode: String,
    val total: BigDecimal,
    val subtotal: BigDecimal,
    val taxTotal: BigDecimal,
    val shippingTotal: BigDecimal,
    val discountTotal: BigDecimal,
    val giftCardCode: String? = null,
    val giftCardTotal: BigDecimal = BigDecimal.ZERO,
    val items: List<CartLineItemDto>,
    val itemCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,

    // Additional cart metadata
    val type: String = "default", // default, quote, draft_order, etc.
    val completedAt: Instant? = null,
    val context: Map<String, Any>? = null,
    val metadata: Map<String, String>? = null
) {
    companion object {
        fun from(cart: Cart, correlationId: String? = null): CartResponse {
            return CartResponse(
                cart = CartDto(
                    id = cart.id,
                    customerId = cart.customerId,
                    email = cart.email,
                    regionId = cart.regionId,
                    currencyCode = cart.currencyCode,
                    total = cart.total,
                    subtotal = cart.subtotal,
                    taxTotal = cart.tax,
                    shippingTotal = cart.shipping,
                    discountTotal = cart.discount,
                    giftCardCode = cart.giftCardCode,
                    giftCardTotal = cart.giftCardTotal,
                    items = cart.items.filter { it.deletedAt == null }
                        .map { CartLineItemDto.from(it) },
                    itemCount = cart.items.filter { it.deletedAt == null }.size,
                    createdAt = cart.createdAt,
                    updatedAt = cart.updatedAt,
                    completedAt = cart.completedAt,
                    metadata = cart.metadata?.mapValues { it.value.toString() }
                ),
                correlationId = correlationId
            )
        }
    }
}

data class CartLineItemDto(
    val id: String,
    val cartId: String,
    val variantId: String? = null,
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    val productHandle: String? = null,
    val variantTitle: String? = null,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val total: BigDecimal,
    val currencyCode: String,
    val taxLines: List<TaxLineDto>? = null,
    val adjustments: List<AdjustmentDto>? = null,
    val metadata: Map<String, String>? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(item: CartLineItem): CartLineItemDto {
            return CartLineItemDto(
                id = item.id,
                cartId = item.cart?.id ?: "",
                variantId = item.variantId,
                title = item.title,
                description = item.description,
                thumbnail = item.thumbnail,
                productHandle = item.productHandle,
                variantTitle = item.variantTitle,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                total = item.total,
                currencyCode = item.currencyCode,
                metadata = item.metadata?.mapValues { it.value.toString() },
                createdAt = item.createdAt,
                updatedAt = item.updatedAt
            )
        }
    }
}

data class TaxLineDto(
    val id: String,
    val rate: BigDecimal,
    val name: String,
    val code: String? = null,
    val total: BigDecimal,
    val metadata: Map<String, String>? = null
)

data class AdjustmentDto(
    val id: String,
    val amount: BigDecimal,
    val description: String,
    val promotionId: String? = null,
    val discountId: String? = null
)

/**
 * Simplified cart summary for list operations
 */
data class CartSummaryDto(
    val id: String,
    val customerId: String? = null,
    val email: String? = null,
    val regionId: String,
    val currencyCode: String,
    val total: BigDecimal,
    val itemCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(cart: Cart): CartSummaryDto {
            return CartSummaryDto(
                id = cart.id,
                customerId = cart.customerId,
                email = cart.email,
                regionId = cart.regionId,
                currencyCode = cart.currencyCode,
                total = cart.total,
                itemCount = cart.items.filter { it.deletedAt == null }.size,
                createdAt = cart.createdAt,
                updatedAt = cart.updatedAt
            )
        }
    }
}

/**
 * Cart operation result - wraps the cart DTO with operation metadata
 */
data class CartOperationResult(
    val success: Boolean,
    val cart: CartDto? = null,
    val error: ErrorDto? = null,
    val correlationId: String? = null,
    val operation: String? = null,
    val timestamp: Instant = Instant.now()
)

data class ErrorDto(
    val code: String,
    val message: String,
    val type: String? = null,
    val details: Map<String, Any>? = null
)