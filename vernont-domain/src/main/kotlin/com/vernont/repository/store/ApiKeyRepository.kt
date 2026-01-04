package com.vernont.repository.store

import com.vernont.domain.store.ApiKey
import com.vernont.domain.store.ApiKeyType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, String> {

    @EntityGraph(value = "ApiKey.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<ApiKey>

    fun findByToken(token: String): ApiKey?

    fun findByTokenAndDeletedAtIsNull(token: String): ApiKey?

    fun findByTokenAndRevokedAtIsNull(token: String): ApiKey?

    @Query("SELECT ak FROM ApiKey ak WHERE ak.token = :token AND ak.revoked = false AND ak.deletedAt IS NULL")
    fun findValidByToken(@Param("token") token: String): ApiKey?

    fun findByIdAndDeletedAtIsNull(id: String): ApiKey?

    fun findByStoreId(storeId: String): List<ApiKey>

    fun findByStoreIdAndDeletedAtIsNull(storeId: String): List<ApiKey>

    @EntityGraph(value = "ApiKey.withStore", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByStoreIdAndDeletedAtIsNull(storeId: String): List<ApiKey>

    fun findByType(type: ApiKeyType): List<ApiKey>

    fun findByTypeAndDeletedAtIsNull(type: ApiKeyType): List<ApiKey>

    fun findByRevoked(revoked: Boolean): List<ApiKey>

    fun findByRevokedAndDeletedAtIsNull(revoked: Boolean): List<ApiKey>

    fun findByDeletedAtIsNull(): List<ApiKey>

    @Query("SELECT ak FROM ApiKey ak WHERE ak.store.id = :storeId AND ak.type = :type AND ak.revoked = false AND ak.deletedAt IS NULL")
    fun findActiveByStoreIdAndType(@Param("storeId") storeId: String, @Param("type") type: ApiKeyType): List<ApiKey>

    @Query("SELECT COUNT(ak) FROM ApiKey ak WHERE ak.store.id = :storeId AND ak.deletedAt IS NULL")
    fun countByStoreId(@Param("storeId") storeId: String): Long

    @Query("SELECT COUNT(ak) FROM ApiKey ak WHERE ak.revoked = false AND ak.deletedAt IS NULL")
    fun countActiveApiKeys(): Long

    fun existsByToken(token: String): Boolean
}
