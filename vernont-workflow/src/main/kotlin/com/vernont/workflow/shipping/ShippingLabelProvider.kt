package com.vernont.workflow.shipping

import java.math.BigDecimal

/**
 * Request to create a shipping label
 */
data class CreateLabelRequest(
    val shipFromAddress: ShippingAddress,
    val shipToAddress: ShippingAddress,
    val parcels: List<Parcel>,
    val carrier: String? = null,
    val service: String? = null,
    val weight: Weight? = null,
    val metadata: Map<String, Any>? = null
)

data class ShippingAddress(
    val name: String?,
    val company: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val state: String?,
    val postalCode: String,
    val country: String,
    val phone: String? = null,
    val email: String? = null
)

data class Parcel(
    val length: Double,
    val width: Double,
    val height: Double,
    val dimensionUnit: String = "in",
    val weight: Double,
    val weightUnit: String = "lb"
)

data class Weight(
    val value: Double,
    val unit: String = "lb"
)

/**
 * Result of a successful label purchase
 */
data class LabelResult(
    val labelId: String,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val labelUrl: String?,
    val carrier: String?,
    val service: String?,
    val cost: BigDecimal?,
    val currency: String = "USD",
    val providerData: Map<String, Any>? = null
)

/**
 * Result of a label void attempt
 */
data class VoidResult(
    val success: Boolean,
    val refundAmount: BigDecimal? = null,
    val error: String? = null
)

/**
 * Interface for shipping label providers (Shippo, ShipStation, EasyPost, ShipEngine, etc.)
 *
 * Implementations should:
 * - Support idempotency keys to prevent double-purchases
 * - Handle provider-specific API calls
 * - Convert responses to common LabelResult/VoidResult types
 */
interface ShippingLabelProvider {

    /**
     * Provider identifier (e.g., "shippo", "shipstation", "easypost", "shipengine")
     */
    val name: String

    /**
     * Check if this provider is currently available/configured
     */
    fun isAvailable(): Boolean

    /**
     * Create a shipping label
     *
     * @param idempotencyKey Unique key to prevent duplicate purchases on retry
     * @param request Label creation request
     * @return Label result with tracking info and label URL
     */
    suspend fun createLabel(idempotencyKey: String, request: CreateLabelRequest): LabelResult

    /**
     * Void/refund a shipping label
     *
     * @param labelId The provider's label ID
     * @return Result indicating success/failure and any refund amount
     */
    suspend fun voidLabel(labelId: String): VoidResult
}
