package com.vernont.domain.customer

import com.vernont.domain.auth.User
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

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
        Index(name = "idx_customer_deleted_at", columnList = "deleted_at")
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
}
