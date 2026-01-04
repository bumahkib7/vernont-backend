package com.vernont.repository.store

import com.vernont.domain.store.SalesChannel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SalesChannelRepository : JpaRepository<SalesChannel, String> {

    fun findByName(name: String): SalesChannel?

    fun findByNameAndDeletedAtIsNull(name: String): SalesChannel?

    fun findByIdAndDeletedAtIsNull(id: String): SalesChannel?

    fun findByDeletedAtIsNull(): List<SalesChannel>

    @Query("SELECT sc FROM SalesChannel sc WHERE sc.isDisabled = false AND sc.deletedAt IS NULL")
    fun findAllActive(): List<SalesChannel>

    @Query("SELECT sc FROM SalesChannel sc WHERE LOWER(sc.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND sc.deletedAt IS NULL")
    fun searchByName(@Param("searchTerm") searchTerm: String): List<SalesChannel>

    @Query("SELECT COUNT(sc) FROM SalesChannel sc WHERE sc.deletedAt IS NULL")
    fun countActiveSalesChannels(): Long

    @Query("SELECT COUNT(sc) FROM SalesChannel sc WHERE sc.isDisabled = false AND sc.deletedAt IS NULL")
    fun countEnabledSalesChannels(): Long

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: String): Boolean
}
