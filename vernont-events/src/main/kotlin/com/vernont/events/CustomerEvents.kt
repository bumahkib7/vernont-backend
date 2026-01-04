package com.vernont.events

import java.time.Instant

/**
 * Customer-related domain events.
 */

/**
 * Fired when a new customer registers in the system.
 *
 * @property email Customer email address
 * @property firstName Customer first name
 * @property lastName Customer last name
 * @property phone Customer phone number (optional)
 * @property address Customer address (optional)
 */
data class CustomerRegistered(
    override val aggregateId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val address: String? = null,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when an existing customer's profile is updated.
 *
 * @property email Updated email address
 * @property firstName Updated first name
 * @property lastName Updated last name
 * @property phone Updated phone number
 * @property address Updated address
 * @property isActive Whether the customer account is active
 */
data class CustomerUpdated(
    override val aggregateId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val address: String?,
    val isActive: Boolean,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a new customer is created.
 *
 * @property email Customer email
 * @property firstName Customer first name
 * @property lastName Customer last name
 * @property hasAccount Whether customer has an account
 */
data class CustomerCreated(
    override val aggregateId: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val hasAccount: Boolean,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a customer is deleted.
 *
 * @property email Customer email
 * @property deletedAt When the customer was deleted
 */
data class CustomerDeleted(
    override val aggregateId: String,
    val email: String,
    val deletedAt: Instant,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a customer account is created (customer with hasAccount = true).
 *
 * @property customerId Customer ID
 * @property email Customer email
 * @property userId User ID linked to customer
 */
data class CustomerAccountCreated(
    override val aggregateId: String,
    val customerId: String,
    val email: String,
    val userId: String?,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a customer address is created.
 *
 * @property customerId Customer ID
 * @property addressId Address ID
 * @property countryCode Country code
 * @property city City
 */
data class CustomerAddressCreated(
    override val aggregateId: String,
    val customerId: String,
    val addressId: String,
    val countryCode: String,
    val city: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a customer address is updated.
 *
 * @property customerId Customer ID
 * @property addressId Address ID
 */
data class CustomerAddressUpdated(
    override val aggregateId: String,
    val customerId: String,
    val addressId: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a customer address is deleted.
 *
 * @property customerId Customer ID
 * @property addressId Address ID
 * @property deletedAt When the address was deleted
 */
data class CustomerAddressDeleted(
    override val aggregateId: String,
    val customerId: String,
    val addressId: String,
    val deletedAt: Instant,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)
