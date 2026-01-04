package com.vernont.repository.fulfillment

import com.vernont.domain.fulfillment.ShippingProfile
import com.vernont.domain.fulfillment.ShippingProfileType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ShippingProfileRepository : JpaRepository<ShippingProfile, String> {

    fun findByName(name: String): ShippingProfile?

    fun findByNameAndDeletedAtIsNull(name: String): ShippingProfile?

    fun findByIdAndDeletedAtIsNull(id: String): ShippingProfile?

    fun findByType(type: ShippingProfileType): List<ShippingProfile>

    fun findByTypeAndDeletedAtIsNull(type: ShippingProfileType): List<ShippingProfile>

    fun findByDeletedAtIsNull(): List<ShippingProfile>

    @Query("SELECT sp FROM ShippingProfile sp WHERE sp.type = :type AND sp.deletedAt IS NULL")
    fun findDefaultProfile(@Param("type") type: ShippingProfileType = ShippingProfileType.DEFAULT): ShippingProfile?

    @Query("SELECT COUNT(sp) FROM ShippingProfile sp WHERE sp.deletedAt IS NULL")
    fun countActiveProfiles(): Long

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: String): Boolean
}
