package com.vernont.domain.order

import com.vernont.domain.common.BaseEntity
import com.vernont.domain.customer.CustomerAddress
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "order_address",
    indexes = [
        Index(name = "idx_order_address_country_code", columnList = "country_code"),
        Index(name = "idx_order_address_deleted_at", columnList = "deleted_at")
    ]
)
class OrderAddress : BaseEntity() {

    @Column
    var firstName: String? = null

    @Column
    var lastName: String? = null

    @Column
    var company: String? = null

    @Column
    var phone: String? = null

    @NotBlank
    @Column(nullable = false)
    var address1: String = ""

    @Column
    var address2: String? = null

    @NotBlank
    @Column(nullable = false)
    var city: String = ""

    @Column
    var province: String? = null

    @Column
    var postalCode: String? = null

    @NotBlank
    @Column(name = "country_code", nullable = false, length = 2)
    var countryCode: String = ""

    fun getFullName(): String {
        return when {
            firstName != null && lastName != null -> "$firstName $lastName"
            firstName != null -> firstName!!
            lastName != null -> lastName!!
            else -> ""
        }
    }

    fun getFullAddress(): String {
        val parts = mutableListOf<String>()

        parts.add(address1)
        address2?.let { parts.add(it) }
        parts.add(city)
        province?.let { parts.add(it) }
        postalCode?.let { parts.add(it) }
        parts.add(countryCode)

        return parts.joinToString(", ")
    }

    companion object {
        fun fromCustomerAddress(customerAddress: CustomerAddress): OrderAddress {
            return OrderAddress().apply {
                firstName = customerAddress.firstName
                lastName = customerAddress.lastName
                company = customerAddress.company
                phone = customerAddress.phone
                address1 = customerAddress.address1
                address2 = customerAddress.address2
                city = customerAddress.city
                province = customerAddress.province
                postalCode = customerAddress.postalCode
                countryCode = customerAddress.countryCode
            }
        }

        fun fromCommonAddress(commonAddress: com.vernont.domain.common.Address): OrderAddress {
            return OrderAddress().apply {
                firstName = commonAddress.firstName
                lastName = commonAddress.lastName
                phone = commonAddress.phone
                address1 = commonAddress.address1
                address2 = commonAddress.address2
                city = commonAddress.city
                province = commonAddress.province
                postalCode = commonAddress.postalCode
                countryCode = commonAddress.countryCode
            }
        }
    }
}
