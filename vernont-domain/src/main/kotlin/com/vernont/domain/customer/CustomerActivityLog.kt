package com.vernont.domain.customer

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

/**
 * Audit log for customer activity.
 * Records all significant events related to a customer for tracking and compliance.
 */
@Entity
@Table(
    name = "customer_activity_log",
    indexes = [
        Index(name = "idx_customer_activity_customer", columnList = "customer_id"),
        Index(name = "idx_customer_activity_type", columnList = "activity_type"),
        Index(name = "idx_customer_activity_occurred", columnList = "occurred_at")
    ]
)
class CustomerActivityLog {

    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString()

    /**
     * The customer this activity relates to
     */
    @Column(name = "customer_id", nullable = false, length = 36)
    var customerId: String = ""

    /**
     * Type of activity
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 50)
    var activityType: CustomerActivityType = CustomerActivityType.ACCOUNT_CREATED

    /**
     * Human-readable description of the activity
     */
    @Column(columnDefinition = "TEXT")
    var description: String? = null

    /**
     * Additional metadata as JSON (e.g., order ID, old/new values, etc.)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: Map<String, Any>? = null

    /**
     * Who performed this action (admin user ID or "system" or "customer")
     */
    @Column(name = "performed_by", length = 36)
    var performedBy: String? = null

    /**
     * When this activity occurred
     */
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now()

    /**
     * Record creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    companion object {
        fun create(
            customerId: String,
            activityType: CustomerActivityType,
            description: String? = null,
            metadata: Map<String, Any>? = null,
            performedBy: String? = null,
            occurredAt: Instant = Instant.now()
        ): CustomerActivityLog {
            return CustomerActivityLog().apply {
                this.customerId = customerId
                this.activityType = activityType
                this.description = description
                this.metadata = metadata
                this.performedBy = performedBy
                this.occurredAt = occurredAt
            }
        }

        // Factory methods for common activity types

        fun accountCreated(customerId: String, performedBy: String? = null): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.ACCOUNT_CREATED,
                description = "Customer account created",
                performedBy = performedBy
            )
        }

        fun accountSuspended(customerId: String, reason: String, performedBy: String): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.ACCOUNT_SUSPENDED,
                description = "Account suspended: $reason",
                metadata = mapOf("reason" to reason),
                performedBy = performedBy
            )
        }

        fun accountActivated(customerId: String, performedBy: String): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.ACCOUNT_ACTIVATED,
                description = "Account activated",
                performedBy = performedBy
            )
        }

        fun tierChanged(
            customerId: String,
            previousTier: CustomerTier,
            newTier: CustomerTier,
            reason: String?,
            performedBy: String
        ): CustomerActivityLog {
            val isUpgrade = newTier.ordinal > previousTier.ordinal
            return create(
                customerId = customerId,
                activityType = if (performedBy == "system") CustomerActivityType.TIER_UPGRADED else CustomerActivityType.TIER_CHANGED_MANUALLY,
                description = "Tier ${if (isUpgrade) "upgraded" else "changed"} from ${previousTier.displayName} to ${newTier.displayName}",
                metadata = mapOf(
                    "previousTier" to previousTier.name,
                    "newTier" to newTier.name,
                    "reason" to (reason ?: "")
                ),
                performedBy = performedBy
            )
        }

        fun emailSent(customerId: String, subject: String, performedBy: String): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.EMAIL_SENT,
                description = "Email sent: $subject",
                metadata = mapOf("subject" to subject),
                performedBy = performedBy
            )
        }

        fun giftCardSent(customerId: String, amount: Int, giftCardId: String, performedBy: String): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.GIFT_CARD_SENT,
                description = "Gift card sent: ${amount / 100.0}",
                metadata = mapOf("amount" to amount, "giftCardId" to giftCardId),
                performedBy = performedBy
            )
        }

        fun orderPlaced(customerId: String, orderId: String, orderTotal: Int): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.ORDER_PLACED,
                description = "Order placed: ${orderTotal / 100.0}",
                metadata = mapOf("orderId" to orderId, "total" to orderTotal),
                performedBy = "customer"
            )
        }

        fun passwordResetRequested(customerId: String, performedBy: String): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.PASSWORD_RESET_REQUESTED,
                description = "Password reset requested",
                performedBy = performedBy
            )
        }

        fun login(customerId: String, ipAddress: String? = null): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.LOGIN,
                description = "Customer logged in",
                metadata = ipAddress?.let { mapOf("ipAddress" to it) },
                performedBy = "customer"
            )
        }

        fun addedToGroup(customerId: String, groupId: String, groupName: String, performedBy: String): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.ADDED_TO_GROUP,
                description = "Added to group: $groupName",
                metadata = mapOf("groupId" to groupId, "groupName" to groupName),
                performedBy = performedBy
            )
        }

        fun removedFromGroup(customerId: String, groupId: String, groupName: String, performedBy: String): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.REMOVED_FROM_GROUP,
                description = "Removed from group: $groupName",
                metadata = mapOf("groupId" to groupId, "groupName" to groupName),
                performedBy = performedBy
            )
        }

        fun noteAdded(customerId: String, performedBy: String): CustomerActivityLog {
            return create(
                customerId = customerId,
                activityType = CustomerActivityType.NOTE_ADDED,
                description = "Internal note added",
                performedBy = performedBy
            )
        }
    }
}
