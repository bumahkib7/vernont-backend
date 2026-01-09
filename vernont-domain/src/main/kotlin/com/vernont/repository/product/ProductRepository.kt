package com.vernont.repository.product

import com.vernont.domain.product.Product
import com.vernont.domain.product.ProductSource
import com.vernont.domain.product.ProductStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

@Repository
interface ProductRepository : JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {

    @EntityGraph(value = "Product.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Product>

    @EntityGraph(value = "Product.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Product?

    @EntityGraph(value = "Product.withVariants", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithVariantsById(id: String): Product?

    @EntityGraph(value = "Product.withVariants", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithVariantsByIdAndDeletedAtIsNull(id: String): Product?

    @EntityGraph(value = "Product.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findSummaryById(id: String): Product?

    @EntityGraph(value = "Product.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findSummaryByIdAndDeletedAtIsNull(id: String): Product?

    fun findByHandle(handle: String): Product?

    fun findByHandleAndDeletedAtIsNull(handle: String): Product?

    @EntityGraph(value = "Product.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByHandleAndDeletedAtIsNull(handle: String, deletedAt: Any?): Product?

    fun findByStatus(status: ProductStatus): List<Product>

    fun findByStatusAndDeletedAtIsNull(status: ProductStatus): List<Product>

    fun findByCollectionId(collectionId: String): List<Product>

    fun findByCollectionIdAndDeletedAtIsNull(collectionId: String): List<Product>

    fun findByTypeId(typeId: String): List<Product>

    fun findByTypeIdAndDeletedAtIsNull(typeId: String): List<Product>

    fun findByDeletedAtIsNull(): List<Product>

    fun findByExternalKey(externalKey: String): Product?

    fun findByExternalKeyAndDeletedAtIsNull(externalKey: String): Product?

    fun findAllByExternalKeyStartingWith(prefix: String): List<Product>

    @Query(
        """
        SELECT p
        FROM Product p
        WHERE p.source = :source
          AND p.externalKey LIKE CONCAT(:prefix, '%')
          AND p.deletedAt IS NULL
        """
    )
    fun findBySourceAndExternalKeyStartingWith(
        @Param("source") source: ProductSource,
        @Param("prefix") prefix: String
    ): List<Product>

    @Query(
        """
        SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
        FROM Product p
        WHERE p.brand.id = :brandId
          AND p.deletedAt IS NULL
        """
    )
    fun existsActiveByBrandId(@Param("brandId") brandId: String): Boolean

    @EntityGraph(value = "Product.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByDeletedAtIsNull(): List<Product>

    @Query("SELECT p FROM Product p JOIN p.tags t WHERE t.id = :tagId AND p.deletedAt IS NULL")
    fun findByTagId(@Param("tagId") tagId: String): List<Product>

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId AND p.deletedAt IS NULL")
    fun findByCategoryId(@Param("categoryId") categoryId: String): List<Product>

    @Query("SELECT p FROM Product p WHERE p.source = :source AND p.deletedAt IS NULL")
    fun findBySourceAndDeletedAtIsNull(
        @Param("source") source: ProductSource,
        pageable: Pageable
    ): Page<Product>

    fun findBySourceAndDeletedAtIsNull(source: ProductSource): List<Product>

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.deletedAt IS NULL")
    @EntityGraph(value = "Product.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByStatusWithSummary(@Param("status") status: ProductStatus): List<Product>

    fun existsByHandle(handle: String): Boolean

    fun existsByHandleAndIdNot(handle: String, id: String): Boolean

    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = :status AND p.deletedAt IS NULL")
    fun countByStatus(@Param("status") status: ProductStatus): Long

    @Query("SELECT p FROM Product p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND p.deletedAt IS NULL")
    fun searchByTitle(@Param("searchTerm") searchTerm: String): List<Product>

    // PostgreSQL Full-Text Search
    @Query(
        value = """
            SELECT p.* FROM product p
            WHERE p.search_vector @@ plainto_tsquery('english', :query)
              AND p.status = 'PUBLISHED'
              AND p.deleted_at IS NULL
            ORDER BY ts_rank(p.search_vector, plainto_tsquery('english', :query)) DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun fullTextSearch(
        @Param("query") query: String,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<Product>

    @Query(
        value = """
            SELECT COUNT(*) FROM product p
            WHERE p.search_vector @@ plainto_tsquery('english', :query)
              AND p.status = 'PUBLISHED'
              AND p.deleted_at IS NULL
        """,
        nativeQuery = true
    )
    fun countFullTextSearch(@Param("query") query: String): Long

    // Weighted full-text search with phrase matching
    @Query(
        value = """
            SELECT p.*, ts_rank_cd(p.search_vector, query) AS rank
            FROM product p, to_tsquery('english', :tsQuery) query
            WHERE p.search_vector @@ query
              AND p.status = 'PUBLISHED'
              AND p.deleted_at IS NULL
            ORDER BY rank DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    fun advancedFullTextSearch(
        @Param("tsQuery") tsQuery: String,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int
    ): List<Product>

    // Search suggestions using trigram similarity
    @Query(
        value = """
            SELECT title FROM (
                SELECT DISTINCT p.title, similarity(p.title, :query) AS sim
                FROM product p
                WHERE p.status = 'PUBLISHED'
                  AND p.deleted_at IS NULL
                  AND (
                    p.title ILIKE CONCAT('%', :query, '%')
                    OR similarity(p.title, :query) > 0.1
                  )
                ORDER BY sim DESC, p.title
                LIMIT :limit
            ) AS suggestions
        """,
        nativeQuery = true
    )
    fun findTitleSuggestions(
        @Param("query") query: String,
        @Param("limit") limit: Int
    ): List<String>

    // Brand suggestions
    @Query(
        value = """
            SELECT name FROM (
                SELECT DISTINCT b.name, similarity(b.name, :query) AS sim
                FROM brand b
                JOIN product p ON p.brand_id = b.id
                WHERE p.status = 'PUBLISHED'
                  AND p.deleted_at IS NULL
                  AND b.deleted_at IS NULL
                  AND (
                    b.name ILIKE CONCAT('%', :query, '%')
                    OR similarity(b.name, :query) > 0.2
                  )
                ORDER BY sim DESC, b.name
                LIMIT :limit
            ) AS suggestions
        """,
        nativeQuery = true
    )
    fun findBrandSuggestions(
        @Param("query") query: String,
        @Param("limit") limit: Int
    ): List<String>

    @Query("SELECT COUNT(p) FROM Product p WHERE p.brand.id = :brandId AND p.deletedAt IS NULL")
    fun countByBrandId(@Param("brandId") brandId: String): Long

    @Query("SELECT p.handle FROM Product p WHERE p.status = com.vernont.domain.product.ProductStatus.PUBLISHED AND p.deletedAt IS NULL AND p.handle IS NOT NULL")
    fun findAllPublicHandles(): List<String>

    @Query("SELECT p.handle FROM Product p WHERE p.status = com.vernont.domain.product.ProductStatus.PUBLISHED AND p.deletedAt IS NULL AND p.handle IS NOT NULL")
    fun findPublicHandles(pageable: org.springframework.data.domain.Pageable): List<String>

    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = com.vernont.domain.product.ProductStatus.PUBLISHED AND p.deletedAt IS NULL AND p.handle IS NOT NULL")
    fun countPublicHandles(): Long

    @Query("""
        SELECT p.handle
        FROM Product p
        JOIN p.brand b
        WHERE p.status = com.vernont.domain.product.ProductStatus.PUBLISHED
          AND p.deletedAt IS NULL
          AND p.handle IS NOT NULL
          AND b.tier = com.vernont.domain.product.BrandTier.LUXURY
    """)
    fun findPublicHandlesByLuxuryBrands(pageable: org.springframework.data.domain.Pageable): List<String>

    @Query("""
        SELECT COUNT(p)
        FROM Product p
        JOIN p.brand b
        WHERE p.status = com.vernont.domain.product.ProductStatus.PUBLISHED
          AND p.deletedAt IS NULL
          AND p.handle IS NOT NULL
          AND b.tier = com.vernont.domain.product.BrandTier.LUXURY
    """)
    fun countPublicHandlesByLuxuryBrands(): Long

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.source = :source
          AND p.deletedAt IS NULL
          AND p.thumbnail IS NOT NULL
          AND p.thumbnail <> ''
          AND NOT EXISTS (
              SELECT 1 FROM ProductImage pi
              WHERE pi.product.id = p.id
                AND pi.deletedAt IS NULL
          )
        """
    )
    fun findProductsMissingImages(
        @Param("source") source: ProductSource,
        pageable: Pageable
    ): Page<Product>

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.status = :status
          AND p.source = :source
          AND p.deletedAt IS NULL
        """
    )
    fun findByStatusAndSource(
        @Param("status") status: ProductStatus,
        @Param("source") source: ProductSource,
        pageable: Pageable
    ): Page<Product>

    // Size-related queries
    fun countByExtractedSizeIsNotNull(): Long

    fun findByExtractedSizeAndDeletedAtIsNull(size: String, pageable: Pageable): Page<Product>

    @Query("SELECT DISTINCT p.extractedSize FROM Product p WHERE p.extractedSize IS NOT NULL ORDER BY p.extractedSize")
    fun findAllDistinctSizes(): List<String>

    /**
     * Find products by status created before a certain time (for cleanup jobs)
     */
    @Query("""
        SELECT p FROM Product p
        WHERE p.status = :status
          AND p.createdAt < :cutoff
          AND p.deletedAt IS NULL
        ORDER BY p.createdAt ASC
    """)
    fun findByStatusAndCreatedAtBefore(
        @Param("status") status: ProductStatus,
        @Param("cutoff") cutoff: java.time.Instant
    ): List<Product>

    /**
     * Extract distinct colors from variant options.
     * Looks for options with title 'Color' or 'Colour'.
     */
    @Query(
        value = """
            SELECT DISTINCT pvo.value
            FROM product_variant_option pvo
            JOIN product_variant pv ON pvo.variant_id = pv.id
            JOIN product p ON pv.product_id = p.id
            JOIN product_option po ON pvo.option_id = po.id
            WHERE p.status = 'PUBLISHED'
              AND p.deleted_at IS NULL
              AND pv.deleted_at IS NULL
              AND pvo.deleted_at IS NULL
              AND po.deleted_at IS NULL
              AND (LOWER(po.title) = 'color' OR LOWER(po.title) = 'colour')
              AND pvo.value IS NOT NULL
              AND pvo.value != ''
            ORDER BY pvo.value
        """,
        nativeQuery = true
    )
    fun findAllDistinctColors(): List<String>
}
