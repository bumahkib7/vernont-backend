package com.vernont.domain.store

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "store_currency",
    indexes = [
        Index(name = "idx_store_currency_code", columnList = "currency_code"),
        Index(name = "idx_store_currency_store_id", columnList = "store_id"),
        Index(name = "idx_store_currency_is_default", columnList = "is_default"),
        Index(name = "idx_store_currency_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "StoreCurrency.full",
    attributeNodes = [
        NamedAttributeNode("store")
    ]
)
@NamedEntityGraph(
    name = "StoreCurrency.summary",
    attributeNodes = []
)
class StoreCurrency : BaseEntity() {

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store? = null

    fun setAsDefault() {
        this.isDefault = true
    }

    fun unsetDefault() {
        this.isDefault = false
    }
}
