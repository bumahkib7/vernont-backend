package com.vernont.workflow.flows.product.dto

data class StorefrontVariantDto(
    val id: String,
    val title: String?,
    val sku: String?,
    val priceMinor: Long?,
    val compareAtPriceMinor: Long? = null,  // Original price before discount
    val currency: String?,
    val inventoryQuantity: Int?
)

data class StorefrontProductDto(
    val id: String,
    val handle: String,
    val title: String,
    val description: String?,
    val thumbnail: String?,
    val imageUrls: List<String>,
    val brand: String?,
    val lowestPriceMinor: Long?,
    val currency: String?,
    val variants: List<StorefrontVariantDto>
)
