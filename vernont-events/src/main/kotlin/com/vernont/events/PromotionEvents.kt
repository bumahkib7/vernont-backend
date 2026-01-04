package com.vernont.events

import java.math.BigDecimal
import java.time.Instant

/**
 * Promotion-related domain events.
 */

/**
 * Fired when a new promotion is created.
 *
 * @property code Promotion code
 * @property type Type of discount (percentage, fixed amount, etc.)
 * @property value Discount value
 * @property validFrom Start date of promotion validity
 * @property validUntil End date of promotion validity
 */
data class PromotionCreated(
    override val aggregateId: String,
    val code: String,
    val type: String,
    val value: BigDecimal,
    val validFrom: Instant?,
    val validUntil: Instant?,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a promotion is activated.
 *
 * @property code Promotion code
 * @property activatedAt When the promotion was activated
 */
data class PromotionActivated(
    override val aggregateId: String,
    val code: String,
    val activatedAt: Instant,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a promotion expires or is deactivated.
 *
 * @property code Promotion code
 * @property reason Reason for expiration
 * @property expiredAt When the promotion expired
 */
data class PromotionExpired(
    override val aggregateId: String,
    val code: String,
    val reason: String,
    val expiredAt: Instant,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a discount is applied to an order.
 *
 * @property orderId ID of the order
 * @property promotionId ID of the promotion
 * @property code Promotion code used
 * @property discountAmount Amount discounted
 * @property originalAmount Original order amount
 * @property newAmount New order amount after discount
 */
data class DiscountApplied(
    override val aggregateId: String,
    val orderId: String,
    val promotionId: String,
    val code: String,
    val discountAmount: BigDecimal,
    val originalAmount: BigDecimal,
    val newAmount: BigDecimal,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)

/**
 * Fired when a promotion is applied to a cart.
 *
 * @property cartId ID of the cart
 * @property promotionCode Promotion code applied
 * @property discountAmount Amount discounted
 * @property timestamp When the promotion was applied
 */
data class CartPromotionAppliedEvent(
    override val aggregateId: String,
    val cartId: String,
    val promotionCode: String,
    val discountAmount: BigDecimal,
    val timestamp: Instant,
    override val eventId: String = java.util.UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1
) : DomainEvent(eventId, aggregateId, occurredAt, version)