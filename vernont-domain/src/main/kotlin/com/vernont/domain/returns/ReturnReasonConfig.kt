package com.vernont.domain.returns

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

/**
 * Configurable return reason entity.
 * Allows admins to define custom return reasons that customers can select.
 */
@Entity
@Table(
    name = "return_reason_config",
    indexes = [
        Index(name = "idx_return_reason_value", columnList = "value"),
        Index(name = "idx_return_reason_deleted_at", columnList = "deleted_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_return_reason_value", columnNames = ["value"])
    ]
)
class ReturnReasonConfig : BaseEntity() {

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
         * Seed default return reasons based on the ReturnReason enum
         */
        fun createDefaults(): List<ReturnReasonConfig> {
            return listOf(
                ReturnReasonConfig().apply {
                    value = "wrong_item"
                    label = "Wrong Item"
                    description = "Customer received the wrong item"
                    displayOrder = 1
                },
                ReturnReasonConfig().apply {
                    value = "damaged"
                    label = "Damaged"
                    description = "Item arrived damaged or defective"
                    displayOrder = 2
                },
                ReturnReasonConfig().apply {
                    value = "not_as_described"
                    label = "Not as Described"
                    description = "Item doesn't match the description"
                    displayOrder = 3
                },
                ReturnReasonConfig().apply {
                    value = "size_too_small"
                    label = "Size Too Small"
                    description = "Item is too small"
                    displayOrder = 4
                },
                ReturnReasonConfig().apply {
                    value = "size_too_large"
                    label = "Size Too Large"
                    description = "Item is too large"
                    displayOrder = 5
                },
                ReturnReasonConfig().apply {
                    value = "changed_mind"
                    label = "Changed Mind"
                    description = "Customer no longer wants the item"
                    displayOrder = 6
                },
                ReturnReasonConfig().apply {
                    value = "quality_issue"
                    label = "Quality Issue"
                    description = "Item quality is not as expected"
                    displayOrder = 7
                },
                ReturnReasonConfig().apply {
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
