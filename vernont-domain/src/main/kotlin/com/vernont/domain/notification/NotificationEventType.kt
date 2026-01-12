package com.vernont.domain.notification

/**
 * Types of events that can trigger notifications.
 */
enum class NotificationEventType(
    val displayName: String,
    val entityType: String?,
    val defaultBrowserEnabled: Boolean = true,
    val defaultInAppEnabled: Boolean = true
) {
    // Order events
    ORDER_CREATED("New Order", "ORDER"),
    ORDER_PAID("Order Paid", "ORDER"),
    ORDER_CANCELLED("Order Cancelled", "ORDER"),
    ORDER_FULFILLED("Order Fulfilled", "ORDER"),
    REFUND_CREATED("Refund Created", "ORDER"),

    // Customer events
    CUSTOMER_REGISTERED("New Customer", "CUSTOMER"),

    // Product events
    LOW_STOCK_ALERT("Low Stock Alert", "PRODUCT", defaultBrowserEnabled = false, defaultInAppEnabled = false),
    PRODUCT_OUT_OF_STOCK("Product Out of Stock", "PRODUCT"),

    // Fulfillment events
    FULFILLMENT_CREATED("Fulfillment Created", "ORDER", defaultBrowserEnabled = false, defaultInAppEnabled = false),
    FULFILLMENT_SHIPPED("Fulfillment Shipped", "ORDER", defaultBrowserEnabled = false, defaultInAppEnabled = false),
    FULFILLMENT_DELIVERED("Fulfillment Delivered", "ORDER", defaultBrowserEnabled = false, defaultInAppEnabled = false),

    // Security events (high/critical only)
    SECURITY_ALERT("Security Alert", "SECURITY_EVENT");

    companion object {
        fun fromString(value: String): NotificationEventType? {
            return entries.find { it.name == value }
        }
    }
}

/**
 * Entity types for navigation purposes.
 */
enum class NotificationEntityType {
    ORDER,
    CUSTOMER,
    PRODUCT,
    SECURITY_EVENT
}

/**
 * Notification channel types.
 */
enum class NotificationChannel {
    BROWSER,
    IN_APP
}
