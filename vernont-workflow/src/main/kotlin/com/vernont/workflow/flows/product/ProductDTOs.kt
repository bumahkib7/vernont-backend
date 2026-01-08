package com.vernont.workflow.flows.product

import com.vernont.domain.product.ProductStatus
import java.math.BigDecimal

data class CreateProductInput(
    val title: String = "",
    val description: String? = null,
    val handle: String = "",
    val status: ProductStatus = ProductStatus.DRAFT,
    val shippingProfileId: String = "default",
    val images: List<String> = emptyList(),
    val thumbnail: String? = null,
    val options: List<ProductOptionInput> = emptyList(),
    val variants: List<ProductVariantInput> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val salesChannelIds: List<String> = emptyList()
)


data class ProductOptionInput(
    val title: String = "",
    val values: List<String> = emptyList()
)

data class ProductVariantInput(
    val title: String = "",
    val sku: String? = null,
    val ean: String? = null,
    val barcode: String? = null,
    val inventoryQuantity: Int = 0,
    val manageInventory: Boolean = true,
    val allowBackorder: Boolean = false,
    val options: Map<String, String> = emptyMap(), // e.g., {"Color": "Red", "Size": "M"}
    val prices: List<ProductVariantPriceInput> = emptyList()
)

data class ProductVariantPriceInput(
    val currencyCode: String = "GBP",
    val amount: BigDecimal = BigDecimal.ZERO,
    val regionId: String? = null // Optional, for region-specific pricing
)

data class UpdateProductVariantInput(
    val id: String,
    val title: String? = null,
    val sku: String? = null,
    val ean: String? = null,
    val upc: String? = null,
    val barcode: String? = null,
    val hsCode: String? = null,
    val inventoryQuantity: Int? = null,
    val allowBackorder: Boolean? = null,
    val manageInventory: Boolean? = null,
    val weight: Int? = null,
    val length: Int? = null,
    val height: Int? = null,
    val width: Int? = null,
    val originCountry: String? = null,
    val midCode: String? = null,
    val material: String? = null,
    val metadata: Map<String, Any>? = null,
    val options: Map<String, String>? = null,
    val prices: List<ProductVariantPriceInput>? = null
)
