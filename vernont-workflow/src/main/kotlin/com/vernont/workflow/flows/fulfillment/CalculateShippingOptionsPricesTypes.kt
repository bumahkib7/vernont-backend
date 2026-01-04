package com.vernont.workflow.flows.fulfillment

import java.math.BigDecimal

data class CalculateShippingOptionsPricesInput(
    val id: String,
    val providerId: String,
    val optionData: Map<String, Any> = emptyMap(),
    val data: Map<String, Any> = emptyMap(),
    val context: Map<String, Any> = emptyMap(),
    val contextId: String? = null // Added optional contextId
)

data class CalculatedPrice(
    val calculatedAmount: BigDecimal,
    val isCalculatedPriceTaxInclusive: Boolean
)