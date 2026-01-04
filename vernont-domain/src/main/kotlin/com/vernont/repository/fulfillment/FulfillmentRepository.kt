package com.vernont.repository.fulfillment

import com.vernont.domain.fulfillment.Fulfillment
import com.vernont.domain.fulfillment.FulfillmentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface FulfillmentRepository : JpaRepository<Fulfillment, String> {

    /**
     * Find fulfillment by ID with items loaded
     */
    @Query("""
        SELECT f FROM Fulfillment f 
        LEFT JOIN FETCH f.items fi
        WHERE f.id = :id 
        AND f.deletedAt IS NULL
    """)
    fun findByIdWithItems(@Param("id") id: String): Optional<Fulfillment>

    fun findByOrderId(orderId: String): List<Fulfillment>

    fun findByOrderIdAndDeletedAtIsNull(orderId: String): List<Fulfillment>

    fun findByIdAndDeletedAtIsNull(id: String): Fulfillment?

    fun findByProviderId(providerId: String): List<Fulfillment>

    fun findByProviderIdAndDeletedAtIsNull(providerId: String): List<Fulfillment>

    fun findByLocationId(locationId: String): List<Fulfillment>

    fun findByLocationIdAndDeletedAtIsNull(locationId: String): List<Fulfillment>

    fun findByDeletedAtIsNull(): List<Fulfillment>

    @Query("SELECT f FROM Fulfillment f WHERE f.orderId = :orderId AND f.deletedAt IS NULL")
    fun findByOrderIdAndStatus(@Param("orderId") orderId: String): List<Fulfillment>
}
