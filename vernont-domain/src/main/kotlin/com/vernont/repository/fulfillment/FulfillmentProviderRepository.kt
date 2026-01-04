package com.vernont.repository.fulfillment

import com.vernont.domain.fulfillment.FulfillmentProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FulfillmentProviderRepository : JpaRepository<FulfillmentProvider, String> {

    fun findByName(name: String): FulfillmentProvider?

    fun findByNameAndDeletedAtIsNull(name: String): FulfillmentProvider?

    fun findByIdAndDeletedAtIsNull(id: String): FulfillmentProvider?

    fun findByDeletedAtIsNull(): List<FulfillmentProvider>

    @Query("SELECT fp FROM FulfillmentProvider fp WHERE fp.isActive = true AND fp.deletedAt IS NULL")
    fun findAllActive(): List<FulfillmentProvider>

    @Query("SELECT COUNT(fp) FROM FulfillmentProvider fp WHERE fp.deletedAt IS NULL")
    fun countActiveProviders(): Long

    @Query("SELECT COUNT(fp) FROM FulfillmentProvider fp WHERE fp.isActive = true AND fp.deletedAt IS NULL")
    fun countEnabledProviders(): Long

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: String): Boolean
    @Query(
        """
    SELECT f
    FROM FulfillmentProvider f
    WHERE f.id IN :fulfillmentProviderIds
      AND f.deletedAt IS NULL
    """
    )
    fun findByIdIn(
        @Param("fulfillmentProviderIds") fulfillmentProviderIds: List<String>
    ): List<FulfillmentProvider>
}
