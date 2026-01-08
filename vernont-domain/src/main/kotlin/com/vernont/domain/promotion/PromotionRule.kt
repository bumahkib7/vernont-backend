package com.vernont.domain.promotion

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

@Entity
@Table(
    name = "promotion_rule",
    indexes = [
        Index(name = "idx_promotion_rule_promotion_id", columnList = "promotion_id"),
        Index(name = "idx_promotion_rule_type", columnList = "type"),
        Index(name = "idx_promotion_rule_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "PromotionRule.full",
    attributeNodes = [
        NamedAttributeNode("promotion")
    ]
)
@NamedEntityGraph(
    name = "PromotionRule.withPromotion",
    attributeNodes = [
        NamedAttributeNode("promotion")
    ]
)
class PromotionRule : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    var promotion: Promotion? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: PromotionRuleType = PromotionRuleType.MIN_SUBTOTAL

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column
    var value: String? = null

    @Column(name = "attribute")
    var attribute: String? = null

    @Column(name = "operator")
    var operator: String? = null

    fun evaluate(context: Map<String, Any>): Boolean {
        return when (type) {
            PromotionRuleType.MIN_SUBTOTAL -> evaluateMinSubtotal(context)
            PromotionRuleType.MIN_QUANTITY -> evaluateMinQuantity(context)
            PromotionRuleType.PRODUCT_IDS -> evaluateProductIds(context)
            PromotionRuleType.PRODUCT_COLLECTIONS -> evaluateProductCollections(context)
            PromotionRuleType.PRODUCT_TYPES -> evaluateProductTypes(context)
            PromotionRuleType.PRODUCT_TAGS -> evaluateProductTags(context)
            PromotionRuleType.CUSTOMER_GROUPS -> evaluateCustomerGroups(context)
        }
    }

    private fun evaluateMinSubtotal(context: Map<String, Any>): Boolean {
        val subtotal = context["subtotal"] as? BigDecimal ?: return false
        val minValue = value?.toBigDecimalOrNull() ?: return false
        return subtotal >= minValue
    }

    private fun evaluateMinQuantity(context: Map<String, Any>): Boolean {
        val quantity = context["quantity"] as? Int ?: return false
        val minValue = value?.toIntOrNull() ?: return false
        return quantity >= minValue
    }

    private fun evaluateProductIds(context: Map<String, Any>): Boolean {
        val productIds = context["productIds"] as? Set<*> ?: return false
        val requiredIds = value?.split(",")?.toSet() ?: return false
        return productIds.any { it in requiredIds }
    }

    private fun evaluateProductCollections(context: Map<String, Any>): Boolean {
        val collectionIds = context["collectionIds"] as? Set<*> ?: return false
        val requiredIds = value?.split(",")?.toSet() ?: return false
        return collectionIds.any { it in requiredIds }
    }

    private fun evaluateProductTypes(context: Map<String, Any>): Boolean {
        val typeIds = context["typeIds"] as? Set<*> ?: return false
        val requiredIds = value?.split(",")?.toSet() ?: return false
        return typeIds.any { it in requiredIds }
    }

    private fun evaluateProductTags(context: Map<String, Any>): Boolean {
        val tagIds = context["tagIds"] as? Set<String> ?: return false
        val requiredIds = value?.split(",")?.toSet() ?: return false
        return tagIds.any { it in requiredIds }
    }

    private fun evaluateCustomerGroups(context: Map<String, Any>): Boolean {
        val customerGroupId = context["customerGroupId"] as? String ?: return false
        val requiredIds = value?.split(",")?.toSet() ?: return false
        return customerGroupId in requiredIds
    }

    fun updateValue(newValue: String) {
        this.value = newValue
    }
}

enum class PromotionRuleType {
    MIN_SUBTOTAL,
    MIN_QUANTITY,
    PRODUCT_IDS,
    PRODUCT_COLLECTIONS,
    PRODUCT_TYPES,
    PRODUCT_TAGS,
    CUSTOMER_GROUPS
}
