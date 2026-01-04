package com.vernont.api.seeder

import com.vernont.domain.region.Country
import com.vernont.repository.region.CountryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

private val logger = KotlinLogging.logger {}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Component
class CountrySeeder(
    private val countryRepository: CountryRepository,
    webClientBuilder: WebClient.Builder
) : CommandLineRunner {

    private val webClient = webClientBuilder.baseUrl("https://restcountries.com/v3.1").build()

    override fun run(vararg args: String) = runBlocking {
        if (withContext(Dispatchers.IO) { countryRepository.count() } > 0) {
            logger.info { "Country seeding skipped: Countries already exist." }
            return@runBlocking
        }

        logger.info { "Starting country seeding..." }

        try {
            val countries = fetchCountries()
            if (countries.isEmpty()) {
                logger.warn { "No countries fetched from API." }
                return@runBlocking
            }

            logger.info { "Fetched ${countries.size} countries. Starting batch insertion..." }

            // Process and insert in batches
            val batchSize = 50
            
            // Using a flow to process and save in chunks
            countries.asFlow()
                .mapNotNull { dto ->
                    try {
                        mapToEntity(dto)
                    } catch (e: Exception) {
                        logger.warn { "Skipping country ${dto.name?.common}: ${e.message}" }
                        null
                    }
                }
                .chunked(batchSize)
                .onEach { batch ->
                    saveBatch(batch)
                }
                .collect()

            logger.info { "Country seeding completed successfully." }

        } catch (e: Exception) {
            logger.error(e) { "Failed to seed countries" }
        }
    }

    private suspend fun fetchCountries(): List<CountryDto> {
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/all")
                    .queryParam("fields", "cca2,cca3,ccn3,name")
                    .build()
            }
            .retrieve()
            .awaitBody<List<CountryDto>>()
    }

    @Transactional
    protected suspend fun saveBatch(entities: List<Country>) {
        withContext(Dispatchers.IO) {
            countryRepository.saveAll(entities)
            logger.debug { "Saved batch of ${entities.size} countries" }
        }
    }

    private fun mapToEntity(dto: CountryDto): Country {
        return Country().apply {
            this.iso2 = dto.cca2 ?: throw IllegalArgumentException("Missing ISO2")
            this.iso3 = dto.cca3 ?: throw IllegalArgumentException("Missing ISO3")
            this.numCode = dto.ccn3?.toIntOrNull() ?: throw IllegalArgumentException("Missing or invalid numeric code")
            this.name = dto.name?.common ?: throw IllegalArgumentException("Missing common name")
            this.displayName = dto.name.official ?: this.name
        }
    }

    // DTOs for API response
    data class CountryDto(
        val cca2: String?,
        val cca3: String?,
        val ccn3: String?,
        val name: NameDto?
    )

    data class NameDto(
        val common: String?,
        val official: String?
    )
}
