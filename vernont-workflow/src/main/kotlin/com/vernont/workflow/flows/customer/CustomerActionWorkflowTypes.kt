package com.vernont.workflow.flows.customer

/**
 * Input/Output types for customer action workflows
 */

// =============================================================================
// Send Customer Email
// =============================================================================

data class SendCustomerEmailInput(
    val customerId: String,
    val email: String,
    val subject: String,
    val body: String
)

data class SendCustomerEmailOutput(
    val success: Boolean,
    val messageId: String? = null
)

// =============================================================================
// Send Gift Card
// =============================================================================

data class SendGiftCardInput(
    val customerId: String,
    val customerEmail: String,
    val customerName: String,
    val amount: Int, // In cents
    val currencyCode: String = "GBP",
    val message: String? = null,
    val expiresInDays: Int? = null // Optional expiration
)

data class SendGiftCardOutput(
    val giftCardId: String,
    val giftCardCode: String,
    val amount: Int,
    val emailSent: Boolean
)

// =============================================================================
// Reset Customer Password
// =============================================================================

data class ResetCustomerPasswordInput(
    val customerId: String,
    val email: String
)

data class ResetCustomerPasswordOutput(
    val success: Boolean,
    val resetTokenSent: Boolean
)

// =============================================================================
// Suspend Customer
// =============================================================================

data class SuspendCustomerInput(
    val customerId: String,
    val reason: String,
    val performedBy: String
)

data class SuspendCustomerOutput(
    val success: Boolean,
    val suspendedAt: String
)

// =============================================================================
// Change Tier
// =============================================================================

data class ChangeTierInput(
    val customerId: String,
    val newTier: String,
    val reason: String?,
    val performedBy: String
)

data class ChangeTierOutput(
    val success: Boolean,
    val previousTier: String,
    val newTier: String
)

// =============================================================================
// Activate Customer
// =============================================================================

data class ActivateCustomerInput(
    val customerId: String,
    val performedBy: String
)

data class ActivateCustomerOutput(
    val success: Boolean,
    val activatedAt: String
)
