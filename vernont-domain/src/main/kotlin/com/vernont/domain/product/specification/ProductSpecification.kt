package com.vernont.domain.product.specification

import com.vernont.domain.product.*
import jakarta.persistence.criteria.*
import org.springframework.data.jpa.domain.Specification
import java.math.BigDecimal

/**
 * JPA Specification builder for dynamic product filtering.
 * Supports filtering by brand, category, size, price range, and sale status.
 */
object ProductSpecification {

    fun publishedAndNotDeleted(): Specification<Product> = Specification { root, _, cb ->
        cb.and(
            cb.equal(root.get<ProductStatus>("status"), ProductStatus.PUBLISHED),
            cb.isNull(root.get<Any>("deletedAt"))
        )
    }

    fun withBrandId(brandId: String?): Specification<Product>? {
        if (brandId.isNullOrBlank()) return null
        return Specification { root, _, cb ->
            val brandJoin = root.join<Product, Brand>("brand", JoinType.LEFT)
            cb.equal(brandJoin.get<String>("id"), brandId)
        }
    }

    fun withBrandIds(brandIds: List<String>?): Specification<Product>? {
        if (brandIds.isNullOrEmpty()) return null
        return Specification { root, _, _ ->
            val brandJoin = root.join<Product, Brand>("brand", JoinType.LEFT)
            brandJoin.get<String>("id").`in`(brandIds)
        }
    }

    fun withCategoryId(categoryId: String?): Specification<Product>? {
        if (categoryId.isNullOrBlank()) return null
        return Specification { root, _, cb ->
            val categoriesJoin = root.join<Product, ProductCategory>("categories", JoinType.LEFT)
            cb.equal(categoriesJoin.get<String>("id"), categoryId)
        }
    }

    fun withCategoryIds(categoryIds: List<String>?): Specification<Product>? {
        if (categoryIds.isNullOrEmpty()) return null
        return Specification { root, _, _ ->
            val categoriesJoin = root.join<Product, ProductCategory>("categories", JoinType.LEFT)
            categoriesJoin.get<String>("id").`in`(categoryIds)
        }
    }

    fun withSizes(sizes: List<String>?): Specification<Product>? {
        if (sizes.isNullOrEmpty()) return null
        return Specification { root, _, _ ->
            root.get<String>("extractedSize").`in`(sizes)
        }
    }

    // Note: Color filtering is handled via in-memory filtering in StorefrontProductService
    // because ProductOption.values is an ElementCollection which is complex to query in JPA

    /**
     * Filter products that have prices in the specified region.
     * A product is available in a region if any of its variants has a price with that regionId.
     */
    fun withRegionId(regionId: String?): Specification<Product>? {
        if (regionId.isNullOrBlank()) return null
        return Specification { root, query, cb ->
            val subquery = query.subquery(Long::class.java)
            val variantRoot = subquery.from(ProductVariant::class.java)
            val priceJoin = variantRoot.join<ProductVariant, ProductVariantPrice>("prices", JoinType.INNER)

            subquery.select(cb.literal(1L))
                .where(
                    cb.equal(variantRoot.get<Product>("product"), root),
                    cb.isNull(variantRoot.get<Any>("deletedAt")),
                    cb.isNull(priceJoin.get<Any>("deletedAt")),
                    cb.equal(priceJoin.get<String>("regionId"), regionId)
                )

            cb.exists(subquery)
        }
    }

    /**
     * Filter products that have prices in the specified currency.
     */
    fun withCurrencyCode(currencyCode: String?): Specification<Product>? {
        if (currencyCode.isNullOrBlank()) return null
        return Specification { root, query, cb ->
            val subquery = query.subquery(Long::class.java)
            val variantRoot = subquery.from(ProductVariant::class.java)
            val priceJoin = variantRoot.join<ProductVariant, ProductVariantPrice>("prices", JoinType.INNER)

            subquery.select(cb.literal(1L))
                .where(
                    cb.equal(variantRoot.get<Product>("product"), root),
                    cb.isNull(variantRoot.get<Any>("deletedAt")),
                    cb.isNull(priceJoin.get<Any>("deletedAt")),
                    cb.equal(cb.upper(priceJoin.get("currencyCode")), currencyCode.uppercase())
                )

            cb.exists(subquery)
        }
    }

    fun withMinPrice(minPrice: Long?): Specification<Product>? {
        if (minPrice == null) return null
        val minPriceDecimal = BigDecimal.valueOf(minPrice).movePointLeft(2)
        return Specification { root, query, cb ->
            // Subquery to check if any variant has a price >= minPrice
            val subquery = query.subquery(Long::class.java)
            val variantRoot = subquery.from(ProductVariant::class.java)
            val priceJoin = variantRoot.join<ProductVariant, ProductVariantPrice>("prices", JoinType.INNER)

            subquery.select(cb.literal(1L))
                .where(
                    cb.equal(variantRoot.get<Product>("product"), root),
                    cb.isNull(variantRoot.get<Any>("deletedAt")),
                    cb.isNull(priceJoin.get<Any>("deletedAt")),
                    cb.greaterThanOrEqualTo(priceJoin.get("amount"), minPriceDecimal)
                )

            cb.exists(subquery)
        }
    }

    fun withMaxPrice(maxPrice: Long?): Specification<Product>? {
        if (maxPrice == null) return null
        val maxPriceDecimal = BigDecimal.valueOf(maxPrice).movePointLeft(2)
        return Specification { root, query, cb ->
            // Subquery to check if any variant has a price <= maxPrice
            val subquery = query.subquery(Long::class.java)
            val variantRoot = subquery.from(ProductVariant::class.java)
            val priceJoin = variantRoot.join<ProductVariant, ProductVariantPrice>("prices", JoinType.INNER)

            subquery.select(cb.literal(1L))
                .where(
                    cb.equal(variantRoot.get<Product>("product"), root),
                    cb.isNull(variantRoot.get<Any>("deletedAt")),
                    cb.isNull(priceJoin.get<Any>("deletedAt")),
                    cb.lessThanOrEqualTo(priceJoin.get("amount"), maxPriceDecimal)
                )

            cb.exists(subquery)
        }
    }

    fun onSale(): Specification<Product> = Specification { root, query, cb ->
        // Subquery to check if any variant has compareAtPrice > amount
        val subquery = query.subquery(Long::class.java)
        val variantRoot = subquery.from(ProductVariant::class.java)
        val priceJoin = variantRoot.join<ProductVariant, ProductVariantPrice>("prices", JoinType.INNER)

        subquery.select(cb.literal(1L))
            .where(
                cb.equal(variantRoot.get<Product>("product"), root),
                cb.isNull(variantRoot.get<Any>("deletedAt")),
                cb.isNull(priceJoin.get<Any>("deletedAt")),
                cb.isNotNull(priceJoin.get<BigDecimal>("compareAtPrice")),
                cb.greaterThan(priceJoin.get<BigDecimal>("compareAtPrice"), priceJoin.get<BigDecimal>("amount"))
            )

        cb.exists(subquery)
    }

    fun withTitleLike(query: String?): Specification<Product>? {
        if (query.isNullOrBlank()) return null
        return Specification { root, _, cb ->
            val variants = root.join<Product, ProductVariant>("variants", JoinType.LEFT)

            // Search in title OR variant SKU/barcode
            cb.or(
                cb.like(cb.lower(root.get("title")), "%${query.lowercase()}%"),
                cb.like(cb.lower(variants.get("sku")), "%${query.lowercase()}%"),
                cb.like(cb.lower(variants.get("barcode")), "%${query.lowercase()}%")
            )
        }
    }

    /**
     * Combine multiple specifications with AND logic
     */
    fun combineSpecs(vararg specs: Specification<Product>?): Specification<Product> {
        return specs.filterNotNull().reduceOrNull { acc, spec -> acc.and(spec) }
            ?: Specification { _, _, _ -> null }
    }
}
