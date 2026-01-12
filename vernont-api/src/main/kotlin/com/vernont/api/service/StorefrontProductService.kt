package com.vernont.api.service

import com.vernont.domain.product.Product
import com.vernont.domain.product.ProductStatus
import com.vernont.domain.product.ProductVariantPrice
import com.vernont.domain.product.specification.ProductSpecification
import com.vernont.infrastructure.cache.ManagedCache
import com.vernont.repository.product.BrandRepository
import com.vernont.repository.product.ProductCategoryRepository
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.flows.product.*
import com.vernont.workflow.flows.product.dto.StorefrontProductDto
import com.vernont.workflow.flows.product.dto.StorefrontVariantDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@Service
class StorefrontProductService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val categoryRepository: ProductCategoryRepository
) {

    @ManagedCache(
        cacheName = "'storefront:products'",
        key = "#input.toCacheKey()",
        ttlSeconds = 300,  // 5 minutes
        cacheEmpty = false
    )
    fun listProducts(input: ListProductsInput): ListProductsOutput {
        logger.debug { "Listing products with filters: ${input.toCacheKey()}" }

        val handle = input.handle
        val query = input.query

        // CASE 1: Handle-based lookup (single product)
        if (!handle.isNullOrBlank()) {
            return handleSingleProductLookup(handle)
        }

        // CASE 2: Full-text search with query
        if (!query.isNullOrBlank()) {
            return handleFullTextSearch(input)
        }

        // CASE 3: Filtered listing using JPA Specifications
        return handleFilteredListing(input)
    }

    @ManagedCache(
        cacheName = "'storefront:filters'",
        key = "'options'",
        ttlSeconds = 600,  // 10 minutes
        cacheEmpty = true
    )
    fun getFilterOptions(): FilterOptions {
        logger.debug { "Fetching filter options" }

        // Get brands with product counts
        val brands = brandRepository.findAllByDeletedAtIsNull()
            .filter { it.active }
            .map { brand ->
                val count = productRepository.countByBrandId(brand.id)
                FilterOption(
                    id = brand.id,
                    name = brand.name,
                    slug = brand.slug,
                    count = count
                )
            }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }

        // Get categories with product counts
        val categories = categoryRepository.findAllActivePublic()
            .map { category ->
                val count = categoryRepository.countProductsByCategoryId(category.id)
                FilterOption(
                    id = category.id,
                    name = category.name,
                    slug = category.handle,
                    count = count
                )
            }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }

        // Get distinct sizes
        val sizes = productRepository.findAllDistinctSizes()
            .filter { it.isNotBlank() }

        // Get distinct colors from variant titles
        val colors = try {
            productRepository.findAllDistinctColors()
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .distinct()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch colors, returning empty list" }
            emptyList()
        }

        // Get price range
        val priceRange = calculatePriceRange()

        return FilterOptions(
            brands = brands,
            categories = categories,
            sizes = sizes,
            colors = colors,
            priceRange = priceRange
        )
    }

    private fun handleSingleProductLookup(handle: String): ListProductsOutput {
        val product = productRepository.findWithVariantsByIdAndDeletedAtIsNull(handle)
            ?: productRepository.findByHandleAndDeletedAtIsNull(handle)
                ?.takeIf { it.status == ProductStatus.PUBLISHED }

        if (product == null) {
            return ListProductsOutput(
                items = emptyList(),
                page = 0,
                size = 1,
                total = 0
            )
        }

        return ListProductsOutput(
            items = listOf(toStorefrontDto(product)),
            page = 0,
            size = 1,
            total = 1
        )
    }

    private fun handleFullTextSearch(input: ListProductsInput): ListProductsOutput {
        val query = input.query!!
        val offset = input.page * input.size

        return try {
            val products = productRepository.fullTextSearch(query, input.size, offset)
            val total = productRepository.countFullTextSearch(query)

            // Apply additional filters to full-text results
            val filteredProducts = applyInMemoryFilters(products, input)

            ListProductsOutput(
                items = filteredProducts.map { toStorefrontDto(it) },
                page = input.page,
                size = input.size,
                total = total
            )
        } catch (e: Exception) {
            logger.warn(e) { "Full-text search failed, falling back to specification search" }
            handleFilteredListing(input.copy(query = null))
        }
    }

    private fun handleFilteredListing(input: ListProductsInput): ListProductsOutput {
        val sort = buildSort(input.sortBy, input.sortDirection)
        val pageable = PageRequest.of(input.page, input.size, sort)

        // Build combined specification
        val spec = ProductSpecification.combineSpecs(
            ProductSpecification.publishedAndNotDeleted(),
            // Region/currency filtering - products must have prices in the specified region/currency
            input.regionId?.let { ProductSpecification.withRegionId(it) },
            input.currencyCode?.let { ProductSpecification.withCurrencyCode(it) },
            input.brandId?.let { ProductSpecification.withBrandId(it) },
            input.brandIds?.let { ProductSpecification.withBrandIds(it) },
            input.categoryId?.let { ProductSpecification.withCategoryId(it) },
            input.categoryIds?.let { ProductSpecification.withCategoryIds(it) },
            input.sizes?.let { ProductSpecification.withSizes(it) },
            input.minPrice?.let { ProductSpecification.withMinPrice(it) },
            input.maxPrice?.let { ProductSpecification.withMaxPrice(it) },
            if (input.onSale == true) ProductSpecification.onSale() else null,
            input.query?.let { ProductSpecification.withTitleLike(it) }
        )

        val page = productRepository.findAll(spec, pageable)

        // Pass region/currency context for proper price selection
        return ListProductsOutput(
            items = page.content.map { toStorefrontDto(it, input.regionId, input.currencyCode) },
            page = input.page,
            size = input.size,
            total = page.totalElements
        )
    }

    private fun applyInMemoryFilters(products: List<Product>, input: ListProductsInput): List<Product> {
        var filtered = products

        // Filter by brand
        input.brandId?.let { brandId ->
            filtered = filtered.filter { it.brand?.id == brandId }
        }
        input.brandIds?.let { brandIds ->
            if (brandIds.isNotEmpty()) {
                filtered = filtered.filter { it.brand?.id in brandIds }
            }
        }

        // Filter by category
        input.categoryId?.let { categoryId ->
            filtered = filtered.filter { product ->
                product.categories.any { it.id == categoryId }
            }
        }
        input.categoryIds?.let { categoryIds ->
            if (categoryIds.isNotEmpty()) {
                filtered = filtered.filter { product ->
                    product.categories.any { it.id in categoryIds }
                }
            }
        }

        // Filter by size
        input.sizes?.let { sizes ->
            if (sizes.isNotEmpty()) {
                filtered = filtered.filter { it.extractedSize in sizes }
            }
        }

        // Filter by color (from variant options)
        input.colors?.let { colors ->
            if (colors.isNotEmpty()) {
                val lowerColors = colors.map { it.lowercase().trim() }
                filtered = filtered.filter { product ->
                    product.variants.any { variant ->
                        if (variant.deletedAt != null) return@any false
                        variant.options.any { opt ->
                            opt.deletedAt == null &&
                                opt.option?.title?.lowercase() in listOf("color", "colour") &&
                                opt.value.lowercase().trim() in lowerColors
                        }
                    }
                }
            }
        }

        // Filter by price range
        input.minPrice?.let { minPrice ->
            val min = BigDecimal.valueOf(minPrice).movePointLeft(2)
            filtered = filtered.filter { product ->
                getLowestPrice(product)?.let { it >= min } ?: false
            }
        }
        input.maxPrice?.let { maxPrice ->
            val max = BigDecimal.valueOf(maxPrice).movePointLeft(2)
            filtered = filtered.filter { product ->
                getLowestPrice(product)?.let { it <= max } ?: false
            }
        }

        // Filter on sale
        if (input.onSale == true) {
            filtered = filtered.filter { product ->
                product.variants.any { variant ->
                    variant.prices.any { price ->
                        price.deletedAt == null &&
                            price.compareAtPrice != null &&
                            price.compareAtPrice!! > price.amount
                    }
                }
            }
        }

        return filtered
    }

    private fun buildSort(sortBy: String?, sortDirection: String?): Sort {
        val direction = if (sortDirection?.lowercase() == "desc") Sort.Direction.DESC else Sort.Direction.ASC

        return when (sortBy?.lowercase()) {
            "title" -> Sort.by(direction, "title")
            "newest" -> Sort.by(Sort.Direction.DESC, "createdAt")
            "price" -> Sort.by(direction, "id")  // Price sorting needs special handling
            else -> Sort.by(Sort.Direction.DESC, "createdAt")  // Default: newest first
        }
    }

    private fun calculatePriceRange(): PriceRange? {
        // Simple approach: get from first published product with prices
        val products = productRepository.findByStatusAndDeletedAtIsNull(ProductStatus.PUBLISHED)
        if (products.isEmpty()) return null

        var minPrice: BigDecimal? = null
        var maxPrice: BigDecimal? = null
        var currency = "GBP"

        for (product in products) {
            for (variant in product.variants.filter { it.deletedAt == null }) {
                for (price in variant.prices.filter { it.deletedAt == null }) {
                    if (minPrice == null || price.amount < minPrice) {
                        minPrice = price.amount
                        currency = price.currencyCode
                    }
                    if (maxPrice == null || price.amount > maxPrice) {
                        maxPrice = price.amount
                    }
                }
            }
        }

        if (minPrice == null || maxPrice == null) return null

        return PriceRange(
            min = minPrice.movePointRight(2).longValueExact(),
            max = maxPrice.movePointRight(2).longValueExact(),
            currency = currency
        )
    }

    private fun getLowestPrice(product: Product): BigDecimal? {
        return product.variants
            .filter { it.deletedAt == null }
            .flatMap { it.prices }
            .filter { it.deletedAt == null }
            .minByOrNull { it.amount }
            ?.amount
    }

    /**
     * Convert Product to StorefrontProductDto with optional region/currency filtering.
     * If regionId or currencyCode is provided, prices are filtered to that region/currency.
     * Otherwise, all prices are considered and the lowest is selected.
     */
    private fun toStorefrontDto(
        product: Product,
        regionId: String? = null,
        currencyCode: String? = null
    ): StorefrontProductDto {
        val variants = product.variants
            .filter { it.deletedAt == null }

        // Helper to filter prices based on region/currency
        val allPrices = variants.flatMap { v -> v.prices.filter { it.deletedAt == null } }

        // Filter prices for a variant based on region/currency
        fun getFilteredPrices(prices: Collection<ProductVariantPrice>): List<ProductVariantPrice> {
            var filtered = prices.filter { it.deletedAt == null }

            // Filter by region if specified
            if (!regionId.isNullOrBlank()) {
                val regionFiltered = filtered.filter { it.regionId == regionId }
                if (regionFiltered.isNotEmpty()) {
                    filtered = regionFiltered
                }
            }

            // Filter by currency if specified
            if (!currencyCode.isNullOrBlank()) {
                val currencyFiltered = filtered.filter { it.currencyCode.equals(currencyCode, ignoreCase = true) }
                if (currencyFiltered.isNotEmpty()) {
                    filtered = currencyFiltered
                }
            }

            return filtered
        }

        // Sort variants by their filtered lowest price
        val sortedVariants = variants.sortedBy { variant ->
            getFilteredPrices(variant.prices).minOfOrNull { it.amount }
        }

        // Get lowest price across all variants (filtered)
        val lowestPrice: ProductVariantPrice? = sortedVariants
            .flatMap { getFilteredPrices(it.prices) }
            .minByOrNull { it.amount }

        return StorefrontProductDto(
            id = product.id,
            handle = product.handle,
            title = product.title,
            description = product.description,
            thumbnail = product.thumbnail,
            imageUrls = product.images.filter { it.deletedAt == null }.map { it.url },
            brand = product.brand?.name,
            lowestPriceMinor = lowestPrice?.amount?.movePointRight(2)?.longValueExact(),
            currency = lowestPrice?.currencyCode,
            variants = sortedVariants.map { variant ->
                val filteredPrices = getFilteredPrices(variant.prices)
                val price: ProductVariantPrice? = filteredPrices.minByOrNull { it.amount }
                val compareAtPrice: ProductVariantPrice? = filteredPrices
                    .filter { p -> p.compareAtPrice != null && p.compareAtPrice!! > p.amount }
                    .minByOrNull { it.amount }

                StorefrontVariantDto(
                    id = variant.id,
                    title = variant.title,
                    sku = variant.sku,
                    priceMinor = price?.amount?.movePointRight(2)?.longValueExact(),
                    compareAtPriceMinor = compareAtPrice?.compareAtPrice?.movePointRight(2)?.longValueExact(),
                    currency = price?.currencyCode,
                    inventoryQuantity = null
                )
            },
            metadata = product.metadata
        )
    }
}
