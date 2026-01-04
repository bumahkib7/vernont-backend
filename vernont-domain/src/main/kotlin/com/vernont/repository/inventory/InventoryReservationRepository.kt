package com.vernont.repository.inventory

import com.vernont.domain.inventory.InventoryReservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InventoryReservationRepository : JpaRepository<InventoryReservation, String> {

    fun findByOrderId(orderId: String): List<InventoryReservation>

    fun findByOrderIdAndDeletedAtIsNullAndReleasedAtIsNull(orderId: String): List<InventoryReservation>

    fun findByLineItemId(lineItemId: String): List<InventoryReservation>

    fun findByInventoryLevelId(inventoryLevelId: String): List<InventoryReservation>

    fun findByDeletedAtIsNullAndReleasedAtIsNull(): List<InventoryReservation>

    @Query("SELECT ir FROM InventoryReservation ir WHERE ir.orderId = :orderId AND ir.releasedAt IS NULL AND ir.deletedAt IS NULL")
    fun findActiveByOrderId(@Param("orderId") orderId: String): List<InventoryReservation>

    @Query("SELECT ir FROM InventoryReservation ir WHERE ir.lineItemId = :lineItemId AND ir.releasedAt IS NULL AND ir.deletedAt IS NULL")
    fun findActiveByLineItemId(@Param("lineItemId") lineItemId: String): List<InventoryReservation>
}
