package com.vernont.domain.order.dto

import com.vernont.domain.order.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant

/**
 * Request to create a new order
 */
data class CreateOrderRequest(
    val customerId: String? = null,
    
    @field:Email(message = "Email must be valid")
    @field:NotBlank(message = "Email is required")
    val email: String,
    
    val cartId: String? = null,
    
    @field:NotBlank(message = "Region ID is required")
    val regionId: String,
    
    @field:NotBlank(message = "Currency code is required")
    @field:Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    val currencyCode: String,
    
    @field:NotEmpty(message = "At least one item is required")
    @field:Valid
    val items: List<CreateOrderLineItemRequest>,
    
    @field:Valid
    val shippingAddress: CreateOrderAddressRequest? = null,
    
    @field:Valid
    val billingAddress: CreateOrderAddressRequest? = null,
    
    val shippingMethodId: String? = null,
    val paymentMethodId: String? = null
)

/**
 * Request to create an order line item
 */
data class CreateOrderLineItemRequest(
    val variantId: String? = null,
    
    @field:NotBlank(message = "Title is required")
    val title: String,
    
    val description: String? = null,
    val thumbnail: String? = null,
    
    @field:Min(value = 1, message = "Quantity must be at least 1")
    val quantity: Int,
    
    @field:NotBlank(message = "Currency code is required")
    val currencyCode: String,
    
    @field:DecimalMin(value = "0.0", message = "Unit price must be non-negative")
    @field:Digits(integer = 10, fraction = 4, message = "Unit price must have at most 4 decimal places")
    val unitPrice: BigDecimal,
    
    val discount: BigDecimal? = null,
    val isGiftcard: Boolean = false,
    val allowDiscounts: Boolean = true,
    val hasShipping: Boolean = true
)

/**
 * Request to create an order address
 */
data class CreateOrderAddressRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val company: String? = null,
    val phone: String? = null,
    
    @field:NotBlank(message = "Address line 1 is required")
    val address1: String,
    
    val address2: String? = null,
    
    @field:NotBlank(message = "City is required")
    val city: String,
    
    val province: String? = null,
    val postalCode: String? = null,
    
    @field:NotBlank(message = "Country code is required")
    @field:Size(min = 2, max = 2, message = "Country code must be 2 characters")
    val countryCode: String
)

/**
 * Request to update an order
 */
data class UpdateOrderRequest(
    @field:Email(message = "Email must be valid")
    val email: String? = null,
    
    val shippingMethodId: String? = null,
    val paymentMethodId: String? = null
)

/**
 * Order address response
 */
data class OrderAddressResponse(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val company: String?,
    val phone: String?,
    val address1: String,
    val address2: String?,
    val city: String,
    val province: String?,
    val postalCode: String?,
    val countryCode: String,
    val fullAddress: String
) {
    companion object {
        fun from(address: OrderAddress): OrderAddressResponse {
            return OrderAddressResponse(
                id = address.id,
                firstName = address.firstName,
                lastName = address.lastName,
                company = address.company,
                phone = address.phone,
                address1 = address.address1,
                address2 = address.address2,
                city = address.city,
                province = address.province,
                postalCode = address.postalCode,
                countryCode = address.countryCode,
                fullAddress = address.getFullAddress()
            )
        }
    }
}

/**
 * Order line item response
 */
data class OrderLineItemResponse(
    val id: String,
    val variantId: String?,
    val title: String,
    val description: String?,
    val thumbnail: String?,
    val quantity: Int,
    val currencyCode: String,
    val unitPrice: BigDecimal,
    val total: BigDecimal,
    val discount: BigDecimal?,
    val isGiftcard: Boolean,
    val allowDiscounts: Boolean,
    val hasShipping: Boolean,
    val fulfilledQuantity: Int,
    val returnedQuantity: Int,
    val shippedQuantity: Int
) {
    companion object {
        fun from(lineItem: OrderLineItem): OrderLineItemResponse {
            return OrderLineItemResponse(
                id = lineItem.id,
                variantId = lineItem.variantId,
                title = lineItem.title,
                description = lineItem.description,
                thumbnail = lineItem.thumbnail,
                quantity = lineItem.quantity,
                currencyCode = lineItem.currencyCode,
                unitPrice = lineItem.unitPrice,
                total = lineItem.total,
                discount = lineItem.discount,
                isGiftcard = lineItem.isGiftcard,
                allowDiscounts = lineItem.allowDiscounts,
                hasShipping = lineItem.hasShipping,
                fulfilledQuantity = lineItem.fulfilledQuantity,
                returnedQuantity = lineItem.returnedQuantity,
                shippedQuantity = lineItem.shippedQuantity
            )
        }
    }
}

/**
 * Full order response
 */
data class OrderResponse(
    val id: String,
    val displayId: Int,
    val customerId: String?,
    val email: String,
    val cartId: String?,
    val regionId: String,
    val currencyCode: String,
    val subtotal: BigDecimal,
    val tax: BigDecimal,
    val shipping: BigDecimal,
    val discount: BigDecimal,
    val total: BigDecimal,
    val status: OrderStatus,
    val fulfillmentStatus: FulfillmentStatus,
    val paymentStatus: PaymentStatus,
    val items: List<OrderLineItemResponse>,
    val shippingAddress: OrderAddressResponse?,
    val billingAddress: OrderAddressResponse?,
    val shippingMethodId: String?,
    val paymentMethodId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(order: Order): OrderResponse {
            return OrderResponse(
                id = order.id,
                displayId = order.displayId,
                customerId = order.customerId,
                email = order.email,
                cartId = order.cartId,
                regionId = order.regionId,
                currencyCode = order.currencyCode,
                subtotal = order.subtotal,
                tax = order.tax,
                shipping = order.shipping,
                discount = order.discount,
                total = order.total,
                status = order.status,
                fulfillmentStatus = order.fulfillmentStatus,
                paymentStatus = order.paymentStatus,
                items = order.items.map { OrderLineItemResponse.from(it) },
                shippingAddress = order.shippingAddress?.let { OrderAddressResponse.from(it) },
                billingAddress = order.billingAddress?.let { OrderAddressResponse.from(it) },
                shippingMethodId = order.shippingMethodId,
                paymentMethodId = order.paymentMethodId,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt
            )
        }
    }
}

/**
 * Order summary response for list views
 */
data class OrderSummaryResponse(
    val id: String,
    val displayId: Int,
    val customerId: String?,
    val email: String,
    val status: OrderStatus,
    val fulfillmentStatus: FulfillmentStatus,
    val paymentStatus: PaymentStatus,
    val total: BigDecimal,
    val currencyCode: String,
    val itemCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(order: Order): OrderSummaryResponse {
            return OrderSummaryResponse(
                id = order.id,
                displayId = order.displayId,
                customerId = order.customerId,
                email = order.email,
                status = order.status,
                fulfillmentStatus = order.fulfillmentStatus,
                paymentStatus = order.paymentStatus,
                total = order.total,
                currencyCode = order.currencyCode,
                itemCount = order.items.size,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt
            )
        }
    }
}