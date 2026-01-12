package com.vernont.repository.region

import com.vernont.domain.region.TaxRate
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TaxRateRepository : JpaRepository<TaxRate, String> {

    @EntityGraph(value = "TaxRate.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): Optional<TaxRate>

    @EntityGraph(value = "TaxRate.withRegion", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): TaxRate?

    fun findByDeletedAtIsNull(): List<TaxRate>

    @EntityGraph(value = "TaxRate.withRegion", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllWithRegionByDeletedAtIsNull(): List<TaxRate>

    fun findByRegionIdAndDeletedAtIsNull(regionId: String): List<TaxRate>

    @EntityGraph(value = "TaxRate.withRegion", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllWithRegionByRegionIdAndDeletedAtIsNull(regionId: String): List<TaxRate>

    fun findByCodeAndDeletedAtIsNull(code: String): TaxRate?

    fun findByNameAndDeletedAtIsNull(name: String): TaxRate?

    @Query("SELECT t FROM TaxRate t WHERE t.region.id = :regionId AND t.deletedAt IS NULL ORDER BY t.rate DESC")
    fun findByRegionOrderByRateDesc(@Param("regionId") regionId: String): List<TaxRate>

    @Query("SELECT COUNT(t) FROM TaxRate t WHERE t.deletedAt IS NULL")
    fun countActiveTaxRates(): Long

    @Query("SELECT COUNT(t) FROM TaxRate t WHERE t.region.id = :regionId AND t.deletedAt IS NULL")
    fun countByRegionId(@Param("regionId") regionId: String): Long

    fun existsByCodeAndDeletedAtIsNull(code: String): Boolean

    fun existsByCodeAndIdNotAndDeletedAtIsNull(code: String, id: String): Boolean

    fun existsByNameAndRegionIdAndDeletedAtIsNull(name: String, regionId: String): Boolean

    fun existsByNameAndRegionIdAndIdNotAndDeletedAtIsNull(name: String, regionId: String, id: String): Boolean
}
