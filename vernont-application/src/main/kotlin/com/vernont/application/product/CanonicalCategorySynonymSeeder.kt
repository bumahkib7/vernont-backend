package com.vernont.application.product

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.vernont.domain.product.CanonicalCategory
import com.vernont.domain.product.CanonicalCategorySynonym
import com.vernont.repository.product.CanonicalCategoryRepository
import com.vernont.repository.product.CanonicalCategorySynonymRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.ResourcePatternUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val synonymSeedLogger = KotlinLogging.logger {}

@Component
class CanonicalCategorySynonymSeeder(
    private val canonicalCategoryRepository: CanonicalCategoryRepository,
    private val synonymRepository: CanonicalCategorySynonymRepository,
    private val resourceLoader: ResourceLoader,
    @Value("\${canonical-category.synonym.seed.enabled:true}") private val enabled: Boolean = true,
    @Value("\${canonical-category.synonym.seed.resource-path:taxonomy/synonyms.yml}") private val resourcePath: String
) {
    private val yamlMapper = ObjectMapper(YAMLFactory())

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun onApplicationReady() {
        if (!enabled) {
            synonymSeedLogger.info { "Canonical category synonym seeding is disabled" }
            return
        }

        try {
            synonymSeedLogger.info { "Seeding canonical category synonyms" }
            seedSynonyms()
        } catch (ex: Exception) {
            synonymSeedLogger.error(ex) { "Failed to seed canonical category synonyms" }
        }
    }

    private fun seedSynonyms() {
        val resource = resourceLoader.getResource("classpath:$resourcePath")
        if (!resource.exists()) {
            synonymSeedLogger.warn { "Canonical category synonym resource not found: $resourcePath" }
            return
        }

        val synonymData = yamlMapper.readValue(resource.inputStream, object : TypeReference<List<Map<String, Any>>>() {})
        var count = 0
        var updated = 0
        var missingCategories = 0
        synonymData.forEach { entry ->
            val categoryId = entry["category_id"]?.toString()
            val synonyms = (entry["synonyms"] as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf { syn -> syn.isNotEmpty() } }

            if (categoryId != null && synonyms != null) {
                val category = resolveCategory(categoryId)
                if (category != null) {
                    synonyms.forEach { synonymName ->
                        val existing = synonymRepository.findByNameIgnoreCase(synonymName)
                        if (existing == null) {
                            val synonym = CanonicalCategorySynonym().apply {
                                this.id = UUID.randomUUID().toString()
                                this.name = synonymName
                                this.category = category
                            }
                            synonymRepository.save(synonym)
                            count++
                        } else if (existing.category?.id != category.id) {
                            existing.category = category
                            synonymRepository.save(existing)
                            updated++
                        }
                    }
                } else {
                    missingCategories++
                }
            }
        }
        if (missingCategories > 0) {
            synonymSeedLogger.warn { "Skipped $missingCategories synonym entries due to missing categories." }
        }
        synonymSeedLogger.info { "Seeded $count new canonical category synonyms. Updated $updated existing." }
    }

    private fun resolveCategory(categoryId: String): CanonicalCategory? {
        canonicalCategoryRepository.findBySlugAndDeletedAtIsNull(categoryId)?.let { return it }
        if (!categoryId.startsWith("gid://")) {
            val shopifySlug = "gid://shopify/TaxonomyCategory/$categoryId"
            return canonicalCategoryRepository.findBySlugAndDeletedAtIsNull(shopifySlug)
        }
        return null
    }
}
