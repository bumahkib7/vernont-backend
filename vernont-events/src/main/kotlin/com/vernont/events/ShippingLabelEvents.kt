package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Event published when a shipping label is purchased from a provider
 */
class ShipmentLabelPurchased(
    val fulfillmentId: String,
    val orderId: String,
    val labelId: String,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val carrier: String?,
    val service: String?,
    val cost: BigDecimal?,
    val labelUrl: String?,
    val provider: String,
    val idempotencyKey: String,
    val purchasedAt: Instant = Instant.now()
) : DomainEvent(
    aggregateId = fulfillmentId,
    occurredAt = purchasedAt
)

/**
 * Event published when a shipping label is successfully voided/refunded
 */
class ShipmentLabelVoided(
    val fulfillmentId: String,
    val orderId: String,
    val labelId: String,
    val provider: String,
    val refundAmount: BigDecimal?,
    val voidedAt: Instant = Instant.now()
) : DomainEvent(
    aggregateId = fulfillmentId,
    occurredAt = voidedAt
)

/**
 * Event published when a shipping label void/refund fails - requires ops attention
 */
class ShipmentLabelVoidFailed(
    val fulfillmentId: String,
    val orderId: String,
    val labelId: String,
    val provider: String,
    val error: String,
    val requiresManualIntervention: Boolean = true,
    val failedAt: Instant = Instant.now()
) : DomainEvent(
    aggregateId = fulfillmentId,
    occurredAt = failedAt
)

/**
 * Event published when an order is updated (status change, etc.)
 */
class OrderUpdated(
    override val aggregateId: String,
    val orderId: String,
    val updateType: String,
    val previousStatus: String?,
    val newStatus: String?,
    val updatedFields: List<String> = emptyList(),
    val updatedAt: Instant = Instant.now()
) : DomainEvent(
    aggregateId = aggregateId,
    occurredAt = updatedAt
)
