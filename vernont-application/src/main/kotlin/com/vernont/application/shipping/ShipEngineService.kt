package com.vernont.application.shipping

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.application.config.ShipEngineProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * ShipEngine Shipping Service
 * Handles all ShipEngine API interactions for shipping label generation and tracking
 * API Documentation: https://www.shipengine.com/docs/
 */
@Service
@EnableConfigurationProperties(ShipEngineProperties::class)
class ShipEngineService(
    private val shipEngineProperties: ShipEngineProperties,
    private val objectMapper: ObjectMapper
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    companion object {
        const val SHIPENGINE_API_URL = "https://api.shipengine.com/v1"
    }

    @PostConstruct
    fun init() {
        if (shipEngineProperties.isConfigured()) {
            val mode = if (shipEngineProperties.useSandbox) "SANDBOX" else "PRODUCTION"
            logger.info { "ShipEngine initialized in $mode mode" }
        } else {
            logger.info { "ShipEngine is not configured - shipping labels will be manual only" }
        }
    }

    /**
     * Check if ShipEngine is properly configured and enabled
     */
    fun isAvailable(): Boolean = shipEngineProperties.isConfigured()

    /**
     * Get configuration info for frontend
     */
    fun getConfig(): ShipEngineConfig {
        // Sandbox carriers have specific IDs - production carriers need to be connected
        // UPS first as it supports international from any origin
        val sandboxCarriers = listOf(
            CarrierInfo("se-358070", "UPS - Sandbox (International)"),
            CarrierInfo("se-358071", "FedEx - Sandbox (International)"),
            CarrierInfo("se-358069", "Stamps.com/USPS - Sandbox (US origin only)")
        )

        val productionCarriers = listOf(
            CarrierInfo("royal_mail", "Royal Mail"),
            CarrierInfo("dpd", "DPD"),
            CarrierInfo("evri_uk", "Evri (UK)"),
            CarrierInfo("dhl_express", "DHL Express")
        )

        return ShipEngineConfig(
            enabled = shipEngineProperties.enabled,
            isConfigured = shipEngineProperties.isConfigured(),
            sandboxMode = shipEngineProperties.useSandbox,
            defaultCarrierId = shipEngineProperties.defaultCarrierId,
            defaultServiceCode = shipEngineProperties.defaultServiceCode,
            availableCarriers = if (shipEngineProperties.useSandbox) sandboxCarriers else productionCarriers
        )
    }

    /**
     * Create a shipping label
     */
    suspend fun createLabel(request: CreateLabelRequest): ShipEngineLabelResult {
        if (!isAvailable()) {
            throw ShipEngineException("ShipEngine is not configured")
        }

        logger.info { "Creating ShipEngine label for shipment" }

        try {
            val fromAddress = request.fromAddress ?: buildFromAddress()

            // Build shipment object - use carrier_id if provided (se-xxx format), otherwise use service_code
            // Filter out null values to avoid API errors
            val shipToMap = mutableMapOf<String, Any>(
                "name" to (request.toAddress.name.ifBlank { "Customer" }),
                "phone" to (request.toAddress.phone ?: "+1 555 555 5555"),
                "address_line1" to (request.toAddress.street1.ifBlank { throw ShipEngineException("ship_to address_line1 is required") }),
                "city_locality" to (request.toAddress.city.ifBlank { throw ShipEngineException("ship_to city is required") }),
                "postal_code" to (request.toAddress.postalCode.ifBlank { throw ShipEngineException("ship_to postal_code is required") }),
                "country_code" to (request.toAddress.countryCode.ifBlank { "US" }),
                "address_residential_indicator" to "unknown"
            )
            request.toAddress.street2?.takeIf { it.isNotBlank() }?.let { shipToMap["address_line2"] = it }
            request.toAddress.stateProvince?.takeIf { it.isNotBlank() }?.let { shipToMap["state_province"] = it }

            val shipFromMap = mutableMapOf<String, Any>(
                "name" to (fromAddress.name.ifBlank { "Warehouse" }),
                "phone" to (fromAddress.phone ?: "+1 555 555 5555"),
                "address_line1" to (fromAddress.street1.ifBlank { throw ShipEngineException("ship_from address_line1 is required") }),
                "city_locality" to (fromAddress.city.ifBlank { throw ShipEngineException("ship_from city is required") }),
                "postal_code" to (fromAddress.postalCode.ifBlank { throw ShipEngineException("ship_from postal_code is required") }),
                "country_code" to (fromAddress.countryCode.ifBlank { "US" })
            )
            fromAddress.company?.takeIf { it.isNotBlank() }?.let { shipFromMap["company_name"] = it }
            fromAddress.street2?.takeIf { it.isNotBlank() }?.let { shipFromMap["address_line2"] = it }
            fromAddress.stateProvince?.takeIf { it.isNotBlank() }?.let { shipFromMap["state_province"] = it }

            // Check if this is an international shipment
            val isInternational = fromAddress.countryCode.uppercase() != request.toAddress.countryCode.uppercase()

            val shipmentMap = mutableMapOf<String, Any?>(
                "service_code" to request.serviceCode,
                "ship_to" to shipToMap,
                "ship_from" to shipFromMap,
                "packages" to listOf(
                    mapOf(
                        "weight" to mapOf(
                            "value" to request.parcel.weight,
                            "unit" to "ounce"
                        ),
                        "dimensions" to mapOf(
                            "length" to request.parcel.length,
                            "width" to request.parcel.width,
                            "height" to request.parcel.height,
                            "unit" to "inch"
                        )
                    )
                )
            )

            // Only add carrier_id if it looks like a valid ShipEngine carrier ID (starts with "se-")
            if (request.carrierId.isNotBlank() && request.carrierId.startsWith("se-")) {
                shipmentMap["carrier_id"] = request.carrierId
            }

            // Add customs information for international shipments
            if (isInternational) {
                shipmentMap["customs"] = mapOf(
                    "contents" to "merchandise",
                    "non_delivery" to "return_to_sender",
                    "customs_items" to listOf(
                        mapOf(
                            "description" to (request.customsDescription ?: "Merchandise"),
                            "quantity" to (request.customsQuantity ?: 1),
                            "value" to mapOf(
                                "amount" to (request.customsValue ?: 10.0),
                                "currency" to "USD"
                            ),
                            "country_of_origin" to fromAddress.countryCode.uppercase(),
                            "harmonized_tariff_code" to (request.harmonizedTariffCode ?: "")
                        )
                    )
                )
            }

            val labelPayload = mapOf(
                "shipment" to shipmentMap,
                "label_format" to shipEngineProperties.labelFormat,
                "label_layout" to "4x6"
            )

            val response = makeApiRequest("POST", "/labels", labelPayload)
            val labelResponse = objectMapper.readValue(response, ShipEngineLabelResponse::class.java)

            logger.info { "ShipEngine label created: ${labelResponse.labelId}, tracking: ${labelResponse.trackingNumber}" }

            return ShipEngineLabelResult(
                labelId = labelResponse.labelId,
                trackingNumber = labelResponse.trackingNumber ?: "",
                trackingUrl = labelResponse.trackingUrl,
                labelDownloadUrl = labelResponse.labelDownload?.pdf ?: labelResponse.labelDownload?.png ?: "",
                labelDownloadPng = labelResponse.labelDownload?.png,
                carrier = labelResponse.carrierCode ?: request.serviceCode.split("_").firstOrNull() ?: "",
                serviceCode = labelResponse.serviceCode ?: request.serviceCode,
                shipmentCost = labelResponse.shipmentCost?.amount?.toString() ?: "0.00",
                currency = labelResponse.shipmentCost?.currency ?: "USD"
            )
        } catch (e: ShipEngineException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to create ShipEngine label: ${e.message}" }
            throw ShipEngineException("Failed to create shipping label: ${e.message}", e)
        }
    }

    /**
     * Get shipping rates for a shipment
     */
    suspend fun getRates(request: GetRatesRequest): List<ShipEngineRate> {
        if (!isAvailable()) {
            throw ShipEngineException("ShipEngine is not configured")
        }

        val fromAddress = request.fromAddress ?: buildFromAddress()

        val ratesPayload = mapOf(
            "rate_options" to mapOf(
                "carrier_ids" to request.carrierIds
            ),
            "shipment" to mapOf(
                "ship_to" to mapOf(
                    "name" to request.toAddress.name,
                    "address_line1" to request.toAddress.street1,
                    "address_line2" to request.toAddress.street2,
                    "city_locality" to request.toAddress.city,
                    "state_province" to request.toAddress.stateProvince,
                    "postal_code" to request.toAddress.postalCode,
                    "country_code" to request.toAddress.countryCode
                ),
                "ship_from" to mapOf(
                    "name" to fromAddress.name,
                    "address_line1" to fromAddress.street1,
                    "city_locality" to fromAddress.city,
                    "state_province" to fromAddress.stateProvince,
                    "postal_code" to fromAddress.postalCode,
                    "country_code" to fromAddress.countryCode
                ),
                "packages" to listOf(
                    mapOf(
                        "weight" to mapOf(
                            "value" to request.parcel.weight,
                            "unit" to "ounce"
                        ),
                        "dimensions" to mapOf(
                            "length" to request.parcel.length,
                            "width" to request.parcel.width,
                            "height" to request.parcel.height,
                            "unit" to "inch"
                        )
                    )
                )
            )
        )

        val response = makeApiRequest("POST", "/rates", ratesPayload)
        val ratesResponse = objectMapper.readValue(response, ShipEngineRatesResponse::class.java)

        return ratesResponse.rateResponse?.rates ?: emptyList()
    }

    /**
     * Track a package
     */
    suspend fun trackPackage(carrierCode: String, trackingNumber: String): ShipEngineTrackingResult {
        if (!isAvailable()) {
            throw ShipEngineException("ShipEngine is not configured")
        }

        val response = makeApiRequest(
            "GET",
            "/tracking?carrier_code=$carrierCode&tracking_number=$trackingNumber"
        )

        return objectMapper.readValue(response, ShipEngineTrackingResult::class.java)
    }

    /**
     * Build from address from configuration
     */
    private fun buildFromAddress(): ShipEngineAddress {
        val config = shipEngineProperties.fromAddress
        return ShipEngineAddress(
            name = config.name,
            company = config.company.ifBlank { null },
            street1 = config.street1,
            street2 = config.street2.ifBlank { null },
            city = config.city,
            stateProvince = config.stateProvince.ifBlank { null },
            postalCode = config.postalCode,
            countryCode = config.countryCode,
            phone = config.phone.ifBlank { null }
        )
    }

    /**
     * Make API request to ShipEngine
     */
    private fun makeApiRequest(method: String, endpoint: String, payload: Any? = null): String {
        val apiKey = shipEngineProperties.getActiveApiKey()

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("$SHIPENGINE_API_URL$endpoint"))
            .header("API-Key", apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))

        val request = when (method) {
            "GET" -> requestBuilder.GET().build()
            "POST" -> requestBuilder.POST(
                HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))
            ).build()
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            val errorMessage = try {
                val errorBody = objectMapper.readTree(response.body())
                val errors = errorBody.path("errors")
                if (errors.isArray && errors.size() > 0) {
                    errors[0].path("message").asText(response.body())
                } else {
                    errorBody.path("message").asText(response.body())
                }
            } catch (e: Exception) {
                response.body()
            }
            throw ShipEngineException("ShipEngine API error (${response.statusCode()}): $errorMessage")
        }

        return response.body()
    }
}

// ============ Data Classes ============

data class ShipEngineConfig(
    val enabled: Boolean,
    val isConfigured: Boolean,
    val sandboxMode: Boolean,
    val defaultCarrierId: String,
    val defaultServiceCode: String,
    val availableCarriers: List<CarrierInfo>
)

data class CarrierInfo(
    val code: String,
    val name: String
)

data class CreateLabelRequest(
    val toAddress: ShipEngineAddress,
    val fromAddress: ShipEngineAddress? = null,
    val parcel: ShipEngineParcel,
    val carrierId: String,
    val serviceCode: String,
    // Customs fields for international shipments
    val customsDescription: String? = null,
    val customsQuantity: Int? = null,
    val customsValue: Double? = null,
    val harmonizedTariffCode: String? = null
)

data class GetRatesRequest(
    val toAddress: ShipEngineAddress,
    val fromAddress: ShipEngineAddress? = null,
    val parcel: ShipEngineParcel,
    val carrierIds: List<String>
)

data class ShipEngineAddress(
    val name: String,
    val company: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val stateProvince: String? = null,
    val postalCode: String,
    val countryCode: String,
    val phone: String? = null,
    val email: String? = null
)

data class ShipEngineParcel(
    val length: Double,  // inches
    val width: Double,   // inches
    val height: Double,  // inches
    val weight: Double   // oz
)

data class ShipEngineLabelResult(
    val labelId: String,
    val trackingNumber: String,
    val trackingUrl: String?,
    val labelDownloadUrl: String,
    val labelDownloadPng: String?,
    val carrier: String,
    val serviceCode: String,
    val shipmentCost: String,
    val currency: String
)

// ============ ShipEngine API Response Classes ============

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineLabelResponse(
    @JsonProperty("label_id")
    val labelId: String,
    @JsonProperty("tracking_number")
    val trackingNumber: String? = null,
    @JsonProperty("tracking_url")
    val trackingUrl: String? = null,
    @JsonProperty("carrier_code")
    val carrierCode: String? = null,
    @JsonProperty("service_code")
    val serviceCode: String? = null,
    @JsonProperty("shipment_cost")
    val shipmentCost: ShipEngineCost? = null,
    @JsonProperty("label_download")
    val labelDownload: LabelDownload? = null,
    val status: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineCost(
    val currency: String,
    val amount: Double
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LabelDownload(
    val pdf: String? = null,
    val png: String? = null,
    val zpl: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineRatesResponse(
    @JsonProperty("rate_response")
    val rateResponse: RateResponse? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RateResponse(
    val rates: List<ShipEngineRate> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineRate(
    @JsonProperty("rate_id")
    val rateId: String,
    @JsonProperty("carrier_id")
    val carrierId: String,
    @JsonProperty("carrier_code")
    val carrierCode: String,
    @JsonProperty("service_code")
    val serviceCode: String,
    @JsonProperty("service_type")
    val serviceType: String? = null,
    @JsonProperty("shipping_amount")
    val shippingAmount: ShipEngineCost,
    @JsonProperty("delivery_days")
    val deliveryDays: Int? = null,
    @JsonProperty("estimated_delivery_date")
    val estimatedDeliveryDate: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipEngineTrackingResult(
    @JsonProperty("tracking_number")
    val trackingNumber: String,
    @JsonProperty("status_code")
    val statusCode: String,
    @JsonProperty("status_description")
    val statusDescription: String,
    @JsonProperty("carrier_status_code")
    val carrierStatusCode: String? = null,
    @JsonProperty("carrier_status_description")
    val carrierStatusDescription: String? = null,
    @JsonProperty("tracking_url")
    val trackingUrl: String? = null,
    val events: List<TrackingEvent> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrackingEvent(
    @JsonProperty("occurred_at")
    val occurredAt: String,
    val description: String,
    @JsonProperty("city_locality")
    val cityLocality: String? = null,
    @JsonProperty("state_province")
    val stateProvince: String? = null,
    @JsonProperty("postal_code")
    val postalCode: String? = null,
    @JsonProperty("country_code")
    val countryCode: String? = null
)

/**
 * Custom exception for ShipEngine errors
 */
class ShipEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
