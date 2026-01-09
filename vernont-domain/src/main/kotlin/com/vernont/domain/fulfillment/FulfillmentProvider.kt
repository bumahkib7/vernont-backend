package com.vernont.domain.fulfillment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "fulfillment_provider",
    indexes = [
        Index(name = "idx_fulfillment_provider_is_active", columnList = "is_active"),
        Index(name = "idx_fulfillment_provider_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "FulfillmentProvider.summary",
    attributeNodes = []
)
class FulfillmentProvider : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(name = "provider_id", nullable = false, unique = true)
    var providerId: String = ""

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var config: MutableMap<String, Any>? = null

    fun activate() {
        this.isActive = true
    }

    fun deactivate() {
        this.isActive = false
    }

    fun updateConfig(newConfig: MutableMap<String, Any>) {
        this.config = newConfig
    }

    fun isAvailable(): Boolean {
        return isActive && !isDeleted()
    }
}
