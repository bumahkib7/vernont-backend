package com.vernont.application.product

import com.vernont.domain.product.CanonicalCategory
import com.vernont.domain.product.CanonicalCategoryMapping
import com.vernont.domain.product.Product
import com.vernont.repository.product.CanonicalCategoryRepository
import com.vernont.repository.product.CanonicalCategoryMappingRepository
import com.vernont.repository.product.CanonicalCategorySynonymRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer

private val logger = KotlinLogging.logger {}

data class CategoryClassification(
    val category: CanonicalCategory?,
    val confidence: Double,
    val matchedBy: String
)

@Service
class CanonicalCategoryService(
    private val categoryRepository: CanonicalCategoryRepository,
    private val mappingRepository: CanonicalCategoryMappingRepository,
    private val synonymRepository: CanonicalCategorySynonymRepository
) {

    @Cacheable("canonical-category", key = "'slug:'+ #slug")
    fun findBySlug(slug: String): CanonicalCategory? =
        categoryRepository.findBySlugAndDeletedAtIsNull(slug)

    @Cacheable("canonical-category", key = "'syn:'+ #name")
    fun findBySynonym(name: String): CanonicalCategory? =
        synonymRepository.findByNameIgnoreCase(name.trim())
            ?.category

    /**
     * Returns a ranked list of category candidates (including the best mapping if present).
     */
    @Transactional(readOnly = true)
    fun suggest(
        product: Product,
        externalSource: String?,
        externalCategoryId: String?,
        externalCategoryName: String?,
        limit: Int = 5
    ): List<CategoryClassification> {
        val results = mutableListOf<CategoryClassification>()

        val normExtKey = externalCategoryId?.ifBlank { null } ?: externalCategoryName?.ifBlank { null }
        normExtKey?.let { key ->
            mappingRepository.findByExternalSourceAndExternalKeyAndDeletedAtIsNull(
                externalSource ?: "UNKNOWN",
                key
            )?.let { mapping ->
                results.add(CategoryClassification(mapping.canonicalCategory, mapping.confidence ?: 0.95, "mapping"))
                return results.take(limit)
            }
        }

        val text = buildString {
            append(product.title.lowercase()).append(" ")
            product.brand?.name?.let { append(it.lowercase()).append(" ") }
            product.description?.let { append(it.lowercase()).append(" ") }
            externalCategoryName?.let { append(it.lowercase()).append(" ") }
        }
        val normalizedTokens = tokenize(normalize(text))

        // Synonym direct hit
        val synHit = heuristicKeywords(text)
        synHit?.let { key ->
            findBySynonym(key)?.let { cat ->
                results.add(CategoryClassification(cat, 0.65, "synonym"))
            }
        }

        // Keyword heuristic slug
        heuristicKeywords(text)?.let { slug ->
            findBySlug(slug)?.let { cat ->
                results.add(CategoryClassification(cat, 0.55, "heuristic"))
            }
        }

        // Hybrid weighted scoring across taxonomy (tokens + synonyms + path + tf-idf)
        val categories = categoryRepository.findAll().associateBy { it.slug }
        val tokenCache = categories.mapValues { (_, cat) ->
            val base = categoryTokens(cat)
            val pathTokens = tokenize(normalize(cat.path ?: ""))
            base + pathTokens
        }

        val tfidfScores = tfidfScores(normalizedTokens, tokenCache)

        val scored = tokenCache.mapNotNull { (slug, tokens) ->
            if (tokens.isEmpty()) return@mapNotNull null
            val overlap = normalizedTokens.toSet().intersect(tokens)
            val tokenScore = overlap.size.toDouble() / normalizedTokens.size.coerceAtLeast(1)

            val tfidfScore = tfidfScores[slug] ?: 0.0
            val synBoost = if (synHit != null && slug.contains(synHit)) 0.2 else 0.0

            val combined = 0.45 * tokenScore + 0.45 * tfidfScore + synBoost
            val conf = 0.35 + combined.coerceIn(0.0, 0.65)
            CategoryClassification(categories[slug], conf, "tfidf")
        }.sortedByDescending { it.confidence }

        results.addAll(scored)

        return results
            .distinctBy { it.category?.id }
            .sortedByDescending { it.confidence }
            .take(limit)
    }

    /**
        * Try to classify a product using existing mappings and simple heuristics.
        * This is intentionally lightweight; a richer classifier can replace this service.
        */
    @Transactional(readOnly = true)
    fun classify(
        product: Product,
        externalSource: String?,
        externalCategoryId: String?,
        externalCategoryName: String?
    ): CategoryClassification? {
        return suggest(product, externalSource, externalCategoryId, externalCategoryName, limit = 5).firstOrNull()
    }

    /**
     * Deterministic text matcher (no ML): exact match > synonym match > token overlap.
     */
    @Transactional(readOnly = true)
    fun matchByText(input: String, minConfidence: Double = 0.6): CategoryClassification? {
        val normalized = normalize(input)
        if (normalized.isBlank()) return null

        // Exact synonym match
        findBySynonym(input)?.let { cat ->
            return CategoryClassification(cat, 0.95, "synonym-exact")
        }

        val categories = categoryRepository.findAll()
        if (categories.isEmpty()) return null

        // Exact category name match
        categories.firstOrNull { normalize(it.name) == normalized }?.let { cat ->
            return CategoryClassification(cat, 0.9, "name-exact")
        }

        val inputTokens = tokenize(normalized).toSet()
        if (inputTokens.isEmpty()) return null

        val synonymsByCategory = synonymRepository.findAll()
            .filter { it.category != null }
            .groupBy { it.category!!.id }

        var best: CategoryClassification? = null
        categories.forEach { cat ->
            val tokens = mutableSetOf<String>()
            tokens.addAll(tokenize(normalize(cat.name)))
            synonymsByCategory[cat.id]
                ?.map { it.name }
                ?.forEach { syn -> tokens.addAll(tokenize(normalize(syn))) }

            if (tokens.isEmpty()) return@forEach

            val overlap = inputTokens.intersect(tokens)
            if (overlap.isEmpty()) return@forEach

            val overlapScore = overlap.size.toDouble() / inputTokens.size.coerceAtLeast(1)
            val confidence = (0.4 + 0.6 * overlapScore).coerceIn(0.4, 0.9)
            val candidate = CategoryClassification(cat, confidence, "token-overlap")
            if (best == null || candidate.confidence > best!!.confidence) {
                best = candidate
            }
        }

        return best?.takeIf { it.confidence >= minConfidence }
    }

    private fun heuristicKeywords(text: String): String? {
        val normalized = normalize(text)
        fun has(vararg tokens: String) = tokens.any { normalized.contains(it) }

        return when {
            has("sneaker", "trainer", "running shoe", "sneakers", "trainers") -> "shoes-sneakers"
            has("boot", "chelsea", "combat", "hiking boot") -> "shoes-boots"
            has("heel", "pump", "stiletto") -> "shoes-heels"
            has("loafer", "mule") -> "shoes-loafers"
            has("sandal", "flip flops", "slides") -> "shoes-sandals"
            has("bag", "tote", "backpack", "shoulder bag", "crossbody") -> "bags"
            has("wallet", "cardholder") -> "wallets"
            has("dress", "gown") -> "dresses"
            has("skirt") -> "skirts"
            has("shirt", "button-down", "button down", "button up", "polo") -> "tops-shirts"
            has("tee", "t-shirt", "tshirt") -> "tops-tshirts"
            has("hoodie", "sweatshirt") -> "tops-hoodies"
            has("sweater", "knit", "cardigan") -> "knitwear"
            has("coat", "jacket", "parka", "puffer", "anorak", "blazer") -> "outerwear"
            has("jean", "denim", "pant", "trouser", "chino", "cargo") -> "bottoms"
            has("short", "shorts") -> "shorts"
            has("swim", "bikini", "one-piece", "swimsuit") -> "swimwear"
            has("hat", "cap", "beanie", "bucket") -> "hats"
            has("belt") -> "belts"
            has("scarf") -> "scarves"
            has("sock") -> "socks"
            has("glove", "mitten") -> "gloves"
            has("jewel", "bracelet", "ring", "earring", "necklace") -> "jewelry"
            else -> null
        }
    }

    private fun tokenize(text: String): List<String> =
        text.split("\\s+".toRegex()).mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }

    private fun categoryTokens(cat: CanonicalCategory): Set<String> {
        val tokens = mutableSetOf<String>()
        tokens.addAll(tokenize(normalize(cat.name)))
        cat.synonyms.forEach { syn -> tokens.addAll(tokenize(normalize(syn.name))) }
        return tokens
    }

    private fun tfidfScores(
        productTokens: List<String>,
        categoryTokens: Map<String, Set<String>>
    ): Map<String, Double> {
        if (productTokens.isEmpty() || categoryTokens.isEmpty()) return emptyMap()

        val productTf = productTokens.groupingBy { it }.eachCount().mapValues { (_, c) ->
            c.toDouble() / productTokens.size.coerceAtLeast(1)
        }

        val docCount = categoryTokens.size.toDouble().coerceAtLeast(1.0)
        val df = mutableMapOf<String, Int>()
        categoryTokens.values.forEach { tokens ->
            tokens.forEach { t -> df[t] = (df[t] ?: 0) + 1 }
        }
        val idf = df.mapValues { (_, count) ->
            kotlin.math.ln((docCount + 1) / (count + 1)) + 1.0
        }

        val productVec = productTf.mapValues { (token, tf) -> tf * (idf[token] ?: 0.0) }

        fun dot(a: Map<String, Double>, b: Map<String, Double>): Double =
            a.entries.sumOf { (k, v) -> v * (b[k] ?: 0.0) }

        fun norm(vec: Map<String, Double>): Double =
            kotlin.math.sqrt(vec.values.sumOf { it * it }.coerceAtLeast(1e-12))

        val productNorm = norm(productVec)

        val scores = mutableMapOf<String, Double>()
        categoryTokens.forEach { (slug, tokens) ->
            val tfCat = tokens.groupingBy { it }.eachCount().mapValues { (_, c) ->
                c.toDouble() / tokens.size.coerceAtLeast(1)
            }
            val catVec = tfCat.mapValues { (token, tf) -> tf * (idf[token] ?: 0.0) }
            val sim = dot(productVec, catVec) / (productNorm * norm(catVec))
            if (sim > 0) scores[slug] = sim.coerceIn(0.0, 1.0)
        }
        return scores
    }

    private fun normalize(input: String): String {
        val lower = input.lowercase()
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return normalized.replace("[^a-z0-9\\s-]".toRegex(), " ")
    }

    @Transactional
    fun upsertMapping(
        externalSource: String,
        externalKey: String,
        canonicalCategory: CanonicalCategory,
        confidence: Double? = null,
        notes: String? = null
    ): CanonicalCategoryMapping {
        val existing = mappingRepository.findByExternalSourceAndExternalKeyAndDeletedAtIsNull(externalSource, externalKey)
        val mapping = existing ?: CanonicalCategoryMapping().apply {
            this.externalSource = externalSource
            this.externalKey = externalKey
        }
        mapping.canonicalCategory = canonicalCategory
        mapping.confidence = confidence
        mapping.notes = notes
        return mappingRepository.save(mapping)
    }
}
