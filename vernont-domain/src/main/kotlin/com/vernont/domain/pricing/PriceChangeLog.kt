package com.vernont.domain.pricing

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "price_change_log",
    indexes = [
        Index(name = "idx_price_change_log_variant", columnList = "variant_id"),
        Index(name = "idx_price_change_log_product", columnList = "product_id"),
        Index(name = "idx_price_change_log_changed_at", columnList = "changed_at"),
        Index(name = "idx_price_change_log_change_type", columnList = "change_type")
    ]
)
class PriceChangeLog : BaseEntity() {

    @Column(name = "variant_id", nullable = false, length = 36)
    var variantId: String = ""

    @Column(name = "product_id", nullable = false, length = 36)
    var productId: String = ""

    @Column(name = "product_title")
    var productTitle: String? = null

    @Column(name = "variant_title")
    var variantTitle: String? = null

    @Column(name = "sku")
    var sku: String? = null

    @Column(name = "previous_amount", precision = 19, scale = 4)
    var previousAmount: BigDecimal? = null

    @Column(name = "new_amount", nullable = false, precision = 19, scale = 4)
    var newAmount: BigDecimal = BigDecimal.ZERO

    @Column(name = "previous_compare_at", precision = 19, scale = 4)
    var previousCompareAt: BigDecimal? = null

    @Column(name = "new_compare_at", precision = 19, scale = 4)
    var newCompareAt: BigDecimal? = null

    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = "GBP"

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 50)
    var changeType: PriceChangeType = PriceChangeType.MANUAL

    @Column(name = "change_source", length = 50)
    var changeSource: String? = null // ADMIN_UI, API, SCHEDULED_JOB, IMPORT

    @Column(name = "rule_id", length = 36)
    var ruleId: String? = null

    @Column(name = "rule_name")
    var ruleName: String? = null

    @Column(name = "changed_by", length = 36)
    var changedBy: String? = null

    @Column(name = "changed_by_name")
    var changedByName: String? = null

    @Column(name = "changed_at", nullable = false)
    var changedAt: Instant = Instant.now()

    /**
     * Additional metadata about the change
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "change_metadata", columnDefinition = "jsonb")
    var changeMetadata: MutableMap<String, Any>? = null

    /**
     * Calculate the price difference
     */
    fun getPriceDifference(): BigDecimal {
        return newAmount - (previousAmount ?: BigDecimal.ZERO)
    }

    /**
     * Calculate the percentage change
     */
    fun getPercentageChange(): BigDecimal? {
        val prev = previousAmount ?: return null
        if (prev == BigDecimal.ZERO) return null
        return ((newAmount - prev) / prev) * BigDecimal(100)
    }

    companion object {
        fun create(
            variantId: String,
            productId: String,
            previousAmount: BigDecimal?,
            newAmount: BigDecimal,
            changeType: PriceChangeType,
            changedBy: String?,
            productTitle: String? = null,
            variantTitle: String? = null,
            sku: String? = null,
            currencyCode: String = "GBP"
        ): PriceChangeLog {
            return PriceChangeLog().apply {
                this.variantId = variantId
                this.productId = productId
                this.previousAmount = previousAmount
                this.newAmount = newAmount
                this.changeType = changeType
                this.changedBy = changedBy
                this.productTitle = productTitle
                this.variantTitle = variantTitle
                this.sku = sku
                this.currencyCode = currencyCode
                this.changedAt = Instant.now()
            }
        }

        fun createBulkUpdate(
            variantId: String,
            productId: String,
            previousAmount: BigDecimal?,
            newAmount: BigDecimal,
            changedBy: String?,
            productTitle: String? = null,
            variantTitle: String? = null
        ): PriceChangeLog {
            return create(
                variantId = variantId,
                productId = productId,
                previousAmount = previousAmount,
                newAmount = newAmount,
                changeType = PriceChangeType.BULK_UPDATE,
                changedBy = changedBy,
                productTitle = productTitle,
                variantTitle = variantTitle
            ).apply {
                changeSource = "ADMIN_UI"
            }
        }

        fun createFromRule(
            variantId: String,
            productId: String,
            previousAmount: BigDecimal?,
            newAmount: BigDecimal,
            ruleId: String,
            ruleName: String,
            changedBy: String?
        ): PriceChangeLog {
            return create(
                variantId = variantId,
                productId = productId,
                previousAmount = previousAmount,
                newAmount = newAmount,
                changeType = PriceChangeType.RULE_APPLIED,
                changedBy = changedBy
            ).apply {
                this.ruleId = ruleId
                this.ruleName = ruleName
            }
        }
    }
}

enum class PriceChangeType(val displayName: String) {
    MANUAL("Manual Edit"),
    BULK_UPDATE("Bulk Update"),
    RULE_APPLIED("Rule Applied"),
    IMPORT("Import"),
    SYNC("External Sync"),
    ROLLBACK("Rollback")
}
