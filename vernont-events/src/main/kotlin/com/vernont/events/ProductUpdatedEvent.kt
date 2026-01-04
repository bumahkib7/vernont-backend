package com.vernont.events

import java.time.Instant

class ProductUpdatedEvent(
    override val aggregateId: String,
    title: String,
    handle: String,
    status: String,
    timestamp: Instant
): DomainEvent(aggregateId = aggregateId)