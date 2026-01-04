package com.vernont.application.ai

import com.vernont.domain.product.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * AI-powered product description generator using Llama3
 *
 * Enhances affiliate product listings with:
 * - SEO-optimized descriptions
 * - Compelling marketing copy
 * - Feature highlights
 * - Use cases and benefits
 */
@Service
class ProductDescriptionGenerator(
    private val llamaService: LlamaService
) {

    /**
     * Generate an enhanced description for a product
     *
     * @param product The product to generate description for
     * @param maxLength Maximum character length for the description
     * @return Generated description, or original if generation fails
     */
    suspend fun generateDescription(
        product: Product,
        maxLength: Int = 500
    ): GeneratedDescription = coroutineScope {
        try {
            logger.info { "Generating description for product: ${product.title}" }

            val prompt = buildDescriptionPrompt(product, maxLength)

            val descriptionTask = async {
                try {
                    llamaService.generate(
                        prompt = prompt,
                        temperature = 0.7, // Balanced between creative and factual
                        maxTokens = (maxLength * 1.5).toInt() // Tokens ≈ 0.75 * characters
                    )
                } catch (ex: Exception) {
                    logger.warn(ex) { "Failed to generate description for ${product.title}" }
                    null
                }
            }

            val generated = descriptionTask.await()

            if (generated == null || generated.isBlank()) {
                logger.warn { "Empty description generated, using original" }
                return@coroutineScope GeneratedDescription(
                    enhancedDescription = product.description ?: "",
                    originalDescription = product.description ?: "",
                    isAiGenerated = false
                )
            }

            // Clean up the response
            val cleanedDescription = cleanDescription(generated, maxLength)

            logger.debug { "Generated ${cleanedDescription.length} char description for ${product.title}" }

            GeneratedDescription(
                enhancedDescription = cleanedDescription,
                originalDescription = product.description ?: "",
                isAiGenerated = true,
                keyFeatures = extractKeyFeatures(cleanedDescription)
            )

        } catch (ex: Exception) {
            logger.error(ex) { "Unexpected error generating description for ${product.title}" }
            GeneratedDescription(
                enhancedDescription = product.description ?: "",
                originalDescription = product.description ?: "",
                isAiGenerated = false
            )
        }
    }

    /**
     * Generate descriptions for multiple products in batch
     * Processes products sequentially to avoid overwhelming Ollama
     */
    suspend fun generateDescriptionsBatch(
        products: List<Product>,
        maxLength: Int = 500
    ): Map<String, GeneratedDescription> {
        logger.info { "Generating descriptions for ${products.size} products" }

        val results = mutableMapOf<String, GeneratedDescription>()

        for (product in products) {
            try {
                val description = generateDescription(product, maxLength)
                results[product.id] = description
            } catch (ex: Exception) {
                logger.error(ex) { "Failed to generate description for product ${product.id}" }
                results[product.id] = GeneratedDescription(
                    enhancedDescription = product.description ?: "",
                    originalDescription = product.description ?: "",
                    isAiGenerated = false
                )
            }
        }

        logger.info { "Generated ${results.count { it.value.isAiGenerated }} AI descriptions out of ${products.size}" }

        return results
    }

    /**
     * Generate a short marketing tagline for a product
     */
    suspend fun generateTagline(product: Product): String {
        try {
            val prompt = """
Generate a short, catchy marketing tagline (max 10 words) for this product:

Product Name: ${product.title}
Category: ${product.type?.value ?: "General"}
Brand: ${product.brand?.name ?: "N/A"}
${product.description?.let { "Description: $it" } ?: ""}

The tagline should:
- Be memorable and engaging
- Highlight the main benefit
- Be suitable for e-commerce
- Be concise (max 10 words)

Respond with ONLY the tagline, no explanations.
            """.trimIndent()

            val tagline = llamaService.generate(
                prompt = prompt,
                temperature = 0.8, // Higher creativity for taglines
                maxTokens = 50
            )

            return tagline.trim().removeSurrounding("\"")

        } catch (ex: Exception) {
            logger.warn(ex) { "Failed to generate tagline for ${product.title}" }
            return product.title.take(50) // Fallback to truncated title
        }
    }

    private fun buildDescriptionPrompt(product: Product, maxLength: Int): String {
        val brandInfo = product.brand?.let { "Brand: ${it.name}" } ?: ""
        val categoryInfo = product.type?.let { "Category: ${it.value}" } ?: ""
        val originalDesc = product.description ?: "No description available"

        return """
You are an expert e-commerce copywriter. Write an engaging product description for online shoppers.

Product Information:
- Name: ${product.title}
- $brandInfo
- $categoryInfo
- Original Description: $originalDesc

Requirements:
1. Write a compelling description (max $maxLength characters)
2. Highlight key features and benefits
3. Use persuasive but honest language
4. Focus on what makes this product valuable
5. Include relevant keywords for SEO
6. Write in second person ("you" perspective)
7. Be concise and scannable

Write the description now (max $maxLength characters):
        """.trimIndent()
    }

    private fun cleanDescription(rawDescription: String, maxLength: Int): String {
        var cleaned = rawDescription.trim()

        // Remove common AI prefixes
        val prefixes = listOf(
            "Here is the description:",
            "Here's the description:",
            "Description:",
            "Product Description:"
        )
        prefixes.forEach { prefix ->
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
            }
        }

        // Remove quotes if the entire text is quoted
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.removeSurrounding("\"")
        }

        // Truncate if too long
        if (cleaned.length > maxLength) {
            // Try to truncate at sentence boundary
            val lastPeriod = cleaned.take(maxLength).lastIndexOf('.')
            cleaned = if (lastPeriod > maxLength / 2) {
                cleaned.take(lastPeriod + 1)
            } else {
                cleaned.take(maxLength).trim() + "..."
            }
        }

        return cleaned
    }

    private fun extractKeyFeatures(description: String): List<String> {
        // Simple extraction: look for sentences that mention features
        val sentences = description.split(". ")
        val keywords = listOf("feature", "include", "with", "has", "offer", "provide", "design")

        return sentences
            .filter { sentence ->
                keywords.any { keyword -> sentence.contains(keyword, ignoreCase = true) }
            }
            .map { it.trim() }
            .take(3) // Max 3 key features
    }
}

/**
 * Result of AI description generation
 */
data class GeneratedDescription(
    val enhancedDescription: String,
    val originalDescription: String,
    val isAiGenerated: Boolean,
    val keyFeatures: List<String> = emptyList()
)
