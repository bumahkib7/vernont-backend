package com.vernont.workflow.flows.fulfillment

import com.vernont.domain.fulfillment.dto.FulfillmentResponse
import com.vernont.repository.fulfillment.FulfillmentRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.shipping.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Shipment item - what and how much to ship
 */
data class ShipmentItemInput(
    val orderLineItemId: String,
    val quantity: Int
)

/**
 * Input for creating a shipment
 */
data class CreateShipmentInput(
    val orderId: String,
    val fulfillmentId: String,
    val items: List<ShipmentItemInput>,
    val labels: List<String>? = null,
    val trackingNumbers: List<String>? = null,
    val noNotification: Boolean = false,
    val metadata: Map<String, Any>? = null,
    /** Force new label purchase even if one exists */
    val forceNewLabel: Boolean = false
)

/**
 * Create Shipment Workflow - Production-Safe Implementation
 *
 * This workflow creates a shipment with proper transaction boundaries:
 * - T1 (prepareShipment): Read-only validation, no external calls
 * - External: Label purchase via ShippingLabelProvider (NO transaction)
 * - T2 (applyLabelResult): Write shipped status + outbox events
 * - T3 (markVoidOutcome): Handle compensation outcomes
 *
 * Safety guarantees:
 * - Idempotent label purchases (won't double-buy on retry)
 * - Atomic state + events (outbox pattern)
 * - External calls outside DB transactions
 * - Compensation tracking for ops visibility
 *
 * @example
 * val result = createShipmentWorkflow.execute(
 *   CreateShipmentInput(
 *     orderId = "order_123",
 *     fulfillmentId = "ful_123",
 *     items = listOf(ShipmentItemInput("line_123", quantity = 2)),
 *     trackingNumbers = listOf("TRACK123")
 *   )
 * )
 */
@Component
@WorkflowTypes(input = CreateShipmentInput::class, output = FulfillmentResponse::class)
class CreateShipmentWorkflow(
    private val shippingTxService: ShippingTxService,
    private val providerRegistry: ShippingProviderRegistry,
    private val fulfillmentRepository: FulfillmentRepository
) : Workflow<CreateShipmentInput, FulfillmentResponse> {

    override val name = WorkflowConstants.CreateShipment.NAME

    /**
     * Execute the shipment workflow.
     *
     * NO @Transactional here - transactions are managed by ShippingTxService.
     */
    override suspend fun execute(
        input: CreateShipmentInput,
        context: WorkflowContext
    ): WorkflowResult<FulfillmentResponse> {
        val correlationId = context.executionId
        logger.info { "Starting create shipment workflow: order=${input.orderId}, fulfillment=${input.fulfillmentId}" }

        var labelPurchased = false
        var labelResult: LabelResult? = null
        var prepared: PreparedShipment? = null

        try {
            // ========== STEP 1: Prepare (T1 - read-only transaction) ==========
            context.recordStepStart("prepare-shipment", input)
            val startTime = System.currentTimeMillis()

            prepared = shippingTxService.prepareShipment(input)

            context.recordStepComplete(
                "prepare-shipment",
                context.getMetadata("lastStepIndex") as? Int ?: 0,
                prepared,
                System.currentTimeMillis() - startTime
            )

            logger.info {
                "Shipment prepared: provider=${prepared.provider}, " +
                    "labelAlreadyPurchased=${prepared.labelAlreadyPurchased}, " +
                    "idempotencyKey=${prepared.idempotencyKey}"
            }

            // ========== STEP 2: External Label Purchase (NO transaction) ==========
            if (!prepared.labelAlreadyPurchased || input.forceNewLabel) {
                context.recordStepStart("purchase-label", prepared.idempotencyKey)
                val labelStartTime = System.currentTimeMillis()

                // Mark as pending before external call (small T1b transaction)
                shippingTxService.markLabelPendingPurchase(prepared.fulfillmentId, prepared.idempotencyKey)

                // Get provider (defaults to ShipEngine)
                val provider = providerRegistry.getProvider(prepared.provider)
                    ?: providerRegistry.getDefaultProvider()

                // Check if provider needs label purchase
                if (provider.name != "manual" && provider.isAvailable()) {
                    logger.info { "Purchasing label via ${provider.name}" }

                    // Build label request
                    val labelRequest = buildLabelRequest(prepared, input)

                    // External call - NO TRANSACTION
                    labelResult = provider.createLabel(prepared.idempotencyKey, labelRequest)
                    labelPurchased = true

                    logger.info {
                        "Label purchased: labelId=${labelResult.labelId}, " +
                            "tracking=${labelResult.trackingNumber}, cost=${labelResult.cost}"
                    }
                } else {
                    // Manual provider or manual tracking numbers provided
                    labelResult = buildManualLabelResult(input)
                    logger.info { "Using manual/provided tracking: ${labelResult?.trackingNumber}" }
                }

                context.recordStepComplete(
                    "purchase-label",
                    context.getMetadata("lastStepIndex") as? Int ?: 1,
                    labelResult,
                    System.currentTimeMillis() - labelStartTime
                )
            } else {
                // Reuse existing label
                logger.info { "Reusing existing label: ${prepared.existingLabelId}" }
                labelResult = LabelResult(
                    labelId = prepared.existingLabelId ?: "existing",
                    trackingNumber = prepared.existingTrackingNumber,
                    trackingUrl = null,
                    labelUrl = null,
                    carrier = null,
                    service = null,
                    cost = null
                )
            }

            // ========== STEP 3: Apply Result (T2 - write transaction) ==========
            context.recordStepStart("apply-shipment", labelResult)
            val applyStartTime = System.currentTimeMillis()

            val result = shippingTxService.applyLabelResult(
                prepared = prepared,
                labelResult = labelResult,
                correlationId = correlationId
            )

            context.recordStepComplete(
                "apply-shipment",
                context.getMetadata("lastStepIndex") as? Int ?: 2,
                result,
                System.currentTimeMillis() - applyStartTime
            )

            logger.info {
                "Shipment completed: fulfillment=${result.fulfillmentId}, " +
                    "tracking=${result.trackingNumber}, shippedAt=${result.shippedAt}"
            }

            // Load final fulfillment for response
            val fulfillment = fulfillmentRepository.findByIdWithItems(result.fulfillmentId).orElseThrow()
            return WorkflowResult.success(FulfillmentResponse.from(fulfillment))

        } catch (e: Exception) {
            logger.error(e) { "Shipment workflow failed: ${e.message}" }

            // ========== COMPENSATION: Void label if purchased but not applied ==========
            if (labelPurchased && labelResult != null && prepared != null) {
                handleLabelCompensation(prepared, labelResult, correlationId, e)
            }

            return WorkflowResult.failure(e)
        }
    }

    /**
     * Handle compensation when label was purchased but DB apply failed.
     */
    private suspend fun handleLabelCompensation(
        prepared: PreparedShipment,
        labelResult: LabelResult,
        correlationId: String?,
        originalError: Exception
    ) {
        logger.warn { "Attempting to void label after workflow failure: labelId=${labelResult.labelId}" }

        try {
            val provider = providerRegistry.getProvider(prepared.provider)

            if (provider != null && provider.name != "manual") {
                // Attempt void (external call - NO transaction)
                val voidResult = provider.voidLabel(labelResult.labelId)

                // Record outcome (T3 transaction)
                shippingTxService.markVoidOutcome(
                    fulfillmentId = prepared.fulfillmentId,
                    success = voidResult.success,
                    error = voidResult.error ?: originalError.message,
                    correlationId = correlationId
                )

                if (voidResult.success) {
                    logger.info { "Label voided successfully: ${labelResult.labelId}" }
                } else {
                    logger.error { "Label void FAILED - requires manual intervention: ${labelResult.labelId}" }
                }
            }
        } catch (voidError: Exception) {
            logger.error(voidError) { "Failed to void label - requires manual intervention: ${labelResult.labelId}" }

            // Still try to record the failure
            try {
                shippingTxService.markVoidOutcome(
                    fulfillmentId = prepared.fulfillmentId,
                    success = false,
                    error = "Void attempt failed: ${voidError.message}",
                    correlationId = correlationId
                )
            } catch (recordError: Exception) {
                logger.error(recordError) { "Failed to record void failure - ALERT OPS" }
            }
        }
    }

    /**
     * Build label request from prepared shipment data
     */
    private fun buildLabelRequest(prepared: PreparedShipment, input: CreateShipmentInput): CreateLabelRequest {
        // Calculate total weight from items (default 1 lb per item)
        val totalWeight = prepared.items.sumOf { it.quantityToShip * 1.0 }

        // Ship-from address comes from ShipEngine config (shipengine.from-address.*)
        // Pass empty address - ShipEngineProvider will use its default
        return CreateLabelRequest(
            shipFromAddress = ShippingAddress(
                name = null,
                street1 = "",
                city = "",
                state = null,
                postalCode = "",
                country = "US"
            ),
            shipToAddress = prepared.shipToAddress,
            parcels = listOf(
                Parcel(
                    length = 10.0,
                    width = 10.0,
                    height = 10.0,
                    dimensionUnit = "in",
                    weight = totalWeight,
                    weightUnit = "lb"
                )
            ),
            weight = Weight(totalWeight, "lb"),
            metadata = input.metadata
        )
    }

    /**
     * Build a manual label result from provided tracking numbers
     */
    private fun buildManualLabelResult(input: CreateShipmentInput): LabelResult? {
        val trackingNumber = input.trackingNumbers?.firstOrNull()

        return LabelResult(
            labelId = "manual_${System.currentTimeMillis()}",
            trackingNumber = trackingNumber,
            trackingUrl = trackingNumber?.let { generateTrackingUrl(it) },
            labelUrl = input.labels?.firstOrNull(),
            carrier = null,
            service = null,
            cost = null
        )
    }

    /**
     * Generate tracking URL from tracking number (best effort)
     */
    private fun generateTrackingUrl(trackingNumber: String): String {
        // Simple heuristic based on tracking number format
        return when {
            trackingNumber.startsWith("1Z") -> "https://www.ups.com/track?tracknum=$trackingNumber"
            trackingNumber.length == 22 -> "https://www.fedex.com/apps/fedextrack/?tracknumbers=$trackingNumber"
            trackingNumber.length in 20..22 -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=$trackingNumber"
            else -> "https://track.example.com/$trackingNumber"
        }
    }
}
