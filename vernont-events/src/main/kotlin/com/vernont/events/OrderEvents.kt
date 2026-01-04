package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Order-related domain events.
 */

/**
 * Represents an item in an order.
 */
data class OrderItem(
    val productId: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal
)

/**
 * Event published when an order is canceled
 */
data class OrderCanceled(
    override val aggregateId: String,
    val orderId: String,
    val customerId: String,
    val reason: String,
    val canceledBy: String?,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a new order is created in the system.
 *
 * @property customerId ID of the customer who placed the order
 * @property items List of items in the order
 * @property totalAmount Total order amount
 * @property shippingAddress Shipping address for the order
 * @property status Initial order status (typically "PENDING")
 */
data class OrderCreated(
    override val aggregateId: String,
    val customerId: String,
    val items: List<OrderItem>,
    val totalAmount: BigDecimal,
    val shippingAddress: String,
    val status: String = "PENDING",
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when an order is successfully completed.
 *
 * @property customerId ID of the customer
 * @property totalAmount Final order amount
 * @property completedAt Timestamp when the order was completed
 */
data class OrderCompleted(
    override val aggregateId: String,
    val customerId: String,
    val totalAmount: BigDecimal,
    val completedAt: Instant,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when an order is cancelled.
 *
 * @property customerId ID of the customer
 * @property reason Reason for cancellation (e.g., "CUSTOMER_REQUEST", "PAYMENT_FAILED", "OUT_OF_STOCK")
 * @property refundAmount Amount to be refunded
 */
data class OrderCancelled(
    override val aggregateId: String,
    val customerId: String,
    val reason: String,
    val refundAmount: BigDecimal,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)
