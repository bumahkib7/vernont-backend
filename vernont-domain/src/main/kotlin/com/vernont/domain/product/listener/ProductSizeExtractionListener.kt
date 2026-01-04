package com.vernont.domain.product.listener

import com.vernont.domain.product.Product
import com.vernont.domain.product.util.SizeExtractor
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import org.slf4j.LoggerFactory

/**
 * JPA EntityListener that automatically extracts size information from product titles
 * before persisting or updating products.
 *
 * This ensures new products always have size information if available in the title.
 */
class ProductSizeExtractionListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PrePersist
    @PreUpdate
    fun extractSize(product: Product) {
        // Only extract if not already set (allow manual override)
        if (product.extractedSize == null && product.title.isNotBlank()) {
            try {
                val sizeInfo = SizeExtractor.extractSize(product.title)
                if (sizeInfo != null) {
                    product.extractedSize = sizeInfo.size
                    product.sizeType = sizeInfo.type.name
                    logger.debug("Extracted size ${sizeInfo.size} (${sizeInfo.type}) from product: ${product.title}")
                }
            } catch (e: Exception) {
                logger.warn("Failed to extract size from product title: ${product.title}", e)
            }
        }
    }
}
