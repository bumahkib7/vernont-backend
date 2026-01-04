package com.vernont.domain.fulfillment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "shipping_profile",
    indexes = [
        Index(name = "idx_shipping_profile_name", columnList = "name"),
        Index(name = "idx_shipping_profile_type", columnList = "type"),
        Index(name = "idx_shipping_profile_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ShippingProfile.full",
    attributeNodes = [
        NamedAttributeNode("shippingOptions")
    ]
)
@NamedEntityGraph(
    name = "ShippingProfile.withOptions",
    attributeNodes = [
        NamedAttributeNode(value = "shippingOptions", subgraph = "optionsSubgraph")
    ],
    subgraphs = [
        NamedSubgraph(
            name = "optionsSubgraph",
            attributeNodes = [
                NamedAttributeNode("provider")
            ]
        )
    ]
)
@NamedEntityGraph(
    name = "ShippingProfile.summary",
    attributeNodes = []
)
class ShippingProfile : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: ShippingProfileType = ShippingProfileType.DEFAULT

    @OneToMany(mappedBy = "profile", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var shippingOptions: MutableSet<ShippingOption> = mutableSetOf()

    @Column(name = "product_ids", columnDefinition = "TEXT[]")
    var productIds: Array<String>? = null

    fun addShippingOption(option: ShippingOption) {
        shippingOptions.add(option)
        option.profile = this
    }

    fun removeShippingOption(option: ShippingOption) {
        shippingOptions.remove(option)
        option.profile = null
    }

    fun getActiveShippingOptions(): List<ShippingOption> {
        return shippingOptions.filter { it.isActive }
    }

    fun hasActiveOptions(): Boolean {
        return shippingOptions.any { it.isActive }
    }

    fun isDefault(): Boolean {
        return type == ShippingProfileType.DEFAULT
    }

    fun isGiftCard(): Boolean {
        return type == ShippingProfileType.GIFT_CARD
    }

    fun appliesToProduct(productId: String): Boolean {
        if (isDefault()) return true
        return productIds?.contains(productId) ?: false
    }
}

enum class ShippingProfileType {
    DEFAULT,
    GIFT_CARD,
    CUSTOM
}
