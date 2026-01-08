package com.vernont.domain.pricing

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "pricing_rule",
    indexes = [
        Index(name = "idx_pricing_rule_status", columnList = "status"),
        Index(name = "idx_pricing_rule_type", columnList = "type"),
        Index(name = "idx_pricing_rule_dates", columnList = "start_at, end_at"),
        Index(name = "idx_pricing_rule_deleted_at", columnList = "deleted_at")
    ]
)
class PricingRule : BaseEntity() {

    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var type: PricingRuleType = PricingRuleType.PERCENTAGE_DISCOUNT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PricingRuleStatus = PricingRuleStatus.INACTIVE

    @Column(nullable = false)
    var priority: Int = 0

    /**
     * Rule configuration stored as JSONB.
     * Example for PERCENTAGE_DISCOUNT: {"percentage": 10}
     * Example for FIXED_DISCOUNT: {"amount": 500} (in cents)
     * Example for TIERED: {"tiers": [{"minQty": 1, "price": 1000}, {"minQty": 10, "price": 900}]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var config: MutableMap<String, Any> = mutableMapOf()

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 50)
    var targetType: TargetType? = null

    /**
     * List of IDs for the target type (product IDs, category IDs, etc.)
     */
    @ElementCollection
    @CollectionTable(name = "pricing_rule_targets", joinColumns = [JoinColumn(name = "rule_id")])
    @Column(name = "target_id")
    var targetIds: MutableList<String> = mutableListOf()

    @Column(name = "start_at")
    var startAt: Instant? = null

    @Column(name = "end_at")
    var endAt: Instant? = null

    /**
     * Check if the rule is currently active based on status and time constraints
     */
    fun isActive(): Boolean {
        if (status != PricingRuleStatus.ACTIVE) return false
        val now = Instant.now()
        if (startAt != null && now.isBefore(startAt)) return false
        if (endAt != null && now.isAfter(endAt)) return false
        return true
    }

    /**
     * Check if this rule applies to a specific product/variant
     */
    fun appliesTo(productId: String, categoryIds: List<String> = emptyList(), collectionIds: List<String> = emptyList()): Boolean {
        if (targetType == null || targetType == TargetType.ALL) return true

        return when (targetType) {
            TargetType.PRODUCTS -> targetIds.contains(productId)
            TargetType.CATEGORIES -> categoryIds.any { targetIds.contains(it) }
            TargetType.COLLECTIONS -> collectionIds.any { targetIds.contains(it) }
            else -> true
        }
    }

    /**
     * Calculate the adjusted price based on the rule configuration
     */
    fun calculateAdjustedPrice(basePrice: BigDecimal, quantity: Int = 1): BigDecimal {
        if (!isActive()) return basePrice

        return when (type) {
            PricingRuleType.PERCENTAGE_DISCOUNT -> {
                val percentage = (config["percentage"] as? Number)?.toDouble()?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
                basePrice * (BigDecimal.ONE - percentage / BigDecimal(100))
            }
            PricingRuleType.FIXED_DISCOUNT -> {
                val amount = (config["amount"] as? Number)?.toDouble()?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
                (basePrice - amount).coerceAtLeast(BigDecimal.ZERO)
            }
            PricingRuleType.MARKUP -> {
                val percentage = (config["percentage"] as? Number)?.toDouble()?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
                basePrice * (BigDecimal.ONE + percentage / BigDecimal(100))
            }
            PricingRuleType.QUANTITY_BASED -> {
                val minQuantity = (config["minQuantity"] as? Number)?.toInt() ?: 1
                val discountPercentage = (config["percentage"] as? Number)?.toDouble()?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
                if (quantity >= minQuantity) {
                    basePrice * (BigDecimal.ONE - discountPercentage / BigDecimal(100))
                } else {
                    basePrice
                }
            }
            PricingRuleType.TIERED -> {
                @Suppress("UNCHECKED_CAST")
                val tiers = config["tiers"] as? List<Map<String, Any>> ?: emptyList()
                val applicableTier = tiers
                    .filter { (it["minQty"] as? Number)?.toInt() ?: 0 <= quantity }
                    .maxByOrNull { (it["minQty"] as? Number)?.toInt() ?: 0 }

                if (applicableTier != null) {
                    (applicableTier["price"] as? Number)?.toDouble()?.let { BigDecimal.valueOf(it) } ?: basePrice
                } else {
                    basePrice
                }
            }
            PricingRuleType.TIME_BASED -> {
                // Time-based rules are handled by isActive() check
                val discountPercentage = (config["percentage"] as? Number)?.toDouble()?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
                basePrice * (BigDecimal.ONE - discountPercentage / BigDecimal(100))
            }
        }
    }

    companion object {
        fun createPercentageDiscount(name: String, percentage: BigDecimal): PricingRule {
            return PricingRule().apply {
                this.name = name
                this.type = PricingRuleType.PERCENTAGE_DISCOUNT
                this.config = mutableMapOf("percentage" to percentage)
            }
        }

        fun createFixedDiscount(name: String, amountInCents: Int): PricingRule {
            return PricingRule().apply {
                this.name = name
                this.type = PricingRuleType.FIXED_DISCOUNT
                this.config = mutableMapOf("amount" to amountInCents)
            }
        }

        fun createMarkup(name: String, percentage: BigDecimal): PricingRule {
            return PricingRule().apply {
                this.name = name
                this.type = PricingRuleType.MARKUP
                this.config = mutableMapOf("percentage" to percentage)
            }
        }
    }
}
