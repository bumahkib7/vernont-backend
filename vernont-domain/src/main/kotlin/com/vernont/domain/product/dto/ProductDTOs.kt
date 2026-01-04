package com.vernont.domain.product.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.product.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.Instant

/**
 * Request DTOs
 */

data class CreateProductRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,

    @field:NotBlank(message = "Handle is required")
    val handle: String,

    val subtitle: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val isGiftcard: Boolean = false,
    val discountable: Boolean = true,
    val weight: String? = null,
    val length: String? = null,
    val height: String? = null,
    val width: String? = null,
    val originCountry: String? = null,
    val material: String? = null,
    val collectionId: String? = null,
    val typeId: String? = null,
    val tags: List<String>? = null,
    val categories: List<String>? = null,
    @field:Valid
    val options: List<CreateProductOptionRequest>? = null,
    @field:Valid
    val variants: List<CreateProductVariantRequest>? = null,
    @field:Valid
    val images: List<CreateProductImageRequest>? = null
)

data class UpdateProductRequest(
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val discountable: Boolean? = null,
    val weight: String? = null,
    val length: String? = null,
    val height: String? = null,
    val width: String? = null,
    val originCountry: String? = null,
    val material: String? = null,
    val collectionId: String? = null,
    val typeId: String? = null
)

data class CreateProductOptionRequest(
    @field:NotBlank
    val title: String,
    val values: List<String>,
    val position: Int = 0
)

data class CreateProductVariantRequest(
    @field:NotBlank
    val title: String,
    val sku: String? = null,
    val barcode: String? = null,
    val allowBackorder: Boolean = false,
    val manageInventory: Boolean = true,
    val weight: String? = null,
    val length: String? = null,
    val height: String? = null,
    val width: String? = null,
    @field:Valid
    val prices: List<CreateProductVariantPriceRequest>? = null,
    val options: Map<String, String>? = null
)

data class UpdateProductVariantRequest(
    val title: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val allowBackorder: Boolean? = null,
    val manageInventory: Boolean? = null,
    val weight: String? = null,
    val length: String? = null,
    val height: String? = null,
    val width: String? = null
)

data class CreateProductVariantPriceRequest(
    @field:NotBlank
    val currencyCode: String,
    @field:PositiveOrZero
    val amount: BigDecimal,
    val compareAtPrice: BigDecimal? = null,
    val regionId: String? = null,
    val minQuantity: Int? = null,
    val maxQuantity: Int? = null
)

data class CreateProductImageRequest(
    @field:NotBlank
    val url: String,
    val altText: String? = null,
    val position: Int = 0,
    val width: Int? = null,
    val height: Int? = null
)

/**
 * Response DTOs
 */

data class ProductResponse(
    val id: String,
    val title: String,
    val handle: String,
    val subtitle: String?,
    val description: String?,
    val status: ProductStatus,
    val thumbnail: String?,
    val isGiftcard: Boolean,
    val discountable: Boolean,
    val weight: String?,
    val length: String?,
    val height: String?,
    val width: String?,
    val originCountry: String?,
    val material: String?,
    val collectionId: String?,
    val typeId: String?,
    val brandId: String?,
    val brandName: String?,
    val variants: List<ProductVariantResponse>,
    val images: List<ProductImageResponse>,
    val options: List<ProductOptionResponse>,
    val tags: List<String>,
    val categories: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(product: Product): ProductResponse {
            return ProductResponse(
                id = product.id,
                title = product.title,
                handle = product.handle,
                subtitle = product.subtitle,
                description = product.description,
                status = product.status,
                thumbnail = product.thumbnail,
                isGiftcard = product.isGiftcard,
                discountable = product.discountable,
                weight = product.weight,
                length = product.length,
                height = product.height,
                width = product.width,
                originCountry = product.originCountry,
                material = product.material,
                collectionId = product.collection?.id,
                typeId = product.type?.id,
                brandId = product.brand?.id,
                brandName = product.brand?.name,
                variants = product.variants
                    .filter { it.deletedAt == null }
                    .map { ProductVariantResponse.from(it, null) },
                images = product.images
                    .filter { it.deletedAt == null }
                    .sortedBy { it.position }
                    .map { ProductImageResponse.from(it) },
                options = product.options
                    .filter { it.deletedAt == null }
                    .sortedBy { it.position }
                    .map { ProductOptionResponse.from(it) },
                tags = product.tags
                    .filter { it.deletedAt == null }
                    .map { it.value },
                categories = product.categories
                    .filter { it.deletedAt == null }
                    .map { it.name },
                createdAt = product.createdAt,
                updatedAt = product.updatedAt
            )
        }
    }
}

data class ProductSummaryResponse(
    val id: String,
    val title: String,
    val handle: String,
    val subtitle: String?,
    val status: ProductStatus,
    val thumbnail: String?,
    val discountable: Boolean,
    val variantCount: Int,
    val brandName: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(product: Product): ProductSummaryResponse {
            return ProductSummaryResponse(
                id = product.id,
                title = product.title,
                handle = product.handle,
                subtitle = product.subtitle,
                status = product.status,
                thumbnail = product.thumbnail,
                discountable = product.discountable,
                brandName = product.brand?.name,
                variantCount = product.variants.count { it.deletedAt == null },
                createdAt = product.createdAt,
                updatedAt = product.updatedAt
            )
        }
    }
}

data class ProductVariantResponse(
    val id: String,
    val productId: String,
    val title: String,
    val sku: String?,
    val barcode: String?,
    val allowBackorder: Boolean,
    val manageInventory: Boolean,
    val weight: String?,
    val length: String?,
    val height: String?,
    val width: String?,
    val prices: List<ProductVariantPriceResponse>,
    val options: Map<String, String>,
    @JsonProperty("calculated_price")
    val calculatedPrice: CalculatedPrice? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(variant: ProductVariant, calculatedPrice: CalculatedPrice? = null): ProductVariantResponse {
            return ProductVariantResponse(
                id = variant.id,
                productId = variant.product?.id ?: "",
                title = variant.title,
                sku = variant.sku,
                barcode = variant.barcode,
                allowBackorder = variant.allowBackorder,
                manageInventory = variant.manageInventory,
                weight = variant.weight,
                length = variant.length,
                height = variant.height,
                width = variant.width,
                prices = variant.prices
                    .filter { it.deletedAt == null }
                    .map { ProductVariantPriceResponse.from(it) },
                options = variant.options
                    .filter { it.deletedAt == null }
                    .associate { (it.option?.title ?: it.id) to it.value },
                calculatedPrice = calculatedPrice,
                createdAt = variant.createdAt,
                updatedAt = variant.updatedAt
            )
        }
    }
}

data class ProductVariantPriceResponse(
    val id: String,
    val currencyCode: String,
    val amount: BigDecimal,
    val compareAtPrice: BigDecimal?,
    val regionId: String?,
    val minQuantity: Int?,
    val maxQuantity: Int?,
    val hasDiscount: Boolean
) {
    companion object {
        fun from(price: ProductVariantPrice): ProductVariantPriceResponse {
            return ProductVariantPriceResponse(
                id = price.id,
                currencyCode = price.currencyCode,
                amount = price.amount,
                compareAtPrice = price.compareAtPrice,
                regionId = price.regionId,
                minQuantity = price.minQuantity,
                maxQuantity = price.maxQuantity,
                hasDiscount = price.hasDiscount()
            )
        }
    }
}

data class ProductImageResponse(
    val id: String,
    val url: String,
    val altText: String?,
    val position: Int,
    val width: Int?,
    val height: Int?
) {
    companion object {
        fun from(image: ProductImage): ProductImageResponse {
            return ProductImageResponse(
                id = image.id,
                url = image.url,
                altText = image.altText,
                position = image.position,
                width = image.width,
                height = image.height
            )
        }
    }
}

data class ProductOptionResponse(
    val id: String,
    val title: String,
    val values: List<String>,
    val position: Int
) {
    companion object {
        fun from(option: ProductOption): ProductOptionResponse {
            return ProductOptionResponse(
                id = option.id,
                title = option.title,
                values = option.values.toList(),
                position = option.position
            )
        }
    }
}

data class CalculatedPrice(
    @JsonProperty("calculated_amount")
    val calculatedAmount: BigDecimal,
    @JsonProperty("original_amount")
    val originalAmount: BigDecimal,
    @JsonProperty("currency_code")
    val currencyCode: String,
    @JsonProperty("calculated_amount_tax")
    val calculatedAmountTax: BigDecimal? = null,
    @JsonProperty("original_amount_tax")
    val originalAmountTax: BigDecimal? = null,
    @JsonProperty("tax_rate")
    val taxRate: BigDecimal? = null,
    @JsonProperty("price_list_id")
    val priceListId: String? = null,
    @JsonProperty("price_list_type")
    val priceListType: String? = null
)
