package com.vernont.events

import java.time.Instant
import java.util.UUID

/**
 * Fired when a region is created.
 *
 * @property regionId        ID of the created region
 * @property name            Name of the region
 * @property currencyCode    Currency code (e.g. "usd", "eur")
 * @property automaticTaxes  Whether automatic taxes are enabled
 * @property countryCodes    List of ISO 2 country codes belonging to the region
 * @property paymentProviderIds     IDs of attached payment providers
 * @property fulfillmentProviderIds IDs of attached fulfillment providers
 */
data class RegionCreated(
    override val aggregateId: String,
    val regionId: String,
    val name: String,
    val currencyCode: String,
    val automaticTaxes: Boolean,
    val countryCodes: List<String> = emptyList(),
    val paymentProviderIds: List<String> = emptyList(),
    val fulfillmentProviderIds: List<String> = emptyList(),
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)
