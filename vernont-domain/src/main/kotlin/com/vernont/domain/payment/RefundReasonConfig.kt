package com.vernont.domain.payment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

/**
 * Configurable refund reason entity.
 * Allows admins to define custom refund reasons that can be selected when processing refunds.
 */
@Entity
@Table(
    name = "refund_reason_config",
    indexes = [
        Index(name = "idx_refund_reason_value", columnList = "value"),
        Index(name = "idx_refund_reason_deleted_at", columnList = "deleted_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_refund_reason_value", columnNames = ["value"])
    ]
)
class RefundReasonConfig : BaseEntity() {

    @NotBlank
    @Column(nullable = false, length = 100)
    var value: String = ""

    @NotBlank
    @Column(nullable = false)
    var label: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "requires_note", nullable = false)
    var requiresNote: Boolean = false

    companion object {
        /**
         * Seed default refund reasons
         */
        fun createDefaults(): List<RefundReasonConfig> {
            return listOf(
                RefundReasonConfig().apply {
                    value = "product_issue"
                    label = "Product Issue"
                    description = "Product defect or quality issue"
                    displayOrder = 1
                },
                RefundReasonConfig().apply {
                    value = "shipping_damage"
                    label = "Shipping Damage"
                    description = "Item damaged during shipping"
                    displayOrder = 2
                },
                RefundReasonConfig().apply {
                    value = "wrong_item_shipped"
                    label = "Wrong Item Shipped"
                    description = "Incorrect item was shipped"
                    displayOrder = 3
                },
                RefundReasonConfig().apply {
                    value = "order_cancelled"
                    label = "Order Cancelled"
                    description = "Order was cancelled before fulfillment"
                    displayOrder = 4
                },
                RefundReasonConfig().apply {
                    value = "customer_request"
                    label = "Customer Request"
                    description = "Customer requested refund"
                    displayOrder = 5
                },
                RefundReasonConfig().apply {
                    value = "duplicate_order"
                    label = "Duplicate Order"
                    description = "Customer placed duplicate order"
                    displayOrder = 6
                },
                RefundReasonConfig().apply {
                    value = "pricing_error"
                    label = "Pricing Error"
                    description = "Incorrect price was charged"
                    displayOrder = 7
                },
                RefundReasonConfig().apply {
                    value = "other"
                    label = "Other"
                    description = "Other reason (please specify)"
                    displayOrder = 99
                    requiresNote = true
                }
            )
        }
    }
}
