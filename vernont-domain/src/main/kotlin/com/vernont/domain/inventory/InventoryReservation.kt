package com.vernont.domain.inventory

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "inventory_reservation",
    indexes = [
        Index(name = "idx_inventory_reservation_order_id", columnList = "order_id"),
        Index(name = "idx_inventory_reservation_line_item_id", columnList = "line_item_id"),
        Index(name = "idx_inventory_reservation_level_id", columnList = "inventory_level_id"),
        Index(name = "idx_inventory_reservation_deleted_at", columnList = "deleted_at")
    ]
)
class InventoryReservation : BaseEntity() {

    @Column(name = "order_id")
    var orderId: String? = null

    @Column(name = "line_item_id")
    var lineItemId: String? = null

    @Column(name = "inventory_level_id", nullable = false)
    var inventoryLevelId: String = ""

    @Column(nullable = false)
    var quantity: Int = 0

    @Column(name = "released_at")
    var releasedAt: Instant? = null

    fun release() {
        this.releasedAt = Instant.now()
    }

    fun isReleased(): Boolean {
        return releasedAt != null
    }
}
