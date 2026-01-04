package com.vernont.repository.store

import com.vernont.domain.store.StoreCurrency
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StoreCurrencyRepository : JpaRepository<StoreCurrency, String>, JpaSpecificationExecutor<StoreCurrency> {

    @EntityGraph(value = "StoreCurrency.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<StoreCurrency>

    fun findByStoreId(storeId: String): List<StoreCurrency>

    fun findByStoreIdAndDeletedAtIsNull(storeId: String): List<StoreCurrency>

    fun findByCurrencyCode(currencyCode: String): List<StoreCurrency>

    fun findByStoreIdAndIsDefaultTrue(storeId: String): StoreCurrency?

    @Query("SELECT sc FROM StoreCurrency sc WHERE sc.store.id = :storeId AND sc.currencyCode = :currencyCode AND sc.deletedAt IS NULL")
    fun findByStoreIdAndCurrencyCode(@Param("storeId") storeId: String, @Param("currencyCode") currencyCode: String): StoreCurrency?

    @Query("SELECT COUNT(sc) FROM StoreCurrency sc WHERE sc.store.id = :storeId AND sc.deletedAt IS NULL")
    fun countByStoreId(@Param("storeId") storeId: String): Long
}
