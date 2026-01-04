package com.vernont.domain.common

import jakarta.persistence.Embeddable
import jakarta.persistence.Column

@Embeddable
data class Address(
    @Column(name = "address_1", nullable = false)
    val address1: String,
    @Column(name = "address_2")
    val address2: String? = null,
    @Column(name = "city", nullable = false)
    val city: String,
    @Column(name = "country_code", nullable = false)
    val countryCode: String,
    @Column(name = "province")
    val province: String? = null,
    @Column(name = "postal_code", nullable = false)
    val postalCode: String,
    @Column(name = "phone")
    val phone: String? = null
)
