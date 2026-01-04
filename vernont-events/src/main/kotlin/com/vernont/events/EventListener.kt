package com.vernont.events

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Sample event listener demonstrating how to listen to domain events.
 *
 * This is a reference implementation showing best practices for:
 * - Using Spring's @EventListener annotation
 * - Handling different event types
 * - Logging and error handling
 * - Async event processing (optional)
 *
 * In a real application, you would create multiple listener components
 * for different domains/concerns (e.g., NotificationListener, AnalyticsListener, etc.)
 */
@Component
class SampleDomainEventListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    // Product Events

    /**
     * Listens for ProductCreated events.
     * Example: Update search index, trigger notifications, etc.
     */
    @EventListener
    fun onProductCreated(event: ProductCreated) {
        logger.info("Product created: {} (id: {}, title: {}, handle: {}, status: {})",
            event.eventId,
            event.aggregateId,
            event.title,
            event.handle,
            event.status)

        // TODO: Implement business logic
        // Examples:
        // - Update search index
        // - Trigger product availability notifications
        // - Update analytics
        // - Send product created webhook
    }

    /**
     * Listens for ProductUpdated events.
     */
    @EventListener
    fun onProductUpdated(event: ProductUpdated) {
        logger.info("Product updated: {} (id: {}, isActive: {})",
            event.eventId,
            event.aggregateId,
            event.isActive)

        // TODO: Implement business logic
        // Examples:
        // - Update search index
        // - Notify customers subscribed to this product
        // - Update inventory management system
    }

    /**
     * Listens for ProductDeleted events.
     */
    @EventListener
    fun onProductDeleted(event: ProductDeleted) {
        logger.info("Product deleted: {} (id: {}, reason: {})",
            event.eventId,
            event.aggregateId,
            event.reason)

        // TODO: Implement business logic
        // Examples:
        // - Remove from search index
        // - Notify customers about deletion
        // - Archive product information
        // - Clean up related data
    }

    // Order Events

    /**
     * Listens for OrderCreated events.
     */
    @EventListener
    fun onOrderCreated(event: OrderCreated) {
        logger.info("Order created: {} (id: {}, customerId: {}, amount: {})",
            event.eventId,
            event.aggregateId,
            event.customerId,
            event.totalAmount)

        // TODO: Implement business logic
        // Examples:
        // - Send order confirmation email
        // - Reserve inventory
        // - Trigger payment processing
        // - Update customer order history
        // - Send webhook notifications
    }

    /**
     * Listens for OrderCompleted events.
     */
    @EventListener
    fun onOrderCompleted(event: OrderCompleted) {
        logger.info("Order completed: {} (id: {}, customerId: {})",
            event.eventId,
            event.aggregateId,
            event.customerId)

        // TODO: Implement business logic
        // Examples:
        // - Send shipment notification
        // - Update customer loyalty points
        // - Trigger fulfillment process
        // - Update analytics
    }

    /**
     * Listens for OrderCancelled events.
     */
    @EventListener
    fun onOrderCancelled(event: OrderCancelled) {
        logger.info("Order cancelled: {} (id: {}, reason: {}, refund: {})",
            event.eventId,
            event.aggregateId,
            event.reason,
            event.refundAmount)

        // TODO: Implement business logic
        // Examples:
        // - Process refund
        // - Release reserved inventory
        // - Send cancellation email
        // - Update order status in fulfillment system
    }

    // Customer Events

    /**
     * Listens for CustomerRegistered events.
     */
    @EventListener
    fun onCustomerRegistered(event: CustomerRegistered) {
        logger.info("Customer registered: {} (id: {}, email: {})",
            event.eventId,
            event.aggregateId,
            event.email)

        // TODO: Implement business logic
        // Examples:
        // - Send welcome email
        // - Create customer profile in marketing system
        // - Initialize customer preferences
        // - Send welcome offer/coupon
    }

    /**
     * Listens for CustomerUpdated events.
     */
    @EventListener
    fun onCustomerUpdated(event: CustomerUpdated) {
        logger.info("Customer updated: {} (id: {}, isActive: {})",
            event.eventId,
            event.aggregateId,
            event.isActive)

        // TODO: Implement business logic
        // Examples:
        // - Update customer profile in external systems
        // - Sync with CRM
        // - Notify customer of profile changes
    }

    // Cart Events

    /**
     * Listens for CartCreated events.
     */
    @EventListener
    fun onCartCreated(event: CartCreated) {
        logger.info("Cart created: {} (id: {}, customerId: {})",
            event.eventId,
            event.aggregateId,
            event.customerId)

        // TODO: Implement business logic
        // Examples:
        // - Initialize cart in cache
        // - Set expiration time for abandoned cart
        // - Track shopping session
    }

    /**
     * Listens for CartItemAdded events.
     */
    @EventListener
    fun onCartItemAdded(event: CartItemAdded) {
        logger.info("Item added to cart: {} (cartId: {}, productId: {}, quantity: {})",
            event.eventId,
            event.aggregateId,
            event.productId,
            event.quantity)

        // TODO: Implement business logic
        // Examples:
        // - Update cart in cache
        // - Trigger "add to cart" analytics
        // - Check inventory levels
        // - Update recommendation engine
    }

    /**
     * Listens for CartItemRemoved events.
     */
    @EventListener
    fun onCartItemRemoved(event: CartItemRemoved) {
        logger.info("Item removed from cart: {} (cartId: {}, productId: {}, quantity: {})",
            event.eventId,
            event.aggregateId,
            event.productId,
            event.quantity)

        // TODO: Implement business logic
        // Examples:
        // - Update cart in cache
        // - Trigger "remove from cart" analytics
        // - Update abandonment tracking
    }
}
