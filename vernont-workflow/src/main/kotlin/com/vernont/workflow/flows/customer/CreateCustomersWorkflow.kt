package com.vernont.workflow.flows.customer

import com.vernont.domain.customer.Customer
import com.vernont.events.CustomerCreated
import com.vernont.events.EventPublisher
import com.vernont.repository.customer.CustomerRepository
import com.vernont.repository.customer.CustomerGroupRepository
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

private val logger = KotlinLogging.logger {}

/**
 * Input for creating customers
 */
data class CreateCustomersInput(
    val customersData: List<CreateCustomerData>,
    val additionalData: Map<String, Any>? = null
)

data class CreateCustomerData(
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val hasAccount: Boolean = false,
    val groupIds: List<String>? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Create Customers Workflow
 *
 * This workflow creates one or more customers. It's the base workflow used by
 * CreateCustomerAccountWorkflow and admin customer creation endpoints.
 *
 * Based on Medusa's createCustomersWorkflow
 *
 * Steps:
 * 1. Validate customer data (email uniqueness, group existence)
 * 2. Create customer entities
 * 3. Link customer groups if provided
 * 4. Publish CustomerCreated events
 * 5. Return created customers
 *
 * @see https://docs.medusajs.com/api/admin#customers_postcustomers
 */
@Component
@WorkflowTypes(input = CreateCustomersInput::class, output = List::class)
class CreateCustomersWorkflow(
    private val customerRepository: CustomerRepository,
    private val customerGroupRepository: CustomerGroupRepository,
    private val eventPublisher: EventPublisher
) : Workflow<CreateCustomersInput, List<Customer>> {

    override val name = WorkflowConstants.CreateCustomers.NAME

    @Transactional
    override suspend fun execute(
        input: CreateCustomersInput,
        context: WorkflowContext
    ): WorkflowResult<List<Customer>> {
        logger.info { "Starting create customers workflow for ${input.customersData.size} customers" }

        try {
            // Step 1: Validate customer data
            val validateStep = createStep<CreateCustomersInput, Unit>(
                name = "validate-customer-data",
                execute = { inp, ctx ->
                    logger.debug { "Validating customer data for ${inp.customersData.size} customers" }

                    inp.customersData.forEach { customerData ->
                        // Check email uniqueness
                        if (customerRepository.existsByEmail(customerData.email)) {
                            throw CustomerEmailAlreadyExistsException("Customer with email ${customerData.email} already exists")
                        }

                        // Validate group IDs exist
                        customerData.groupIds?.forEach { groupId ->
                            val group = customerGroupRepository.findByIdAndDeletedAtIsNull(groupId)
                                ?: throw CustomerGroupNotFoundException("Customer group not found: $groupId")
                        }
                    }

                    ctx.addMetadata("customersValidated", true)
                    logger.debug { "Customer data validation completed" }
                    StepResponse.of(Unit)
                }
            )

            // Step 2: Create customers
            val createCustomersStep = createStep<CreateCustomersInput, List<Customer>>(
                name = "create-customers-step",
                execute = { inp, ctx ->
                    logger.debug { "Creating ${inp.customersData.size} customers" }

                    val customers = inp.customersData.map { customerData ->
                        val customer = Customer().apply {
                            email = customerData.email
                            firstName = customerData.firstName
                            lastName = customerData.lastName
                            phone = customerData.phone
                            hasAccount = customerData.hasAccount
                            metadata = customerData.metadata?.toMutableMap()
                        }

                        // Link customer groups
                        customerData.groupIds?.let { groupIds ->
                            groupIds.forEach { groupId ->
                                val group = customerGroupRepository.findByIdAndDeletedAtIsNull(groupId)
                                    ?: throw CustomerGroupNotFoundException("Customer group not found: $groupId")
                                customer.addToGroup(group)
                            }
                        }

                        customerRepository.save(customer)
                    }

                    ctx.addMetadata("customersCreated", customers.map { it.id })
                    logger.info { "Successfully created ${customers.size} customers" }

                    StepResponse.of(customers)
                }
            )

            // Step 3: Publish events
            val publishEventsStep = createStep<List<Customer>, List<Customer>>(
                name = "publish-customer-created-events",
                execute = { customers, ctx ->
                    logger.debug { "Publishing CustomerCreated events for ${customers.size} customers" }

                    customers.forEach { customer ->
                        eventPublisher.publish(
                            CustomerCreated(
                                aggregateId = customer.id,
                                email = customer.email,
                                firstName = customer.firstName,
                                lastName = customer.lastName,
                                hasAccount = customer.hasAccount
                            )
                        )
                    }

                    ctx.addMetadata("eventsPublished", customers.size)
                    logger.info { "Published CustomerCreated events for ${customers.size} customers" }
                    StepResponse.of(customers)
                }
            )

            // Execute steps
            validateStep.invoke(input, context)
            val createdCustomers = createCustomersStep.invoke(input, context).data
            publishEventsStep.invoke(createdCustomers, context)

            logger.info { "Create customers workflow completed. Created ${createdCustomers.size} customers" }

            return WorkflowResult.success(createdCustomers)

        } catch (e: CustomerEmailAlreadyExistsException) {
            logger.error { "Create customers workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        } catch (e: CustomerGroupNotFoundException) {
            logger.error { "Create customers workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        } catch (e: Exception) {
            logger.error(e) { "Create customers workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}

/**
 * Exception thrown when customer email already exists
 */
class CustomerEmailAlreadyExistsException(message: String) : Exception(message)

/**
 * Exception thrown when customer group not found
 */
class CustomerGroupNotFoundException(message: String) : Exception(message)
