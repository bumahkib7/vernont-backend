package com.vernont.repository.product

import com.vernont.domain.product.PendingImageUpload
import com.vernont.domain.product.PendingUploadStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PendingImageUploadRepository : JpaRepository<PendingImageUpload, String> {

    /**
     * Find all pending uploads for a product
     */
    fun findByProductIdAndDeletedAtIsNullOrderByPosition(productId: String): List<PendingImageUpload>

    /**
     * Find pending uploads by status
     */
    fun findByProductIdAndStatusAndDeletedAtIsNull(
        productId: String,
        status: PendingUploadStatus
    ): List<PendingImageUpload>

    /**
     * Find uploads ready for processing (PENDING status)
     */
    @Query("""
        SELECT p FROM PendingImageUpload p
        WHERE p.productId = :productId
        AND p.status = 'PENDING'
        AND p.deletedAt IS NULL
        ORDER BY p.position
    """)
    fun findPendingForProduct(@Param("productId") productId: String): List<PendingImageUpload>

    /**
     * Find successfully completed uploads for a product
     */
    @Query("""
        SELECT p FROM PendingImageUpload p
        WHERE p.productId = :productId
        AND p.status = 'COMPLETED'
        AND p.deletedAt IS NULL
        ORDER BY p.position
    """)
    fun findCompletedForProduct(@Param("productId") productId: String): List<PendingImageUpload>

    /**
     * Find failed uploads for a product
     */
    @Query("""
        SELECT p FROM PendingImageUpload p
        WHERE p.productId = :productId
        AND p.status = 'FAILED'
        AND p.deletedAt IS NULL
        ORDER BY p.position
    """)
    fun findFailedForProduct(@Param("productId") productId: String): List<PendingImageUpload>

    /**
     * Count uploads by status for a product
     */
    fun countByProductIdAndStatusAndDeletedAtIsNull(
        productId: String,
        status: PendingUploadStatus
    ): Long

    /**
     * Check if all uploads for a product are terminal (completed or failed)
     */
    @Query("""
        SELECT COUNT(p) = 0 FROM PendingImageUpload p
        WHERE p.productId = :productId
        AND p.status IN ('PENDING', 'IN_PROGRESS')
        AND p.deletedAt IS NULL
    """)
    fun areAllTerminal(@Param("productId") productId: String): Boolean

    /**
     * Check if product has any successful uploads
     */
    @Query("""
        SELECT COUNT(p) > 0 FROM PendingImageUpload p
        WHERE p.productId = :productId
        AND p.status = 'COMPLETED'
        AND p.deletedAt IS NULL
    """)
    fun hasSuccessfulUploads(@Param("productId") productId: String): Boolean

    /**
     * Delete completed uploads for a product (cleanup after finalization)
     */
    @Modifying
    @Query("""
        DELETE FROM PendingImageUpload p
        WHERE p.productId = :productId
        AND p.status = 'COMPLETED'
    """)
    fun deleteCompletedForProduct(@Param("productId") productId: String): Int

    /**
     * Delete all uploads for a product (cleanup on failure)
     */
    @Modifying
    @Query("DELETE FROM PendingImageUpload p WHERE p.productId = :productId")
    fun deleteAllForProduct(@Param("productId") productId: String): Int

    /**
     * Find stale in-progress uploads (for recovery)
     */
    @Query("""
        SELECT p FROM PendingImageUpload p
        WHERE p.status = 'IN_PROGRESS'
        AND p.lastAttemptAt < :threshold
        AND p.deletedAt IS NULL
    """)
    fun findStaleInProgress(@Param("threshold") threshold: Instant): List<PendingImageUpload>

    /**
     * Find products with pending uploads (for batch processing)
     */
    @Query("""
        SELECT DISTINCT p.productId FROM PendingImageUpload p
        WHERE p.status = 'PENDING'
        AND p.deletedAt IS NULL
    """)
    fun findProductIdsWithPendingUploads(): List<String>
}
