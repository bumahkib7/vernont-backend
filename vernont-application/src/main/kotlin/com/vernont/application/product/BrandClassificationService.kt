package com.vernont.application.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vernont.application.ai.LlamaService
import com.vernont.domain.product.Brand
import com.vernont.domain.product.BrandTier
import com.vernont.repository.product.BrandRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Service for automatically classifying brands into tiers (LUXURY, PREMIUM, STANDARD)
 * using Ollama LLM for intelligent brand analysis.
 */
@Service
class BrandClassificationService(
    private val brandRepository: BrandRepository,
    private val llamaService: LlamaService,
    private val objectMapper: ObjectMapper
) {

    /**
     * Classify a single brand using Ollama LLM.
     * Analyzes brand name, description, and website to determine tier.
     *
     * @param brand The brand to classify
     * @return The determined BrandTier
     */
    suspend fun classifyBrand(brand: Brand): BrandTier {
        return try {
            logger.info { "Classifying brand: ${brand.name}" }

            val prompt = buildClassificationPrompt(brand)
            val jsonResponse = llamaService.generateJson(
                prompt = prompt,
                temperature = 0.2 // Low temperature for more deterministic classification
            )

            val result = objectMapper.readValue<BrandClassificationResult>(jsonResponse)

            logger.info {
                "Classified ${brand.name} as ${result.tier} (confidence: ${result.confidence}, reasoning: ${result.reasoning})"
            }

            BrandTier.valueOf(result.tier.uppercase())

        } catch (ex: Exception) {
            logger.error(ex) { "Failed to classify brand ${brand.name}, defaulting to STANDARD" }
            BrandTier.STANDARD
        }
    }

    /**
     * Classify multiple brands in parallel for efficiency.
     *
     * @param brands List of brands to classify
     * @return Map of brand ID to BrandTier
     */
    suspend fun classifyBrands(brands: List<Brand>): Map<String, BrandTier> = coroutineScope {
        logger.info { "Classifying ${brands.size} brands in parallel" }

        brands.map { brand ->
            async(Dispatchers.IO) {
                brand.id to classifyBrand(brand)
            }
        }.awaitAll().toMap()
    }

    /**
     * Classify all unclassified brands (tier = STANDARD) and update database.
     * This is useful for batch processing existing brands.
     *
     * @param overwriteExisting If true, reclassify all brands including already classified ones
     * @return Number of brands classified
     */
    @Transactional
    suspend fun classifyAllBrands(overwriteExisting: Boolean = false): Int = withContext(Dispatchers.IO) {
        val brands = if (overwriteExisting) {
            brandRepository.findAllByDeletedAtIsNull()
        } else {
            brandRepository.findAllByDeletedAtIsNull().filter { it.tier == BrandTier.STANDARD }
        }

        logger.info { "Starting classification for ${brands.size} brands (overwrite: $overwriteExisting)" }

        var classifiedCount = 0

        // Process in batches to avoid overwhelming Ollama
        brands.chunked(5).forEach { batch ->
            val classifications = classifyBrands(batch)

            batch.forEach { brand ->
                val newTier = classifications[brand.id]
                if (newTier != null && newTier != brand.tier) {
                    brand.tier = newTier
                    brandRepository.save(brand)
                    classifiedCount++
                    logger.info { "Updated ${brand.name} to tier: $newTier" }
                }
            }
        }

        logger.info { "Classification complete. Updated $classifiedCount brands" }
        classifiedCount
    }

    /**
     * Classify a specific brand by ID and update in database.
     *
     * @param brandId The brand ID to classify
     * @return The updated Brand entity
     */
    @Transactional
    suspend fun classifyBrandById(brandId: String): Brand? = withContext(Dispatchers.IO) {
        val brand = brandRepository.findByIdAndDeletedAtIsNull(brandId) ?: return@withContext null

        val tier = classifyBrand(brand)
        brand.tier = tier
        brandRepository.save(brand)

        logger.info { "Classified brand ${brand.name} (ID: $brandId) as $tier" }
        brand
    }

    private fun buildClassificationPrompt(brand: Brand): String {
        val brandInfo = StringBuilder()
        brandInfo.append("Brand Name: ${brand.name}\n")

        if (!brand.description.isNullOrBlank()) {
            brandInfo.append("Description: ${brand.description}\n")
        }

        if (!brand.websiteUrl.isNullOrBlank()) {
            brandInfo.append("Website: ${brand.websiteUrl}\n")
        }

        return """
You are a fashion industry expert analyzing brand positioning.

Classify the following fashion brand into one of these tiers:

1. LUXURY - Haute couture/luxury fashion houses
   - Examples: Dior, Gucci, Prada, Chanel, Louis Vuitton, Hermès, Balenciaga
   - Characteristics: Heritage brands, premium pricing, exclusive positioning, high-end craftsmanship

2. PREMIUM - Contemporary/premium brands
   - Examples: Calvin Klein, Hugo Boss, Armani, Polo Ralph Lauren, Michael Kors
   - Characteristics: High quality, mid-to-high pricing, aspirational positioning

3. STANDARD - Mainstream/accessible brands
   - Examples: H&M, Zara, Gap, Uniqlo, Old Navy
   - Characteristics: Accessible pricing, mass market appeal, high street fashion

Brand Information:
$brandInfo

Analyze this brand and respond with ONLY valid JSON in this exact format:
{
  "tier": "LUXURY|PREMIUM|STANDARD",
  "confidence": "high|medium|low",
  "reasoning": "Brief explanation (1-2 sentences)"
}

Do not include any text before or after the JSON.
        """.trimIndent()
    }
}

/**
 * Data class for parsing Ollama JSON response
 */
data class BrandClassificationResult(
    val tier: String,
    val confidence: String,
    val reasoning: String
)
