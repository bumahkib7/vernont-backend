package com.vernont.domain.region

import com.vernont.domain.common.BaseEntity
import com.vernont.domain.fulfillment.FulfillmentProvider
import com.vernont.domain.payment.PaymentProvider
import jakarta.persistence.*
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import java.time.Instant

@Entity
@Table(
    name = "region",
    indexes = [
        Index(name = "idx_region_name", columnList = "name", unique = true),
        Index(name = "idx_region_currency", columnList = "currency_code")
    ]
)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@NamedEntityGraph(
    name = "Region.full",
    attributeNodes = [
        NamedAttributeNode("countries"),
        NamedAttributeNode("paymentProviders"),
        NamedAttributeNode("fulfillmentProviders") // Assuming fulfillment providers also exist
    ]
)
class Region : BaseEntity() {

    @Column(nullable = false)
    var name: String = ""

    @Column(name = "currency_code", nullable = false)
    var currencyCode: String = ""

    @Column(name = "automatic_taxes", nullable = false)
    var automaticTaxes: Boolean = false

    @Column(name = "tax_code")
    var taxCode: String? = null

    @Column(name = "gift_cards_taxable", nullable = false)
    var giftCardsTaxable: Boolean = true

    @Column(name = "tax_rate", precision = 10, scale = 4)
    var taxRate: java.math.BigDecimal = java.math.BigDecimal.ZERO

    @Column(name = "tax_inclusive", nullable = false)
    var taxInclusive: Boolean = false

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "region_country",
        joinColumns = [JoinColumn(name = "region_id")],
        inverseJoinColumns = [JoinColumn(name = "country_id")]
    )
    var countries: MutableSet<Country> = mutableSetOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "region_payment_provider",
        joinColumns = [JoinColumn(name = "region_id")],
        inverseJoinColumns = [JoinColumn(name = "payment_provider_id")]
    )
    var paymentProviders: MutableSet<PaymentProvider> = mutableSetOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "region_fulfillment_provider",
        joinColumns = [JoinColumn(name = "region_id")],
        inverseJoinColumns = [JoinColumn(name = "fulfillment_provider_id")]
    )
    var fulfillmentProviders: MutableSet<FulfillmentProvider> = mutableSetOf() // Assuming FulfillmentProvider entity

    // Helper methods for relationships, if needed
    fun addCountry(country: Country) {
        countries.add(country)
        country.regions.add(this) // Assuming Country also has a regions set
    }

    fun removeCountry(country: Country) {
        countries.remove(country)
        country.regions.remove(this)
    }

}