package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Event published when a new cart is created
 * Matches Medusa's cart.created event structure
 */
data class CartCreatedEvent(
    override val aggregateId: String,
    val customerId: String?,
    val email: String?,
    val regionId: String?,
    val currencyCode: String,
    val itemCount: Int,
    val total: BigDecimal,
    val timestamp: Instant,
    val eventType: String = "cart.created",
    val eventVersion: String = "1.0"
) : DomainEvent(
    aggregateId = aggregateId,
    occurredAt = timestamp,
    version = 1
)