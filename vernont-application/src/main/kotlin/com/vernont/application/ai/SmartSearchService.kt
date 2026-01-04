package com.vernont.application.ai

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Smart Search Enhancement using Llama3
 *
 * Enhances user search queries by:
 * - Query expansion (e.g., "laptop" → "laptop computer notebook ultrabook")
 * - Intent understanding (e.g., "best running shoes" → category: shoes, intent: top-rated, use: running)
 * - Synonym generation for better Elasticsearch matching
 */
@Service
class SmartSearchService(
    private val llamaService: LlamaService,
    private val objectMapper: ObjectMapper
) {

    /**
     * Enhance a user's search query with AI-powered expansions
     *
     * @param query The original user query
     * @return Enhanced query information including expanded terms and filters
     */
    suspend fun enhanceQuery(query: String): EnhancedQuery = coroutineScope {
        try {
            if (query.isBlank()) {
                return@coroutineScope EnhancedQuery(
                    originalQuery = query,
                    expandedTerms = emptyList(),
                    detectedIntent = null,
                    suggestedFilters = emptyMap()
                )
            }

            logger.info { "Enhancing search query: $query" }

            val prompt = buildQueryEnhancementPrompt(query)

            // Run query enhancement asynchronously
            val enhancementResult = async {
                try {
                    val jsonResponse = llamaService.generateJson(prompt, temperature = 0.3)
                    objectMapper.readValue(jsonResponse, QueryEnhancement::class.java)
                } catch (ex: Exception) {
                    logger.warn(ex) { "Failed to enhance query with Llama3, using fallback" }
                    null
                }
            }

            val enhancement = enhancementResult.await()

            if (enhancement == null) {
                // Fallback: return original query with basic tokenization
                return@coroutineScope EnhancedQuery(
                    originalQuery = query,
                    expandedTerms = query.split(" ").filter { it.isNotBlank() },
                    detectedIntent = null,
                    suggestedFilters = emptyMap()
                )
            }

            logger.debug { "Enhanced query: ${enhancement.expandedTerms.size} terms, intent=${enhancement.intent}" }

            EnhancedQuery(
                originalQuery = query,
                expandedTerms = enhancement.expandedTerms,
                detectedIntent = enhancement.intent,
                suggestedFilters = enhancement.suggestedFilters,
                detectedCategory = enhancement.category
            )

        } catch (ex: Exception) {
            logger.error(ex) { "Unexpected error enhancing query" }
            // Always return a fallback to prevent search from breaking
            EnhancedQuery(
                originalQuery = query,
                expandedTerms = query.split(" ").filter { it.isNotBlank() },
                detectedIntent = null,
                suggestedFilters = emptyMap()
            )
        }
    }

    /**
     * Build Elasticsearch query from enhanced query
     * Combines original query with expanded terms using boosting
     */
    fun buildElasticsearchQuery(enhanced: EnhancedQuery): String {
        val shouldClauses = mutableListOf<String>()

        // Original query gets highest boost
        shouldClauses.add("""{"match": {"name": {"query": "${enhanced.originalQuery}", "boost": 3.0}}}""")
        shouldClauses.add("""{"match": {"description": {"query": "${enhanced.originalQuery}", "boost": 2.0}}}""")

        // Expanded terms get medium boost
        enhanced.expandedTerms.forEach { term ->
            shouldClauses.add("""{"match": {"name": {"query": "$term", "boost": 1.5}}}""")
            shouldClauses.add("""{"match": {"description": {"query": "$term", "boost": 1.0}}}""")
        }

        val mustClauses = mutableListOf<String>()

        // Apply detected filters
        enhanced.detectedCategory?.let { category ->
            mustClauses.add("""{"match": {"category": "$category"}}""")
        }

        enhanced.suggestedFilters["brand"]?.let { brand ->
            mustClauses.add("""{"match": {"brand": "$brand"}}""")
        }

        enhanced.suggestedFilters["minPrice"]?.let { minPrice ->
            mustClauses.add("""{"range": {"price": {"gte": $minPrice}}}""")
        }

        enhanced.suggestedFilters["maxPrice"]?.let { maxPrice ->
            mustClauses.add("""{"range": {"price": {"lte": $maxPrice}}}""")
        }

        val query = buildString {
            append("""{"bool": {""")
            if (mustClauses.isNotEmpty()) {
                append(""""must": [${mustClauses.joinToString(",")}],""")
            }
            append(""""should": [${shouldClauses.joinToString(",")}],""")
            append(""""minimum_should_match": 1""")
            append("}}")
        }

        return query
    }

    private fun buildQueryEnhancementPrompt(query: String): String {
        return """
You are an e-commerce search expert. Analyze this search query and enhance it for better product matching.

User Query: "$query"

Generate a JSON response with the following structure:
{
  "expandedTerms": ["synonym1", "synonym2", "related_term1"],
  "intent": "browse|buy|compare|research",
  "category": "detected_product_category_or_null",
  "suggestedFilters": {
    "brand": "detected_brand_or_null",
    "minPrice": detected_min_price_or_null,
    "maxPrice": detected_max_price_or_null
  }
}

Rules:
1. expandedTerms should include synonyms and related terms (max 5)
2. intent should be one of: browse, buy, compare, research
3. category should be a product category like "electronics", "clothing", "shoes", etc. or null
4. suggestedFilters extracts structured data from the query (e.g., "cheap laptops" → maxPrice: 500)
5. Keep all terms relevant to e-commerce product search
6. If query mentions a brand, extract it to suggestedFilters.brand

Examples:
Query: "best running shoes"
{
  "expandedTerms": ["running sneakers", "athletic shoes", "jogging footwear", "sport shoes"],
  "intent": "research",
  "category": "shoes",
  "suggestedFilters": {}
}

Query: "cheap gaming laptop under 1000"
{
  "expandedTerms": ["gaming computer", "gaming notebook", "budget gaming laptop"],
  "intent": "buy",
  "category": "electronics",
  "suggestedFilters": {"maxPrice": 1000}
}

Query: "nike air max"
{
  "expandedTerms": ["nike sneakers", "air max shoes"],
  "intent": "browse",
  "category": "shoes",
  "suggestedFilters": {"brand": "Nike"}
}

Now analyze: "$query"
        """.trimIndent()
    }
}

/**
 * Enhanced query with AI-generated insights
 */
data class EnhancedQuery(
    val originalQuery: String,
    val expandedTerms: List<String>,
    val detectedIntent: String?,
    val suggestedFilters: Map<String, Any>,
    val detectedCategory: String? = null
)

/**
 * Internal DTO for parsing Llama3 JSON response
 */
private data class QueryEnhancement(
    val expandedTerms: List<String> = emptyList(),
    val intent: String? = null,
    val category: String? = null,
    val suggestedFilters: Map<String, Any> = emptyMap()
)
