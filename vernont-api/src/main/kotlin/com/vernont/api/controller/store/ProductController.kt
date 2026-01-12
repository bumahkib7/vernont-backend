package com.vernont.api.controller.store

import com.vernont.application.review.ReviewService
import com.vernont.domain.product.Product
import com.vernont.domain.product.dto.*
import com.vernont.infrastructure.storage.PresignedUrlService
import com.vernont.repository.product.ProductRepository
import com.vernont.api.service.StoreProductService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.persistence.criteria.Predicate

private val logger = KotlinLogging.logger {}

/**
 * Store Product Controller - Medusa-compatible API
 *
 * Implements Medusa's store API for products
 * @see https://docs.medusajs.com/api/store#products
 */
@RestController
@RequestMapping("/store/products")
@CrossOrigin(origins = ["http://localhost:8000", "http://localhost:9000", "http://localhost:3000"])
class ProductController(
    private val productRepository: ProductRepository,
    private val storeProductService: StoreProductService,
    private val presignedUrlService: PresignedUrlService,
    private val reviewService: ReviewService
) {

    @GetMapping
    fun getProducts(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) handle: String?,
        @RequestParam(name = "region_id", required = false) regionId: String?,
        @RequestParam(name = "currency_code", required = false, defaultValue = "usd") currencyCode: String,
        @RequestParam(required = false) fields: String?
    ): ResponseEntity<StoreProductListResponse> {
        val spec = Specification<Product> { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            if (handle != null) {
                predicates.add(cb.equal(root.get<String>("handle"), handle))
            }

            predicates.add(cb.isNull(root.get<java.time.OffsetDateTime>("deletedAt")))

            cb.and(*predicates.toTypedArray())
        }

        val pageable = PageRequest.of(offset / limit, limit)
        val productsPage = productRepository.findAll(spec, pageable)

        val variantIds = productsPage.content.flatMap { product ->
            product.variants.filter { it.deletedAt == null }.map { it.id }
        }
        val inventoryMap = storeProductService.getInventoryForVariants(variantIds)
        val priceMap = storeProductService.getPricesForVariants(variantIds, currencyCode, regionId)

        // Fetch review stats for all products
        val productIds = productsPage.content.map { it.id }
        val reviewStatsMap = try {
            reviewService.getProductStatsForMultiple(productIds)
                .mapValues { (_, stats) ->
                    StoreProductReviewStatsDto(
                        averageRating = stats.averageRating,
                        reviewCount = stats.totalReviews,
                        recommendationPercent = stats.recommendationPercent
                    )
                }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch review stats for products" }
            emptyMap()
        }

        val response = StoreProductListResponse(
            products = productsPage.content.map { product ->
                signMedia(StoreProductDto.from(
                    product,
                    inventoryMap,
                    priceMap,
                    reviewStatsMap[product.id]
                ))
            },
            count = productsPage.totalElements.toInt(),
            offset = offset,
            limit = limit
        )

        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable id: String,
        @RequestParam(name = "region_id", required = false) regionId: String?,
        @RequestParam(name = "currency_code", required = false, defaultValue = "usd") currencyCode: String
    ): ResponseEntity<StoreProductResponse> {
        val product = productRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val variantIds = product.variants.filter { it.deletedAt == null }.map { it.id }
        val inventoryMap = storeProductService.getInventoryForVariants(variantIds)
        val priceMap = storeProductService.getPricesForVariants(variantIds, currencyCode, regionId)

        // Fetch review stats for this product
        val reviewStats = try {
            val stats = reviewService.getProductStats(id)
            StoreProductReviewStatsDto(
                averageRating = stats.averageRating,
                reviewCount = stats.totalReviews,
                recommendationPercent = stats.recommendationPercent
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch review stats for product $id" }
            null
        }

        return ResponseEntity.ok(
            StoreProductResponse(
                product = signMedia(StoreProductDto.from(product, inventoryMap, priceMap, reviewStats))
            )
        )
    }

    private fun signMedia(dto: StoreProductDto): StoreProductDto {
        val signedImages = dto.images.map { img ->
            img.copy(url = presignedUrlService.signIfNeeded(img.url) ?: img.url)
        }
        val signedThumbnail = presignedUrlService.signIfNeeded(dto.thumbnail) ?: dto.thumbnail
        val signedVariants = dto.variants.map { variant ->
            variant.copy(
                images = variant.images.map { img ->
                    img.copy(url = presignedUrlService.signIfNeeded(img.url) ?: img.url)
                }
            )
        }
        return dto.copy(
            images = signedImages,
            thumbnail = signedThumbnail,
            variants = signedVariants
        )
    }
}

data class StoreProductListResponse(
    val products: List<StoreProductDto>,
    val count: Int,
    val offset: Int? = null,
    val limit: Int? = null
)

data class StoreProductResponse(
    val product: StoreProductDto
)
