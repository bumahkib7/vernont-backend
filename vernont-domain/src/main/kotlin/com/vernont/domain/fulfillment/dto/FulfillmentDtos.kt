package com.vernont.domain.fulfillment.dto

import com.vernont.domain.fulfillment.Fulfillment
import com.vernont.domain.fulfillment.FulfillmentProvider
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

/**
 * Request to create a fulfillment
 */
data class CreateFulfillmentRequest(
    @field:NotBlank
    val providerId: String,

    val orderId: String? = null,
    val claimOrderId: String? = null,
    val swapId: String? = null,
    val locationId: String? = null,
    val trackingNumbers: List<String> = emptyList(),
    val trackingUrls: List<String> = emptyList(),
    val noNotification: Boolean = false,
    val note: String? = null,
    val data: String? = null
)

/**
 * Request to update fulfillment
 */
data class UpdateFulfillmentRequest(
    val trackingNumbers: List<String>? = null,
    val trackingUrls: List<String>? = null,
    val note: String? = null,
    val data: String? = null
)

/**
 * Request to ship a fulfillment
 */
data class ShipFulfillmentRequest(
    @field:NotBlank
    val fulfillmentId: String,

    val trackingNumbers: List<String> = emptyList(),
    val trackingUrls: List<String> = emptyList(),
    val noNotification: Boolean = false
)

/**
 * Request to cancel a fulfillment
 */
data class CancelFulfillmentRequest(
    @field:NotBlank
    val fulfillmentId: String,

    val reason: String? = null
)

/**
 * Fulfillment response DTO
 */
data class FulfillmentResponse(
    val id: String,
    val orderId: String?,
    val claimOrderId: String?,
    val swapId: String?,
    val provider: FulfillmentProviderSummary?,
    val locationId: String?,
    val trackingNumbers: List<String>,
    val trackingUrls: List<String>,
    val data: Map<String, Any>?,
    val shippedAt: Instant?,
    val canceledAt: Instant?,
    val noNotification: Boolean,
    val note: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(fulfillment: Fulfillment): FulfillmentResponse {
            val status = when {
                fulfillment.isCanceled() -> "canceled"
                fulfillment.isShipped() -> "shipped"
                else -> "pending"
            }

            return FulfillmentResponse(
                id = fulfillment.id,
                orderId = fulfillment.orderId,
                claimOrderId = fulfillment.claimOrderId,
                swapId = fulfillment.swapId,
                provider = fulfillment.provider?.let { FulfillmentProviderSummary.from(it) },
                locationId = fulfillment.locationId,
                trackingNumbers = fulfillment.getTrackingNumbersList(),
                trackingUrls = fulfillment.getTrackingUrlsList(),
                data = fulfillment.data,
                shippedAt = fulfillment.shippedAt,
                canceledAt = fulfillment.canceledAt,
                noNotification = fulfillment.noNotification,
                note = fulfillment.note,
                status = status,
                createdAt = fulfillment.createdAt,
                updatedAt = fulfillment.updatedAt
            )
        }
    }
}

/**
 * Fulfillment summary response
 */
data class FulfillmentSummaryResponse(
    val id: String,
    val orderId: String?,
    val provider: FulfillmentProviderSummary?,
    val status: String,
    val shippedAt: Instant?,
    val createdAt: Instant
) {
    companion object {
        fun from(fulfillment: Fulfillment): FulfillmentSummaryResponse {
            val status = when {
                fulfillment.isCanceled() -> "canceled"
                fulfillment.isShipped() -> "shipped"
                else -> "pending"
            }

            return FulfillmentSummaryResponse(
                id = fulfillment.id,
                orderId = fulfillment.orderId,
                provider = fulfillment.provider?.let { FulfillmentProviderSummary.from(it) },
                status = status,
                shippedAt = fulfillment.shippedAt,
                createdAt = fulfillment.createdAt
            )
        }
    }
}

/**
 * Fulfillment provider summary DTO
 */
data class FulfillmentProviderSummary(
    val id: String,
    val name: String,
    val isActive: Boolean
) {
    companion object {
        fun from(provider: FulfillmentProvider): FulfillmentProviderSummary {
            return FulfillmentProviderSummary(
                id = provider.id,
                name = provider.name,
                isActive = provider.isActive
            )
        }
    }
}

/**
 * Fulfillment provider response DTO
 */
data class FulfillmentProviderResponse(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val config: MutableMap<String, Any>?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(provider: FulfillmentProvider): FulfillmentProviderResponse {
            return FulfillmentProviderResponse(
                id = provider.id,
                name = provider.name,
                isActive = provider.isActive,
                config = provider.config,
                createdAt = provider.createdAt,
                updatedAt = provider.updatedAt
            )
        }
    }
}
