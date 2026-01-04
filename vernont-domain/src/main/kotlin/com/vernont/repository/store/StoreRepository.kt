package com.vernont.repository.store

import com.vernont.domain.store.Store
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StoreRepository : JpaRepository<Store, String> {

    fun findByName(name: String): Store?

    fun findByNameAndDeletedAtIsNull(name: String): Store?

    fun findByIdAndDeletedAtIsNull(id: String): Store?

    fun findByDeletedAtIsNull(): List<Store>

    fun findByDefaultCurrencyCode(currencyCode: String): List<Store>

    fun findByDefaultCurrencyCodeAndDeletedAtIsNull(currencyCode: String): List<Store>

    @Query("SELECT s FROM Store s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND s.deletedAt IS NULL")
    fun searchByName(@Param("searchTerm") searchTerm: String): List<Store>

    @Query("SELECT COUNT(s) FROM Store s WHERE s.deletedAt IS NULL")
    fun countActiveStores(): Long

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: String): Boolean
}
