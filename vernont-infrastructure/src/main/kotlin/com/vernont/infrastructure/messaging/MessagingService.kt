package com.vernont.infrastructure.messaging

/**
 * Abstraction for message publishing.
 * Implementations can use Kafka (Redpanda) for dev or SQS for production.
 */
interface MessagingService {

    /**
     * Publish an event to the messaging system.
     * @param topic The topic/queue name
     * @param key The partition key (e.g., inventoryItemId)
     * @param event The event object to publish (will be serialized to JSON)
     */
    fun publish(topic: String, key: String, event: Any)

    /**
     * Publish a raw message payload.
     * @param topic The topic/queue name
     * @param key Optional partition key
     * @param payload The message payload as string (JSON)
     */
    fun publishRaw(topic: String, key: String?, payload: String)
}

/**
 * Message topics/queues for the system
 */
object MessagingTopics {
    const val INVENTORY_EVENTS = "inventory-events"
    const val ORDER_EVENTS = "order-events"
    const val PRODUCT_EVENTS = "product-events"
    const val FULFILLMENT_EVENTS = "fulfillment-events"
    const val ADMIN_EVENTS = "admin-events"
}
