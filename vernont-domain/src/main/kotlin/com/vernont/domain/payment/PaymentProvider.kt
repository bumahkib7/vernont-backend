package com.vernont.domain.payment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "payment_provider",
    indexes = [
        Index(name = "idx_payment_provider_is_active", columnList = "is_active"),
        Index(name = "idx_payment_provider_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "PaymentProvider.summary",
    attributeNodes = []
)
class PaymentProvider : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var config: Map<String, Any>? = null

    @Column(name = "is_test_mode", nullable = false)
    var isTestMode: Boolean = false

    fun activate() {
        this.isActive = true
    }

    fun deactivate() {
        this.isActive = false
    }

    fun enableTestMode() {
        this.isTestMode = true
    }

    fun disableTestMode() {
        this.isTestMode = false
    }

    fun updateConfig(newConfig: Map<String, Any>?) {
        this.config = newConfig
    }

    fun isAvailable(): Boolean {
        return isActive && !isDeleted()
    }
}
