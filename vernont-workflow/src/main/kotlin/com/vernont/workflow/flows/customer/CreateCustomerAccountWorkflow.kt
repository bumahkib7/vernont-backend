package com.vernont.workflow.flows.customer

import com.vernont.domain.auth.User
import com.vernont.domain.customer.Customer
import com.vernont.events.CustomerAccountCreated
import com.vernont.events.EventPublisher
import com.vernont.repository.auth.UserRepository
import com.vernont.repository.customer.CustomerRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import com.vernont.workflow.common.WorkflowPasswordEncoder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Input for customer account creation
 */
data class CreateCustomerAccountInput(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Create Customer Account Workflow
 *
 * This workflow creates a customer account with authentication. It's used for
 * customer self-registration in the storefront.
 *
 * Based on Medusa's createCustomerAccountWorkflow
 *
 * Steps:
 * 1. Validate customer account creation (email uniqueness, password strength)
 * 2. Create user account with hashed password
 * 3. Create customer record linked to user
 * 4. Publish CustomerAccountCreated event
 * 5. Return created customer
 */
@Component
@WorkflowTypes(input = CreateCustomerAccountInput::class, output = Customer::class)
class CreateCustomerAccountWorkflow(
    private val customerRepository: CustomerRepository,
    private val passwordEncoder: WorkflowPasswordEncoder,
    private val userRepository: UserRepository,
    private val roleRepository: com.vernont.repository.auth.RoleRepository,
    private val eventPublisher: EventPublisher,
    private val workflowEngine: WorkflowEngine
) : Workflow<CreateCustomerAccountInput, Customer> {

    override val name = WorkflowConstants.CreateCustomerAccount.NAME

    @Transactional
    override suspend fun execute(
        input: CreateCustomerAccountInput,
        context: WorkflowContext
    ): WorkflowResult<Customer> {
        // Normalize email to lowercase for consistency
        val normalizedInput = input.copy(email = input.email.lowercase().trim())
        logger.info { "Starting create customer account workflow for email: ${normalizedInput.email}" }

        try {
            // Step 1: Validate customer account creation
            val validateStep = createStep<CreateCustomerAccountInput, Unit>(
                name = "validate-customer-account-creation",
                execute = { inp, ctx ->
                    logger.debug { "Validating customer account creation for email: ${inp.email}" }

                    // Check email uniqueness in users
                    if (userRepository.existsByEmailIgnoreCase(inp.email)) {
                        throw CustomerAccountAlreadyExistsException("User account with email ${inp.email} already exists")
                    }

                    // Check email uniqueness in customers
                    if (customerRepository.existsByEmail(inp.email)) {
                        throw CustomerAccountAlreadyExistsException("Customer with email ${inp.email} already exists")
                    }

                    // Validate password strength
                    if (inp.password.length < 8) {
                        throw WeakPasswordException("Password must be at least 8 characters long")
                    }

                    ctx.addMetadata("accountValidated", true)
                    logger.debug { "Customer account validation completed" }
                    StepResponse.of(Unit)
                }
            )

            // Step 2: Create user account with hashed password
            val createUserStep = createStep<CreateCustomerAccountInput, User>(
                name = "create-user-account",
                execute = { inp, ctx ->
                    logger.debug { "Creating user account for email: ${inp.email}" }

                    val customerRole = roleRepository.findByName("CUSTOMER")
                        ?: throw RuntimeException("Customer role not found in database. Ensure 'CUSTOMER' role exists.")

                    val user = User().apply {
                        email = inp.email
                        firstName = inp.firstName
                        lastName = inp.lastName
                        passwordHash = passwordEncoder.encode(inp.password)
                        isActive = true
                        emailVerified = false // Set to false, implement email verification separately
                        addRole(customerRole)
                    }

                    val savedUser = userRepository.save(user)

                    ctx.addMetadata("userId", savedUser.id)
                    logger.info { "Successfully created user account: ${savedUser.id}" }

                    StepResponse.of(savedUser)
                }
            )

            // Step 3: Create customer record linked to user
            val createCustomerStep = createStep<Pair<CreateCustomerAccountInput, User>, Customer>(
                name = "create-customer-record",
                execute = { (inp, user), ctx ->
                    logger.debug { "Creating customer record for user: ${user.id}" }

                    val customer = Customer().apply {
                        email = inp.email
                        firstName = inp.firstName
                        lastName = inp.lastName
                        phone = inp.phone
                        metadata = inp.metadata?.toMutableMap()
                        hasAccount = true
                    }

                    // Link customer to user
                    customer.linkToUser(user)
                    val savedCustomer = customerRepository.save(customer)

                    ctx.addMetadata("customerId", savedCustomer.id)
                    logger.info { "Successfully created customer record: ${savedCustomer.id}" }

                    StepResponse.of(savedCustomer)
                }
            )

            // Step 4: Publish CustomerAccountCreated event
            val publishEventStep = createStep<Customer, Customer>(
                name = "publish-customer-account-created-event",
                execute = { customer, ctx ->
                    logger.debug { "Publishing CustomerAccountCreated event for customer: ${customer.id}" }

                    eventPublisher.publish(
                        CustomerAccountCreated(
                            aggregateId = customer.id,
                            customerId = customer.id,
                            email = customer.email,
                            userId = customer.user?.id
                        )
                    )

                    ctx.addMetadata("eventPublished", true)
                    logger.info { "Published CustomerAccountCreated event for customer: ${customer.id}" }
                    StepResponse.of(customer)
                }
            )

            // Execute steps
            validateStep.invoke(normalizedInput, context)
            val user = createUserStep.invoke(normalizedInput, context).data
            val customer = createCustomerStep.invoke(Pair(normalizedInput, user), context).data
            publishEventStep.invoke(customer, context)

            logger.info {
                "Create customer account workflow completed. " +
                "Customer ID: ${customer.id}, User ID: ${user.id}"
            }

            return WorkflowResult.success(customer)

        } catch (e: CustomerAccountAlreadyExistsException) {
            logger.error { "Create customer account workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        } catch (e: WeakPasswordException) {
            logger.error { "Create customer account workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        } catch (e: Exception) {
            logger.error(e) { "Create customer account workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}

/**
 * Exception thrown when customer account already exists
 */
class CustomerAccountAlreadyExistsException(message: String) : Exception(message)

/**
 * Exception thrown when password is too weak
 */
class WeakPasswordException(message: String) : Exception(message)
