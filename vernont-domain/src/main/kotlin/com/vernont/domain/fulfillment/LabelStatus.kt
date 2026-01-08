package com.vernont.domain.fulfillment

/**
 * Status of a shipping label purchase/lifecycle
 */
enum class LabelStatus {
    /**
     * Label has not been purchased yet
     */
    NONE,

    /**
     * Label purchase is pending (external call in progress)
     */
    PENDING_PURCHASE,

    /**
     * Label has been purchased successfully
     */
    PURCHASED,

    /**
     * Label has been voided/refunded successfully
     */
    VOIDED,

    /**
     * Label void/refund failed - requires manual intervention
     */
    VOID_FAILED
}
