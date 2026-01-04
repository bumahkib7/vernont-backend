package com.vernont.domain.returns

/**
 * Status of a return request
 */
enum class ReturnStatus {
    /** Return has been requested by customer */
    REQUESTED,
    /** Return has been approved (auto-approved if within 14-day window) */
    APPROVED,
    /** Returned items have been received at warehouse */
    RECEIVED,
    /** Refund has been processed */
    REFUNDED,
    /** Return request was rejected */
    REJECTED,
    /** Return was canceled by customer */
    CANCELED
}

/**
 * Reason for return
 */
enum class ReturnReason {
    SIZE_TOO_SMALL,
    SIZE_TOO_LARGE,
    NOT_AS_DESCRIBED,
    QUALITY_ISSUE,
    CHANGED_MIND,
    WRONG_ITEM,
    DAMAGED,
    OTHER
}
