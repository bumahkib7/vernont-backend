package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Fulfillment-related domain events.
 */

/**
 * Item included in a fulfillment
 */
data class FulfillmentItemData(
    val sku: String,
    val quantity: Int,
    val title: String? = null,
    val lineItemId: String? = null
)

/**
 * Fired when a new fulfillment is created.
 *
 * @property orderId ID of the associated order
 * @property locationId ID of the fulfillment location
 * @property providerId ID of the fulfillment provider (optional)
 * @property status Current fulfillment status
 * @property items Items being fulfilled (for inventory decrement)
 */
data class FulfillmentCreated(
    override val aggregateId: String,
    val orderId: String,
    val locationId: String,
    val providerId: String? = null,
    val status: String,
    val items: List<FulfillmentItemData> = emptyList(),
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a fulfillment is shipped.
 *
 * @property orderId ID of the associated order
 * @property locationId ID of the fulfillment location
 * @property trackingNumbers List of tracking numbers
 * @property shippedAt When the fulfillment was shipped
 * @property estimatedDeliveryAt Estimated delivery time
 */
data class FulfillmentShipped(
    override val aggregateId: String,
    val orderId: String,
    val locationId: String,
    val trackingNumbers: List<String>,
    val shippedAt: Instant,
    val estimatedDeliveryAt: Instant? = null,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a fulfillment is delivered.
 *
 * @property orderId ID of the associated order
 * @property locationId ID of the fulfillment location
 * @property deliveredAt When the fulfillment was delivered
 */
data class FulfillmentDelivered(
    override val aggregateId: String,
    val orderId: String,
    val locationId: String,
    val deliveredAt: Instant,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a fulfillment is cancelled.
 *
 * @property orderId ID of the associated order
 * @property locationId ID of the fulfillment location
 * @property reason Reason for cancellation
 * @property cancelledAt When the fulfillment was cancelled
 */
data class FulfillmentCancelled(
    override val aggregateId: String,
    val orderId: String,
    val locationId: String,
    val reason: String,
    val cancelledAt: Instant,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Alias for FulfillmentCancelled with American spelling
 */
typealias FulfillmentCanceled = FulfillmentCancelled

/**
 * Fired when shipping options prices are calculated.
 *
 * @property aggregateId The ID of the context (e.g., cart ID) for which prices were calculated.
 * @property calculatedPrices The list of calculated prices for shipping options.
 */
data class ShippingOptionsPricesCalculated(
    override val aggregateId: String,
    val shippingOptionIds: List<String>,
    val totalPrice: BigDecimal,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)
