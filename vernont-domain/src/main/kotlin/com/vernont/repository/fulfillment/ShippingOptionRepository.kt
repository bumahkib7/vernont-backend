package com.vernont.repository.fulfillment

import com.vernont.domain.fulfillment.ShippingOption
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ShippingOptionRepository : JpaRepository<ShippingOption, String> {

    @EntityGraph(value = "ShippingOption.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<ShippingOption>

    @EntityGraph(value = "ShippingOption.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): ShippingOption?

    @EntityGraph(value = "ShippingOption.withProfile", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithProfileById(id: String): ShippingOption?

    @EntityGraph(value = "ShippingOption.withProvider", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithProviderById(id: String): ShippingOption?

    fun findByRegionId(regionId: String): List<ShippingOption>

    fun findByRegionIdAndDeletedAtIsNull(regionId: String): List<ShippingOption>

    @Query("SELECT so FROM ShippingOption so WHERE so.regionId = :regionId AND so.isActive = true AND so.deletedAt IS NULL")
    fun findByRegionIdAndIsActiveTrue(@Param("regionId") regionId: String): List<ShippingOption>

    @EntityGraph(value = "ShippingOption.full", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT so FROM ShippingOption so WHERE so.regionId = :regionId AND so.isActive = true AND so.deletedAt IS NULL")
    fun findActiveByRegionId(@Param("regionId") regionId: String): List<ShippingOption>

    fun findByProfileId(profileId: String): List<ShippingOption>

    fun findByProfileIdAndDeletedAtIsNull(profileId: String): List<ShippingOption>

    fun findByProviderId(providerId: String): List<ShippingOption>

    fun findByProviderIdAndDeletedAtIsNull(providerId: String): List<ShippingOption>

    fun findByIsActive(isActive: Boolean): List<ShippingOption>

    @EntityGraph(value = "ShippingOption.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIsActiveAndDeletedAtIsNull(isActive: Boolean): List<ShippingOption>

    @EntityGraph(value = "ShippingOption.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByRegionIdAndIsActiveAndDeletedAtIsNull(regionId: String, isActive: Boolean): List<ShippingOption>

    fun findByDeletedAtIsNull(): List<ShippingOption>

    @Query("SELECT so FROM ShippingOption so WHERE so.regionId = :regionId AND so.isReturn = true AND so.isActive = true AND so.deletedAt IS NULL")
    fun findReturnOptionsByRegionId(@Param("regionId") regionId: String): List<ShippingOption>

    @Query("SELECT COUNT(so) FROM ShippingOption so WHERE so.regionId = :regionId AND so.deletedAt IS NULL")
    fun countByRegionId(@Param("regionId") regionId: String): Long

    @Query("SELECT COUNT(so) FROM ShippingOption so WHERE so.isActive = true AND so.deletedAt IS NULL")
    fun countActiveShippingOptions(): Long
}
