package com.vernont.domain.fulfillment

import com.fasterxml.jackson.annotation.JsonIgnore
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "fulfillment_item",
    indexes = [
        Index(name = "idx_fulfillment_item_fulfillment_id", columnList = "fulfillment_id"),
        Index(name = "idx_fulfillment_item_line_item_id", columnList = "line_item_id"),
        Index(name = "idx_fulfillment_item_deleted_at", columnList = "deleted_at")
    ]
)
class FulfillmentItem : BaseEntity() {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfillment_id", nullable = false)
    var fulfillment: Fulfillment? = null

    @Column(name = "line_item_id", nullable = false)
    var lineItemId: String = ""

    @Column(nullable = false)
    var quantity: Int = 0

    @Column(length = 255)
    var title: String? = null

    @Column(length = 100)
    var sku: String? = null

    @Column(length = 100)
    var barcode: String? = null
}
