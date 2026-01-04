package com.vernont.repository.region

import com.vernont.domain.region.Currency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CurrencyRepository : JpaRepository<Currency, String> {

    fun findByCode(code: String): Currency?

    fun findByCodeAndDeletedAtIsNull(code: String): Currency?

    fun findByIdAndDeletedAtIsNull(id: String): Currency?

    fun findByDeletedAtIsNull(): List<Currency>

    @Query("SELECT c FROM Currency c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND c.deletedAt IS NULL")
    fun searchByName(@Param("searchTerm") searchTerm: String): List<Currency>

    @Query("SELECT COUNT(c) FROM Currency c WHERE c.deletedAt IS NULL")
    fun countActiveCurrencies(): Long

    fun existsByCode(code: String): Boolean

    fun existsByCodeAndIdNot(code: String, id: String): Boolean
}
