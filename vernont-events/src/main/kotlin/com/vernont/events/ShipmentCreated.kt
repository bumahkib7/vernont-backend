package com.vernont.events

import java.time.Instant

/**
 * Event published when a shipment is created
 */
class ShipmentCreated(
    val fulfillmentId: String,
    val orderId: String,
    val trackingNumber: String?,
    val carrierCode: String?,
    val items: List<ShipmentItemData>,
    val shippedAt: Instant = Instant.now()
) : DomainEvent(
    aggregateId = fulfillmentId,
    occurredAt = shippedAt
)

/**
 * Shipment item data for the event
 */
data class ShipmentItemData(
    val lineItemId: String,
    val variantId: String,
    val quantity: Int,
    val title: String
)