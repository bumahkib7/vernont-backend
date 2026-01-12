package com.vernont.workflow.flows.product

import com.vernont.domain.product.Product
import com.vernont.domain.product.ProductStatus
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.engine.*
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.flows.product.dto.StorefrontProductDto
import com.vernont.workflow.flows.product.dto.StorefrontVariantDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

data class ListProductsInput(
    val query: String? = null,
    val regionId: String? = null,      // Filter by region (products with prices in this region)
    val currencyCode: String? = null,  // Currency for prices (defaults to region's currency)
    val brandId: String? = null,
    val brandIds: List<String>? = null,
    val categoryId: String? = null,
    val categoryIds: List<String>? = null,
    val handle: String? = null,
    val sizes: List<String>? = null,
    val colors: List<String>? = null,  // Color filter
    val minPrice: Long? = null,    // In minor units (cents)
    val maxPrice: Long? = null,    // In minor units (cents)
    val onSale: Boolean? = null,
    val sortBy: String? = null,    // price, title, newest
    val sortDirection: String? = null, // asc, desc
    val page: Int = 0,
    val size: Int = 20
) {
    // Generate cache key from all filter parameters
    fun toCacheKey(): String = listOfNotNull(
        query?.let { "q:$it" },
        regionId?.let { "r:$it" },
        currencyCode?.let { "cur:$it" },
        brandId?.let { "b:$it" },
        brandIds?.sorted()?.joinToString(",")?.let { "bs:$it" },
        categoryId?.let { "c:$it" },
        categoryIds?.sorted()?.joinToString(",")?.let { "cs:$it" },
        sizes?.sorted()?.joinToString(",")?.let { "sz:$it" },
        colors?.sorted()?.joinToString(",")?.let { "col:$it" },
        minPrice?.let { "min:$it" },
        maxPrice?.let { "max:$it" },
        onSale?.let { "sale:$it" },
        sortBy?.let { "sort:$it" },
        sortDirection?.let { "dir:$it" },
        "p:$page",
        "s:$size"
    ).joinToString("|").ifEmpty { "all" }
}

data class ListProductsOutput(
    val items: List<StorefrontProductDto>,
    val page: Int,
    val size: Int,
    val total: Long,
    val filters: FilterOptions? = null
)

data class FilterOptions(
    val brands: List<FilterOption> = emptyList(),
    val categories: List<FilterOption> = emptyList(),
    val sizes: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val priceRange: PriceRange? = null
)

data class FilterOption(
    val id: String,
    val name: String,
    val slug: String? = null,
    val count: Long = 0
)

data class PriceRange(
    val min: Long,
    val max: Long,
    val currency: String
)

private data class PageResult(
    val content: List<Product>,
    val totalElements: Long
)

@Component
@WorkflowTypes(input = ListProductsInput::class, output = ListProductsOutput::class)
class ListStorefrontProductsWorkflow(
    private val productRepo: ProductRepository
) : Workflow<ListProductsInput, ListProductsOutput> {

    override val name: String = WorkflowConstants.Product.LIST_STOREFRONT_PRODUCTS

    override suspend fun execute(
        input: ListProductsInput,
        context: WorkflowContext
    ): WorkflowResult<ListProductsOutput> {
        logger.info {
            "Listing storefront products page=${input.page}, size=${input.size}, " +
                    "query=${input.query}, brandId=${input.brandId}, categoryId=${input.categoryId}, handle=${input.handle}"
        }

        return try {
            val loadProductsStep = createStep<ListProductsInput, ListProductsOutput>(
                name = "storefront-load-products",
                execute = { inp, _ ->

                    // CASE 1: lookup by handle (single product)
                    if (!inp.handle.isNullOrBlank()) {
                        val product = productRepo.findWithVariantsByIdAndDeletedAtIsNull(inp.handle)
                            ?: productRepo.findByHandleAndDeletedAtIsNull(inp.handle)
                                ?.takeIf { it.status == ProductStatus.PUBLISHED }

                        if (product == null) {
                            return@createStep StepResponse.of(
                                ListProductsOutput(
                                    items = emptyList(),
                                    page = 0,
                                    size = 1,
                                    total = 0
                                )
                            )
                        }

                        val dto = toStorefrontDto(product)

                        return@createStep StepResponse.of(
                            ListProductsOutput(
                                items = listOf(dto),
                                page = 0,
                                size = 1,
                                total = 1
                            )
                        )
                    }

                    // CASE 2: normal paged listing
                    val pageResult = if (!inp.query.isNullOrBlank()) {
                        // Use PostgreSQL full-text search for better results
                        try {
                            val offset = inp.page * inp.size
                            val products = productRepo.fullTextSearch(inp.query, inp.size, offset)
                            val total = productRepo.countFullTextSearch(inp.query)
                            PageResult(products, total)
                        } catch (e: Exception) {
                            // Fallback to simple search if full-text search fails
                            logger.warn(e) { "Full-text search failed, falling back to simple search" }
                            val products = productRepo.searchByTitle(inp.query)
                            val filtered = products.filter { it.status == ProductStatus.PUBLISHED && it.deletedAt == null }
                            val paged = filtered.drop(inp.page * inp.size).take(inp.size)
                            PageResult(paged, filtered.size.toLong())
                        }
                    } else {
                        val products = productRepo.findByStatusAndDeletedAtIsNull(ProductStatus.PUBLISHED)
                        val paged = products.drop(inp.page * inp.size).take(inp.size)
                        PageResult(paged, products.size.toLong())
                    }

                    val items = pageResult.content.map { product -> toStorefrontDto(product) }

                    StepResponse.of(
                        ListProductsOutput(
                            items = items,
                            page = inp.page,
                            size = inp.size,
                            total = pageResult.totalElements
                        )
                    )
                }
            )

            val output = loadProductsStep.invoke(input, context).data
            WorkflowResult.success(output)

        } catch (e: Exception) {
            logger.error(e) { "ListStorefrontProductsWorkflow failed" }
            WorkflowResult.failure(e)
        }
    }

    private fun toStorefrontDto(product: Product): StorefrontProductDto {
        val variants = product.variants
            .filter { it.deletedAt == null }
            .sortedBy { variant -> variant.prices.filter { it.deletedAt == null }.minOfOrNull { it.amount } }

        val lowestPrice = variants.flatMap { it.prices }
            .filter { it.deletedAt == null }
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
            variants = variants.map { variant ->
                val price = variant.prices.filter { it.deletedAt == null }.minByOrNull { it.amount }
                StorefrontVariantDto(
                    id = variant.id,
                    title = variant.title,
                    sku = variant.sku,
                    priceMinor = price?.amount?.movePointRight(2)?.longValueExact(),
                    currency = price?.currencyCode,
                    inventoryQuantity = null // Inventory lookup requires separate service
                )
            },
            metadata = product.metadata
        )
    }
}
