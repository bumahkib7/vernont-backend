package com.vernont.repository.pricing

import com.vernont.domain.pricing.PriceSet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository for PriceSet entities
 */
@Repository
interface PriceSetRepository : JpaRepository<PriceSet, String> {

    /**
     * Find price sets by type
     */
    fun findByType(type: String): List<PriceSet>

    /**
     * Find price sets by type that are not deleted
     */
    fun findByTypeAndDeletedAtIsNull(type: String): List<PriceSet>

    /**
     * Find price sets that are not deleted
     */
    fun findByDeletedAtIsNull(): List<PriceSet>

    /**
     * Find price set with prices loaded
     */
    @Query("""
        SELECT ps FROM PriceSet ps 
        LEFT JOIN FETCH ps.prices p
        WHERE ps.id = :id 
        AND ps.deletedAt IS NULL
    """)
    fun findByIdWithPrices(@Param("id") id: String): Optional<PriceSet>

    /**
     * Find price sets with prices by type
     */
    @Query("""
        SELECT DISTINCT ps FROM PriceSet ps 
        LEFT JOIN FETCH ps.prices p
        WHERE ps.type = :type 
        AND ps.deletedAt IS NULL
        AND p.deletedAt IS NULL
    """)
    fun findByTypeWithPrices(@Param("type") type: String): List<PriceSet>
}