package com.vernont.workflow.shipping.providers

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.workflow.shipping.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * ShipEngine shipping provider integration.
 *
 * API Docs: https://www.shipengine.com/docs/
 *
 * Supports:
 * - Label creation with idempotency
 * - Label voiding
 * - Multiple carriers (UPS, FedEx, USPS, DHL, etc.)
 */
@Component
class ShipEngineProvider(
    @Value("\${shipengine.api-key:}")
    private val apiKey: String = "",
    @Value("\${shipengine.sandbox-api-key:}")
    private val sandboxApiKey: String = "",
    @Value("\${shipengine.use-sandbox:true}")
    private val useSandbox: Boolean = true,
    @Value("\${shipengine.enabled:true}")
    private val enabled: Boolean = true,
    @Value("\${shipengine.default-carrier-id:}")
    private val defaultCarrierId: String = "",
    @Value("\${shipengine.default-service-code:usps_priority_mail}")
    private val defaultServiceCode: String = "usps_priority_mail",
    @Value("\${shipengine.label-format:pdf}")
    private val labelFormat: String = "pdf",
    // From address config
    @Value("\${shipengine.from-address.name:}")
    private val fromName: String = "",
    @Value("\${shipengine.from-address.company:}")
    private val fromCompany: String = "",
    @Value("\${shipengine.from-address.street1:}")
    private val fromStreet1: String = "",
    @Value("\${shipengine.from-address.street2:}")
    private val fromStreet2: String = "",
    @Value("\${shipengine.from-address.city:}")
    private val fromCity: String = "",
    @Value("\${shipengine.from-address.state-province:}")
    private val fromState: String = "",
    @Value("\${shipengine.from-address.postal-code:}")
    private val fromPostalCode: String = "",
    @Value("\${shipengine.from-address.country-code:US}")
    private val fromCountry: String = "US",
    @Value("\${shipengine.from-address.phone:}")
    private val fromPhone: String = "",
    private val objectMapper: ObjectMapper
) : ShippingLabelProvider {

    override val name: String = "shipengine"

    private val effectiveApiKey: String
        get() = if (useSandbox) sandboxApiKey else apiKey

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl("https://api.shipengine.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("API-Key", effectiveApiKey)
            .build()
    }

    /**
     * Default ship-from address from config
     */
    val defaultFromAddress: ShippingAddress
        get() = ShippingAddress(
            name = fromName,
            company = fromCompany.takeIf { it.isNotBlank() },
            street1 = fromStreet1,
            street2 = fromStreet2.takeIf { it.isNotBlank() },
            city = fromCity,
            state = fromState,
            postalCode = fromPostalCode,
            country = fromCountry,
            phone = fromPhone.takeIf { it.isNotBlank() }
        )

    override fun isAvailable(): Boolean {
        val hasKey = effectiveApiKey.isNotBlank()
        if (!hasKey && enabled) {
            logger.warn { "ShipEngine is enabled but no API key is configured" }
        }
        return enabled && hasKey
    }

    override suspend fun createLabel(idempotencyKey: String, request: CreateLabelRequest): LabelResult {
        logger.info { "ShipEngine: Creating label (idempotencyKey=$idempotencyKey, sandbox=$useSandbox)" }

        if (!isAvailable()) {
            throw IllegalStateException("ShipEngine provider is not configured. Set shipengine.api-key or shipengine.sandbox-api-key")
        }

        return withContext(Dispatchers.IO) {
            try {
                val shipEngineRequest = buildShipEngineRequest(request)

                logger.debug { "ShipEngine request: ${objectMapper.writeValueAsString(shipEngineRequest)}" }

                val response = webClient.post()
                    .uri("/v1/labels")
                    .header("Idempotency-Key", idempotencyKey)
                    .bodyValue(shipEngineRequest)
                    .retrieve()
                    .awaitBody<ShipEngineLabelResponse>()

                logger.info { "ShipEngine label created: labelId=${response.labelId}, tracking=${response.trackingNumber}" }

                LabelResult(
                    labelId = response.labelId,
                    trackingNumber = response.trackingNumber,
                    trackingUrl = response.trackingNumber?.let {
                        "https://track.shipengine.com/$it"
                    },
                    labelUrl = response.labelDownload?.pdf ?: response.labelDownload?.href,
                    carrier = response.carrierCode,
                    service = response.serviceCode,
                    cost = response.shipmentCost?.amount?.let { BigDecimal(it.toString()) },
                    currency = response.shipmentCost?.currency ?: "USD",
                    providerData = mapOf(
                        "shipengine_label_id" to response.labelId,
                        "shipment_id" to (response.shipmentId ?: ""),
                        "carrier_id" to (response.carrierId ?: ""),
                        "label_format" to (response.labelFormat ?: labelFormat),
                        "is_return_label" to (response.isReturnLabel ?: false),
                        "sandbox" to useSandbox
                    )
                )
            } catch (e: Exception) {
                logger.error(e) { "ShipEngine label creation failed: ${e.message}" }
                throw ShipEngineException("Failed to create label: ${e.message}", e)
            }
        }
    }

    override suspend fun voidLabel(labelId: String): VoidResult {
        logger.info { "ShipEngine: Voiding label (labelId=$labelId)" }

        if (!isAvailable()) {
            throw IllegalStateException("ShipEngine provider is not configured")
        }

        return withContext(Dispatchers.IO) {
            try {
                val response = webClient.put()
                    .uri("/v1/labels/$labelId/void")
                    .retrieve()
                    .awaitBodyOrNull<ShipEngineVoidResponse>()

                val approved = response?.approved ?: false

                if (approved) {
                    logger.info { "ShipEngine label voided successfully: $labelId" }
                    VoidResult(
                        success = true,
                        refundAmount = null
                    )
                } else {
                    val errorMsg = response?.message ?: "Void request not approved"
                    logger.warn { "ShipEngine label void not approved: $labelId - $errorMsg" }
                    VoidResult(
                        success = false,
                        error = errorMsg
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "ShipEngine label void failed: ${e.message}" }
                VoidResult(
                    success = false,
                    error = "Void request failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Get tracking info for a shipment
     */
    suspend fun getTracking(trackingNumber: String, carrierCode: String): TrackingInfo? {
        if (!isAvailable()) return null

        return withContext(Dispatchers.IO) {
            try {
                webClient.get()
                    .uri("/v1/tracking?carrier_code=$carrierCode&tracking_number=$trackingNumber")
                    .retrieve()
                    .awaitBodyOrNull<TrackingInfo>()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get tracking info: $trackingNumber" }
                null
            }
        }
    }

    /**
     * Get available rates for a shipment
     */
    suspend fun getRates(request: CreateLabelRequest): List<ShipEngineRate> {
        if (!isAvailable()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val rateRequest = buildRateRequest(request)
                val response = webClient.post()
                    .uri("/v1/rates")
                    .bodyValue(rateRequest)
                    .retrieve()
                    .awaitBody<ShipEngineRatesResponse>()

                response.rateResponse?.rates ?: emptyList()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get rates" }
                emptyList()
            }
        }
    }

    // ========== Request Builders ==========

    private fun buildShipEngineRequest(request: CreateLabelRequest): Map<String, Any?> {
        // Use provided from address or fall back to config default
        val fromAddress = if (request.shipFromAddress.street1.isNotBlank()) {
            request.shipFromAddress
        } else {
            defaultFromAddress
        }

        return mapOf(
            "shipment" to mapOf(
                "carrier_id" to (request.carrier?.let { getCarrierId(it) } ?: defaultCarrierId).takeIf { it.isNotBlank() },
                "service_code" to (request.service ?: defaultServiceCode),
                "ship_to" to buildAddressMap(request.shipToAddress),
                "ship_from" to buildAddressMap(fromAddress),
                "packages" to request.parcels.map { parcel ->
                    mapOf(
                        "weight" to mapOf(
                            "value" to parcel.weight,
                            "unit" to mapWeightUnit(parcel.weightUnit)
                        ),
                        "dimensions" to mapOf(
                            "length" to parcel.length,
                            "width" to parcel.width,
                            "height" to parcel.height,
                            "unit" to mapDimensionUnit(parcel.dimensionUnit)
                        )
                    )
                }
            ),
            "label_format" to labelFormat,
            "label_layout" to "4x6"
        )
    }

    private fun buildAddressMap(address: ShippingAddress): Map<String, Any?> {
        return mapOf(
            "name" to address.name,
            "company_name" to address.company,
            "address_line1" to address.street1,
            "address_line2" to address.street2,
            "city_locality" to address.city,
            "state_province" to address.state,
            "postal_code" to address.postalCode,
            "country_code" to address.country,
            "phone" to address.phone,
            "email" to address.email
        ).filterValues { it != null && (it as? String)?.isNotBlank() != false }
    }

    private fun buildRateRequest(request: CreateLabelRequest): Map<String, Any?> {
        return mapOf(
            "rate_options" to mapOf(
                "carrier_ids" to listOfNotNull(defaultCarrierId.takeIf { it.isNotBlank() })
            ),
            "shipment" to mapOf(
                "ship_to" to buildAddressMap(request.shipToAddress),
                "ship_from" to buildAddressMap(
                    if (request.shipFromAddress.street1.isNotBlank()) request.shipFromAddress else defaultFromAddress
                ),
                "packages" to request.parcels.map { parcel ->
                    mapOf(
                        "weight" to mapOf(
                            "value" to parcel.weight,
                            "unit" to mapWeightUnit(parcel.weightUnit)
                        )
                    )
                }
            )
        )
    }

    private fun getCarrierId(carrier: String): String {
        // If it's already a carrier ID (starts with se-), return as-is
        if (carrier.startsWith("se-")) return carrier

        // Otherwise use default
        return defaultCarrierId
    }

    private fun mapWeightUnit(unit: String): String {
        return when (unit.lowercase()) {
            "lb", "lbs", "pound", "pounds" -> "pound"
            "oz", "ounce", "ounces" -> "ounce"
            "kg", "kilogram", "kilograms" -> "kilogram"
            "g", "gram", "grams" -> "gram"
            else -> "pound"
        }
    }

    private fun mapDimensionUnit(unit: String): String {
        return when (unit.lowercase()) {
            "in", "inch", "inches" -> "inch"
            "cm", "centimeter", "centimeters" -> "centimeter"
            else -> "inch"
        }
    }
}

// ========== Response DTOs ==========

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineLabelResponse(
    @JsonProperty("label_id")
    val labelId: String = "",
    val status: String? = null,
    @JsonProperty("shipment_id")
    val shipmentId: String? = null,
    @JsonProperty("carrier_id")
    val carrierId: String? = null,
    @JsonProperty("carrier_code")
    val carrierCode: String? = null,
    @JsonProperty("service_code")
    val serviceCode: String? = null,
    @JsonProperty("tracking_number")
    val trackingNumber: String? = null,
    @JsonProperty("tracking_status")
    val trackingStatus: String? = null,
    @JsonProperty("label_format")
    val labelFormat: String? = null,
    @JsonProperty("label_download")
    val labelDownload: LabelDownload? = null,
    @JsonProperty("shipment_cost")
    val shipmentCost: ShipmentCost? = null,
    @JsonProperty("is_return_label")
    val isReturnLabel: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LabelDownload(
    val href: String? = null,
    val pdf: String? = null,
    val png: String? = null,
    val zpl: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipmentCost(
    val currency: String? = null,
    val amount: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineVoidResponse(
    val approved: Boolean = false,
    val message: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineRatesResponse(
    @JsonProperty("rate_response")
    val rateResponse: RateResponse? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RateResponse(
    val rates: List<ShipEngineRate>? = null,
    val errors: List<ShipEngineError>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineRate(
    @JsonProperty("rate_id")
    val rateId: String? = null,
    @JsonProperty("carrier_id")
    val carrierId: String? = null,
    @JsonProperty("carrier_code")
    val carrierCode: String? = null,
    @JsonProperty("service_code")
    val serviceCode: String? = null,
    @JsonProperty("service_type")
    val serviceName: String? = null,
    @JsonProperty("shipping_amount")
    val shippingAmount: ShipmentCost? = null,
    @JsonProperty("delivery_days")
    val deliveryDays: Int? = null,
    @JsonProperty("estimated_delivery_date")
    val estimatedDeliveryDate: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineError(
    @JsonProperty("error_code")
    val errorCode: String? = null,
    val message: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrackingInfo(
    @JsonProperty("tracking_number")
    val trackingNumber: String? = null,
    @JsonProperty("status_code")
    val statusCode: String? = null,
    @JsonProperty("status_description")
    val statusDescription: String? = null,
    @JsonProperty("carrier_status_code")
    val carrierStatusCode: String? = null,
    @JsonProperty("carrier_status_description")
    val carrierStatusDescription: String? = null,
    @JsonProperty("estimated_delivery_date")
    val estimatedDeliveryDate: String? = null,
    @JsonProperty("actual_delivery_date")
    val actualDeliveryDate: String? = null
)

class ShipEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
