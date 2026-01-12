package com.vernont.domain.product.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.Product
import com.vernont.domain.product.ProductImage
import com.vernont.domain.product.ProductVariant
import java.time.Instant

data class StoreProductImageDto(
    val id: String,
    val url: String,
    val rank: Int = 0
) {
    companion object {
        fun from(image: ProductImage) = StoreProductImageDto(image.id, image.url, image.position)
    }
}

data class StoreProductOptionValueDto(
    val id: String,
    val value: String,
    @JsonProperty("option_id") val optionId: String? = null
)

data class StorePriceDto(
    val id: String,
    val amount: Int,
    @JsonProperty("currency_code") val currencyCode: String,
    @JsonProperty("variant_id") val variantId: String
)

data class StoreCalculatedPriceDto(
    @JsonProperty("calculated_amount") val calculatedAmount: Int,
    @JsonProperty("original_amount") val originalAmount: Int?,
    @JsonProperty("currency_code") val currencyCode: String,
    @JsonProperty("calculated_price") val calculatedPrice: StorePriceDto
)

data class StoreProductVariantDto(
    val id: String,
    val title: String,
    val sku: String?,
    val barcode: String?,
    @JsonProperty("allow_backorder") val allowBackorder: Boolean,
    @JsonProperty("manage_inventory") val manageInventory: Boolean,
    @JsonProperty("inventory_quantity") val inventoryQuantity: Int,
    @JsonProperty("calculated_price") val calculatedPrice: StoreCalculatedPriceDto?,
    val weight: Int?,
    val length: Int?,
    val height: Int?,
    val width: Int?,
    @JsonProperty("origin_country") val originCountry: String?,
    @JsonProperty("hs_code") val hsCode: String?,
    @JsonProperty("mid_code") val midCode: String?,
    val material: String?,
    val options: List<StoreProductOptionValueDto>,
    val images: List<StoreProductImageDto>,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant
) {
    companion object {
        fun from(
            variant: ProductVariant,
            inventoryQuantity: Int = 0,
            calculatedPrice: StoreCalculatedPriceDto? = null
        ) = StoreProductVariantDto(
            id = variant.id,
            title = variant.title,
            sku = variant.sku,
            barcode = variant.barcode,
            allowBackorder = variant.allowBackorder,
            manageInventory = variant.manageInventory,
            inventoryQuantity = inventoryQuantity,
            calculatedPrice = calculatedPrice,
            weight = variant.weight?.toIntOrNull(),
            length = variant.length?.toIntOrNull(),
            height = variant.height?.toIntOrNull(),
            width = variant.width?.toIntOrNull(),
            originCountry = variant.originCountry,
            hsCode = variant.hsCode,
            midCode = variant.midCode,
            material = variant.material,
            options = variant.options.map { StoreProductOptionValueDto(it.id, it.value, it.option?.id) },
            images = emptyList(),
            createdAt = variant.createdAt,
            updatedAt = variant.updatedAt
        )
    }
}

data class StoreProductOptionDto(
    val id: String,
    val title: String,
    val values: List<StoreProductOptionValueDto>
)

/**
 * Review statistics for a product (used in product listings)
 */
data class StoreProductReviewStatsDto(
    @JsonProperty("average_rating") val averageRating: java.math.BigDecimal,
    @JsonProperty("review_count") val reviewCount: Int,
    @JsonProperty("recommendation_percent") val recommendationPercent: Int? = null
)

data class StoreProductDto(
    val id: String,
    val title: String,
    val handle: String,
    val subtitle: String?,
    val description: String?,
    val status: String,
    @JsonProperty("is_giftcard") val isGiftcard: Boolean,
    val discountable: Boolean,
    val thumbnail: String?,
    val weight: Int?,
    val length: Int?,
    val height: Int?,
    val width: Int?,
    @JsonProperty("origin_country") val originCountry: String?,
    @JsonProperty("hs_code") val hsCode: String?,
    @JsonProperty("mid_code") val midCode: String?,
    val material: String?,
    @JsonProperty("collection_id") val collectionId: String?,
    @JsonProperty("type_id") val typeId: String?,
    val variants: List<StoreProductVariantDto>,
    val images: List<StoreProductImageDto>,
    val options: List<StoreProductOptionDto>,
    val metadata: Map<String, Any?>?,
    @JsonProperty("review_stats") val reviewStats: StoreProductReviewStatsDto? = null,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant
) {
    companion object {
        fun from(
            product: Product,
            inventoryMap: Map<String, Int> = emptyMap(),
            priceMap: Map<String, StoreCalculatedPriceDto> = emptyMap(),
            reviewStats: StoreProductReviewStatsDto? = null
        ) = StoreProductDto(
            id = product.id,
            title = product.title,
            handle = product.handle,
            subtitle = product.subtitle,
            description = product.description,
            status = product.status.name.lowercase(),
            isGiftcard = product.isGiftcard,
            discountable = product.discountable,
            thumbnail = product.thumbnail,
            weight = product.weight?.toIntOrNull(),
            length = product.length?.toIntOrNull(),
            height = product.height?.toIntOrNull(),
            width = product.width?.toIntOrNull(),
            originCountry = product.originCountry,
            hsCode = product.hsCode,
            midCode = product.midCode,
            material = product.material,
            collectionId = product.collection?.id,
            typeId = product.type?.id,
            metadata = product.metadata,
            variants = product.variants.filter { it.deletedAt == null }.map {
                // Default to generous availability (999) when inventory data is missing so frontend doesn't show "Out of stock" for unknown stock.
                val inventoryQty = inventoryMap[it.id] ?: 999
                StoreProductVariantDto.from(
                    it,
                    inventoryQty,
                    priceMap[it.id]
                )
            },
            images = product.images.filter { it.deletedAt == null }.sortedBy { it.position }.map { StoreProductImageDto.from(it) },
            options = buildProductOptions(product),
            reviewStats = reviewStats,
            createdAt = product.createdAt,
            updatedAt = product.updatedAt
        )

        private fun buildProductOptions(product: Product): List<StoreProductOptionDto> {
            // Collect all option values from variants, deduping by the option value string (not the DB id)
            val optionValuesMap = mutableMapOf<String, MutableMap<String, StoreProductOptionValueDto>>()

            product.variants.filter { it.deletedAt == null }.forEach { variant ->
                variant.options.forEach { optionValue ->
                    optionValue.option?.let { option ->
                        optionValuesMap
                            .getOrPut(option.id) { linkedMapOf() }
                            .putIfAbsent(
                                optionValue.value,
                                StoreProductOptionValueDto(optionValue.id, optionValue.value, option.id)
                            )
                    }
                }
            }

            // Build options with their values
            return product.options
                .filter { it.deletedAt == null }
                .sortedBy { it.position }
                .map { option ->
                    StoreProductOptionDto(
                        id = option.id,
                        title = option.title,
                        values = optionValuesMap[option.id]?.values?.toList() ?: emptyList()
                    )
                }
        }
    }
}
