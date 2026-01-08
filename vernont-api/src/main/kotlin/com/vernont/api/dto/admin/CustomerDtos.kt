package com.vernont.api.dto.admin

import com.vernont.domain.customer.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

// =============================================================================
// Response DTOs
// =============================================================================

/**
 * Full customer detail for admin view
 */
data class AdminCustomer(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val fullName: String,
    val phone: String?,
    val hasAccount: Boolean,
    val tier: String,
    val tierDisplay: String,
    val tierOverride: Boolean,
    val totalSpent: Int, // In cents
    val orderCount: Int,
    val status: String,
    val statusDisplay: String,
    val suspendedAt: OffsetDateTime?,
    val suspendedReason: String?,
    val lastLoginAt: OffsetDateTime?,
    val lastOrderAt: OffsetDateTime?,
    val internalNotes: String?,
    val billingAddressId: String?,
    val addresses: List<AdminCustomerAddress>,
    val groups: List<AdminCustomerGroupSummary>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        fun from(customer: Customer): AdminCustomer {
            return AdminCustomer(
                id = customer.id,
                email = customer.getEffectiveEmail(),
                firstName = customer.getEffectiveFirstName(),
                lastName = customer.getEffectiveLastName(),
                fullName = customer.getFullName(),
                phone = customer.phone,
                hasAccount = customer.hasAccount,
                tier = customer.tier.name,
                tierDisplay = customer.tier.displayName,
                tierOverride = customer.tierOverride,
                totalSpent = customer.totalSpent.multiply(BigDecimal(100)).toInt(),
                orderCount = customer.orderCount,
                status = customer.status.name,
                statusDisplay = customer.status.displayName,
                suspendedAt = customer.suspendedAt?.atOffset(ZoneOffset.UTC),
                suspendedReason = customer.suspendedReason,
                lastLoginAt = customer.lastLoginAt?.atOffset(ZoneOffset.UTC),
                lastOrderAt = customer.lastOrderAt?.atOffset(ZoneOffset.UTC),
                internalNotes = customer.internalNotes,
                billingAddressId = customer.billingAddressId,
                addresses = customer.addresses
                    .filter { it.deletedAt == null }
                    .map { AdminCustomerAddress.from(it, customer.billingAddressId) },
                groups = customer.groups
                    .filter { it.deletedAt == null }
                    .map { AdminCustomerGroupSummary.from(it) },
                createdAt = customer.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = customer.updatedAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

/**
 * Customer summary for list view
 */
data class AdminCustomerSummary(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val hasAccount: Boolean,
    val tier: String,
    val tierDisplay: String,
    val status: String,
    val statusDisplay: String,
    val totalSpent: Int,
    val orderCount: Int,
    val createdAt: OffsetDateTime
) {
    companion object {
        fun from(customer: Customer): AdminCustomerSummary {
            return AdminCustomerSummary(
                id = customer.id,
                email = customer.getEffectiveEmail(),
                fullName = customer.getFullName(),
                phone = customer.phone,
                hasAccount = customer.hasAccount,
                tier = customer.tier.name,
                tierDisplay = customer.tier.displayName,
                status = customer.status.name,
                statusDisplay = customer.status.displayName,
                totalSpent = customer.totalSpent.multiply(BigDecimal(100)).toInt(),
                orderCount = customer.orderCount,
                createdAt = customer.createdAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

/**
 * Customer group for admin view
 */
data class AdminCustomerGroup(
    val id: String,
    val name: String,
    val description: String?,
    val customerCount: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        fun from(group: CustomerGroup): AdminCustomerGroup {
            return AdminCustomerGroup(
                id = group.id,
                name = group.name,
                description = group.description,
                customerCount = group.getCustomerCount(),
                createdAt = group.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = group.updatedAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

/**
 * Simplified customer group for embedding in customer detail
 */
data class AdminCustomerGroupSummary(
    val id: String,
    val name: String,
    val description: String?,
    val memberCount: Int
) {
    companion object {
        fun from(group: CustomerGroup): AdminCustomerGroupSummary {
            return AdminCustomerGroupSummary(
                id = group.id,
                name = group.name,
                description = group.description,
                memberCount = group.getCustomerCount()
            )
        }
    }
}

/**
 * Customer activity log entry
 */
data class AdminCustomerActivity(
    val id: String,
    val customerId: String,
    val activityType: String,
    val activityDisplay: String,
    val category: String,
    val description: String?,
    val metadata: Map<String, Any>?,
    val performedBy: String?,
    val occurredAt: OffsetDateTime
) {
    companion object {
        fun from(log: CustomerActivityLog): AdminCustomerActivity {
            return AdminCustomerActivity(
                id = log.id,
                customerId = log.customerId,
                activityType = log.activityType.name,
                activityDisplay = log.activityType.displayName,
                category = log.activityType.category,
                description = log.description,
                metadata = log.metadata,
                performedBy = log.performedBy,
                occurredAt = log.occurredAt.atOffset(ZoneOffset.UTC)
            )
        }
    }
}

/**
 * Customer address for admin view
 */
data class AdminCustomerAddress(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val company: String?,
    val phone: String?,
    val address1: String,
    val address2: String?,
    val city: String,
    val province: String?,
    val postalCode: String?,
    val countryCode: String,
    val fullAddress: String,
    val isDefault: Boolean,
    val isBilling: Boolean
) {
    companion object {
        fun from(address: CustomerAddress, billingAddressId: String?): AdminCustomerAddress {
            return AdminCustomerAddress(
                id = address.id,
                firstName = address.firstName,
                lastName = address.lastName,
                company = address.company,
                phone = address.phone,
                address1 = address.address1,
                address2 = address.address2,
                city = address.city,
                province = address.province,
                postalCode = address.postalCode,
                countryCode = address.countryCode,
                fullAddress = address.getFullAddress(),
                isDefault = address.id == billingAddressId, // Using billing as default
                isBilling = address.id == billingAddressId
            )
        }
    }
}

/**
 * Customer stats for dashboard
 */
data class CustomerStats(
    val totalCustomers: Long,
    val activeCustomers: Long,
    val suspendedCustomers: Long,
    val customersWithAccounts: Long,
    val vipCustomers: Long, // Gold + Platinum
    val newThisMonth: Long
)

// =============================================================================
// Request DTOs
// =============================================================================

data class CreateCustomerRequest(
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val tier: String? = null,
    val groupIds: List<String>? = null,
    val internalNotes: String? = null
)

data class UpdateCustomerRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val internalNotes: String? = null
)

data class SendEmailRequest(
    val subject: String,
    val body: String
)

data class SendGiftCardRequest(
    val amount: Int, // In cents
    val message: String? = null,
    val currencyCode: String = "GBP"
)

data class ChangeTierRequest(
    val tier: String,
    val reason: String? = null
)

data class SuspendCustomerRequest(
    val reason: String
)

data class CreateCustomerGroupRequest(
    val name: String,
    val description: String? = null
)

data class UpdateCustomerGroupRequest(
    val name: String? = null,
    val description: String? = null
)

// =============================================================================
// Response Wrappers
// =============================================================================

data class AdminCustomerResponse(
    val customer: AdminCustomer
)

data class AdminCustomersResponse(
    val customers: List<AdminCustomerSummary>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class AdminCustomerGroupResponse(
    val group: AdminCustomerGroup
)

data class AdminCustomerGroupsResponse(
    val groups: List<AdminCustomerGroup>,
    val count: Int
)

data class AdminCustomerActivitiesResponse(
    val activities: List<AdminCustomerActivity>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class AdminCustomerAddressesResponse(
    val addresses: List<AdminCustomerAddress>
)

data class CustomerStatsResponse(
    val stats: CustomerStats
)
