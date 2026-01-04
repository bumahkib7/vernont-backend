package com.vernont.events

import java.time.Instant

class ProductCreatedEvent(
    override val aggregateId: String,
    val productId: String,
    val brandId: String?,
    val title: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)
