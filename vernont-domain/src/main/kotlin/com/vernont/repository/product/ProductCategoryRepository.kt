package com.vernont.repository.product

import com.vernont.domain.product.ProductCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductCategoryRepository : JpaRepository<ProductCategory, String>,
    org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProductCategory> {

    fun findByHandle(handle: String): ProductCategory?

    fun findByHandleAndDeletedAtIsNull(handle: String): ProductCategory?

    fun findByIdAndDeletedAtIsNull(id: String): ProductCategory?

    fun findByDeletedAtIsNull(): List<ProductCategory>

    fun findByParentCategoryIdIsNull(): List<ProductCategory>

    fun findByParentCategoryIdIsNullAndDeletedAtIsNull(): List<ProductCategory>

    fun findByParentCategoryId(parentCategoryId: String): List<ProductCategory>

    fun findByParentCategoryIdAndDeletedAtIsNull(parentCategoryId: String): List<ProductCategory>

    fun existsByHandle(handle: String): Boolean

    fun existsByHandleAndIdNot(handle: String, id: String): Boolean

    @Query("SELECT pc FROM ProductCategory pc WHERE pc.isActive = true AND pc.deletedAt IS NULL")
    fun findAllActive(): List<ProductCategory>

    @Query("SELECT pc FROM ProductCategory pc WHERE pc.isActive = true AND pc.parent IS NULL AND pc.deletedAt IS NULL")
    fun findRootActiveCategories(): List<ProductCategory>

    @Query("SELECT pc FROM ProductCategory pc WHERE LOWER(pc.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND pc.deletedAt IS NULL")
    fun searchByName(@Param("searchTerm") searchTerm: String): List<ProductCategory>

    @Query("SELECT COUNT(pc) FROM ProductCategory pc WHERE pc.parent.id = :parentId AND pc.deletedAt IS NULL")
    fun countChildCategories(@Param("parentId") parentId: String): Long

    @Query("SELECT pc FROM ProductCategory pc WHERE pc.isActive = true AND pc.isInternal = false AND pc.deletedAt IS NULL")
    fun findAllActivePublic(): List<ProductCategory>

    @org.springframework.data.jpa.repository.EntityGraph(value = "ProductCategory.hierarchy", type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT pc FROM ProductCategory pc WHERE pc.deletedAt IS NULL")
    fun findAllWithHierarchy(): List<ProductCategory>

    @Modifying
    @Query("DELETE FROM ProductCategory c WHERE c.id IN :ids")
    fun deleteByIdIn(@Param("ids") ids: List<String>)

    fun findByExternalId(externalId: String): ProductCategory?

    fun findByExternalIdAndDeletedAtIsNull(externalId: String): ProductCategory?

    /**
     * Batch fetch categories by their external IDs - optimizes N+1 queries
     */
    fun findAllByExternalIdIn(externalIds: List<String>): List<ProductCategory>

    fun existsByExternalId(externalId: String): Boolean

    /**
     * Count products for a given category (published and not deleted)
     */
    @Query(
        """
        SELECT COUNT(DISTINCT p.id)
        FROM Product p
        JOIN p.categories c
        WHERE c.id = :categoryId
          AND p.status = com.vernont.domain.product.ProductStatus.PUBLISHED
          AND p.deletedAt IS NULL
        """
    )
    fun countProductsByCategoryId(@Param("categoryId") categoryId: String): Long
}
