package com.vernont.api.controller

import com.vernont.domain.product.ProductStatus
import com.vernont.infrastructure.cache.ManagedCache
import com.vernont.repository.product.ProductCollectionRepository
import com.vernont.repository.product.ProductRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping
class SitemapController(
    private val sitemapGenerator: SitemapGenerator
) {

    @GetMapping("/sitemap.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    @ManagedCache(
        cacheName = "'sitemaps'",
        key = "'index'",
        ttlSeconds = 3600
    )
    fun getSitemap(): ResponseEntity<String> {
        val sitemap = sitemapGenerator.generateSitemapIndex()
        return ResponseEntity.ok(sitemap)
    }

    @GetMapping("/sitemap-products-{page}.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    @ManagedCache(
        cacheName = "'sitemaps'",
        key = "'products-' + #page",
        ttlSeconds = 3600
    )
    fun getProductSitemap(@PathVariable page: Int): ResponseEntity<String> {
        val sitemap = sitemapGenerator.generateProductSitemap(page)
        return ResponseEntity.ok(sitemap)
    }

    @GetMapping("/sitemap-collections.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    @ManagedCache(
        cacheName = "'sitemaps'",
        key = "'collections'",
        ttlSeconds = 3600
    )
    fun getCollectionsSitemap(): ResponseEntity<String> {
        val sitemap = sitemapGenerator.generateCollectionsSitemap()
        return ResponseEntity.ok(sitemap)
    }

    @GetMapping("/sitemap-static.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    @ManagedCache(
        cacheName = "'sitemaps'",
        key = "'static'",
        ttlSeconds = 3600
    )
    fun getStaticSitemap(): ResponseEntity<String> {
        val sitemap = sitemapGenerator.generateStaticSitemap()
        return ResponseEntity.ok(sitemap)
    }
}

@Component
class SitemapGenerator(
    private val productRepository: ProductRepository,
    private val collectionRepository: ProductCollectionRepository
) {

    @Value("\${app.frontend.base-url:http://localhost:3000}")
    private lateinit var baseUrl: String
    private val maxUrlsPerSitemap = 45000

    fun generateSitemapIndex(): String {
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        xmlBuilder.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        // Only include luxury brand products in sitemap
        val totalProducts = productRepository.countPublicHandlesByLuxuryBrands()
        val totalProductSitemaps = kotlin.math.ceil(totalProducts / maxUrlsPerSitemap.toDouble()).toInt().coerceAtLeast(1)

        addSitemap(xmlBuilder, "/sitemap-static.xml", today)
        addSitemap(xmlBuilder, "/sitemap-collections.xml", today)
        for (page in 1..totalProductSitemaps) {
            addSitemap(xmlBuilder, "/sitemap-products-$page.xml", today)
        }

        xmlBuilder.append("</sitemapindex>")
        return xmlBuilder.toString()
    }

    fun generateProductSitemap(page: Int): String {
        val safePage = page.coerceAtLeast(1)
        val pageable = PageRequest.of(safePage - 1, maxUrlsPerSitemap)
        // Only include luxury brand products in sitemap
        val handles = productRepository.findPublicHandlesByLuxuryBrands(pageable)
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        xmlBuilder.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")

        handles.forEach { handle ->
            addUrl(xmlBuilder, "/products/$handle", today, "weekly", "0.9")
        }

        xmlBuilder.append("</urlset>")
        return xmlBuilder.toString()
    }

    fun generateCollectionsSitemap(): String {
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        xmlBuilder.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        collectionRepository.findAllPublicHandles().forEach { handle ->
            addUrl(xmlBuilder, "/collections/$handle", today, "weekly", "0.7")
        }

        xmlBuilder.append("</urlset>")
        return xmlBuilder.toString()
    }

    fun generateStaticSitemap(): String {
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        xmlBuilder.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        addUrl(xmlBuilder, "/", today, "daily", "1.0")
        addUrl(xmlBuilder, "/collections", today, "daily", "0.8")

        xmlBuilder.append("</urlset>")
        return xmlBuilder.toString()
    }

    private fun addUrl(builder: StringBuilder, loc: String, lastmod: String, changefreq: String, priority: String) {
        val fullUrl = loc.fold(baseUrl) { acc, char ->
            if (acc.endsWith('/') && char == '/') acc else acc + char
        }
        builder.append("<url>")
        builder.append("<loc>${xmlEscape(fullUrl)}</loc>")
        builder.append("<lastmod>${lastmod}</lastmod>")
        builder.append("<changefreq>${changefreq}</changefreq>")
        builder.append("<priority>${priority}</priority>")
        builder.append("</url>")
    }

    private fun addSitemap(builder: StringBuilder, loc: String, lastmod: String) {
        val fullUrl = loc.fold(baseUrl) { acc, char ->
            if (acc.endsWith('/') && char == '/') acc else acc + char
        }
        builder.append("<sitemap>")
        builder.append("<loc>${xmlEscape(fullUrl)}</loc>")
        builder.append("<lastmod>${lastmod}</lastmod>")
        builder.append("</sitemap>")
    }

    private fun xmlEscape(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
