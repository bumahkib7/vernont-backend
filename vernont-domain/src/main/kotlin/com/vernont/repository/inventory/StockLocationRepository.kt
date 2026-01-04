package com.vernont.repository.inventory

import com.vernont.domain.inventory.StockLocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StockLocationRepository : JpaRepository<StockLocation, String> {

    fun findByName(name: String): StockLocation?

    fun findByNameAndDeletedAtIsNull(name: String): StockLocation?

    fun findByIdAndDeletedAtIsNull(id: String): StockLocation?

    fun findByDeletedAtIsNull(): List<StockLocation>

    @Query("SELECT sl FROM StockLocation sl WHERE LOWER(sl.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND sl.deletedAt IS NULL")
    fun searchByName(@Param("searchTerm") searchTerm: String): List<StockLocation>

    @Query("SELECT sl FROM StockLocation sl WHERE LOWER(sl.address) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND sl.deletedAt IS NULL")
    fun searchByAddress(@Param("searchTerm") searchTerm: String): List<StockLocation>

    @Query("SELECT COUNT(sl) FROM StockLocation sl WHERE sl.deletedAt IS NULL")
    fun countActiveLocations(): Long

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: String): Boolean
}
