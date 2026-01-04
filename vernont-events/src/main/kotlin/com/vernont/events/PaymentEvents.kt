package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Payment-related domain events.
 */

/**
 * Fired when a payment is successfully authorized.
 *
 * @property orderId ID of the associated order
 * @property amount Amount authorized
 * @property currency Currency code
 * @property providerId Payment provider ID
 * @property paymentMethodId Payment method ID used
 */
data class PaymentAuthorized(
    override val aggregateId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val providerId: String,
    val paymentMethodId: String?,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a payment is successfully captured.
 *
 * @property orderId ID of the associated order
 * @property amount Original authorized amount
 * @property currency Currency code
 * @property providerId Payment provider ID
 * @property capturedAmount Amount actually captured
 */
data class PaymentCaptured(
    override val aggregateId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val providerId: String,
    val capturedAmount: BigDecimal,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a payment fails.
 *
 * @property orderId ID of the associated order
 * @property amount Amount that failed
 * @property currency Currency code
 * @property reason Reason for failure
 * @property providerId Payment provider ID
 */
data class PaymentFailed(
    override val aggregateId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val reason: String,
    val providerId: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a refund is initiated.
 *
 * @property paymentId ID of the payment being refunded
 * @property orderId ID of the associated order
 * @property amount Refund amount
 * @property currency Currency code
 * @property reason Reason for refund
 */
data class RefundInitiated(
    override val aggregateId: String,
    val paymentId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val reason: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a refund is completed successfully.
 *
 * @property paymentId ID of the payment being refunded
 * @property orderId ID of the associated order
 * @property amount Refund amount
 * @property currency Currency code
 * @property providerId Payment provider ID
 */
data class RefundCompleted(
    override val aggregateId: String,
    val paymentId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val providerId: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a payment is created
 */
data class PaymentCreated(
    override val aggregateId: String,
    val paymentId: String,
    val orderId: String?,
    val amount: BigDecimal,
    val currencyCode: String,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a payment is refunded
 */
data class PaymentRefunded(
    override val aggregateId: String,
    val paymentId: String,
    val orderId: String?,
    val refundId: String,
    val amount: BigDecimal,
    val createdBy: String?,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a payment is canceled
 */
data class PaymentCanceled(
    override val aggregateId: String,
    val paymentId: String,
    val orderId: String?,
    val reason: String?,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)