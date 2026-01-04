package com.vernont.domain.customer

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "customer_address",
    indexes = [
        Index(name = "idx_customer_address_customer_id", columnList = "customer_id"),
        Index(name = "idx_customer_address_country_code", columnList = "country_code"),
        Index(name = "idx_customer_address_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "CustomerAddress.withCustomer",
    attributeNodes = [
        NamedAttributeNode("customer")
    ]
)
class CustomerAddress : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    var customer: Customer? = null

    @Column
    var firstName: String? = null

    @Column
    var lastName: String? = null

    @Column
    var company: String? = null

    @Column
    var phone: String? = null

    @NotBlank
    @Column(name = "address_1", nullable = false)
    var address1: String = ""

    @Column(name = "address_2")
    var address2: String? = null

    @NotBlank
    @Column(nullable = false)
    var city: String = ""

    @Column
    var province: String? = null

    @Column(name = "postal_code")
    var postalCode: String? = null

    @NotBlank
    @Column(name = "country_code", nullable = false, length = 2)
    var countryCode: String = ""

    fun getFullName(): String {
        return when {
            firstName != null && lastName != null -> "$firstName $lastName"
            firstName != null -> firstName!!
            lastName != null -> lastName!!
            else -> customer?.getFullName() ?: ""
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

    fun isSameAddress(other: CustomerAddress): Boolean {
        return this.address1 == other.address1 &&
               this.address2 == other.address2 &&
               this.city == other.city &&
               this.province == other.province &&
               this.postalCode == other.postalCode &&
               this.countryCode == other.countryCode
    }
}
