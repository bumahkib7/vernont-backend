package com.vernont.api.dto.store

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

data class StoreOrderResponse(
    val order: StoreOrder
)

data class StoreOrderListResponse(
    val orders: List<StoreOrder>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class StoreOrder(
    val id: String,
    @JsonProperty("display_id")
    val displayId: Int?,
    val status: String,
    @JsonProperty("fulfillment_status")
    val fulfillmentStatus: String,
    @JsonProperty("payment_status")
    val paymentStatus: String,
    val email: String,
    @JsonProperty("customer_id")
    val customerId: String?,
    val subtotal: Int,
    val tax: Int,
    val shipping: Int,
    val discount: Int,
    val total: Int,
    @JsonProperty("currency_code")
    val currencyCode: String,
    @JsonProperty("item_count")
    val itemCount: Int,
    val items: List<StoreOrderLineItem>? = null,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @JsonProperty("updated_at")
    val updatedAt: OffsetDateTime,
    @JsonProperty("completed_at")
    val completedAt: OffsetDateTime? = null
)

data class StoreOrderLineItem(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnail: String?,
    @JsonProperty("variant_id")
    val variantId: String?,
    val quantity: Int,
    @JsonProperty("unit_price")
    val unitPrice: Int,
    val total: Int,
    @JsonProperty("currency_code")
    val currencyCode: String
)
