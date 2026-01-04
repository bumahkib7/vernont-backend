package com.vernont.api.admin

import com.vernont.application.product.ProductSizeMigrationService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Admin API for managing product size migration.
 * Protected endpoint - should require admin authentication.
 */
@RestController
@RequestMapping("/api/admin/products/migration")
class ProductSizeMigrationController(
    private val migrationService: ProductSizeMigrationService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Get migration statistics
     * GET /api/admin/products/migration/stats
     */
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<Any> {
        val stats = migrationService.getMigrationStats()
        return ResponseEntity.ok(mapOf(
            "totalProducts" to stats.totalProducts,
            "productsWithSize" to stats.productsWithSize,
            "productsWithoutSize" to stats.productsWithoutSize,
            "percentageComplete" to String.format("%.2f%%", stats.percentageComplete)
        ))
    }

    /**
     * Run size extraction migration (dry run)
     * POST /api/admin/products/migration/dry-run
     *
     * This will process all products and log what would be updated without actually saving
     */
    @PostMapping("/dry-run")
    fun dryRun(): ResponseEntity<Any> {
        logger.info("Starting dry-run migration")
        val result = migrationService.migrateProductSizes(dryRun = true)

        return ResponseEntity.ok(mapOf(
            "status" to "completed",
            "dryRun" to true,
            "processed" to result.processed,
            "updated" to result.updated,
            "message" to "Dry run completed. ${result.updated} products would be updated."
        ))
    }

    /**
     * Run size extraction migration (ACTUAL RUN - MODIFIES DATABASE)
     * POST /api/admin/products/migration/execute
     *
     * WARNING: This will update all products in the database
     */
    @PostMapping("/execute")
    fun execute(@RequestParam(defaultValue = "false") confirm: Boolean): ResponseEntity<Any> {
        if (!confirm) {
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Migration not confirmed",
                "message" to "Please add ?confirm=true to execute the migration"
            ))
        }

        logger.warn("Starting ACTUAL migration - this will modify the database")
        val result = migrationService.migrateProductSizes(dryRun = false)

        return ResponseEntity.ok(mapOf(
            "status" to "completed",
            "dryRun" to false,
            "processed" to result.processed,
            "updated" to result.updated,
            "message" to "Migration completed successfully. ${result.updated} products updated."
        ))
    }
}
