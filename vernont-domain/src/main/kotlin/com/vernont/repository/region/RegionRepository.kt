package com.vernont.repository.region

import com.vernont.domain.region.Region
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RegionRepository : JpaRepository<Region, String> {

    @EntityGraph(value = "Region.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Region>

    @EntityGraph(value = "Region.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Region?

    @EntityGraph(value = "Region.withCountries", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithCountriesById(id: String): Region?

    @EntityGraph(value = "Region.withTaxRates", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithTaxRatesById(id: String): Region?

    @EntityGraph(value = "Region.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findSummaryById(id: String): Region?

    fun findByName(name: String): Region?

    fun findByNameAndDeletedAtIsNull(name: String): Region?

    fun findByCurrencyCode(currencyCode: String): List<Region>

    fun findByCurrencyCodeAndDeletedAtIsNull(currencyCode: String): List<Region>

    fun findByDeletedAtIsNull(): List<Region>

    @EntityGraph(value = "Region.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllWithDetailsByDeletedAtIsNull(): List<Region>

    @EntityGraph(value = "Region.summary", type = EntityGraph.EntityGraphType.LOAD)
    fun findAllByDeletedAtIsNull(): List<Region>

    @Query("SELECT r FROM Region r WHERE r.automaticTaxes = true AND r.deletedAt IS NULL")
    fun findAllWithAutomaticTaxes(): List<Region>

    @Query("SELECT r FROM Region r JOIN r.countries c WHERE c.iso2 = :countryCode AND r.deletedAt IS NULL")
    fun findByCountryCode(@Param("countryCode") countryCode: String): Region?

    @Query("SELECT COUNT(r) FROM Region r WHERE r.deletedAt IS NULL")
    fun countActiveRegions(): Long

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: String): Boolean


}
