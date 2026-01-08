package com.vernont.domain.promotion

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Entity
@Table(
    name = "promotion",
    indexes = [
        Index(name = "idx_promotion_code", columnList = "code", unique = true),
        Index(name = "idx_promotion_is_active", columnList = "is_active"),
        Index(name = "idx_promotion_starts_at", columnList = "starts_at"),
        Index(name = "idx_promotion_ends_at", columnList = "ends_at"),
        Index(name = "idx_promotion_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Promotion.full",
    attributeNodes = [
        NamedAttributeNode("rules")
    ]
)
@NamedEntityGraph(
    name = "Promotion.withRules",
    attributeNodes = [
        NamedAttributeNode("rules")
    ]
)
@NamedEntityGraph(
    name = "Promotion.summary",
    attributeNodes = []
)
class Promotion : BaseEntity() {

    @Column
    var name: String? = null

    @NotBlank
    @Column(nullable = false, unique = true)
    var code: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = false

    @Column(name = "is_disabled", nullable = false)
    var isDisabled: Boolean = false

    @Column(name = "starts_at")
    var startsAt: Instant? = null

    @Column(name = "ends_at")
    var endsAt: Instant? = null

    @Column(name = "usage_limit")
    var usageLimit: Int? = null

    @Column(name = "usage_count", nullable = false)
    var usageCount: Int = 0

    @Column(name = "customer_usage_limit")
    var customerUsageLimit: Int = 1

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: PromotionType = PromotionType.PERCENTAGE

    @Column(nullable = false)
    var value: Double = 0.0

    @Column(name = "minimum_amount")
    var minimumAmount: java.math.BigDecimal? = null

    @Column(name = "maximum_discount")
    var maximumDiscount: java.math.BigDecimal? = null

    @Column(name = "is_stackable", nullable = false)
    var isStackable: Boolean = false

    @Column(nullable = false)
    var priority: Int = 0

    // BUY_X_GET_Y fields
    @Column(name = "buy_quantity")
    var buyQuantity: Int? = null

    @Column(name = "get_quantity")
    var getQuantity: Int? = null

    @Column(name = "get_discount_value")
    var getDiscountValue: Double? = null

    @Column(columnDefinition = "TEXT")
    var regions: String? = null

    @OneToMany(mappedBy = "promotion", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var rules: MutableSet<PromotionRule> = mutableSetOf()

    fun addRule(rule: PromotionRule) {
        rules.add(rule)
        rule.promotion = this
    }

    fun removeRule(rule: PromotionRule) {
        rules.remove(rule)
        rule.promotion = null
    }

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

    fun incrementUsage() {
        this.usageCount++
    }

    fun decrementUsage() {
        if (usageCount > 0) {
            this.usageCount--
        }
    }

    fun isValid(): Boolean {
        if (isDisabled || !isActive || isDeleted()) return false

        val now = Instant.now()
        if (startsAt != null && now.isBefore(startsAt)) return false
        if (endsAt != null && now.isAfter(endsAt)) return false

        if (usageLimit != null && usageCount >= usageLimit!!) return false

        return true
    }

    fun hasStarted(): Boolean {
        return startsAt == null || Instant.now().isAfter(startsAt)
    }

    fun hasEnded(): Boolean {
        return endsAt != null && Instant.now().isAfter(endsAt)
    }

    fun hasReachedUsageLimit(): Boolean {
        return usageLimit != null && usageCount >= usageLimit!!
    }

    fun appliesToRegion(regionId: String): Boolean {
        return regions == null || regions?.contains(regionId) == true
    }

    fun updateValue(newValue: Double) {
        require(newValue >= 0.0) { "Promotion value must be non-negative" }
        if (type == PromotionType.PERCENTAGE) {
            require(newValue <= 100.0) { "Percentage promotion value cannot exceed 100" }
        }
        this.value = newValue
    }
}

enum class PromotionType {
    PERCENTAGE,
    FIXED,
    FREE_SHIPPING,
    BUY_X_GET_Y
}
