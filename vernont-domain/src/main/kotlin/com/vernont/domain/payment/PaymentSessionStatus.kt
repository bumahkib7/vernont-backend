package com.vernont.domain.payment

/**
 * Payment Session Status enumeration - equivalent to Medusa's PaymentSessionStatus
 */
enum class PaymentSessionStatus {
    /**
     * Payment session has been created but not yet processed
     */
    PENDING,

    /**
     * Payment session has been authorized but not captured
     */
    AUTHORIZED,

    /**
     * Payment session has been captured (payment completed)
     */
    CAPTURED,

    /**
     * Payment session has been canceled
     */
    CANCELED,

    /**
     * Payment session failed or encountered an error
     */
    ERROR,

    /**
     * Payment session requires action (e.g., 3D Secure authentication)
     */
    REQUIRES_ACTION
}