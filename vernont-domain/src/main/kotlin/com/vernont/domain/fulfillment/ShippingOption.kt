package com.vernont.domain.fulfillment

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal

@Entity
@Table(
    name = "shipping_option",
    indexes = [
        Index(name = "idx_shipping_option_region_id", columnList = "region_id"),
        Index(name = "idx_shipping_option_profile_id", columnList = "profile_id"),
        Index(name = "idx_shipping_option_provider_id", columnList = "provider_id"),
        Index(name = "idx_shipping_option_is_active", columnList = "is_active"),
        Index(name = "idx_shipping_option_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ShippingOption.full",
    attributeNodes = [
        NamedAttributeNode("profile"),
        NamedAttributeNode("provider")
    ]
)
@NamedEntityGraph(
    name = "ShippingOption.withProfile",
    attributeNodes = [
        NamedAttributeNode("profile")
    ]
)
@NamedEntityGraph(
    name = "ShippingOption.withProvider",
    attributeNodes = [
        NamedAttributeNode("provider")
    ]
)
class ShippingOption : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(name = "region_id", nullable = false)
    var regionId: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    var profile: ShippingProfile? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    var provider: FulfillmentProvider? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false)
    var priceType: ShippingPriceType = ShippingPriceType.FLAT_RATE

    @Column(nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "is_return", nullable = false)
    var isReturn: Boolean = false

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var requirements: MutableMap<String, Any>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var data: MutableMap<String, Any>? = null

    /**
     * Minimum estimated delivery days from order placement
     */
    @Column(name = "estimated_days_min")
    var estimatedDaysMin: Int? = null

    /**
     * Maximum estimated delivery days from order placement
     */
    @Column(name = "estimated_days_max")
    var estimatedDaysMax: Int? = null

    /**
     * Carrier name (e.g., "Royal Mail", "DHL", "UPS")
     */
    @Column(length = 100)
    var carrier: String? = null

    fun activate() {
        this.isActive = true
    }

    fun deactivate() {
        this.isActive = false
    }

    fun updateAmount(newAmount: BigDecimal) {
        require(newAmount >= BigDecimal.ZERO) { "Amount must be non-negative" }
        this.amount = newAmount
    }

    fun calculatePrice(cartTotal: BigDecimal? = null): BigDecimal {
        return when (priceType) {
            ShippingPriceType.FLAT_RATE -> amount
            ShippingPriceType.CALCULATED -> amount
        }
    }

    fun isAvailable(): Boolean {
        return isActive && !isDeleted() && provider?.isAvailable() == true
    }

    fun isReturnOption(): Boolean {
        return isReturn
    }
}

enum class ShippingPriceType {
    FLAT_RATE,
    CALCULATED
}
