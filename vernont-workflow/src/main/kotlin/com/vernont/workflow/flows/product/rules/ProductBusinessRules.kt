package com.vernont.workflow.flows.product.rules

import com.vernont.domain.product.Product
import com.vernont.domain.product.ProductStatus
import com.vernont.workflow.flows.product.CreateProductInput
import com.vernont.workflow.flows.product.ImageInput
import com.vernont.workflow.flows.product.ProductVariantInput
import java.math.BigDecimal

/**
 * Business rules for product handles
 */
object HandleRules {
    private val PATTERN = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")  // kebab-case
    private const val MIN_LENGTH = 3
    private const val MAX_LENGTH = 128

    fun validate(handle: String): Result<String> {
        if (handle.isBlank()) {
            return Result.failure(InvalidHandleException("Handle cannot be blank"))
        }
        if (handle.length < MIN_LENGTH) {
            return Result.failure(InvalidHandleException("Handle must be at least $MIN_LENGTH characters"))
        }
        if (handle.length > MAX_LENGTH) {
            return Result.failure(InvalidHandleException("Handle must be at most $MAX_LENGTH characters"))
        }
        if (!PATTERN.matches(handle)) {
            return Result.failure(InvalidHandleException("Handle must be kebab-case (lowercase letters, numbers, and hyphens)"))
        }
        return Result.success(handle)
    }

    /**
     * Normalize a handle (lowercase, replace spaces with hyphens)
     */
    fun normalize(input: String): String {
        return input.lowercase()
            .trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^a-z0-9-]"), "")
            .replace(Regex("-+"), "-")
            .trim('-')
    }
}

/**
 * Business rules for SKUs
 */
object SkuRules {
    private val PATTERN = Regex("^[A-Z0-9-]{3,50}$")
    private const val RANDOM_LENGTH = 8
    private val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // No 0/O/1/I confusion

    fun validate(sku: String): Result<String> {
        if (sku.isBlank()) {
            return Result.failure(InvalidSkuException("SKU cannot be blank"))
        }
        val normalized = sku.uppercase()
        if (!PATTERN.matches(normalized)) {
            return Result.failure(InvalidSkuException("SKU must be 3-50 uppercase alphanumeric characters with optional hyphens"))
        }
        return Result.success(normalized)
    }

    /**
     * Generate a unique SKU with optional category prefix
     */
    fun generate(categoryPrefix: String? = null): String {
        val prefix = categoryPrefix?.take(4)?.uppercase()?.ifBlank { null } ?: "PROD"
        val random = (1..RANDOM_LENGTH)
            .map { ALPHABET.random() }
            .joinToString("")
        return "$prefix-$random"
    }
}

/**
 * Business rules for variant options
 */
object VariantOptionRules {

    /**
     * Validates that:
     * 1. Every variant has exactly one value for each product option
     * 2. Each value is in the allowed list for that option
     * 3. No two variants have the same option combination
     */
    fun validate(
        optionDefinitions: List<ProductOptionDefinition>,
        variants: List<ProductVariantInput>
    ): Result<Unit> {
        if (variants.isEmpty()) {
            return Result.failure(InvalidVariantException("At least one variant is required"))
        }

        val optionTitles = optionDefinitions.map { it.title }.toSet()
        val optionValues = optionDefinitions.associate { it.title to it.values.toSet() }

        val seenCombinations = mutableSetOf<String>()

        for ((index, variant) in variants.withIndex()) {
            val variantOptionTitles = variant.options.keys

            // Rule 1: Every option must be present
            val missing = optionTitles - variantOptionTitles
            val extra = variantOptionTitles - optionTitles

            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                val message = buildString {
                    append("Variant ${index + 1} '${variant.title}':")
                    if (missing.isNotEmpty()) append(" missing options $missing")
                    if (extra.isNotEmpty()) append(" unexpected options $extra")
                }
                return Result.failure(InvalidVariantException(message))
            }

            // Rule 2: Values must be allowed
            for ((optionTitle, value) in variant.options) {
                val allowed = optionValues[optionTitle] ?: emptySet()
                if (value !in allowed) {
                    return Result.failure(
                        InvalidVariantException(
                            "Variant ${index + 1} '${variant.title}': value '$value' not allowed for option '$optionTitle'. Allowed: $allowed"
                        )
                    )
                }
            }

            // Rule 3: Unique combination
            val combination = variant.options.entries
                .sortedBy { it.key }
                .joinToString("|") { "${it.key}:${it.value}" }

            if (combination in seenCombinations) {
                return Result.failure(
                    InvalidVariantException(
                        "Duplicate option combination in variant ${index + 1} '${variant.title}': ${variant.options}"
                    )
                )
            }
            seenCombinations.add(combination)
        }

        return Result.success(Unit)
    }
}

/**
 * Simple representation of option definition for validation
 */
data class ProductOptionDefinition(
    val title: String,
    val values: List<String>
)

/**
 * Business rules for pricing
 */
object PricingRules {
    const val DEFAULT_CURRENCY = "GBP"
    val SUPPORTED_CURRENCIES = setOf("GBP", "EUR", "USD")

    /**
     * Validate prices at create-time (lenient - prices can be missing)
     */
    fun validateForCreate(variants: List<ProductVariantInput>): Result<Unit> {
        for ((index, variant) in variants.withIndex()) {
            for (price in variant.prices) {
                if (price.amount < BigDecimal.ZERO) {
                    return Result.failure(
                        InvalidPriceException(
                            "Variant '${variant.title}': negative price not allowed (${price.currencyCode}: ${price.amount})"
                        )
                    )
                }
                if (price.currencyCode.isBlank()) {
                    return Result.failure(
                        InvalidPriceException("Variant '${variant.title}': currency code is required")
                    )
                }
                if (price.currencyCode !in SUPPORTED_CURRENCIES) {
                    return Result.failure(
                        InvalidPriceException(
                            "Variant '${variant.title}': unsupported currency '${price.currencyCode}'. Supported: $SUPPORTED_CURRENCIES"
                        )
                    )
                }
                // Warn but don't fail if compareAtPrice <= amount
                if (price.compareAtPrice != null && price.compareAtPrice!! <= price.amount) {
                    // Just log warning, don't fail
                }
            }
        }
        return Result.success(Unit)
    }

    /**
     * Validate prices at publish-time (strict - default currency required)
     */
    fun validateForPublish(product: Product): Result<Unit> {
        for (variant in product.variants) {
            val hasDefaultCurrency = variant.prices.any { it.currencyCode == DEFAULT_CURRENCY }
            if (!hasDefaultCurrency) {
                return Result.failure(
                    PublishValidationException(
                        "Variant '${variant.title}' missing price in $DEFAULT_CURRENCY"
                    )
                )
            }
        }
        return Result.success(Unit)
    }
}

/**
 * Business rules for inventory
 */
object InventoryRules {

    fun validateQuantity(quantity: Int, variantTitle: String): Result<Int> {
        if (quantity < 0) {
            return Result.failure(
                InvalidInventoryException("Variant '$variantTitle': inventory quantity cannot be negative")
            )
        }
        return Result.success(quantity)
    }
}

/**
 * Business rules for images
 */
object ImageRules {
    const val MIN_IMAGES_FOR_PUBLISH = 1
    const val MAX_IMAGES = 20
    val ALLOWED_SCHEMES = setOf("https")

    fun validateSource(source: String): Result<ImageSourceType> {
        return when {
            source.isBlank() -> {
                Result.failure(InvalidImageException("Image source cannot be blank"))
            }
            source.startsWith("data:") -> {
                // Reject raw data URLs - must use presigned upload
                Result.failure(
                    InvalidImageException(
                        "Raw data URLs not accepted. Use presigned upload endpoint or provide HTTPS URL."
                    )
                )
            }
            source.startsWith("http://") -> {
                Result.failure(InvalidImageException("HTTPS required for image URLs"))
            }
            source.startsWith("https://") -> {
                Result.success(ImageSourceType.REMOTE_URL)
            }
            else -> {
                Result.failure(InvalidImageException("Invalid image source: must be HTTPS URL"))
            }
        }
    }

    fun validateForCreate(images: List<ImageInput>): Result<Unit> {
        if (images.size > MAX_IMAGES) {
            return Result.failure(
                InvalidImageException("Maximum $MAX_IMAGES images allowed, got ${images.size}")
            )
        }
        for ((index, imageInput) in images.withIndex()) {
            validateSource(imageInput.url).onFailure { e ->
                return Result.failure(
                    InvalidImageException("Image ${index + 1}: ${e.message}")
                )
            }
        }
        return Result.success(Unit)
    }

    fun validateForPublish(product: Product): Result<Unit> {
        if (product.images.size < MIN_IMAGES_FOR_PUBLISH) {
            return Result.failure(
                PublishValidationException("At least $MIN_IMAGES_FOR_PUBLISH image required to publish")
            )
        }
        if (product.thumbnail.isNullOrBlank()) {
            return Result.failure(
                PublishValidationException("Thumbnail is required to publish")
            )
        }
        return Result.success(Unit)
    }
}

enum class ImageSourceType {
    REMOTE_URL,
    S3_KEY
}

/**
 * Business rules for categories and channels
 */
object CategoryChannelRules {

    fun validateForPublish(product: Product): Result<Unit> {
        if (product.categories.isEmpty()) {
            return Result.failure(
                PublishValidationException("At least one category is required to publish")
            )
        }
        return Result.success(Unit)
    }
}

/**
 * Comprehensive validation for product creation
 */
object ProductCreationRules {

    fun validateInput(input: CreateProductInput): Result<Unit> {
        // Validate handle
        HandleRules.validate(input.handle).onFailure { return Result.failure(it) }

        // Validate variants exist
        if (input.variants.isEmpty()) {
            return Result.failure(InvalidVariantException("At least one variant is required"))
        }

        // Validate SKU uniqueness within input
        val skus = input.variants.mapNotNull { it.sku }.filter { it.isNotBlank() }
        val duplicateSkus = skus.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (duplicateSkus.isNotEmpty()) {
            return Result.failure(
                InvalidSkuException("Duplicate SKUs in input: ${duplicateSkus.joinToString()}")
            )
        }

        // Validate variant options against product options
        val optionDefs = input.options.map { ProductOptionDefinition(it.title, it.values) }
        VariantOptionRules.validate(optionDefs, input.variants).onFailure { return Result.failure(it) }

        // Validate pricing
        PricingRules.validateForCreate(input.variants).onFailure { return Result.failure(it) }

        // Validate inventory quantities
        for (variant in input.variants) {
            InventoryRules.validateQuantity(variant.inventoryQuantity, variant.title)
                .onFailure { return Result.failure(it) }
        }

        // Validate images
        ImageRules.validateForCreate(input.images).onFailure { return Result.failure(it) }

        return Result.success(Unit)
    }

    fun validateForPublish(product: Product): Result<Unit> {
        // Must be in READY state
        if (product.status != ProductStatus.READY) {
            return Result.failure(
                PublishValidationException("Product must be in READY state to publish, current: ${product.status}")
            )
        }

        // Validate pricing
        PricingRules.validateForPublish(product).onFailure { return Result.failure(it) }

        // Validate images
        ImageRules.validateForPublish(product).onFailure { return Result.failure(it) }

        // Validate categories
        CategoryChannelRules.validateForPublish(product).onFailure { return Result.failure(it) }

        return Result.success(Unit)
    }
}

// ============================================================================
// EXCEPTION CLASSES
// ============================================================================

/**
 * Base exception for product workflow errors
 */
open class ProductWorkflowException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class InvalidHandleException(message: String) : ProductWorkflowException(message)
class InvalidSkuException(message: String) : ProductWorkflowException(message)
class InvalidVariantException(message: String) : ProductWorkflowException(message)
class InvalidPriceException(message: String) : ProductWorkflowException(message)
class InvalidInventoryException(message: String) : ProductWorkflowException(message)
class InvalidImageException(message: String) : ProductWorkflowException(message)
class PublishValidationException(message: String) : ProductWorkflowException(message)

/**
 * Thrown when product handle conflicts with existing product
 */
class ProductHandleConflictException(handle: String) :
    ProductWorkflowException("Product with handle '$handle' already exists")

/**
 * Thrown when SKU conflicts with existing inventory item
 */
class SkuConflictException(sku: String) :
    ProductWorkflowException("SKU '$sku' already exists")

/**
 * Thrown when no stock location exists but inventory management is requested
 */
class NoStockLocationException(message: String) : ProductWorkflowException(message)

/**
 * Thrown when category is not found
 */
class CategoryNotFoundException(categoryId: String) :
    ProductWorkflowException("Category not found: $categoryId")

/**
 * Thrown when product is not found
 */
class ProductNotFoundException(productId: String) :
    ProductWorkflowException("Product not found: $productId")

/**
 * Thrown when product is in unexpected state
 */
class InvalidProductStateException(message: String) : ProductWorkflowException(message)

// ============================================================================
// IDEMPOTENCY EXCEPTIONS
// ============================================================================

/**
 * Thrown when duplicate request detected and previous execution completed
 */
class IdempotentCompletedException(
    val cachedPayload: Map<String, Any?>
) : ProductWorkflowException("Request already completed successfully")

/**
 * Thrown when duplicate request detected and previous execution failed
 */
class IdempotentFailedException(
    val previousError: String?
) : ProductWorkflowException("Previous request failed: $previousError")

/**
 * Thrown when duplicate request detected and previous execution still running
 */
class IdempotentInProgressException(
    val executionId: String
) : ProductWorkflowException("Request already in progress: $executionId")

/**
 * Thrown when concurrent insert race condition detected
 */
class IdempotentConflictException(
    val idempotencyKey: String
) : ProductWorkflowException("Concurrent request conflict for key: $idempotencyKey")

/**
 * Thrown when product creation failed and was cleaned up
 */
class ProductCreationFailedException(
    val productId: String,
    reason: String
) : ProductWorkflowException("Product creation failed for $productId: $reason")

/**
 * Thrown when SKU generation exhausted retries
 */
class SkuGenerationExhaustedException(message: String) : ProductWorkflowException(message)
