package com.vernont.application.product

import com.vernont.domain.product.CanonicalCategory
import com.vernont.domain.product.CanonicalCategoryMapping
import com.vernont.domain.product.CanonicalCategorySynonym
import com.vernont.repository.product.CanonicalCategoryMappingRepository
import com.vernont.repository.product.CanonicalCategoryRepository
import com.vernont.repository.product.CanonicalCategorySynonymRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

private val seedLogger = KotlinLogging.logger {}

/**
 * Seeds canonical taxonomy from Shopify product taxonomy YAML (full tree) or a fallback CSV. Safe
 * to run multiple times; upserts by slug and synonym name.
 */
@Component
class CanonicalCategorySeeder(
        private val categoryRepository: CanonicalCategoryRepository,
        private val synonymRepository: CanonicalCategorySynonymRepository,
        private val mappingRepository: CanonicalCategoryMappingRepository,
        @Value("\${canonical-category.seed.enabled:true}") private val enabled: Boolean = true,
        @Value("\${canonical-category.seed.resource-pattern:taxonomy/*.yml}")
        private val yamlPattern: String,
        @Value("\${canonical-category.seed.fallback:taxonomy/canonical_categories.csv}")
        private val fallbackCsv: String,
        @Value("\${canonical-category.seed.default-source:SHOPIFY}")
        private val defaultExternalSource: String = "SHOPIFY",
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (!enabled) {
            seedLogger.info { "Canonical category seeding is disabled" }
            return
        }
        seedLogger.info { "Seeding canonical categories" }
        try {
            val loaded = seedFromYaml()
            if (loaded) {
                seedLogger.info { "Canonical taxonomy seeded from YAML" }
            } else {
                seedLogger.warn { "YAML taxonomy not found; falling back to CSV seed" }
                seedFromCsv()
            }
        } catch (ex: Exception) {
            seedLogger.error(ex) { "Failed to seed canonical categories" }
        }
    }

    @Transactional
    fun seedFromYaml(): Boolean {
        val resources =
                PathMatchingResourcePatternResolver().getResources("classpath*:$yamlPattern")
        if (resources.isEmpty()) return false

        val bySlug = repositorySnapshot()
        val pendingLinks = mutableListOf<Pair<String, String>>() // parentSlug -> childSlug

        resources.forEach { res ->
            seedLogger.debug { "Loading taxonomy file ${res.filename}" }
            val loaderOptions = LoaderOptions().apply { codePointLimit = Int.MAX_VALUE }
            val yaml = Yaml(loaderOptions)
            res.inputStream.use { input ->
                val loaded = yaml.load<Any>(input)
                when (loaded) {
                    is Map<*, *> -> {
                        val mapNode = loaded as Map<String, Any>
                        val hasId = mapNode.containsKey("id") || mapNode.containsKey("slug")
                        val looksLikeMappingFile =
                                mapNode.containsKey("rules") &&
                                        mapNode.containsKey("input_taxonomy")
                        val verticals = mapNode["verticals"] as? List<*>
                        if (verticals != null) {
                            verticals.filterIsInstance<Map<String, Any>>().forEach { vertical ->
                                val categories = vertical["categories"] as? List<*>
                                categories?.filterIsInstance<Map<String, Any>>()?.forEach {
                                    parseNode(it, null, bySlug, pendingLinks)
                                }
                            }
                        } else if (!hasId && looksLikeMappingFile) {
                            seedLogger.info { "Skipping taxonomy mapping file ${res.filename}" }
                        } else {
                            parseNode(mapNode, null, bySlug, pendingLinks)
                        }
                    }
                    is List<*> ->
                            loaded.filterIsInstance<Map<String, Any>>()
                                    .filter { it.containsKey("id") || it.containsKey("slug") }
                                    .forEach { parseNode(it, null, bySlug, pendingLinks) }
                    else -> seedLogger.warn { "Unrecognized taxonomy structure in ${res.filename}" }
                }
            }
        }

        // Wire parents now that all nodes are in map (supports string child references)
        pendingLinks.forEach { (parentSlug, childSlug) ->
            val parent = bySlug[parentSlug]
            val child = bySlug[childSlug]
            if (parent == null || child == null) return@forEach
            child.parent = parent
        }

        bySlug.values.forEach { cat ->
            cat.path = buildPath(cat)
            cat.depth = cat.path?.split("/")?.size?.minus(1)
        }

        if (bySlug.isEmpty()) return false
        val saved = categoryRepository.saveAll(bySlug.values)
        seedDefaultMappings(saved)
        return true
    }

    @Transactional
    fun seedFromCsv() {
        val resource = ClassPathResource(fallbackCsv)
        if (!resource.exists()) {
            seedLogger.warn { "Canonical category fallback resource not found: $fallbackCsv" }
            return
        }
        val rows = resource.inputStream.bufferedReader().use { parseRows(it) }
        if (rows.isEmpty()) return

        val bySlug = repositorySnapshot()
        rows.forEach { row ->
            val existing = bySlug[row.slug] ?: CanonicalCategory().apply { slug = row.slug }
            existing.name = row.name
            existing.sortOrder = row.sortOrder
            bySlug[row.slug] = existing
        }
        rows.forEach { row ->
            val node = bySlug[row.slug] ?: return@forEach
            node.parent = row.parentSlug?.let { bySlug[it] }
        }
        bySlug.values.forEach { cat ->
            cat.path = buildPath(cat)
            cat.depth = cat.path?.split("/")?.size?.minus(1)
        }
        val saved = categoryRepository.saveAll(bySlug.values)
        seedDefaultMappings(saved)
    }

    private fun repositorySnapshot(): MutableMap<String, CanonicalCategory> =
            categoryRepository.findAll().associateBy { it.slug }.toMutableMap()

    private fun parseNode(
            node: Map<String, Any>,
            parentSlug: String?,
            bySlug: MutableMap<String, CanonicalCategory>,
            pendingLinks: MutableList<Pair<String, String>>
    ) {
        val slug = (node["id"] ?: node["slug"])?.toString()?.trim()
        if (slug.isNullOrBlank()) {
            val nodeName = node["name"] ?: node["label"]
            seedLogger.warn {
                "Skipping taxonomy node without id/slug. keys=${node.keys} name=${nodeName ?: "unknown"}"
            }
            return
        }
        val name = (node["name"] ?: node["label"] ?: slug).toString()
        val sortOrder = (node["sort_order"] ?: node["sortOrder"])?.toString()?.toIntOrNull()

        val category = bySlug[slug] ?: CanonicalCategory().apply { this.slug = slug }
        category.name = name
        category.sortOrder = sortOrder
        bySlug[slug] = category

        val children = node["children"] as? List<*>
        children?.forEach { child ->
            when (child) {
                is Map<*, *> -> parseNode(child as Map<String, Any>, slug, bySlug, pendingLinks)
                is String -> pendingLinks.add(slug to child)
                else -> seedLogger.warn { "Skipping unsupported child entry under $slug: $child" }
            }
        }

        // synonyms
        val synonyms = (node["synonyms"] ?: node["aliases"]) as? List<*>
        synonyms?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }?.forEach {
                syn ->
            if (category.synonyms.any { it.name.equals(syn, ignoreCase = true) }) return@forEach
            val synonym =
                    synonymRepository.findByNameIgnoreCase(syn)
                            ?: CanonicalCategorySynonym().apply { this.name = syn }
            synonym.category = category
            category.synonyms.add(synonym)
        }

        parentSlug?.let { pendingLinks.add(it to slug) }
    }

    private fun buildPath(category: CanonicalCategory): String {
        val parts = mutableListOf<String>()
        var current: CanonicalCategory? = category
        while (current != null) {
            parts.add(current.slug)
            current = current.parent
        }
        return parts.reversed().joinToString("/")
    }

    private fun seedDefaultMappings(categories: Iterable<CanonicalCategory>) {
        categories.forEach { cat ->
            val existing =
                    mappingRepository.findByExternalSourceAndExternalKeyAndDeletedAtIsNull(
                            defaultExternalSource,
                            cat.slug
                    )
            val mapping =
                    existing
                            ?: CanonicalCategoryMapping().apply {
                                externalSource = defaultExternalSource
                                externalKey = cat.slug
                            }
            mapping.canonicalCategory = cat
            mapping.confidence = mapping.confidence ?: 0.99
            mappingRepository.save(mapping)
        }
    }

    private data class Row(
            val slug: String,
            val name: String,
            val parentSlug: String?,
            val sortOrder: Int?
    )

    private fun parseRows(reader: BufferedReader): List<Row> =
            reader.lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .drop(1)
                    .mapNotNull { line ->
                        val parts = line.split(",")
                        if (parts.size < 2) return@mapNotNull null
                        val slug = parts[0].trim()
                        val name = parts[1].trim()
                        val parent = parts.getOrNull(2)?.trim().takeIf { !it.isNullOrBlank() }
                        val sort =
                                parts.getOrNull(3)
                                        ?.trim()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.toIntOrNull()
                        if (slug.isEmpty() || name.isEmpty()) null
                        else Row(slug, name, parent, sort)
                    }
                    .toList()
}
