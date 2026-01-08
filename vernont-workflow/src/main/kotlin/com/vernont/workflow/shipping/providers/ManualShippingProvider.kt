package com.vernont.workflow.shipping.providers

import com.vernont.workflow.shipping.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Manual shipping provider - no external API calls.
 *
 * Used when tracking numbers and labels are entered manually
 * or when no shipping integration is needed.
 */
@Component
class ManualShippingProvider : ShippingLabelProvider {

    override val name: String = "manual"

    override fun isAvailable(): Boolean = true

    override suspend fun createLabel(idempotencyKey: String, request: CreateLabelRequest): LabelResult {
        logger.info { "Manual shipping - no label purchase needed (idempotencyKey=$idempotencyKey)" }

        // Generate a pseudo label ID for tracking purposes
        val labelId = "manual_${System.currentTimeMillis()}"

        return LabelResult(
            labelId = labelId,
            trackingNumber = null,
            trackingUrl = null,
            labelUrl = null,
            carrier = "manual",
            service = "manual",
            cost = BigDecimal.ZERO,
            currency = "USD",
            providerData = mapOf("manual" to true)
        )
    }

    override suspend fun voidLabel(labelId: String): VoidResult {
        logger.info { "Manual shipping - no label to void (labelId=$labelId)" }

        return VoidResult(
            success = true,
            refundAmount = BigDecimal.ZERO
        )
    }
}
