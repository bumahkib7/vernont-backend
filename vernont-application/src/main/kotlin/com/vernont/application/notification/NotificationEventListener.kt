package com.vernont.application.notification

import com.vernont.domain.notification.NotificationEntityType
import com.vernont.domain.notification.NotificationEventType
import com.vernont.events.*
import com.vernont.repository.auth.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Event listener that creates notifications for admin users when domain events occur.
 * Listens to Spring application events and creates notifications for all admin users
 * who have the relevant notification type enabled.
 */
@Component
class NotificationEventListener(
    private val notificationService: NotificationService,
    private val userRepository: UserRepository
) {

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    /**
     * Get all internal (admin) user IDs.
     */
    private fun getAdminUserIds(): List<String> {
        return userRepository.findAllInternalUsers().map { it.id }
    }

    // ============================================================================
    // Order Events
    // ============================================================================

    @Async
    @EventListener
    fun onOrderCreated(event: OrderCreated) {
        logger.info { "Handling OrderCreated event for order ${event.aggregateId}" }
        try {
            val adminUserIds = getAdminUserIds()
            val formattedAmount = currencyFormatter.format(event.totalAmount)

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.ORDER_CREATED,
                title = "New Order Received",
                message = "Order #${event.aggregateId.takeLast(8)} for $formattedAmount",
                entityType = NotificationEntityType.ORDER,
                entityId = event.aggregateId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for OrderCreated event" }
        }
    }

    @Async
    @EventListener
    fun onOrderCompleted(event: OrderCompleted) {
        logger.info { "Handling OrderCompleted event for order ${event.aggregateId}" }
        try {
            val adminUserIds = getAdminUserIds()
            val formattedAmount = currencyFormatter.format(event.totalAmount)

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.ORDER_PAID,
                title = "Order Payment Received",
                message = "Order #${event.aggregateId.takeLast(8)} - $formattedAmount paid",
                entityType = NotificationEntityType.ORDER,
                entityId = event.aggregateId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for OrderCompleted event" }
        }
    }

    @Async
    @EventListener
    fun onOrderCancelled(event: OrderCancelled) {
        logger.info { "Handling OrderCancelled event for order ${event.aggregateId}" }
        try {
            val adminUserIds = getAdminUserIds()

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.ORDER_CANCELLED,
                title = "Order Cancelled",
                message = "Order #${event.aggregateId.takeLast(8)} was cancelled: ${event.reason}",
                entityType = NotificationEntityType.ORDER,
                entityId = event.aggregateId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for OrderCancelled event" }
        }
    }

    @Async
    @EventListener
    fun onOrderCanceled(event: OrderCanceled) {
        logger.info { "Handling OrderCanceled event for order ${event.orderId}" }
        try {
            val adminUserIds = getAdminUserIds()

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.ORDER_CANCELLED,
                title = "Order Cancelled",
                message = "Order #${event.orderId.takeLast(8)} was cancelled: ${event.reason}",
                entityType = NotificationEntityType.ORDER,
                entityId = event.orderId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for OrderCanceled event" }
        }
    }

    // ============================================================================
    // Customer Events
    // ============================================================================

    @Async
    @EventListener
    fun onCustomerRegistered(event: CustomerRegistered) {
        logger.info { "Handling CustomerRegistered event for customer ${event.aggregateId}" }
        try {
            val adminUserIds = getAdminUserIds()

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.CUSTOMER_REGISTERED,
                title = "New Customer Registered",
                message = "${event.firstName} ${event.lastName} (${event.email})",
                entityType = NotificationEntityType.CUSTOMER,
                entityId = event.aggregateId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for CustomerRegistered event" }
        }
    }

    @Async
    @EventListener
    fun onCustomerCreated(event: CustomerCreated) {
        // Only notify for customers with accounts (not guest checkouts)
        if (!event.hasAccount) return

        logger.info { "Handling CustomerCreated event for customer ${event.aggregateId}" }
        try {
            val adminUserIds = getAdminUserIds()
            val name = listOfNotNull(event.firstName, event.lastName).joinToString(" ").ifBlank { "New customer" }

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.CUSTOMER_REGISTERED,
                title = "New Customer",
                message = "$name (${event.email})",
                entityType = NotificationEntityType.CUSTOMER,
                entityId = event.aggregateId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for CustomerCreated event" }
        }
    }

    // ============================================================================
    // Fulfillment Events
    // ============================================================================

    @Async
    @EventListener
    fun onFulfillmentCreated(event: FulfillmentCreated) {
        logger.info { "Handling FulfillmentCreated event for fulfillment ${event.aggregateId}" }
        try {
            val adminUserIds = getAdminUserIds()

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.FULFILLMENT_CREATED,
                title = "Fulfillment Created",
                message = "Fulfillment for order #${event.orderId.takeLast(8)}",
                entityType = NotificationEntityType.ORDER,
                entityId = event.orderId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for FulfillmentCreated event" }
        }
    }

    @Async
    @EventListener
    fun onFulfillmentShipped(event: FulfillmentShipped) {
        logger.info { "Handling FulfillmentShipped event for fulfillment ${event.aggregateId}" }
        try {
            val adminUserIds = getAdminUserIds()

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.FULFILLMENT_SHIPPED,
                title = "Fulfillment Shipped",
                message = "Order #${event.orderId.takeLast(8)} has been shipped",
                entityType = NotificationEntityType.ORDER,
                entityId = event.orderId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for FulfillmentShipped event" }
        }
    }

    // ============================================================================
    // Payment Events
    // ============================================================================

    @Async
    @EventListener
    fun onPaymentCaptured(event: PaymentCaptured) {
        logger.info { "Handling PaymentCaptured event for order ${event.orderId}" }
        try {
            val adminUserIds = getAdminUserIds()
            val formattedAmount = currencyFormatter.format(event.amount)

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.ORDER_PAID,
                title = "Payment Captured",
                message = "$formattedAmount captured for order #${event.orderId.takeLast(8)}",
                entityType = NotificationEntityType.ORDER,
                entityId = event.orderId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for PaymentCaptured event" }
        }
    }

    @Async
    @EventListener
    fun onRefundCompleted(event: RefundCompleted) {
        logger.info { "Handling RefundCompleted event for order ${event.orderId}" }
        try {
            val adminUserIds = getAdminUserIds()
            val formattedAmount = currencyFormatter.format(event.amount)

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.REFUND_CREATED,
                title = "Refund Completed",
                message = "$formattedAmount refunded for order #${event.orderId.takeLast(8)}",
                entityType = NotificationEntityType.ORDER,
                entityId = event.orderId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for RefundCompleted event" }
        }
    }

    @Async
    @EventListener
    fun onPaymentRefunded(event: PaymentRefunded) {
        val orderId = event.orderId ?: return
        logger.info { "Handling PaymentRefunded event for order $orderId" }
        try {
            val adminUserIds = getAdminUserIds()
            val formattedAmount = currencyFormatter.format(event.amount)

            notificationService.createNotificationsForUsers(
                userIds = adminUserIds,
                eventType = NotificationEventType.REFUND_CREATED,
                title = "Refund Issued",
                message = "$formattedAmount refunded for order #${orderId.takeLast(8)}",
                entityType = NotificationEntityType.ORDER,
                entityId = orderId
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create notifications for PaymentRefunded event" }
        }
    }
}
