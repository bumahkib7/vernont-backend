package com.vernont.workflow.flows.region

import com.vernont.domain.region.Region
import com.vernont.domain.region.Country
import com.vernont.domain.payment.PaymentProvider
import com.vernont.domain.fulfillment.FulfillmentProvider
import com.vernont.events.EventPublisher
import com.vernont.events.RegionCreated
import com.vernont.repository.region.RegionRepository
import com.vernont.repository.region.CountryRepository
import com.vernont.repository.payment.PaymentProviderRepository
import com.vernont.repository.fulfillment.FulfillmentProviderRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Input DTO for creating a single region
 * Kotlin version of Medusa's CreateRegionDTO
 */
data class CreateRegionRequest(
    val name: String,
    val currencyCode: String,
    val automaticTaxes: Boolean = false,
    val taxCode: String? = null,
    val giftCardsTaxable: Boolean = true,
    val taxRate: BigDecimal? = null,          // overrides default if provided
    val taxInclusive: Boolean = false,
    val countryCodes: List<String> = emptyList(),      // ISO2 codes ("us", "de", ...)
    val paymentProviderIds: List<String> = emptyList(),
    val fulfillmentProviderIds: List<String> = emptyList(),
    val metadata: Map<String, Any?>? = null   // if your Region has metadata, map it
)

/**
 * Workflow input: list of regions
 */
data class CreateRegionsInput(
    val regions: List<CreateRegionRequest>
)

/**
 * One Region DTO returned from the workflow
 */
data class RegionDto(
    val id: String,
    val name: String,
    val currencyCode: String,
    val automaticTaxes: Boolean,
    val taxCode: String?,
    val giftCardsTaxable: Boolean,
    val taxRate: BigDecimal,
    val taxInclusive: Boolean,
    val countryCodes: List<String>,
    val paymentProviderIds: List<String>,
    val fulfillmentProviderIds: List<String>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val metadata: Map<String, Any?>? = null
)

/**
 * Workflow output (list of RegionDTOs)
 */
data class CreateRegionsOutput(
    val regions: List<RegionDto>
)

@Component
@WorkflowTypes(
    input = CreateRegionsInput::class,
    output = CreateRegionsOutput::class
)
class CreateRegionsWorkflow(
    private val regionRepository: RegionRepository,
    private val countryRepository: CountryRepository,
    private val paymentProviderRepository: PaymentProviderRepository,
    private val fulfillmentProviderRepository: FulfillmentProviderRepository,
    private val eventPublisher: EventPublisher
) : Workflow<CreateRegionsInput, CreateRegionsOutput> {

    override val name: String = WorkflowConstants.CreateRegions.CREATE_REGIONS

    @Transactional
    override suspend fun execute(
        input: CreateRegionsInput,
        context: WorkflowContext
    ): WorkflowResult<CreateRegionsOutput> {
        logger.info { "Starting create regions workflow for ${input.regions.size} region(s)" }

        return try {
            if (input.regions.isEmpty()) {
                throw IllegalArgumentException("At least one region must be provided")
            }

            // 1. Validate input
            val validateStep = createStep<CreateRegionsInput, Unit>(
                name = "validate-create-regions-input",
                execute = { inp, _ ->
                    inp.regions.forEachIndexed { index, region ->
                        require(region.name.isNotBlank()) {
                            "Region name cannot be blank at index $index"
                        }
                        require(region.currencyCode.isNotBlank()) {
                            "Region currencyCode cannot be blank at index $index"
                        }
                    }
                    StepResponse.of(Unit)
                }
            )

            // 2. Create base Region entities (name, currency, taxes, countries)
            val createRegionsStep = createStep<CreateRegionsInput, List<Region>>(
                name = "create-regions",
                execute = { inp, ctx ->
                    logger.debug { "Creating ${inp.regions.size} region entity/entities" }

                    val created = inp.regions.map { req ->
                        val region = Region().apply {
                            this.name = req.name
                            this.currencyCode = req.currencyCode.uppercase()
                            this.automaticTaxes = req.automaticTaxes
                            this.taxCode = req.taxCode
                            this.giftCardsTaxable = req.giftCardsTaxable
                            this.taxInclusive = req.taxInclusive
                            this.taxRate = req.taxRate
                                ?.divide(BigDecimal(100))  // treat as percentage
                                ?: this.taxRate

                            this.metadata = req.metadata?.toMutableMap()
                        }

                        // Countries (ManyToMany)
                        if (req.countryCodes.isNotEmpty()) {
                            req.countryCodes.forEach { iso2 ->
                                val code = iso2.uppercase()
                                val country = countryRepository.findByIso2(code).orElseThrow {
                                    IllegalArgumentException("Country with ISO2 code $code not found")
                                }
                                region.addCountry(country)
                            }
                        }

                        regionRepository.save(region)
                    }

                    ctx.addMetadata("regions", created)
                    StepResponse.of(created)
                }
            )

            // 3. Set payment providers
            val setPaymentProvidersStep = createStep<List<Region>, List<Region>>(
                name = "set-regions-payment-providers",
                execute = { regions, ctx ->
                    logger.debug { "Setting payment providers for ${regions.size} region(s)" }

                    val requests = input.regions
                    val updated = mutableListOf<Region>()

                    regions.forEachIndexed { index, region ->
                        val req = requests[index]
                        if (req.paymentProviderIds.isNotEmpty()) {
                            val providers: List<PaymentProvider> =
                                paymentProviderRepository.findByIdIn(req.paymentProviderIds)

                            region.paymentProviders.clear()
                            region.paymentProviders.addAll(providers)
                        }
                        updated += regionRepository.save(region)
                    }

                    ctx.addMetadata("regionsWithPaymentProviders", updated)
                    StepResponse.of(updated)
                }
            )

            // 4. Set fulfillment providers (since Region has them)
            val setFulfillmentProvidersStep = createStep<List<Region>, List<Region>>(
                name = "set-regions-fulfillment-providers",
                execute = { regions, ctx ->
                    logger.debug { "Setting fulfillment providers for ${regions.size} region(s)" }

                    val requests = input.regions
                    val updated = mutableListOf<Region>()

                    regions.forEachIndexed { index, region ->
                        val req = requests[index]
                        if (req.fulfillmentProviderIds.isNotEmpty()) {
                            val providers: List<FulfillmentProvider> =
                                fulfillmentProviderRepository.findByIdIn(req.fulfillmentProviderIds)

                            region.fulfillmentProviders.clear()
                            region.fulfillmentProviders.addAll(providers)
                        }
                        updated += regionRepository.save(region)
                    }

                    ctx.addMetadata("regionsWithProviders", updated)
                    StepResponse.of(updated)
                }
            )

            // 5. Create price preferences (placeholder – hook for later)
            val createPricePreferencesStep = createStep<List<Region>, Unit>(
                name = "create-price-preferences",
                execute = { regions, _ ->
                    logger.debug {
                        "Price preferences placeholder for regions: ${regions.map { it.id }}"
                    }
                    StepResponse.of(Unit)
                }
            )

            // 6. Emit region.created events
            val emitEventsStep = createStep<List<Region>, Unit>(
                name = "emit-region-created-events",
                execute = { regions, _ ->
                    logger.debug { "Emitting region.created events for ${regions.size} region(s)" }

                    regions.forEach { region ->
                        eventPublisher.publish(
                            RegionCreated(
                                aggregateId = region.id,
                                regionId = region.id,
                                name = region.name,
                                currencyCode = region.currencyCode,
                                automaticTaxes = region.automaticTaxes,
                                countryCodes = region.countries.map { it.iso2 },
                                paymentProviderIds = region.paymentProviders.map { it.id },
                                fulfillmentProviderIds = region.fulfillmentProviders.map { it.id }
                            )
                        )
                    }


                    StepResponse.of(Unit)
                }
            )

            // ---- Execute all steps in order ----
            validateStep.invoke(input, context)

            val baseRegions = createRegionsStep.invoke(input, context).data
            val withPaymentProviders = setPaymentProvidersStep.invoke(baseRegions, context).data
            val finalRegions = setFulfillmentProvidersStep.invoke(withPaymentProviders, context).data

            createPricePreferencesStep.invoke(finalRegions, context)
            emitEventsStep.invoke(finalRegions, context)

            // Map to DTOs
            val dtoList = finalRegions.map { region ->
                RegionDto(
                    id = region.id,
                    name = region.name,
                    currencyCode = region.currencyCode,
                    automaticTaxes = region.automaticTaxes,
                    taxCode = region.taxCode,
                    giftCardsTaxable = region.giftCardsTaxable,
                    taxRate = region.taxRate,
                    taxInclusive = region.taxInclusive,
                    countryCodes = region.countries
                        .map { it.iso2.lowercase() }
                        .sorted(),
                    paymentProviderIds = region.paymentProviders.map { it.id }.sorted(),
                    fulfillmentProviderIds = region.fulfillmentProviders.map { it.id }.sorted(),
                    createdAt = region.createdAt,
                    updatedAt = region.updatedAt,
                    metadata =  region.metadata
                )
            }

            logger.info { "Create regions workflow succeeded for ${dtoList.size} region(s)" }
            WorkflowResult.success(CreateRegionsOutput(dtoList))

        } catch (e: Exception) {
            logger.error(e) { "Create regions workflow failed: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }
}
