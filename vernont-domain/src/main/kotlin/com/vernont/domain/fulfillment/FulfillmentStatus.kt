package com.vernont.domain.fulfillment

/**
 * Fulfillment status enumeration
 */
enum class FulfillmentStatus {
    /**
     * Fulfillment is in draft status (not yet confirmed)
     */
    DRAFT,
    
    /**
     * Fulfillment has been created but not yet processed
     */
    PENDING,

    /**
     * Fulfillment is being prepared
     */
    PROCESSING,

    /**
     * Fulfillment has been shipped
     */
    SHIPPED,

    /**
     * Fulfillment has been delivered
     */
    DELIVERED,

    /**
     * Fulfillment has been canceled
     */
    CANCELED,

    /**
     * Fulfillment has been partially shipped
     */
    PARTIALLY_SHIPPED,

    /**
     * Fulfillment returned by customer
     */
    RETURNED,

    /**
     * Fulfillment failed
     */
    FAILED
}
