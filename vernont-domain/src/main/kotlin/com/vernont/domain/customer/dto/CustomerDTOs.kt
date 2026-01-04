package com.vernont.domain.customer.dto

import com.vernont.domain.customer.Customer
import com.vernont.domain.customer.CustomerAddress
import jakarta.validation.constraints.*
import java.time.Instant

/**
 * Request to register a new customer
 */
data class RegisterCustomerRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,
    
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    
    val phone: String? = null,
    val hasAccount: Boolean = false
)

/**
 * Request to update customer profile
 */
data class UpdateCustomerRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null
)

/**
 * Request to create a customer address
 */
data class CreateAddressRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val company: String? = null,
    val phone: String? = null,
    
    @field:NotBlank(message = "Address line 1 is required")
    val address1: String,
    
    val address2: String? = null,
    
    @field:NotBlank(message = "City is required")
    val city: String,
    
    val province: String? = null,
    val postalCode: String? = null,
    
    @field:NotBlank(message = "Country code is required")
    @field:Size(min = 2, max = 2, message = "Country code must be 2 characters")
    val countryCode: String
)

/**
 * Request to update a customer address
 */
data class UpdateAddressRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val company: String? = null,
    val phone: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val city: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null
)

/**
 * Customer address response
 */
data class CustomerAddressResponse(
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
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(address: CustomerAddress): CustomerAddressResponse {
            return CustomerAddressResponse(
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
                createdAt = address.createdAt,
                updatedAt = address.updatedAt
            )
        }
    }
}

/**
 * Full customer response with addresses
 */
data class CustomerResponse(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val fullName: String,
    val phone: String?,
    val hasAccount: Boolean,
    val billingAddressId: String?,
    val addresses: List<CustomerAddressResponse>,
    val groupIds: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(customer: Customer): CustomerResponse {
            return CustomerResponse(
                id = customer.id,
                email = customer.getEffectiveEmail(),
                firstName = customer.getEffectiveFirstName(),
                lastName = customer.getEffectiveLastName(),
                fullName = customer.getFullName(),
                phone = customer.phone,
                hasAccount = customer.hasAccount,
                billingAddressId = customer.billingAddressId,
                addresses = customer.addresses.map { CustomerAddressResponse.from(it) },
                groupIds = customer.groups.map { it.id },
                createdAt = customer.createdAt,
                updatedAt = customer.updatedAt
            )
        }
    }
}

/**
 * Summary customer response for list views (without addresses)
 */
data class CustomerSummaryResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val hasAccount: Boolean,
    val addressCount: Int,
    val createdAt: Instant
) {
    companion object {
        fun from(customer: Customer): CustomerSummaryResponse {
            return CustomerSummaryResponse(
                id = customer.id,
                email = customer.getEffectiveEmail(),
                fullName = customer.getFullName(),
                phone = customer.phone,
                hasAccount = customer.hasAccount,
                addressCount = customer.addresses.size,
                createdAt = customer.createdAt
            )
        }
    }
}
