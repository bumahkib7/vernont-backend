package com.vernont.api.seeder

import com.vernont.domain.region.Currency
import com.vernont.repository.region.CurrencyRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

private val logger = KotlinLogging.logger {}

@Component
@org.springframework.core.annotation.Order(1)
class CurrencySeeder(
    private val currencyRepository: CurrencyRepository,
    webClientBuilder: WebClient.Builder
) : CommandLineRunner {

    private val webClient = webClientBuilder.baseUrl("https://restcountries.com/v3.1").build()

    // Known zero-decimal currencies
    private val zeroDecimalCurrencies = setOf(
        "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "MGA", "PYG", 
        "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun run(vararg args: String) = runBlocking {
        if (withContext(Dispatchers.IO) { currencyRepository.count() } > 0) {
            logger.info { "Currency seeding skipped: Currencies already exist." }
            return@runBlocking
        }

        logger.info { "Starting currency seeding..." }

        try {
            val response = fetchCurrencies()
            if (response.isEmpty()) {
                logger.warn { "No currency data fetched from API." }
                return@runBlocking
            }

            // Extract unique currencies from the response
            // Response is List<Map<String, Map<String, CurrencyDetail>>>
            // We need to flatten this to a unique set of currencies based on code
            val uniqueCurrencies = response
                .mapNotNull { it.currencies }
                .flatMap { it.entries }
                .associateBy({ it.key }, { it.value }) // Deduplicate by currency code
            
            logger.info { "Found ${uniqueCurrencies.size} unique currencies. Starting batch insertion..." }

            val batchSize = 50

            uniqueCurrencies.entries.asFlow()
                .mapNotNull { (code, detail) ->
                    try {
                        mapToEntity(code, detail)
                    } catch (e: Exception) {
                        logger.warn { "Skipping currency $code: ${e.message}" }
                        null
                    }
                }
                .chunked(batchSize)
                .onEach { batch ->
                    saveBatch(batch)
                }
                .collect()

            logger.info { "Currency seeding completed successfully." }

        } catch (e: Exception) {
            logger.error(e) { "Failed to seed currencies" }
        }
    }

    private suspend fun fetchCurrencies(): List<CountryCurrencyResponse> {
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/all")
                    .queryParam("fields", "currencies")
                    .build()
            }
            .retrieve()
            .awaitBody<List<CountryCurrencyResponse>>()
    }

    @Transactional
    protected suspend fun saveBatch(entities: List<Currency>) {
        withContext(Dispatchers.IO) {
            currencyRepository.saveAll(entities)
            logger.debug { "Saved batch of ${entities.size} currencies" }
        }
    }

    private fun mapToEntity(code: String, detail: CurrencyDetail): Currency {
        return Currency().apply {
            this.code = code
            this.name = detail.name ?: throw IllegalArgumentException("Missing name")
            this.symbol = detail.symbol ?: code
            this.symbolNative = detail.symbol ?: code // API doesn't provide native symbol separately in this view, fallback to symbol
            this.decimalDigits = if (zeroDecimalCurrencies.contains(code)) 0 else 2
            this.rounding = 0.0
            this.includesTax = false
        }
    }

    // DTOs for API response
    data class CountryCurrencyResponse(
        val currencies: Map<String, CurrencyDetail>?
    )

    data class CurrencyDetail(
        val name: String?,
        val symbol: String?
    )
}
