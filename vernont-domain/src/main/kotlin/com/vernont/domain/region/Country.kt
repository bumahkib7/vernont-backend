package com.vernont.domain.region

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy

@Entity
@Table(
    name = "country",
    indexes = [
        Index(name = "idx_country_iso2", columnList = "iso2", unique = true),
        Index(name = "idx_country_iso3", columnList = "iso3", unique = true),
        Index(name = "idx_country_num_code", columnList = "num_code", unique = true),
        Index(name = "idx_country_name", columnList = "name", unique = true)
    ]
)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@NamedEntityGraph(
    name = "Country.full",
    attributeNodes = [
        NamedAttributeNode("regions")
    ]
)
class Country : BaseEntity() {

    @Column(name = "iso_2", length = 2, nullable = false, unique = true)
    var iso2: String = ""

    @Column(name = "iso_3", length = 3, nullable = false, unique = true)
    var iso3: String = ""

    @Column(name = "num_code", nullable = false, unique = true)
    var numCode: Int = 0

    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column(name = "display_name", nullable = false)
    var displayName: String = ""

    @ManyToMany(mappedBy = "countries", fetch = FetchType.LAZY)
    var regions: MutableSet<Region> = mutableSetOf()



    // Helper methods
    fun addRegion(region: Region) {
        regions.add(region)
        region.countries.add(this)
    }

    fun removeRegion(region: Region) {
        regions.remove(region)
        region.countries.remove(this)
    }
}
