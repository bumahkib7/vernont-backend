package com.vernont.application.service

import com.vernont.application.dto.StoreRegionDto
import com.vernont.repository.region.RegionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegionService(
    private val regionRepository: RegionRepository
) {

    @Transactional(readOnly = true)
    fun list(): List<StoreRegionDto> {
        return regionRepository.findAllWithDetailsByDeletedAtIsNull().map { StoreRegionDto.from(it) }
    }

    @Transactional(readOnly = true)
    fun retrieve(id: String): StoreRegionDto {
        val region = regionRepository.findById(id).orElseThrow { 
            RuntimeException("Region with id $id not found") // Replace with a proper not found exception
        }
        return StoreRegionDto.from(region)
    }
}
