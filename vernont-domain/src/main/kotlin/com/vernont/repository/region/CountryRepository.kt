package com.vernont.repository.region

import com.vernont.domain.region.Country
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CountryRepository : JpaRepository<Country, String> {

    @EntityGraph(value = "Country.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findCountryById(id: String): Optional<Country>

    @EntityGraph(value = "Country.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByName(name: String): Optional<Country>

    fun findByIso2(iso2: String): Optional<Country>
}