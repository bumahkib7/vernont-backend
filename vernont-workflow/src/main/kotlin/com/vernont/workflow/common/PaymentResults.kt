package com.vernont.workflow.common

import java.math.BigDecimal

/**
 * Result of payment authorization
 */
data class PaymentAuthorizationResult(
    val success: Boolean,
    val authId: String? = null,
    val amount: BigDecimal? = null,
    val externalId: String? = null,
    val requiresMore: Boolean = false,
    val authorizationData: Map<String, Any>? = null,
    val providerData: Map<String, Any?>? = null,
    val error: String? = null
)

/**
 * Result of payment capture
 */
data class PaymentCaptureResult(
    val success: Boolean,
    val captureId: String? = null,
    val amount: BigDecimal? = null,
    val capturedAmount: BigDecimal? = null,
    val providerData: Map<String, Any?>? = null,
    val error: String? = null
)