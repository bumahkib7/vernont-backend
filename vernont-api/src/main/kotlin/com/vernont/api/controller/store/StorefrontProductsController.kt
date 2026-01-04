package com.vernont.api.controller.store

import com.vernont.api.service.StorefrontProductService
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.flows.product.FilterOptions
import com.vernont.workflow.flows.product.ListProductsInput
import com.vernont.workflow.flows.product.ListProductsOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val storefrontLogger = KotlinLogging.logger {}

data class SearchSuggestionsResponse(
    val products: List<String>,
    val brands: List<String>
)

@RestController
@RequestMapping("/storefront/products")
class StorefrontProductsController(
    private val productService: StorefrontProductService,
    private val productRepository: ProductRepository
) {

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun listProducts(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) regionId: String?,
        @RequestParam(required = false) currencyCode: String?,
        @RequestParam(required = false) brandId: String?,
        @RequestParam(required = false) brandIds: List<String>?,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(required = false) handle: String?,
        @RequestParam(required = false) sizes: List<String>?,
        @RequestParam(required = false) colors: List<String>?,
        @RequestParam(required = false) minPrice: Long?,
        @RequestParam(required = false) maxPrice: Long?,
        @RequestParam(required = false) onSale: Boolean?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDirection: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ListProductsOutput> {
        storefrontLogger.info {
            "Listing products: query=$query regionId=$regionId currencyCode=$currencyCode brandId=$brandId categoryId=$categoryId " +
                    "sizes=$sizes colors=$colors minPrice=$minPrice maxPrice=$maxPrice onSale=$onSale " +
                    "sortBy=$sortBy page=$page size=$size"
        }

        val input = ListProductsInput(
            query = query,
            regionId = regionId,
            currencyCode = currencyCode,
            brandId = brandId,
            brandIds = brandIds,
            categoryId = categoryId,
            categoryIds = categoryIds,
            handle = handle,
            sizes = sizes,
            colors = colors,
            minPrice = minPrice,
            maxPrice = maxPrice,
            onSale = onSale,
            sortBy = sortBy,
            sortDirection = sortDirection,
            page = page,
            size = size
        )

        return try {
            val result = productService.listProducts(input)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            storefrontLogger.error(e) { "Failed to list products" }
            throw e
        }
    }

    @GetMapping("/filters")
    @ResponseStatus(HttpStatus.OK)
    fun getFilterOptions(): ResponseEntity<FilterOptions> {
        storefrontLogger.info { "Fetching filter options" }

        return try {
            val filters = productService.getFilterOptions()
            ResponseEntity.ok(filters)
        } catch (e: Exception) {
            storefrontLogger.error(e) { "Failed to get filter options" }
            throw e
        }
    }

    @GetMapping("/suggestions")
    @ResponseStatus(HttpStatus.OK)
    fun getSearchSuggestions(
        @RequestParam query: String,
        @RequestParam(defaultValue = "5") limit: Int
    ): ResponseEntity<SearchSuggestionsResponse> {
        storefrontLogger.info { "Getting search suggestions for query=$query limit=$limit" }

        if (query.length < 2) {
            return ResponseEntity.ok(SearchSuggestionsResponse(emptyList(), emptyList()))
        }

        return try {
            val productSuggestions = productRepository.findTitleSuggestions(query, limit)
            val brandSuggestions = productRepository.findBrandSuggestions(query, limit)

            ResponseEntity.ok(
                SearchSuggestionsResponse(
                    products = productSuggestions,
                    brands = brandSuggestions
                )
            )
        } catch (e: Exception) {
            storefrontLogger.error(e) { "Failed to get search suggestions" }
            ResponseEntity.ok(SearchSuggestionsResponse(emptyList(), emptyList()))
        }
    }
}
