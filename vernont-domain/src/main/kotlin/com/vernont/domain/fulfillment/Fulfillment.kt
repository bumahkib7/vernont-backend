package com.vernont.domain.fulfillment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "fulfillment",
    indexes = [
        Index(name = "idx_fulfillment_order_id", columnList = "order_id"),
        Index(name = "idx_fulfillment_claim_order_id", columnList = "claim_order_id"),
        Index(name = "idx_fulfillment_swap_id", columnList = "swap_id"),
        Index(name = "idx_fulfillment_provider_id", columnList = "provider_id"),
        Index(name = "idx_fulfillment_location_id", columnList = "location_id"),
        Index(name = "idx_fulfillment_tracking_numbers", columnList = "tracking_numbers"),
        Index(name = "idx_fulfillment_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Fulfillment.full",
    attributeNodes = [
        NamedAttributeNode("provider")
    ]
)
@NamedEntityGraph(
    name = "Fulfillment.withProvider",
    attributeNodes = [
        NamedAttributeNode("provider")
    ]
)
class Fulfillment : BaseEntity() {

    @Column(name = "order_id")
    var orderId: String? = null

    @Column(name = "claim_order_id")
    var claimOrderId: String? = null

    @Column(name = "swap_id")
    var swapId: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    var provider: FulfillmentProvider? = null

    @Column(name = "location_id")
    var locationId: String? = null

    @Column(name = "tracking_numbers", columnDefinition = "TEXT")
    var trackingNumbers: String? = null

    @Column(name = "tracking_urls", columnDefinition = "TEXT")
    var trackingUrls: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var data: Map<String, Any>? = null

    @Column(name = "shipped_at")
    var shippedAt: Instant? = null

    @Column(name = "canceled_at")
    var canceledAt: Instant? = null

    @Column(name = "no_notification", nullable = false)
    var noNotification: Boolean = false

    @Column(columnDefinition = "TEXT")
    var note: String? = null

    @OneToMany(mappedBy = "fulfillment", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<FulfillmentItem> = mutableListOf()

    fun addItem(item: FulfillmentItem) {
        item.fulfillment = this
        items.add(item)
    }

    fun ship() {
        require(shippedAt == null) { "Fulfillment already shipped" }
        require(canceledAt == null) { "Cannot ship canceled fulfillment" }
        this.shippedAt = Instant.now()
    }

    fun cancel() {
        require(canceledAt == null) { "Fulfillment already canceled" }
        this.canceledAt = Instant.now()
    }

    fun addTrackingNumber(trackingNumber: String) {
        val current = trackingNumbers?.split(",")?.toMutableList() ?: mutableListOf()
        if (!current.contains(trackingNumber)) {
            current.add(trackingNumber)
            this.trackingNumbers = current.joinToString(",")
        }
    }

    fun addTrackingUrl(url: String) {
        val current = trackingUrls?.split(",")?.toMutableList() ?: mutableListOf()
        if (!current.contains(url)) {
            current.add(url)
            this.trackingUrls = current.joinToString(",")
        }
    }

    fun isShipped(): Boolean {
        return shippedAt != null
    }

    fun isCanceled(): Boolean {
        return canceledAt != null
    }

    fun isPending(): Boolean {
        return !isShipped() && !isCanceled()
    }

    fun hasTracking(): Boolean {
        return !trackingNumbers.isNullOrBlank()
    }

    fun getTrackingNumbersList(): List<String> {
        return trackingNumbers?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun getTrackingUrlsList(): List<String> {
        return trackingUrls?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
}
