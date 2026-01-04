package com.vernont.application.dto

import com.vernont.domain.region.Country
import com.vernont.domain.region.Region
import java.math.BigDecimal

data class StoreCountryDto(
    val iso_2: String,
    val iso_3: String,
    val num_code: Int,
    val name: String,
    val display_name: String
) {
    companion object {
        fun from(country: Country): StoreCountryDto {
            return StoreCountryDto(
                iso_2 = country.iso2,
                iso_3 = country.iso3,
                num_code = country.numCode,
                name = country.name,
                display_name = country.displayName
            )
        }
    }
}

data class StoreRegionDto(
    val id: String,
    val name: String,
    val currency_code: String,
    val tax_rate: BigDecimal,
    val tax_code: String?,
    val gift_cards_taxable: Boolean,
    val automatic_taxes: Boolean,
    val countries: List<StoreCountryDto>,
    val payment_providers: List<String>,
    val fulfillment_providers: List<String>,
    val metadata: Map<String, Any?>?
) {
    companion object {
        fun from(region: Region): StoreRegionDto {
            return StoreRegionDto(
                id = region.id,
                name = region.name,
                currency_code = region.currencyCode,
                tax_rate = region.taxRate,
                tax_code = region.taxCode,
                gift_cards_taxable = region.giftCardsTaxable,
                automatic_taxes = region.automaticTaxes,
                countries = region.countries.map { StoreCountryDto.from(it) },
                payment_providers = region.paymentProviders.map { it.id },
                fulfillment_providers = region.fulfillmentProviders.map { it.id },
                metadata = region.metadata
            )
        }
    }
}
