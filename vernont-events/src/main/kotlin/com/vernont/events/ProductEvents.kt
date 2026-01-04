package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Product-related domain events.
 */

/**
 * Fired when a new product is created in the system.
 *
 * @property name Product name
 * @property description Product description
 * @property price Product price
 * @property sku Stock keeping unit
 * @property quantity Initial quantity in stock
 * @property categoryId ID of the product category
 */
data class ProductCreated(
    override val aggregateId: String,
    val title: String,
    val handle: String,
    val status: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when an existing product is updated.
 *
 * @property name Updated product name
 * @property description Updated product description
 * @property price Updated product price
 * @property quantity Updated quantity in stock
 * @property isActive Whether the product is active
 */
data class ProductUpdated(
    override val aggregateId: String,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val quantity: Int,
    val isActive: Boolean,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a product is deleted from the system.
 *
 * @property reason Reason for deletion (e.g., "OUT_OF_STOCK", "DISCONTINUED", "USER_REQUESTED")
 */
data class ProductDeleted(
    override val aggregateId: String,
    val reason: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a product is published (made available for sale).
 *
 * @property title Product title
 * @property handle Product handle/slug
 * @property status Current status of the product (usually "PUBLISHED")
 */
data class ProductPublished(
    override val aggregateId: String,
    val title: String,
    val handle: String,
    val status: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

