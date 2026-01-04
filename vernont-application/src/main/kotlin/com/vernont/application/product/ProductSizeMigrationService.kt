package com.vernont.application.product

import com.vernont.domain.product.util.SizeExtractor
import com.vernont.repository.product.ProductRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

/**
 * Service for migrating existing products to extract and populate size information.
 * Processes in batches to avoid overwhelming the database.
 */
@Service
class ProductSizeMigrationService(
    private val productRepository: ProductRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val BATCH_SIZE = 500 // Process 500 products at a time
    }

    /**
     * Migrates all products without size information.
     * Processes in batches to avoid memory/DB issues.
     *
     * @param dryRun If true, only logs what would be updated without saving
     * @return Number of products updated
     */
    fun migrateProductSizes(dryRun: Boolean = false): MigrationResult {
        logger.info("Starting product size migration (dryRun=$dryRun)")

        var totalProcessed = 0
        var totalUpdated = 0
        var currentPage = 0
        var hasMore = true

        while (hasMore) {
            val result = processBatch(currentPage, dryRun)
            totalProcessed += result.processed
            totalUpdated += result.updated
            hasMore = result.hasMore
            currentPage++

            logger.info(
                "Batch $currentPage complete: ${result.processed} processed, ${result.updated} updated. " +
                "Total so far: $totalProcessed processed, $totalUpdated updated"
            )

            // Small delay to avoid overwhelming DB
            Thread.sleep(100)
        }

        logger.info("Migration complete: $totalProcessed products processed, $totalUpdated updated")
        return MigrationResult(
            processed = totalProcessed,
            updated = totalUpdated,
            hasMore = false
        )
    }

    @Transactional
    protected fun processBatch(page: Int, dryRun: Boolean): MigrationResult {
        val pageable = PageRequest.of(page, BATCH_SIZE)

        // Fetch products without extracted size
        val productsPage = productRepository.findAll(pageable)

        if (productsPage.isEmpty) {
            return MigrationResult(0, 0, false)
        }

        var updated = 0

        productsPage.content.forEach { product ->
            // Skip if already has size
            if (product.extractedSize != null) {
                return@forEach
            }

            // Try to extract size from title
            val sizeInfo = SizeExtractor.extractSize(product.title)
            if (sizeInfo != null) {
                if (!dryRun) {
                    product.extractedSize = sizeInfo.size
                    product.sizeType = sizeInfo.type.name
                    // Note: save() will be called automatically at end of transaction
                } else {
                    logger.debug("Would update product ${product.id}: ${product.title} -> size ${sizeInfo.size}")
                }
                updated++
            }
        }

        // Only save if not dry run (JPA will handle dirty checking)
        if (!dryRun && updated > 0) {
            productRepository.flush()
        }

        return MigrationResult(
            processed = productsPage.content.size,
            updated = updated,
            hasMore = productsPage.hasNext()
        )
    }

    /**
     * Gets migration statistics
     */
    fun getMigrationStats(): MigrationStats {
        val total = productRepository.count()
        val withSize = productRepository.countByExtractedSizeIsNotNull()
        val withoutSize = total - withSize

        return MigrationStats(
            totalProducts = total,
            productsWithSize = withSize,
            productsWithoutSize = withoutSize,
            percentageComplete = if (total > 0) (withSize.toDouble() / total * 100) else 0.0
        )
    }
}

data class MigrationResult(
    val processed: Int,
    val updated: Int,
    val hasMore: Boolean
)

data class MigrationStats(
    val totalProducts: Long,
    val productsWithSize: Long,
    val productsWithoutSize: Long,
    val percentageComplete: Double
)
