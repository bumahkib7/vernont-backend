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

    // ========== Shipping Label Fields (for production-safe idempotency) ==========

    /**
     * Idempotency key for label purchase (prevents double-buy on retries)
     * Format: "label:{orderId}:{fulfillmentId}"
     */
    @Column(name = "label_idempotency_key", length = 100)
    var labelIdempotencyKey: String? = null

    /**
     * External label ID from shipping provider (e.g., ShipEngine label ID)
     */
    @Column(name = "label_id", length = 100)
    var labelId: String? = null

    /**
     * Current status of the shipping label
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "label_status", length = 20)
    var labelStatus: LabelStatus = LabelStatus.NONE

    /**
     * URL to download the shipping label (PDF)
     */
    @Column(name = "label_url", columnDefinition = "TEXT")
    var labelUrl: String? = null

    /**
     * Cost of the label in cents (or smallest currency unit)
     */
    @Column(name = "label_cost")
    var labelCost: Long? = null

    /**
     * Carrier code (e.g., "ups", "fedex", "usps")
     */
    @Column(name = "carrier_code", length = 50)
    var carrierCode: String? = null

    /**
     * Service code (e.g., "ground", "priority", "express")
     */
    @Column(name = "service_code", length = 50)
    var serviceCode: String? = null

    /**
     * Error message if label void failed
     */
    @Column(name = "label_void_error", columnDefinition = "TEXT")
    var labelVoidError: String? = null

    /**
     * When the label was purchased
     */
    @Column(name = "label_purchased_at")
    var labelPurchasedAt: Instant? = null

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

    // ========== Label Operations ==========

    /**
     * Generate idempotency key for label purchase
     */
    fun generateLabelIdempotencyKey(): String {
        return "label:${orderId ?: "unknown"}:$id"
    }

    /**
     * Check if label is already purchased
     */
    fun hasLabelPurchased(): Boolean {
        return labelStatus == LabelStatus.PURCHASED && labelId != null
    }

    /**
     * Check if label purchase is safe to proceed
     */
    fun canPurchaseLabel(): Boolean {
        return labelStatus == LabelStatus.NONE || labelStatus == LabelStatus.VOIDED
    }

    /**
     * Mark label as pending purchase (call before external API)
     */
    fun markLabelPendingPurchase(idempotencyKey: String) {
        require(canPurchaseLabel()) { "Cannot purchase label in status: $labelStatus" }
        this.labelIdempotencyKey = idempotencyKey
        this.labelStatus = LabelStatus.PENDING_PURCHASE
    }

    /**
     * Apply successful label purchase result
     */
    fun applyLabelPurchase(
        labelId: String,
        trackingNumber: String?,
        labelUrl: String?,
        carrier: String?,
        service: String?,
        costCents: Long?
    ) {
        this.labelId = labelId
        this.labelStatus = LabelStatus.PURCHASED
        this.labelUrl = labelUrl
        this.labelCost = costCents
        this.carrierCode = carrier
        this.serviceCode = service
        this.labelPurchasedAt = Instant.now()

        trackingNumber?.let { addTrackingNumber(it) }
    }

    /**
     * Mark label as successfully voided
     */
    fun markLabelVoided() {
        require(labelStatus == LabelStatus.PURCHASED) { "Can only void purchased labels" }
        this.labelStatus = LabelStatus.VOIDED
    }

    /**
     * Mark label void as failed (requires ops attention)
     */
    fun markLabelVoidFailed(error: String) {
        this.labelStatus = LabelStatus.VOID_FAILED
        this.labelVoidError = error
    }

    /**
     * Check if fulfillment requires ops attention for label issues
     */
    fun requiresLabelAttention(): Boolean {
        return labelStatus == LabelStatus.VOID_FAILED
    }
}
