package com.vernont.domain.store

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "sales_channel",
    indexes = [
        Index(name = "idx_sales_channel_name", columnList = "name"),
        Index(name = "idx_sales_channel_store_id", columnList = "store_id"),
        Index(name = "idx_sales_channel_is_active", columnList = "is_active"),
        Index(name = "idx_sales_channel_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "SalesChannel.full",
    attributeNodes = [
        NamedAttributeNode("store")
    ]
)
@NamedEntityGraph(
    name = "SalesChannel.withStore",
    attributeNodes = [
        NamedAttributeNode("store")
    ]
)
class SalesChannel : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "is_disabled", nullable = false)
    var isDisabled: Boolean = false

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store? = null

    fun activate() {
        this.isActive = true
        this.isDisabled = false
    }

    fun deactivate() {
        this.isActive = false
    }

    fun disable() {
        this.isDisabled = true
        this.isActive = false
    }

    fun enable() {
        this.isDisabled = false
    }

    fun isAvailable(): Boolean {
        return isActive && !isDisabled
    }

    fun updateDescription(newDescription: String) {
        this.description = newDescription
    }
}
