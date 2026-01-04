package com.vernont.workflow.flows.inventory

import com.vernont.domain.inventory.StockLocation
import com.vernont.repository.inventory.StockLocationRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Input for creating a stock location
 */
data class CreateStockLocationInput(
    val name: String,
    val address1: String? = null,
    val address2: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val priority: Int = 0,
    val fulfillmentEnabled: Boolean = true
)

/**
 * Response for stock location creation
 */
data class CreateStockLocationResponse(
    val id: String,
    val name: String,
    val address1: String?,
    val address2: String?,
    val city: String?,
    val countryCode: String?,
    val province: String?,
    val postalCode: String?,
    val phone: String?,
    val priority: Int,
    val fulfillmentEnabled: Boolean,
    val fullAddress: String,
    val createdAt: Instant
)

/**
 * Create Stock Location Workflow (Admin)
 *
 * This workflow creates a new stock location (warehouse, store, etc.)
 * for inventory management.
 *
 * Steps:
 * 1. Validate location name is unique
 * 2. Validate country code format (if provided)
 * 3. Validate priority is non-negative
 * 4. Create stock location entity
 * 5. Return created location
 */
@Component
@WorkflowTypes(CreateStockLocationInput::class, CreateStockLocationResponse::class)
class CreateStockLocationWorkflow(
    private val stockLocationRepository: StockLocationRepository
) : Workflow<CreateStockLocationInput, CreateStockLocationResponse> {

    override val name = WorkflowConstants.CreateStockLocation.NAME

    companion object {
        private val VALID_COUNTRY_CODES = setOf(
            "US", "CA", "GB", "DE", "FR", "IT", "ES", "AU", "JP", "CN",
            "IN", "BR", "MX", "KR", "NL", "SE", "NO", "DK", "FI", "BE",
            "AT", "CH", "PL", "PT", "IE", "NZ", "SG", "HK", "AE", "ZA"
            // Add more as needed
        )
    }

    @Transactional
    override suspend fun execute(
        input: CreateStockLocationInput,
        context: WorkflowContext
    ): WorkflowResult<CreateStockLocationResponse> {
        logger.info { "Starting create stock location workflow for: ${input.name}" }

        try {
            // Step 1: Validate name is unique
            val validateNameStep = createStep<String, Unit>(
                name = "validate-location-name",
                execute = { locationName, _ ->
                    logger.debug { "Validating location name uniqueness: $locationName" }

                    if (locationName.isBlank()) {
                        throw IllegalArgumentException("Location name cannot be blank")
                    }

                    if (locationName.length > 255) {
                        throw IllegalArgumentException("Location name cannot exceed 255 characters")
                    }

                    if (stockLocationRepository.existsByName(locationName)) {
                        throw IllegalArgumentException("A stock location with name '$locationName' already exists")
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 2: Validate country code
            val validateCountryStep = createStep<String?, Unit>(
                name = "validate-country-code",
                execute = { countryCode, _ ->
                    if (countryCode != null) {
                        logger.debug { "Validating country code: $countryCode" }

                        if (countryCode.length != 2) {
                            throw IllegalArgumentException("Country code must be exactly 2 characters (ISO 3166-1 alpha-2)")
                        }

                        val normalizedCode = countryCode.uppercase()
                        if (normalizedCode !in VALID_COUNTRY_CODES) {
                            logger.warn { "Country code '$normalizedCode' is not in common country list - proceeding anyway" }
                        }
                    }
                    StepResponse.of(Unit)
                }
            )

            // Step 3: Validate priority
            val validatePriorityStep = createStep<Int, Unit>(
                name = "validate-location-priority",
                execute = { priority, _ ->
                    logger.debug { "Validating priority: $priority" }

                    if (priority < 0) {
                        throw IllegalArgumentException("Priority must be non-negative")
                    }

                    if (priority > 999) {
                        throw IllegalArgumentException("Priority cannot exceed 999")
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 4: Create stock location
            val createLocationStep = createStep<CreateStockLocationInput, StockLocation>(
                name = "create-stock-location",
                execute = { inp, ctx ->
                    logger.debug { "Creating stock location: ${inp.name}" }

                    val location = StockLocation().apply {
                        name = inp.name
                        address1 = inp.address1
                        address2 = inp.address2
                        city = inp.city
                        countryCode = inp.countryCode?.uppercase()
                        province = inp.province
                        postalCode = inp.postalCode
                        phone = inp.phone
                        priority = inp.priority
                        fulfillmentEnabled = inp.fulfillmentEnabled
                    }

                    val savedLocation = stockLocationRepository.save(location)
                    ctx.addMetadata("locationId", savedLocation.id)

                    logger.info { "Stock location created: ${savedLocation.id} - ${savedLocation.name}" }
                    StepResponse.of(savedLocation)
                },
                compensate = { _, ctx ->
                    // Delete the location if workflow fails after creation
                    try {
                        val locationId = ctx.getMetadata("locationId") as? String
                        if (locationId != null) {
                            val location = stockLocationRepository.findByIdAndDeletedAtIsNull(locationId)
                            if (location != null) {
                                location.deletedAt = Instant.now()
                                stockLocationRepository.save(location)
                                logger.info { "Compensated: Soft-deleted stock location $locationId" }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate stock location creation" }
                    }
                }
            )

            // Execute workflow steps
            validateNameStep.invoke(input.name, context)
            validateCountryStep.invoke(input.countryCode, context)
            validatePriorityStep.invoke(input.priority, context)
            val location = createLocationStep.invoke(input, context).data

            val response = CreateStockLocationResponse(
                id = location.id,
                name = location.name,
                address1 = location.address1,
                address2 = location.address2,
                city = location.city,
                countryCode = location.countryCode,
                province = location.province,
                postalCode = location.postalCode,
                phone = location.phone,
                priority = location.priority,
                fulfillmentEnabled = location.fulfillmentEnabled,
                fullAddress = location.getFullAddress(),
                createdAt = location.createdAt
            )

            logger.info { "Create stock location workflow completed. Location: ${location.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Create stock location workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
