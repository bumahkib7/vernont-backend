package com.vernont.domain.region

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "currency",
    indexes = [
        Index(name = "idx_currency_code", columnList = "code", unique = true),
        Index(name = "idx_currency_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Currency.summary",
    attributeNodes = []
)
class Currency : BaseEntity() {

    @NotBlank
    @Column(nullable = false, unique = true, length = 3)
    var code: String = ""

    @NotBlank
    @Column(nullable = false)
    var symbol: String = ""

    @NotBlank
    @Column(name = "symbol_native", nullable = false)
    var symbolNative: String = ""

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(name = "decimal_digits", nullable = false)
    var decimalDigits: Int = 2

    @Column(nullable = false)
    var rounding: Double = 0.0

    @Column(name = "includes_tax", nullable = false)
    var includesTax: Boolean = false

    fun formatAmount(amount: Double): String {
        return String.format("%s%.${decimalDigits}f", symbol, amount)
    }

    fun isZeroDecimal(): Boolean {
        return decimalDigits == 0
    }

    fun updateExchangeRate(rate: Double) {
        require(rate > 0.0) { "Exchange rate must be positive" }
        this.rounding = rate
    }
}
