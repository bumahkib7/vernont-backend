package com.vernont.api.seeder

import com.vernont.domain.region.Country
import com.vernont.domain.region.Region
import com.vernont.repository.region.CountryRepository
import com.vernont.repository.region.RegionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Seeds regions with their associated countries.
 * Runs after CountrySeeder (Order 2).
 */
@Component
@Order(2) // Run after CountrySeeder
class RegionSeeder(
    private val regionRepository: RegionRepository,
    private val countryRepository: CountryRepository
) : CommandLineRunner {

    // Region definitions with their countries (ISO2 codes)
    private val regionDefinitions = listOf(
        RegionDef(
            name = "United Kingdom",
            currencyCode = "GBP",
            taxRate = BigDecimal("20.00"),
            taxInclusive = true,
            countries = listOf("GB", "GG", "JE", "IM") // UK, Guernsey, Jersey, Isle of Man
        ),
        RegionDef(
            name = "European Union",
            currencyCode = "EUR",
            taxRate = BigDecimal("21.00"),
            taxInclusive = true,
            countries = listOf(
                "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
                "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
                "PL", "PT", "RO", "SK", "SI", "ES", "SE"
            )
        ),
        RegionDef(
            name = "United States",
            currencyCode = "USD",
            taxRate = BigDecimal.ZERO, // Tax calculated at state level
            taxInclusive = false,
            countries = listOf("US")
        ),
        RegionDef(
            name = "Canada",
            currencyCode = "CAD",
            taxRate = BigDecimal("5.00"), // GST
            taxInclusive = false,
            countries = listOf("CA")
        ),
        RegionDef(
            name = "Australia",
            currencyCode = "AUD",
            taxRate = BigDecimal("10.00"), // GST
            taxInclusive = true,
            countries = listOf("AU", "NZ") // Australia and New Zealand
        ),
        RegionDef(
            name = "United Arab Emirates",
            currencyCode = "AED",
            taxRate = BigDecimal("5.00"), // VAT
            taxInclusive = true,
            countries = listOf("AE", "SA", "QA", "KW", "BH", "OM") // GCC countries
        ),
        RegionDef(
            name = "Singapore",
            currencyCode = "SGD",
            taxRate = BigDecimal("9.00"), // GST (increased to 9% in 2024)
            taxInclusive = true,
            countries = listOf("SG", "MY", "TH", "VN", "PH", "ID") // Southeast Asia
        ),
        RegionDef(
            name = "Hong Kong",
            currencyCode = "HKD",
            taxRate = BigDecimal.ZERO, // No GST/VAT
            taxInclusive = false,
            countries = listOf("HK", "MO", "TW") // Hong Kong, Macau, Taiwan
        ),
        RegionDef(
            name = "Japan",
            currencyCode = "JPY",
            taxRate = BigDecimal("10.00"), // Consumption tax
            taxInclusive = true,
            countries = listOf("JP", "KR") // Japan and South Korea
        ),
        RegionDef(
            name = "Switzerland",
            currencyCode = "CHF",
            taxRate = BigDecimal("8.10"), // VAT
            taxInclusive = true,
            countries = listOf("CH", "LI") // Switzerland and Liechtenstein
        )
    )

    @Transactional
    override fun run(vararg args: String) {
        if (regionRepository.count() > 0) {
            logger.info { "Region seeding skipped: Regions already exist." }
            return
        }

        // Wait for countries to be seeded
        val countryCount = countryRepository.count()
        if (countryCount == 0L) {
            logger.warn { "No countries found. Region seeding requires countries to be seeded first." }
            return
        }

        logger.info { "Starting region seeding with $countryCount countries available..." }

        var created = 0
        var skipped = 0

        for (def in regionDefinitions) {
            try {
                val region = createRegion(def)
                if (region != null) {
                    regionRepository.save(region)
                    created++
                    logger.info { "Created region: ${def.name} (${def.currencyCode}) with ${region.countries.size} countries" }
                } else {
                    skipped++
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create region: ${def.name}" }
                skipped++
            }
        }

        logger.info { "Region seeding completed. Created: $created, Skipped: $skipped" }
    }

    private fun createRegion(def: RegionDef): Region? {
        // Check if region already exists
        if (regionRepository.existsByName(def.name)) {
            logger.debug { "Region already exists: ${def.name}" }
            return null
        }

        // Find countries for this region
        val countries = def.countries.mapNotNull { iso2 ->
            countryRepository.findByIso2(iso2).orElse(null)
        }.toMutableSet()

        if (countries.isEmpty()) {
            logger.warn { "No countries found for region: ${def.name}" }
            // Still create the region without countries
        }

        return Region().apply {
            this.name = def.name
            this.currencyCode = def.currencyCode
            this.taxRate = def.taxRate
            this.taxInclusive = def.taxInclusive
            this.automaticTaxes = true
            this.giftCardsTaxable = false
            this.countries = countries
        }
    }

    data class RegionDef(
        val name: String,
        val currencyCode: String,
        val taxRate: BigDecimal,
        val taxInclusive: Boolean,
        val countries: List<String>
    )
}
