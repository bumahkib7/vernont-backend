package com.vernont.api.dto.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.customer.Customer
import com.vernont.domain.customer.CustomerAddress
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class StoreCustomerResponse(
    val customer: StoreCustomer
)

data class StoreCustomer(
    val id: String,
    val email: String,
    @JsonProperty("first_name")
    val firstName: String?,
    @JsonProperty("last_name")
    val lastName: String?,
    @JsonProperty("billing_address_id")
    val billingAddressId: String?,
    val phone: String?,
    @JsonProperty("has_account")
    val hasAccount: Boolean,
    val orders: List<StoreOrderSummary>? = null,
    val addresses: List<StoreCustomerAddress>? = null,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @JsonProperty("updated_at")
    val updatedAt: OffsetDateTime,
    @JsonProperty("deleted_at")
    val deletedAt: OffsetDateTime?,
    val metadata: Map<String, Any>? = null
) {
    companion object {
        fun from(customer: Customer, orders: List<StoreOrderSummary>? = null): StoreCustomer {
            return StoreCustomer(
                id = customer.id,
                email = customer.getEffectiveEmail(),
                firstName = customer.getEffectiveFirstName(),
                lastName = customer.getEffectiveLastName(),
                billingAddressId = customer.billingAddressId,
                phone = customer.phone,
                hasAccount = customer.hasAccount,
                orders = orders,
                createdAt = customer.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = customer.updatedAt.atOffset(ZoneOffset.UTC),
                deletedAt = customer.deletedAt?.atOffset(ZoneOffset.UTC),
                metadata = null // TODO: Implement metadata support
            )
        }
    }
}

data class StoreOrderSummary(
    val id: String,
    @JsonProperty("display_id")
    val displayId: Int?,
    val status: String,
    @JsonProperty("fulfillment_status")
    val fulfillmentStatus: String,
    @JsonProperty("payment_status")
    val paymentStatus: String,
    val total: Int,
    @JsonProperty("currency_code")
    val currencyCode: String,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @JsonProperty("updated_at")
    val updatedAt: OffsetDateTime
)

data class StoreCustomerAddressResponse(
    val address: StoreCustomerAddress
)

data class StoreCustomerAddressesResponse(
    val addresses: List<StoreCustomerAddress>,
    val count: Int,
    val offset: Int,
    val limit: Int
)

data class StoreCustomerAddress(
    val id: String,
    @JsonProperty("customer_id")
    val customerId: String?,
    @JsonProperty("company")
    val company: String?,
    @JsonProperty("first_name")
    val firstName: String?,
    @JsonProperty("last_name")
    val lastName: String?,
    @JsonProperty("address_1")
    val address1: String?,
    @JsonProperty("address_2")
    val address2: String?,
    val city: String?,
    @JsonProperty("country_code")
    val countryCode: String?,
    val province: String?,
    @JsonProperty("postal_code")
    val postalCode: String?,
    val phone: String?,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @JsonProperty("updated_at")
    val updatedAt: OffsetDateTime,
    @JsonProperty("deleted_at")
    val deletedAt: OffsetDateTime?,
    val metadata: Map<String, Any>? = null
) {
    companion object {
        fun from(address: CustomerAddress): StoreCustomerAddress {
            return StoreCustomerAddress(
                id = address.id,
                customerId = address.customer?.id,
                company = address.company,
                firstName = address.firstName,
                lastName = address.lastName,
                address1 = address.address1,
                address2 = address.address2,
                city = address.city,
                countryCode = address.countryCode,
                province = address.province,
                postalCode = address.postalCode,
                phone = address.phone,
                createdAt = address.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = address.updatedAt.atOffset(ZoneOffset.UTC),
                deletedAt = address.deletedAt?.atOffset(ZoneOffset.UTC),
                metadata = null // TODO: Implement metadata support
            )
        }
    }
}

data class StoreRegisterCustomerRequest(
    val email: String,
    @JsonProperty("company_name")
    val companyName: String? = null,
    @JsonProperty("first_name")
    val firstName: String? = null,
    @JsonProperty("last_name")
    val lastName: String? = null,
    val phone: String? = null,
    val metadata: Map<String, Any>? = null
)

data class StorePostCustomerReq(
    @JsonProperty("first_name")
    val firstName: String?,
    @JsonProperty("last_name")
    val lastName: String?,
    val phone: String?,
    val email: String?,
    val password: String?,
    val metadata: Map<String, Any>?
)

data class StorePostCustomerAddressReq(
    @JsonProperty("first_name")
    val firstName: String?,
    @JsonProperty("last_name")
    val lastName: String?,
    val company: String?,
    @JsonProperty("address_1")
    val address1: String?,
    @JsonProperty("address_2")
    val address2: String?,
    val city: String?,
    @JsonProperty("country_code")
    val countryCode: String?,
    val province: String?,
    @JsonProperty("postal_code")
    val postalCode: String?,
    val phone: String?,
    val metadata: Map<String, Any>?
)
