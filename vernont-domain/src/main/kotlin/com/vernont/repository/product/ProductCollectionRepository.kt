package com.vernont.repository.product

import com.vernont.domain.product.ProductCollection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductCollectionRepository : JpaRepository<ProductCollection, String>, 
    org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProductCollection> {

    fun findByHandle(handle: String): ProductCollection?

    fun findByHandleAndDeletedAtIsNull(handle: String): ProductCollection?

    fun findByIdAndDeletedAtIsNull(id: String): ProductCollection?

    fun findByDeletedAtIsNull(): List<ProductCollection>

    fun existsByHandle(handle: String): Boolean

    fun existsByHandleAndIdNot(handle: String, id: String): Boolean

    @Query("SELECT pc FROM ProductCollection pc WHERE LOWER(pc.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND pc.deletedAt IS NULL")
    fun searchByTitle(@Param("searchTerm") searchTerm: String): List<ProductCollection>

    @Query("SELECT COUNT(pc) FROM ProductCollection pc WHERE pc.deletedAt IS NULL")
    fun countActiveCollections(): Long

    @Query("SELECT c.handle FROM ProductCollection c WHERE c.published = true AND c.deletedAt IS NULL AND c.handle IS NOT NULL")
    fun findAllPublicHandles(): List<String>

    @Query("SELECT c FROM ProductCollection c WHERE c.published = true AND c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    fun findAllPublishedCollections(): List<ProductCollection>

    @Modifying
    @Query("DELETE FROM ProductCollection c WHERE c.id IN :ids")
    fun deleteByIdIn(@Param("ids") ids: List<String>)
}
