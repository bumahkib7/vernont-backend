package com.vernont.domain.giftcard

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.Instant

/**
 * Gift card entity for store credit and gifting.
 * Supports partial redemption (balance can be used across multiple orders).
 */
@Entity
@Table(
    name = "gift_card",
    indexes = [
        Index(name = "idx_gift_card_code", columnList = "code", unique = true),
        Index(name = "idx_gift_card_status", columnList = "status"),
        Index(name = "idx_gift_card_issued_to", columnList = "issued_to_customer_id"),
        Index(name = "idx_gift_card_expires_at", columnList = "expires_at")
    ]
)
class GiftCard : BaseEntity() {

    /**
     * Unique gift card code (16 characters, alphanumeric, formatted as XXXX-XXXX-XXXX-XXXX)
     */
    @Column(nullable = false, unique = true, length = 19)
    var code: String = ""

    /**
     * Initial amount loaded on the card (in minor currency units, e.g., cents/pence)
     */
    @Column(name = "initial_amount", nullable = false)
    var initialAmount: Int = 0

    /**
     * Remaining balance (in minor currency units)
     */
    @Column(name = "remaining_amount", nullable = false)
    var remainingAmount: Int = 0

    /**
     * Currency code (ISO 4217)
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = "GBP"

    /**
     * Current status of the gift card
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: GiftCardStatus = GiftCardStatus.ACTIVE

    /**
     * Expiration date (null = never expires)
     */
    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    /**
     * Customer this gift card was issued to (optional)
     */
    @Column(name = "issued_to_customer_id", length = 36)
    var issuedToCustomerId: String? = null

    /**
     * Admin user who issued the gift card
     */
    @Column(name = "issued_by_user_id", length = 36)
    var issuedByUserId: String? = null

    /**
     * Optional message to accompany the gift card
     */
    @Column(columnDefinition = "TEXT")
    var message: String? = null

    /**
     * Email address the gift card was sent to
     */
    @Column(name = "recipient_email")
    var recipientEmail: String? = null

    /**
     * Name of recipient (for display purposes)
     */
    @Column(name = "recipient_name")
    var recipientName: String? = null

    /**
     * Timestamp when the gift card was first redeemed
     */
    @Column(name = "first_redeemed_at")
    var firstRedeemedAt: Instant? = null

    /**
     * Timestamp when the gift card was fully redeemed
     */
    @Column(name = "fully_redeemed_at")
    var fullyRedeemedAt: Instant? = null

    /**
     * Customer who redeemed the gift card (may differ from issuedToCustomerId)
     */
    @Column(name = "redeemed_by_customer_id", length = 36)
    var redeemedByCustomerId: String? = null

    // ==========================================================================
    // Business Logic
    // ==========================================================================

    /**
     * Check if the gift card can be redeemed
     */
    fun canRedeem(): Boolean {
        if (!status.canRedeem) return false
        if (remainingAmount <= 0) return false
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false
        return true
    }

    /**
     * Check if the gift card is expired
     */
    fun isExpired(): Boolean {
        return expiresAt != null && Instant.now().isAfter(expiresAt)
    }

    /**
     * Redeem an amount from the gift card
     * @param amount Amount to redeem (in minor currency units)
     * @param customerId Customer performing the redemption
     * @return Actual amount redeemed (may be less than requested if balance is lower)
     */
    fun redeem(amount: Int, customerId: String): Int {
        require(canRedeem()) { "Gift card cannot be redeemed" }
        require(amount > 0) { "Redemption amount must be positive" }

        val amountToRedeem = minOf(amount, remainingAmount)
        remainingAmount -= amountToRedeem

        // Track first redemption
        if (firstRedeemedAt == null) {
            firstRedeemedAt = Instant.now()
            redeemedByCustomerId = customerId
        }

        // Update status if fully redeemed
        if (remainingAmount == 0) {
            status = GiftCardStatus.FULLY_REDEEMED
            fullyRedeemedAt = Instant.now()
        }

        return amountToRedeem
    }

    /**
     * Add balance to the gift card (for admin adjustments)
     */
    fun addBalance(amount: Int) {
        require(amount > 0) { "Amount must be positive" }
        remainingAmount += amount

        // Reactivate if it was fully redeemed
        if (status == GiftCardStatus.FULLY_REDEEMED && remainingAmount > 0) {
            status = GiftCardStatus.ACTIVE
            fullyRedeemedAt = null
        }
    }

    /**
     * Disable the gift card
     */
    fun disable() {
        status = GiftCardStatus.DISABLED
    }

    /**
     * Enable a disabled gift card
     */
    fun enable() {
        if (status == GiftCardStatus.DISABLED) {
            status = if (remainingAmount > 0) GiftCardStatus.ACTIVE else GiftCardStatus.FULLY_REDEEMED
        }
    }

    /**
     * Get balance as formatted string
     */
    fun getFormattedBalance(): String {
        val amount = remainingAmount / 100.0
        return when (currencyCode) {
            "GBP" -> "£%.2f".format(amount)
            "USD" -> "$%.2f".format(amount)
            "EUR" -> "€%.2f".format(amount)
            else -> "%.2f $currencyCode".format(amount)
        }
    }

    /**
     * Get initial amount as formatted string
     */
    fun getFormattedInitialAmount(): String {
        val amount = initialAmount / 100.0
        return when (currencyCode) {
            "GBP" -> "£%.2f".format(amount)
            "USD" -> "$%.2f".format(amount)
            "EUR" -> "€%.2f".format(amount)
            else -> "%.2f $currencyCode".format(amount)
        }
    }

    companion object {
        private val SECURE_RANDOM = SecureRandom()
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excluded O, 0, I, 1 for readability

        /**
         * Generate a unique gift card code in format XXXX-XXXX-XXXX-XXXX
         */
        fun generateCode(): String {
            val sb = StringBuilder()
            repeat(16) { i ->
                if (i > 0 && i % 4 == 0) sb.append('-')
                sb.append(CODE_CHARS[SECURE_RANDOM.nextInt(CODE_CHARS.length)])
            }
            return sb.toString()
        }

        /**
         * Create a new gift card
         */
        fun create(
            amount: Int,
            currencyCode: String = "GBP",
            issuedToCustomerId: String? = null,
            issuedByUserId: String,
            message: String? = null,
            recipientEmail: String? = null,
            recipientName: String? = null,
            expiresAt: Instant? = null
        ): GiftCard {
            require(amount > 0) { "Amount must be positive" }

            return GiftCard().apply {
                this.code = generateCode()
                this.initialAmount = amount
                this.remainingAmount = amount
                this.currencyCode = currencyCode
                this.status = GiftCardStatus.ACTIVE
                this.issuedToCustomerId = issuedToCustomerId
                this.issuedByUserId = issuedByUserId
                this.message = message
                this.recipientEmail = recipientEmail
                this.recipientName = recipientName
                this.expiresAt = expiresAt
            }
        }
    }
}
