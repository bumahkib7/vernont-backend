package com.vernont.application.payment

import com.vernont.application.config.StripeProperties
import com.stripe.Stripe
import com.stripe.model.PaymentIntent
import com.stripe.param.PaymentIntentCreateParams
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Stripe Payment Service
 * Handles all Stripe API interactions for payment processing
 */
@Service
@EnableConfigurationProperties(StripeProperties::class)
class StripeService(
    private val stripeProperties: StripeProperties
) {
    @PostConstruct
    fun init() {
        if (stripeProperties.isConfigured()) {
            Stripe.apiKey = stripeProperties.secretKey
            logger.info { "Stripe initialized successfully" }
        } else {
            logger.warn { "Stripe is not configured - payment processing will not work" }
        }
    }

    /**
     * Check if Stripe is properly configured and enabled
     */
    fun isAvailable(): Boolean = stripeProperties.enabled && stripeProperties.isConfigured()

    /**
     * Get the publishable key for frontend
     */
    fun getPublishableKey(): String = stripeProperties.publishableKey

    /**
     * Create a PaymentIntent for a cart checkout
     *
     * @param amount Amount in the currency's smallest unit (e.g., cents for USD, pence for GBP)
     * @param currencyCode ISO currency code (e.g., "gbp", "usd")
     * @param cartId The cart ID for reference
     * @param customerEmail Optional customer email for receipts
     * @param metadata Additional metadata to attach to the payment
     * @return PaymentIntentResult with client_secret for frontend
     */
    fun createPaymentIntent(
        amount: Long,
        currencyCode: String,
        cartId: String,
        customerEmail: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): PaymentIntentResult {
        if (!isAvailable()) {
            throw IllegalStateException("Stripe is not configured")
        }

        logger.info { "Creating PaymentIntent for cart: $cartId, amount: $amount $currencyCode" }

        try {
            val paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currencyCode.lowercase())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )
                .putMetadata("cart_id", cartId)

            // Add customer email if provided
            if (!customerEmail.isNullOrBlank()) {
                paramsBuilder.setReceiptEmail(customerEmail)
                paramsBuilder.putMetadata("customer_email", customerEmail)
            }

            // Add additional metadata
            metadata.forEach { (key, value) ->
                paramsBuilder.putMetadata(key, value)
            }

            val paymentIntent = PaymentIntent.create(paramsBuilder.build())

            logger.info { "PaymentIntent created: ${paymentIntent.id} for cart: $cartId" }

            return PaymentIntentResult(
                paymentIntentId = paymentIntent.id,
                clientSecret = paymentIntent.clientSecret,
                status = paymentIntent.status,
                amount = paymentIntent.amount,
                currencyCode = paymentIntent.currency
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to create PaymentIntent for cart: $cartId" }
            throw PaymentException("Failed to create payment: ${e.message}", e)
        }
    }

    /**
     * Retrieve a PaymentIntent by ID
     */
    fun getPaymentIntent(paymentIntentId: String): PaymentIntent {
        if (!isAvailable()) {
            throw IllegalStateException("Stripe is not configured")
        }

        return try {
            PaymentIntent.retrieve(paymentIntentId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve PaymentIntent: $paymentIntentId" }
            throw PaymentException("Failed to retrieve payment: ${e.message}", e)
        }
    }

    /**
     * Cancel a PaymentIntent
     * @return true if canceled successfully
     */
    fun cancelPaymentIntent(paymentIntentId: String): Boolean {
        if (!isAvailable()) {
            throw IllegalStateException("Stripe is not configured")
        }

        return try {
            val paymentIntent = PaymentIntent.retrieve(paymentIntentId)
            paymentIntent.cancel()
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel PaymentIntent: $paymentIntentId" }
            throw PaymentException("Failed to cancel payment: ${e.message}", e)
        }
    }

    /**
     * Check if a PaymentIntent is successful (paid)
     */
    fun isPaymentSuccessful(paymentIntentId: String): Boolean {
        return try {
            val paymentIntent = getPaymentIntent(paymentIntentId)
            paymentIntent.status == "succeeded"
        } catch (e: Exception) {
            logger.error(e) { "Failed to check payment status: $paymentIntentId" }
            false
        }
    }

    /**
     * Convert BigDecimal amount to Stripe's smallest unit (cents/pence)
     * Stripe expects amounts in the smallest currency unit
     */
    fun toStripeAmount(amount: BigDecimal): Long {
        return amount.multiply(BigDecimal(100)).toLong()
    }

    /**
     * Convert Stripe's smallest unit back to BigDecimal
     */
    fun fromStripeAmount(amount: Long): BigDecimal {
        return BigDecimal(amount).divide(BigDecimal(100))
    }
}

/**
 * Result of creating a PaymentIntent
 */
data class PaymentIntentResult(
    val paymentIntentId: String,
    val clientSecret: String,
    val status: String,
    val amount: Long,
    val currencyCode: String
)

/**
 * Custom exception for payment errors
 */
class PaymentException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
