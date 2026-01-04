package com.vernont.api.controller.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.domain.region.Region
import com.vernont.repository.region.RegionRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/store/regions")
@CrossOrigin(origins = ["http://localhost:8000", "http://localhost:9000", "http://localhost:3000"])
class RegionController(
    private val regionRepository: RegionRepository
) {

    @GetMapping
    fun listRegions(): ResponseEntity<StoreRegionListResponse> {
        val regions = regionRepository.findByDeletedAtIsNull()
        return ResponseEntity.ok(StoreRegionListResponse(
            regions = regions.map { StoreRegionDto.from(it) }
        ))
    }

    @GetMapping("/{id}")
    fun getRegion(@PathVariable id: String): ResponseEntity<StoreRegionResponse> {
        val region = regionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(StoreRegionResponse(region = StoreRegionDto.from(region)))
    }
}

data class StoreRegionDto(
    val id: String,
    val name: String,
    @JsonProperty("currency_code") val currencyCode: String,
    @JsonProperty("tax_rate") val taxRate: Double,
    @JsonProperty("tax_code") val taxCode: String?,
    val countries: List<StoreCountryDto>
) {
    companion object {
        fun from(region: Region) = StoreRegionDto(
            id = region.id,
            name = region.name,
            currencyCode = region.currencyCode,
            taxRate = region.taxRate.toDouble(),
            taxCode = region.taxCode,
            countries = region.countries.map { StoreCountryDto(it.iso2, it.name, it.displayName) }
        )
    }
}

data class StoreCountryDto(
    @JsonProperty("iso_2") val iso2: String,
    val name: String,
    @JsonProperty("display_name") val displayName: String
)

data class StoreRegionListResponse(val regions: List<StoreRegionDto>)
data class StoreRegionResponse(val region: StoreRegionDto)
