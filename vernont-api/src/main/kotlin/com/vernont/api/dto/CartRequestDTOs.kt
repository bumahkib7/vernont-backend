package com.vernont.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal

/**
 * Request DTOs for Cart Operations
 * These DTOs are used for API input validation and conversion to workflow inputs
 */

/**
 * Request to create a new cart
 */
data class CreateCartRequest(
    @field:NotBlank(message = "Region ID is required")
    val regionId: String,
    
    val customerId: String? = null,
    
    @field:Email(message = "Valid email address required")
    val email: String? = null,
    
    val currencyCode: String? = null,
    
    @field:Valid
    val items: List<CreateCartLineItemRequest>? = null,
    
    val metadata: Map<String, String>? = null
) {
    /**
     * Convert to workflow input
     */
    fun toWorkflowInput(correlationId: String? = null): com.vernont.workflow.flows.cart.CreateCartInput {
        return com.vernont.workflow.flows.cart.CreateCartInput(
            regionId = regionId,
            customerId = customerId,
            email = email,
            currencyCode = currencyCode,
            items = items?.map { it.toWorkflowInput() },
            correlationId = correlationId
        )
    }
}

/**
 * Request to add an item to cart during creation
 */
data class CreateCartLineItemRequest(
    @field:NotBlank(message = "Variant ID is required")
    val variantId: String,
    
    @field:Min(value = 1, message = "Quantity must be at least 1")
    @field:Max(value = 999, message = "Quantity cannot exceed 999")
    val quantity: Int,
    
    @field:DecimalMin(value = "0.0", message = "Unit price must be positive")
    val unitPrice: BigDecimal? = null,
    
    val metadata: Map<String, String>? = null
) {
    /**
     * Convert to workflow input
     */
    fun toWorkflowInput(): com.vernont.workflow.flows.cart.CreateCartLineItemInput {
        return com.vernont.workflow.flows.cart.CreateCartLineItemInput(
            variantId = variantId,
            quantity = quantity,
            unitPrice = unitPrice
        )
    }
}

/**
 * Request to update cart
 */
data class UpdateCartRequest(
    val regionId: String? = null,
    val customerId: String? = null,
    
    @field:Email(message = "Valid email address required")
    val email: String? = null,
    
    val currencyCode: String? = null,
    val metadata: Map<String, String>? = null
)

/**
 * Request to add line item to existing cart
 */
data class AddLineItemRequest(
    @field:NotBlank(message = "Variant ID is required")
    val variantId: String,
    
    @field:Min(value = 1, message = "Quantity must be at least 1")
    @field:Max(value = 999, message = "Quantity cannot exceed 999")
    val quantity: Int,
    
    @field:DecimalMin(value = "0.0", message = "Unit price must be positive")
    val unitPrice: BigDecimal? = null,
    
    val metadata: Map<String, String>? = null
)

/**
 * Request to update line item in cart
 */
data class UpdateLineItemRequest(
    @field:Min(value = 0, message = "Quantity must be non-negative")
    @field:Max(value = 999, message = "Quantity cannot exceed 999")
    val quantity: Int? = null,
    
    @field:DecimalMin(value = "0.0", message = "Unit price must be positive")
    val unitPrice: BigDecimal? = null,
    
    val metadata: Map<String, String>? = null
)

/**
 * Generic error response for cart operations
 */
data class CartErrorResponse(
    val error: ErrorDetail,
    val correlationId: String? = null
)

data class ErrorDetail(
    val code: String,
    val message: String,
    val type: String? = null,
    val details: Map<String, Any>? = null
)