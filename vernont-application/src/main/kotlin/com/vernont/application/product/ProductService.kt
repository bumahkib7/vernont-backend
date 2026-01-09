package com.vernont.application.product

import com.vernont.domain.product.*
import com.vernont.domain.product.dto.*
import com.vernont.events.*
import com.vernont.repository.product.*
import com.vernont.infrastructure.storage.PresignedUrlService
import com.vernont.infrastructure.storage.StorageService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.Base64
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class ProductService(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productTagRepository: ProductTagRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val productCollectionRepository: ProductCollectionRepository,
    private val productTypeRepository: ProductTypeRepository,
    private val regionRepository: com.vernont.repository.region.RegionRepository,
    private val eventPublisher: EventPublisher,
    private val presignedUrlService: com.vernont.infrastructure.storage.PresignedUrlService,
    private val storageService: StorageService
) {

    /**
     * Create a new product with variants, options, and images
     */
    fun createProduct(@Valid request: CreateProductRequest): ProductResponse {
        logger.info { "Creating product: ${request.title}" }

        // Check if handle is unique
        if (productRepository.findByHandle(request.handle) != null) {
            throw ProductHandleAlreadyExistsException("Product with handle '${request.handle}' already exists")
        }

        val product = Product().apply {
            title = request.title
            handle = request.handle
            subtitle = request.subtitle
            description = request.description
            thumbnail = request.thumbnail?.takeUnless { isUploadCandidate(it) }
            isGiftcard = request.isGiftcard
            discountable = request.discountable
            weight = request.weight
            length = request.length
            height = request.height
            width = request.width
            originCountry = request.originCountry
            material = request.material
            status = ProductStatus.DRAFT

            // Set collection if provided
            request.collectionId?.let { collectionId ->
                collection = productCollectionRepository.findByIdAndDeletedAtIsNull(collectionId)
                    ?: throw ProductCollectionNotFoundException("Collection not found: $collectionId")
            }

            // Set type if provided
            request.typeId?.let { typeId ->
                type = productTypeRepository.findByIdAndDeletedAtIsNull(typeId)
                    ?: throw ProductTypeNotFoundException("Product type not found: $typeId")
            }
        }

        val saved = productRepository.save(product)

        // Add tags
        request.tags?.forEach { tagValue ->
            val tag = productTagRepository.findByValue(tagValue)
                ?: productTagRepository.save(ProductTag().apply { value = tagValue })
            saved.tags.add(tag)
        }

        // Add categories
        // TODO: Implement findByName method in ProductCategoryRepository
        // request.categories?.forEach { categoryName ->
        //     val category = productCategoryRepository.findByName(categoryName)
        //         ?: throw ProductCategoryNotFoundException("Category not found: $categoryName")
        //     saved.categories.add(category)
        // }

        // Add options
        request.options?.forEach { optionRequest ->
            val option = ProductOption().apply {
                title = optionRequest.title
                values = optionRequest.values.toMutableList()
                position = optionRequest.position
            }
            saved.addOption(option)
        }

        // Add images
        request.images?.forEach { imageRequest ->
            val shouldUpload = isUploadCandidate(imageRequest.url)
            val imageUrl = uploadIfDataUrl(imageRequest.url, saved.id)
                ?: if (shouldUpload) return@forEach else imageRequest.url
            val image = ProductImage().apply {
                url = imageUrl
                altText = imageRequest.altText
                position = imageRequest.position
                width = imageRequest.width
                height = imageRequest.height
            }
            saved.addImage(image)
        }

        // Upload thumbnail if provided as data URL (or default to first image)
        uploadIfDataUrl(request.thumbnail, saved.id)?.let { uploaded ->
            saved.thumbnail = uploaded
        }
        if (saved.thumbnail.isNullOrBlank()) {
            saved.thumbnail = saved.images.firstOrNull()?.url
        }

        // Add variants
        request.variants?.forEach { variantRequest ->
            val variant = ProductVariant().apply {
                title = variantRequest.title
                sku = variantRequest.sku
                barcode = variantRequest.barcode
                allowBackorder = variantRequest.allowBackorder
                manageInventory = variantRequest.manageInventory
                weight = variantRequest.weight
                length = variantRequest.length
                height = variantRequest.height
                width = variantRequest.width
            }
            saved.addVariant(variant)

            // Add prices to variant
            variantRequest.prices?.forEach { priceRequest ->
                val price = ProductVariantPrice().apply {
                    currencyCode = priceRequest.currencyCode
                    amount = priceRequest.amount
                    compareAtPrice = priceRequest.compareAtPrice
                    regionId = priceRequest.regionId
                    minQuantity = priceRequest.minQuantity
                    maxQuantity = priceRequest.maxQuantity
                }
                variant.addPrice(price)
            }

            // Add options to variant
            variantRequest.options?.forEach { (optionTitle, value) ->
                val option = saved.options.find { it.title == optionTitle }
                    ?: throw ProductOptionNotFoundException("Option not found: $optionTitle")

                val variantOption = ProductVariantOption().apply {
                    this.variant = variant
                    this.option = option
                    this.value = value
                }
                variant.addOption(variantOption)
            }
        }

        val final = productRepository.save(saved)

        eventPublisher.publish(
            ProductCreated(
                aggregateId = final.id,
                title = final.title,
                handle = final.handle,
                status = final.status.name
            )
        )

        logger.info { "Product created: ${final.id}" }
        return signProductMedia(ProductResponse.from(final))
    }

    /**
     * Update an existing product
     */
    fun updateProduct(id: String, @Valid request: UpdateProductRequest): ProductResponse {
        logger.info { "Updating product: $id" }

        val product = productRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ProductNotFoundException("Product not found: $id")

        product.apply {
            request.title?.let { title = it }
            request.subtitle?.let { subtitle = it }
            request.description?.let { description = it }
            request.thumbnail?.let { thumb ->
                val uploaded = uploadIfDataUrl(thumb, id)
                uploaded?.let { thumbnail = it }
            }
            request.discountable?.let { discountable = it }
            request.weight?.let { weight = it }
            request.length?.let { length = it }
            request.height?.let { height = it }
            request.width?.let { width = it }
            request.originCountry?.let { originCountry = it }
            request.material?.let { material = it }

            request.collectionId?.let { collectionId ->
                collection = productCollectionRepository.findByIdAndDeletedAtIsNull(collectionId)
                    ?: throw ProductCollectionNotFoundException("Collection not found: $collectionId")
            }

            request.typeId?.let { typeId ->
                type = productTypeRepository.findByIdAndDeletedAtIsNull(typeId)
                    ?: throw ProductTypeNotFoundException("Product type not found: $typeId")
            }
        }

        val updated = productRepository.save(product)

        eventPublisher.publish(
            ProductUpdated(
                aggregateId = updated.id,
                name = updated.title,
                description = updated.description ?: "",
                price = updated.variants.firstOrNull()?.prices?.firstOrNull()?.amount ?: BigDecimal.ZERO,
                quantity = 0,
                isActive = updated.status == ProductStatus.PUBLISHED
            )
        )

        logger.info { "Product updated: ${updated.id}" }
        return signProductMedia(ProductResponse.from(updated))
    }

    private fun uploadIfDataUrl(source: String?, productId: String): String? {
        if (source.isNullOrBlank()) return null

        // Handle raw base64 without data: prefix
        if (!source.startsWith("data:", ignoreCase = true)) {
            if (isLikelyBase64(source)) {
                val decoded = runCatching { Base64.getDecoder().decode(source) }.getOrNull()
                if (decoded != null) {
                    val key = buildObjectKey(productId, "bin")
                    return runBlocking {
                        storageService.uploadFileWithSize(
                            key,
                            decoded.inputStream(),
                            "application/octet-stream",
                            decoded.size.toLong(),
                            metadata = mapOf("productId" to productId)
                        )
                    }
                } else {
                    logger.warn { "Detected likely base64 for product $productId but failed to decode; skipping upload" }
                    return null
                }
            }
            return source
        }

        return try {
            val (bytes, contentType, ext) = parseDataUrl(source)
            val key = buildObjectKey(productId, ext)
            runBlocking {
                storageService.uploadFileWithSize(
                    key,
                    bytes.inputStream(),
                    contentType,
                    bytes.size.toLong(),
                    metadata = mapOf("productId" to productId)
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to upload data URL for product $productId; skipping upload" }
            null
        }
    }

    private fun isLikelyBase64(value: String): Boolean {
        if (value.length < 200) return false
        if (value.contains("://")) return false
        val cleaned = value.replace("=", "")
        return cleaned.all { it.isLetterOrDigit() || it == '+' || it == '/' }
    }

    private fun isUploadCandidate(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.startsWith("data:", ignoreCase = true) || isLikelyBase64(value)
    }

    private fun parseDataUrl(dataUrl: String): Triple<ByteArray, String, String> {
        val prefix = dataUrl.substringBefore(",")
        val base64Data = dataUrl.substringAfter(",")
        val contentType = prefix.substringAfter("data:").substringBefore(";").ifBlank { "application/octet-stream" }
        val ext = contentType.substringAfter("/").substringBefore(";").ifBlank { "bin" }
        val bytes = java.util.Base64.getDecoder().decode(base64Data)
        return Triple(bytes, contentType, ext)
    }

    private fun buildObjectKey(productId: String, extension: String): String {
        val ext = extension.ifBlank { "bin" }
        return "products/$productId/${java.util.UUID.randomUUID()}.$ext"
    }

    /**
     * Delete product (soft delete)
     */
    fun deleteProduct(id: String) {
        logger.info { "Deleting product: $id" }

        val product = productRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ProductNotFoundException("Product not found: $id")

        product.softDelete()
        productRepository.save(product)

        eventPublisher.publish(
            ProductDeleted(
                aggregateId = id,
                reason = "Deleted by user"
            )
        )

        logger.info { "Product deleted: $id" }
    }

    /**
     * Get product by ID
     */
    @Transactional(readOnly = true)
    fun getProduct(id: String): ProductResponse {
        val product = productRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ProductNotFoundException("Product not found: $id")

        return signProductMedia(ProductResponse.from(product))
    }

    /**
     * Get product by handle
     */
    @Transactional(readOnly = true)
    fun getProductByHandle(handle: String): ProductResponse {
        val product = productRepository.findByHandleAndDeletedAtIsNull(handle)
            ?: throw ProductNotFoundException("Product not found with handle: $handle")

        return signProductMedia(ProductResponse.from(product))
    }

    /**
     * List all products with pagination and filtering
     */
    @Transactional(readOnly = true)
    fun listProducts(
        pageable: Pageable,
        handle: String? = null,
        regionId: String? = null,
        fields: String? = null
    ): Page<ProductResponse> {
        val spec = Specification<Product> { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            if (handle != null) {
                predicates.add(cb.equal(root.get<String>("handle"), handle))
            }

            predicates.add(cb.isNull(root.get<java.time.OffsetDateTime>("deletedAt")))

            cb.and(*predicates.toTypedArray())
        }

        val products = productRepository.findAll(spec, pageable)

        // If regionId is provided, we need to calculate prices
        if (regionId != null) {
            val region = regionRepository.findByIdAndDeletedAtIsNull(regionId)
            
            return products.map { product ->
                val response = ProductResponse.from(product)
                
                // Calculate prices for variants
                val variantsWithPrices = product.variants
                    .filter { it.deletedAt == null }
                    .map { variant ->
                        val price = variant.prices.find { it.regionId == regionId } 
                            ?: variant.prices.find { it.currencyCode == region?.currencyCode }
                            ?: variant.prices.firstOrNull() // Fallback
                        
                        val calculatedPrice = price?.let {
                            val taxRate = region?.taxRate ?: BigDecimal.ZERO
                            val taxAmount = it.amount.multiply(taxRate).divide(BigDecimal(100))
                            val calculatedAmount = it.amount.add(taxAmount)

                            CalculatedPrice(
                                calculatedAmount = calculatedAmount,
                                originalAmount = it.amount,
                                currencyCode = it.currencyCode,
                                calculatedAmountTax = taxAmount,
                                originalAmountTax = taxAmount,
                                taxRate = taxRate,
                                priceListId = null,
                                priceListType = null
                            )
                        }
                        
                        ProductVariantResponse.from(variant, calculatedPrice)
                    }
                
                signProductMedia(response.copy(variants = variantsWithPrices))
            }
        }

        return products.map { signProductMedia(ProductResponse.from(it)) }
    }

    /**
     * List product summaries with pagination (for admin list view)
     */
    @Transactional(readOnly = true)
    fun listProductSummaries(pageable: Pageable): Page<ProductSummaryResponse> {
        val spec = Specification<Product> { root, _, cb ->
            cb.isNull(root.get<java.time.OffsetDateTime>("deletedAt"))
        }
        return productRepository.findAll(spec, pageable)
            .map { signProductSummary(ProductSummaryResponse.from(it)) }
    }

    /**
     * List products by status
     */
    @Transactional(readOnly = true)
    fun listProductsByStatus(status: ProductStatus): List<ProductSummaryResponse> {
        return productRepository.findByStatusAndDeletedAtIsNull(status)
            .map { signProductSummary(ProductSummaryResponse.from(it)) }
    }

    /**
     * Search products by title or handle
     */
    @Transactional(readOnly = true)
    fun searchProducts(query: String, pageable: Pageable): Page<ProductSummaryResponse> {
        // TODO: Implement findByTitleContainingIgnoreCaseOrHandleContainingIgnoreCase method in ProductRepository
        // return productRepository.findByTitleContainingIgnoreCaseOrHandleContainingIgnoreCase(query, query, pageable)
        //     .map { ProductSummaryResponse.from(it) }
        return productRepository.findAll(pageable)
            .map { signProductSummary(ProductSummaryResponse.from(it)) }
    }

    /**
     * Publish product (make it available for sale)
     */
    fun publishProduct(id: String): ProductResponse {
        logger.info { "Publishing product: $id" }

        val product = productRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ProductNotFoundException("Product not found: $id")

        if (product.variants.isEmpty()) {
            throw ProductPublishException("Cannot publish product without variants")
        }

        product.publish()
        val published = productRepository.save(product)

        eventPublisher.publish(
            ProductPublished(
                aggregateId = product.id,
                title = product.title,
                handle = product.handle,
                status = product.status.name
            )
        )

        logger.info { "Product published: $id" }
        return signProductMedia(ProductResponse.from(published))
    }

    /**
     * Unpublish product (draft)
     */
    fun unpublishProduct(id: String): ProductResponse {
        logger.info { "Unpublishing product: $id" }

        val product = productRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ProductNotFoundException("Product not found: $id")

        product.draft()
        val drafted = productRepository.save(product)

        logger.info { "Product unpublished: $id" }
        return signProductMedia(ProductResponse.from(drafted))
    }

    /**
     * Add variant to product
     */
    fun addVariant(productId: String, @Valid request: CreateProductVariantRequest): ProductResponse {
        logger.info { "Adding variant to product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val variant = ProductVariant().apply {
            title = request.title
            sku = request.sku
            barcode = request.barcode
            allowBackorder = request.allowBackorder
            manageInventory = request.manageInventory
            weight = request.weight
            length = request.length
            height = request.height
            width = request.width
        }

        product.addVariant(variant)

        // Add prices
        request.prices?.forEach { priceRequest ->
            val price = ProductVariantPrice().apply {
                currencyCode = priceRequest.currencyCode
                amount = priceRequest.amount
                compareAtPrice = priceRequest.compareAtPrice
                regionId = priceRequest.regionId
                minQuantity = priceRequest.minQuantity
                maxQuantity = priceRequest.maxQuantity
            }
            variant.addPrice(price)
        }

        // Add options
        request.options?.forEach { (optionTitle, value) ->
            val option = product.options.find { it.title == optionTitle }
                ?: throw ProductOptionNotFoundException("Option not found: $optionTitle")

            val variantOption = ProductVariantOption().apply {
                this.variant = variant
                this.option = option
                this.value = value
            }
            variant.addOption(variantOption)
        }

        val updated = productRepository.save(product)

        logger.info { "Variant added to product: $productId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Update variant
     */
    fun updateVariant(
        productId: String,
        variantId: String,
        @Valid request: UpdateProductVariantRequest
    ): ProductResponse {
        logger.info { "Updating variant $variantId for product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val variant = product.variants.find { it.id == variantId }
            ?: throw ProductVariantNotFoundException("Variant not found: $variantId")

        variant.apply {
            request.title?.let { title = it }
            request.sku?.let { sku = it }
            request.barcode?.let { barcode = it }
            request.allowBackorder?.let { allowBackorder = it }
            request.manageInventory?.let { manageInventory = it }
            request.weight?.let { weight = it }
            request.length?.let { length = it }
            request.height?.let { height = it }
            request.width?.let { width = it }
        }

        // Update prices if provided
        request.prices?.let { priceRequests ->
            // Remove existing prices
            variant.prices.forEach { it.softDelete() }
            variant.prices.clear()

            // Add new prices
            priceRequests.forEach { priceRequest ->
                val price = ProductVariantPrice().apply {
                    currencyCode = priceRequest.currencyCode
                    amount = priceRequest.amount
                    compareAtPrice = priceRequest.compareAtPrice
                    regionId = priceRequest.regionId
                    minQuantity = priceRequest.minQuantity
                    maxQuantity = priceRequest.maxQuantity
                }
                variant.addPrice(price)
            }
        }

        val updated = productRepository.save(product)

        logger.info { "Variant updated: $variantId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Delete variant
     */
    fun deleteVariant(productId: String, variantId: String): ProductResponse {
        logger.info { "Deleting variant $variantId from product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val variant = product.variants.find { it.id == variantId }
            ?: throw ProductVariantNotFoundException("Variant not found: $variantId")

        product.removeVariant(variant)
        variant.softDelete()

        val updated = productRepository.save(product)

        logger.info { "Variant deleted: $variantId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Add image to product
     */
    fun addImage(productId: String, @Valid request: CreateProductImageRequest): ProductResponse {
        logger.info { "Adding image to product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val shouldUpload = isUploadCandidate(request.url)
        val imageUrl = uploadIfDataUrl(request.url, productId)
            ?: if (shouldUpload) return signProductMedia(ProductResponse.from(product)) else request.url

        val image = ProductImage().apply {
            url = imageUrl
            altText = request.altText
            position = request.position
            width = request.width
            height = request.height
        }

        product.addImage(image)
        val updated = productRepository.save(product)

        logger.info { "Image added to product: $productId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Delete image from product
     */
    fun deleteImage(productId: String, imageId: String): ProductResponse {
        logger.info { "Deleting image $imageId from product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val image = product.images.find { it.id == imageId }
            ?: throw ProductImageNotFoundException("Image not found: $imageId")

        product.images.remove(image)
        image.softDelete()

        val updated = productRepository.save(product)

        logger.info { "Image deleted: $imageId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Update image properties (position, alt text)
     */
    fun updateImage(productId: String, imageId: String, request: UpdateProductImageRequest): ProductResponse {
        logger.info { "Updating image $imageId for product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val image = product.images.find { it.id == imageId }
            ?: throw ProductImageNotFoundException("Image not found: $imageId")

        request.altText?.let { image.altText = it }
        request.position?.let { image.position = it }

        val updated = productRepository.save(product)

        logger.info { "Image updated: $imageId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Reorder images - sets positions based on the order of imageIds
     */
    fun reorderImages(productId: String, request: ReorderImagesRequest): ProductResponse {
        logger.info { "Reordering images for product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val activeImages = product.images.filter { it.deletedAt == null }
        val imageMap = activeImages.associateBy { it.id }

        // Update positions based on the order in the request
        request.imageIds.forEachIndexed { index, imageId ->
            imageMap[imageId]?.position = index
        }

        // Update thumbnail to first image if it's in the list
        if (request.imageIds.isNotEmpty()) {
            val firstImage = imageMap[request.imageIds.first()]
            if (firstImage != null) {
                product.thumbnail = firstImage.url
            }
        }

        val updated = productRepository.save(product)

        logger.info { "Images reordered for product: $productId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Set product thumbnail
     */
    fun setThumbnail(productId: String, request: SetThumbnailRequest): ProductResponse {
        logger.info { "Setting thumbnail for product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val thumbnailUrl = when {
            request.imageId != null -> {
                val image = product.images.find { it.id == request.imageId && it.deletedAt == null }
                    ?: throw ProductImageNotFoundException("Image not found: ${request.imageId}")
                image.url
            }
            request.url != null -> request.url
            else -> throw IllegalArgumentException("Either imageId or url must be provided")
        }

        product.thumbnail = thumbnailUrl

        val updated = productRepository.save(product)

        logger.info { "Thumbnail set for product: $productId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Add option to product
     */
    fun addOption(productId: String, @Valid request: CreateProductOptionRequest): ProductResponse {
        logger.info { "Adding option to product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        if (product.options.any { it.title == request.title }) {
            throw ProductOptionAlreadyExistsException("Option with title '${request.title}' already exists")
        }

        val option = ProductOption().apply {
            title = request.title
            values = request.values.toMutableList()
            position = request.position
        }

        product.addOption(option)
        val updated = productRepository.save(product)

        logger.info { "Option added to product: $productId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Delete option from product
     */
    fun deleteOption(productId: String, optionId: String): ProductResponse {
        logger.info { "Deleting option $optionId from product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val option = product.options.find { it.id == optionId }
            ?: throw ProductOptionNotFoundException("Option not found: $optionId")

        product.options.remove(option)
        option.softDelete()

        val updated = productRepository.save(product)

        logger.info { "Option deleted: $optionId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    /**
     * Update option
     */
    fun updateOption(
        productId: String,
        optionId: String,
        @Valid request: UpdateProductOptionRequest
    ): ProductResponse {
        logger.info { "Updating option $optionId for product: $productId" }

        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ProductNotFoundException("Product not found: $productId")

        val option = product.options.find { it.id == optionId }
            ?: throw ProductOptionNotFoundException("Option not found: $optionId")

        request.title?.let { newTitle ->
            if (product.options.any { it.id != optionId && it.title == newTitle }) {
                throw ProductOptionAlreadyExistsException("Option with title '$newTitle' already exists")
            }
            option.title = newTitle
        }
        request.values?.let { option.values = it.toMutableList() }
        request.position?.let { option.position = it }

        val updated = productRepository.save(product)

        logger.info { "Option updated: $optionId" }
        return signProductMedia(ProductResponse.from(updated))
    }

    private fun signProductMedia(response: ProductResponse): ProductResponse {
        val signedImages = response.images.map { img ->
            img.copy(url = presignedUrlService.signIfNeeded(img.url) ?: img.url)
        }
        val signedThumbnail = presignedUrlService.signIfNeeded(response.thumbnail) ?: response.thumbnail
        return response.copy(images = signedImages, thumbnail = signedThumbnail)
    }

    private fun signProductSummary(response: ProductSummaryResponse): ProductSummaryResponse {
        return response.copy(thumbnail = presignedUrlService.signIfNeeded(response.thumbnail) ?: response.thumbnail)
    }
}

// Custom exceptions
class ProductNotFoundException(message: String) : RuntimeException(message)
class ProductHandleAlreadyExistsException(message: String) : RuntimeException(message)
class ProductCollectionNotFoundException(message: String) : RuntimeException(message)
class ProductTypeNotFoundException(message: String) : RuntimeException(message)
class ProductCategoryNotFoundException(message: String) : RuntimeException(message)
class ProductPublishException(message: String) : RuntimeException(message)
class ProductVariantNotFoundException(message: String) : RuntimeException(message)
class ProductImageNotFoundException(message: String) : RuntimeException(message)
class ProductOptionNotFoundException(message: String) : RuntimeException(message)
class ProductOptionAlreadyExistsException(message: String) : RuntimeException(message)
