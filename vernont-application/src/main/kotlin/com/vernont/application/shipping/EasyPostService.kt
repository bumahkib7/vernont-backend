package com.vernont.application.shipping

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.application.config.EasyPostProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * EasyPost Shipping Service
 * Handles all EasyPost API interactions for shipping label generation
 */
@Service
@EnableConfigurationProperties(EasyPostProperties::class)
class EasyPostService(
    private val easyPostProperties: EasyPostProperties,
    private val objectMapper: ObjectMapper
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    companion object {
        const val EASYPOST_API_URL = "https://api.easypost.com/v2"
    }

    @PostConstruct
    fun init() {
        if (easyPostProperties.isConfigured()) {
            val mode = if (easyPostProperties.useTestMode) "TEST" else "PRODUCTION"
            logger.info { "EasyPost initialized in $mode mode" }
        } else {
            logger.info { "EasyPost is not configured - shipping labels will be manual only" }
        }
    }

    /**
     * Check if EasyPost is properly configured and enabled
     */
    fun isAvailable(): Boolean = easyPostProperties.isConfigured()

    /**
     * Get configuration info for frontend
     */
    fun getConfig(): EasyPostConfig {
        return EasyPostConfig(
            enabled = easyPostProperties.enabled,
            isConfigured = easyPostProperties.isConfigured(),
            testMode = easyPostProperties.useTestMode,
            defaultCarrier = easyPostProperties.defaultCarrier,
            defaultService = easyPostProperties.defaultService,
            availableCarriers = listOf("USPS", "UPS", "FedEx", "DHL", "RoyalMail", "DPD", "Evri")
        )
    }

    /**
     * Create a shipment and buy a label in one step
     */
    suspend fun createShipmentAndBuyLabel(request: CreateShipmentRequest): EasyPostLabelResult {
        if (!isAvailable()) {
            throw EasyPostException("EasyPost is not configured")
        }

        logger.info { "Creating EasyPost shipment for order" }

        try {
            // Step 1: Create shipment
            val shipment = createShipment(request)
            logger.debug { "Shipment created: ${shipment.id}" }

            // Step 2: Find the best rate
            val selectedRate = findBestRate(shipment.rates, request.carrier, request.service)
                ?: throw EasyPostException("No shipping rates available for the selected carrier/service")

            logger.debug { "Selected rate: ${selectedRate.carrier} ${selectedRate.service} - ${selectedRate.rate} ${selectedRate.currency}" }

            // Step 3: Buy the label
            val purchasedShipment = buyLabel(shipment.id, selectedRate.id)

            logger.info { "EasyPost label purchased: ${purchasedShipment.trackingCode}" }

            return EasyPostLabelResult(
                shipmentId = purchasedShipment.id,
                trackingCode = purchasedShipment.trackingCode ?: "",
                trackingUrl = purchasedShipment.tracker?.publicUrl ?: generateTrackingUrl(selectedRate.carrier, purchasedShipment.trackingCode),
                labelUrl = purchasedShipment.postageLabel?.labelUrl ?: "",
                labelPdfUrl = purchasedShipment.postageLabel?.labelPdfUrl,
                carrier = selectedRate.carrier,
                service = selectedRate.service,
                rate = selectedRate.rate,
                currency = selectedRate.currency
            )
        } catch (e: EasyPostException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to create EasyPost shipment: ${e.message}" }
            throw EasyPostException("Failed to create shipping label: ${e.message}", e)
        }
    }

    /**
     * Create a shipment (without purchasing)
     */
    private fun createShipment(request: CreateShipmentRequest): EasyPostShipment {
        val fromAddress = request.fromAddress ?: buildFromAddress()

        val shipmentPayload = mapOf(
            "shipment" to mapOf(
                "to_address" to mapOf(
                    "name" to request.toAddress.name,
                    "street1" to request.toAddress.street1,
                    "street2" to request.toAddress.street2,
                    "city" to request.toAddress.city,
                    "state" to request.toAddress.state,
                    "zip" to request.toAddress.zip,
                    "country" to request.toAddress.country,
                    "phone" to request.toAddress.phone,
                    "email" to request.toAddress.email
                ),
                "from_address" to mapOf(
                    "name" to fromAddress.name,
                    "company" to fromAddress.company,
                    "street1" to fromAddress.street1,
                    "street2" to fromAddress.street2,
                    "city" to fromAddress.city,
                    "state" to fromAddress.state,
                    "zip" to fromAddress.zip,
                    "country" to fromAddress.country,
                    "phone" to fromAddress.phone
                ),
                "parcel" to mapOf(
                    "length" to request.parcel.length,
                    "width" to request.parcel.width,
                    "height" to request.parcel.height,
                    "weight" to request.parcel.weight
                ),
                "options" to mapOf(
                    "label_format" to "PDF"
                )
            )
        )

        val response = makeApiRequest("POST", "/shipments", shipmentPayload)
        return objectMapper.readValue(response, EasyPostShipment::class.java)
    }

    /**
     * Buy a label for a shipment
     */
    private fun buyLabel(shipmentId: String, rateId: String): EasyPostShipment {
        val payload = mapOf("rate" to mapOf("id" to rateId))
        val response = makeApiRequest("POST", "/shipments/$shipmentId/buy", payload)
        return objectMapper.readValue(response, EasyPostShipment::class.java)
    }

    /**
     * Get available rates for a shipment
     */
    fun getRates(request: CreateShipmentRequest): List<EasyPostRate> {
        if (!isAvailable()) {
            throw EasyPostException("EasyPost is not configured")
        }

        val shipment = createShipment(request)
        return shipment.rates
    }

    /**
     * Find the best matching rate
     */
    private fun findBestRate(rates: List<EasyPostRate>, preferredCarrier: String?, preferredService: String?): EasyPostRate? {
        // Try exact match first
        if (preferredCarrier != null && preferredService != null) {
            rates.find {
                it.carrier.equals(preferredCarrier, ignoreCase = true) &&
                it.service.equals(preferredService, ignoreCase = true)
            }?.let { return it }
        }

        // Try carrier match
        if (preferredCarrier != null) {
            rates.find { it.carrier.equals(preferredCarrier, ignoreCase = true) }?.let { return it }
        }

        // Return cheapest rate as fallback
        return rates.minByOrNull { it.rate.toDoubleOrNull() ?: Double.MAX_VALUE }
    }

    /**
     * Build from address from configuration
     */
    private fun buildFromAddress(): EasyPostAddress {
        val config = easyPostProperties.fromAddress
        return EasyPostAddress(
            name = config.name,
            company = config.company,
            street1 = config.street1,
            street2 = config.street2.ifBlank { null },
            city = config.city,
            state = config.state.ifBlank { null },
            zip = config.zip,
            country = config.country,
            phone = config.phone.ifBlank { null }
        )
    }

    /**
     * Make API request to EasyPost
     */
    private fun makeApiRequest(method: String, endpoint: String, payload: Any? = null): String {
        val apiKey = easyPostProperties.getActiveApiKey()
        val auth = Base64.getEncoder().encodeToString("$apiKey:".toByteArray())

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("$EASYPOST_API_URL$endpoint"))
            .header("Authorization", "Basic $auth")
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
                errorBody.path("error").path("message").asText(response.body())
            } catch (e: Exception) {
                response.body()
            }
            throw EasyPostException("EasyPost API error (${response.statusCode()}): $errorMessage")
        }

        return response.body()
    }

    /**
     * Generate tracking URL for common carriers
     */
    private fun generateTrackingUrl(carrier: String, trackingCode: String?): String? {
        if (trackingCode.isNullOrBlank()) return null

        return when (carrier.uppercase()) {
            "USPS" -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=$trackingCode"
            "UPS" -> "https://www.ups.com/track?loc=en_US&tracknum=$trackingCode"
            "FEDEX" -> "https://www.fedex.com/fedextrack/?trknbr=$trackingCode"
            "DHL" -> "https://www.dhl.com/en/express/tracking.html?AWB=$trackingCode"
            "ROYALMAIL" -> "https://www.royalmail.com/track-your-item#/tracking-results/$trackingCode"
            "DPD" -> "https://www.dpd.co.uk/tracking/trackingSearch.do?search.searchType=0&search.parcelNumber=$trackingCode"
            "EVRI", "HERMES" -> "https://www.evri.com/track/parcel/$trackingCode"
            else -> null
        }
    }
}

// ============ Data Classes ============

data class EasyPostConfig(
    val enabled: Boolean,
    val isConfigured: Boolean,
    val testMode: Boolean,
    val defaultCarrier: String,
    val defaultService: String,
    val availableCarriers: List<String>
)

data class CreateShipmentRequest(
    val toAddress: EasyPostAddress,
    val fromAddress: EasyPostAddress? = null,
    val parcel: EasyPostParcel,
    val carrier: String? = null,
    val service: String? = null
)

data class EasyPostAddress(
    val name: String,
    val company: String? = null,
    val street1: String,
    val street2: String? = null,
    val city: String,
    val state: String? = null,
    val zip: String,
    val country: String,
    val phone: String? = null,
    val email: String? = null
)

data class EasyPostParcel(
    val length: Double,  // inches
    val width: Double,   // inches
    val height: Double,  // inches
    val weight: Double   // oz
)

data class EasyPostLabelResult(
    val shipmentId: String,
    val trackingCode: String,
    val trackingUrl: String?,
    val labelUrl: String,
    val labelPdfUrl: String?,
    val carrier: String,
    val service: String,
    val rate: String,
    val currency: String
)

// ============ EasyPost API Response Classes ============

@JsonIgnoreProperties(ignoreUnknown = true)
data class EasyPostShipment(
    val id: String,
    @JsonProperty("tracking_code")
    val trackingCode: String? = null,
    val rates: List<EasyPostRate> = emptyList(),
    @JsonProperty("selected_rate")
    val selectedRate: EasyPostRate? = null,
    val tracker: EasyPostTracker? = null,
    @JsonProperty("postage_label")
    val postageLabel: EasyPostPostageLabel? = null,
    val status: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EasyPostRate(
    val id: String,
    val carrier: String,
    val service: String,
    val rate: String,
    val currency: String,
    @JsonProperty("delivery_days")
    val deliveryDays: Int? = null,
    @JsonProperty("est_delivery_days")
    val estDeliveryDays: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EasyPostTracker(
    val id: String,
    @JsonProperty("public_url")
    val publicUrl: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EasyPostPostageLabel(
    @JsonProperty("label_url")
    val labelUrl: String? = null,
    @JsonProperty("label_pdf_url")
    val labelPdfUrl: String? = null
)

/**
 * Custom exception for EasyPost errors
 */
class EasyPostException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
