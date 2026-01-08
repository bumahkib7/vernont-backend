package com.vernont.domain.customer

import com.vernont.domain.auth.User
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.Email
import java.math.BigDecimal
import java.time.Instant

/**
 * Customer - Represents a customer who can place orders
 *
 * Design:
 * - Guest customers: hasAccount = false, no userId (email, firstName, lastName stored here)
 * - Registered customers: hasAccount = true, linked to User via userId
 *   - When linked, User is the source of truth for email, firstName, lastName
 *   - Customer-specific data: addresses, phone, groups, orders
 *
 * This design allows:
 * - Guest checkout without account creation
 * - Account creation after guest checkout (linking existing orders)
 * - Staff users (User without Customer record)
 * - Customer users (User with Customer record)
 */
@Entity
@Table(
    name = "customer",
    indexes = [
        Index(name = "idx_customer_email", columnList = "email"),
        Index(name = "idx_customer_user_id", columnList = "user_id", unique = true),
        Index(name = "idx_customer_phone", columnList = "phone"),
        Index(name = "idx_customer_has_account", columnList = "has_account"),
        Index(name = "idx_customer_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_customer_tier", columnList = "tier"),
        Index(name = "idx_customer_status", columnList = "status"),
        Index(name = "idx_customer_total_spent", columnList = "total_spent")
    ]
)
@NamedEntityGraph(
    name = "Customer.full",
    attributeNodes = [
        NamedAttributeNode("addresses"),
        NamedAttributeNode("groups"),
        NamedAttributeNode("user")
    ]
)
@NamedEntityGraph(
    name = "Customer.withAddresses",
    attributeNodes = [
        NamedAttributeNode("addresses")
    ]
)
@NamedEntityGraph(
    name = "Customer.withGroups",
    attributeNodes = [
        NamedAttributeNode("groups")
    ]
)
@NamedEntityGraph(
    name = "Customer.withUser",
    attributeNodes = [
        NamedAttributeNode("user")
    ]
)
class Customer : BaseEntity() {

    /**
     * User account link (null for guest customers)
     * When set, user becomes source of truth for email, firstName, lastName
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    var user: User? = null

    /**
     * Email - for guest customers or cached from user
     * For registered customers, sync from user.email
     */
    @Email
    @Column(nullable = false)
    var email: String = ""

    /**
     * First name - for guest customers or cached from user
     * For registered customers, sync from user.firstName
     */
    @Column
    var firstName: String? = null

    /**
     * Last name - for guest customers or cached from user
     * For registered customers, sync from user.lastName
     */
    @Column
    var lastName: String? = null

    /**
     * Phone number - customer-specific field
     */
    @Column
    var phone: String? = null

    /**
     * Has account - true if linked to a User
     */
    @Column(nullable = false)
    var hasAccount: Boolean = false

    @Column
    var billingAddressId: String? = null

    @OneToMany(mappedBy = "customer", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var addresses: MutableSet<CustomerAddress> = mutableSetOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "customer_group_customers",
        joinColumns = [JoinColumn(name = "customer_id")],
        inverseJoinColumns = [JoinColumn(name = "customer_group_id")]
    )
    var groups: MutableSet<CustomerGroup> = mutableSetOf()

    // ==========================================================================
    // Tier and Status Fields
    // ==========================================================================

    /**
     * Customer loyalty tier based on total spend
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var tier: CustomerTier = CustomerTier.BRONZE

    /**
     * If true, tier was manually set and won't auto-upgrade
     */
    @Column(nullable = false)
    var tierOverride: Boolean = false

    /**
     * Cached total amount spent (in cents for precision)
     * Updated when orders are completed
     */
    @Column(nullable = false, precision = 19, scale = 2)
    var totalSpent: BigDecimal = BigDecimal.ZERO

    /**
     * Total number of completed orders
     */
    @Column(nullable = false)
    var orderCount: Int = 0

    /**
     * Account status (ACTIVE, SUSPENDED, BANNED)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CustomerStatus = CustomerStatus.ACTIVE

    /**
     * When the account was suspended
     */
    @Column
    var suspendedAt: Instant? = null

    /**
     * Reason for suspension
     */
    @Column(columnDefinition = "TEXT")
    var suspendedReason: String? = null

    /**
     * Last login timestamp
     */
    @Column
    var lastLoginAt: Instant? = null

    /**
     * Last order timestamp
     */
    @Column
    var lastOrderAt: Instant? = null

    /**
     * Internal notes about this customer (admin only)
     */
    @Column(columnDefinition = "TEXT")
    var internalNotes: String? = null

    fun getFullName(): String {
        return when {
            firstName != null && lastName != null -> "$firstName $lastName"
            firstName != null -> firstName!!
            lastName != null -> lastName!!
            else -> email
        }
    }

    fun addAddress(address: CustomerAddress) {
        addresses.add(address)
        address.customer = this
    }

    fun removeAddress(address: CustomerAddress) {
        addresses.remove(address)
        address.customer = null
        if (billingAddressId == address.id) {
            billingAddressId = null
        }
    }

    fun setBillingAddress(address: CustomerAddress) {
        require(addresses.contains(address)) { "Address must belong to this customer" }
        billingAddressId = address.id
    }

    fun getBillingAddress(): CustomerAddress? {
        return addresses.find { it.id == billingAddressId }
    }

    fun addToGroup(group: CustomerGroup) {
        groups.add(group)
        group.customers.add(this)
    }

    fun removeFromGroup(group: CustomerGroup) {
        groups.remove(group)
        group.customers.remove(this)
    }

    fun isInGroup(groupName: String): Boolean {
        return groups.any { it.name == groupName }
    }

    /**
     * Link customer to user account
     * Syncs email, firstName, lastName from user
     */
    fun linkToUser(user: User) {
        this.user = user
        this.hasAccount = true
        syncFromUser()
    }

    /**
     * Unlink from user account (convert to guest)
     */
    fun unlinkFromUser() {
        this.user = null
        this.hasAccount = false
    }

    /**
     * Sync customer fields from linked user
     * Call this when user updates their profile
     */
    fun syncFromUser() {
        user?.let {
            this.email = it.email
            this.firstName = it.firstName
            this.lastName = it.lastName
        }
    }

    /**
     * Get effective email from user if linked, otherwise return customer email
     */
    fun getEffectiveEmail(): String {
        return user?.email ?: email
    }

    /**
     * Get effective first name from user if linked, otherwise return customer first name
     */
    fun getEffectiveFirstName(): String? {
        return user?.firstName ?: firstName
    }

    /**
     * Get effective last name from user if linked, otherwise return customer last name
     */
    fun getEffectiveLastName(): String? {
        return user?.lastName ?: lastName
    }

    fun hasAccountCreated(): Boolean = hasAccount

    // ==========================================================================
    // Status Management
    // ==========================================================================

    /**
     * Suspend the customer account
     */
    fun suspend(reason: String) {
        this.status = CustomerStatus.SUSPENDED
        this.suspendedAt = Instant.now()
        this.suspendedReason = reason
    }

    /**
     * Activate/reactivate the customer account
     */
    fun activate() {
        this.status = CustomerStatus.ACTIVE
        this.suspendedAt = null
        this.suspendedReason = null
    }

    /**
     * Ban the customer account
     */
    fun ban(reason: String) {
        this.status = CustomerStatus.BANNED
        this.suspendedAt = Instant.now()
        this.suspendedReason = reason
    }

    /**
     * Check if customer can place orders
     */
    fun canPlaceOrders(): Boolean = status.canOrder

    // ==========================================================================
    // Tier Management
    // ==========================================================================

    /**
     * Update total spent and potentially upgrade tier
     * Call this when an order is completed
     */
    fun recordOrderCompleted(orderTotal: BigDecimal) {
        this.totalSpent = this.totalSpent.add(orderTotal)
        this.orderCount++
        this.lastOrderAt = Instant.now()

        // Auto-upgrade tier if not manually overridden
        if (!tierOverride) {
            val newTier = CustomerTier.forSpend(totalSpent)
            if (newTier.ordinal > tier.ordinal) {
                tier = newTier
            }
        }
    }

    /**
     * Manually set tier (with override flag)
     */
    fun setTierManually(newTier: CustomerTier) {
        this.tier = newTier
        this.tierOverride = true
    }

    /**
     * Clear tier override, allowing auto-tier based on spend
     */
    fun clearTierOverride() {
        this.tierOverride = false
        this.tier = CustomerTier.forSpend(totalSpent)
    }

    /**
     * Get the spend needed to reach the next tier
     */
    fun getSpendToNextTier(): BigDecimal? {
        return CustomerTier.spendToNextTier(totalSpent)
    }

    /**
     * Record customer login
     */
    fun recordLogin() {
        this.lastLoginAt = Instant.now()
    }
}
