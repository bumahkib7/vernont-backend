package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Shopping cart-related domain events.
 */

/**
 * Fired when a new shopping cart is created.
 *
 * @property customerId ID of the customer who owns the cart
 */
data class CartCreated(
    override val aggregateId: String,
    val customerId: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when an item is added to the shopping cart.
 *
 * @property customerId ID of the customer
 * @property productId ID of the product being added
 * @property quantity Quantity of the product added
 * @property unitPrice Price per unit
 * @property totalPrice Total price for this item (quantity * unitPrice)
 */
data class CartItemAdded(
    override val aggregateId: String,
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when an item is removed from the shopping cart.
 *
 * @property customerId ID of the customer
 * @property productId ID of the product being removed
 * @property quantity Quantity of the product removed
 * @property totalPrice Total price of items removed
 */
data class CartItemRemoved(
    override val aggregateId: String,
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val totalPrice: BigDecimal,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)
