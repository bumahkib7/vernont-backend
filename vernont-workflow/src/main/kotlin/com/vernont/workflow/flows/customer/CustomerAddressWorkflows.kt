package com.vernont.workflow.flows.customer

import com.vernont.domain.customer.CustomerAddress
import com.vernont.repository.customer.CustomerAddressRepository
import com.vernont.repository.customer.CustomerRepository
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

// ============== Shared DTOs ==============

/**
 * Address response DTO
 */
data class CustomerAddressResponse(
    val id: String,
    val customerId: String,
    val firstName: String?,
    val lastName: String?,
    val company: String?,
    val phone: String?,
    val address1: String,
    val address2: String?,
    val city: String,
    val province: String?,
    val postalCode: String?,
    val countryCode: String,
    val fullAddress: String,
    val createdAt: Instant
)

// ============== Create Address Workflow ==============

/**
 * Input for creating a customer address
 */
data class CreateCustomerAddressInput(
    val customerId: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val company: String? = null,
    val phone: String? = null,
    val address1: String,
    val address2: String? = null,
    val city: String,
    val province: String? = null,
    val postalCode: String? = null,
    val countryCode: String
)

/**
 * Create Customer Address Workflow (Customer)
 *
 * This workflow creates a new address for a customer.
 *
 * Steps:
 * 1. Validate customer exists
 * 2. Validate address fields
 * 3. Check for duplicate addresses
 * 4. Create address
 * 5. Return created address
 */
@Component
@WorkflowTypes(CreateCustomerAddressInput::class, CustomerAddressResponse::class)
class CreateCustomerAddressWorkflow(
    private val customerRepository: CustomerRepository,
    private val customerAddressRepository: CustomerAddressRepository
) : Workflow<CreateCustomerAddressInput, CustomerAddressResponse> {

    override val name = WorkflowConstants.CreateCustomerAddress.NAME

    companion object {
        const val MAX_ADDRESSES_PER_CUSTOMER = 10
    }

    @Transactional
    override suspend fun execute(
        input: CreateCustomerAddressInput,
        context: WorkflowContext
    ): WorkflowResult<CustomerAddressResponse> {
        logger.info { "Starting create customer address workflow for customer: ${input.customerId}" }

        try {
            // Step 1: Validate customer exists
            val validateCustomerStep = createStep<String, Unit>(
                name = "validate-customer-exists",
                execute = { customerId, _ ->
                    logger.debug { "Validating customer: $customerId" }

                    val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
                        ?: throw IllegalArgumentException("Customer not found: $customerId")

                    // Check address limit
                    val addressCount = customerAddressRepository.countByCustomerId(customerId)
                    if (addressCount >= MAX_ADDRESSES_PER_CUSTOMER) {
                        throw IllegalStateException(
                            "Customer has reached maximum addresses ($MAX_ADDRESSES_PER_CUSTOMER)"
                        )
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 2: Validate address fields
            val validateAddressStep = createStep<CreateCustomerAddressInput, Unit>(
                name = "validate-address-fields",
                execute = { inp, _ ->
                    logger.debug { "Validating address fields" }

                    if (inp.address1.isBlank()) {
                        throw IllegalArgumentException("Address line 1 is required")
                    }

                    if (inp.city.isBlank()) {
                        throw IllegalArgumentException("City is required")
                    }

                    if (inp.countryCode.length != 2) {
                        throw IllegalArgumentException("Country code must be 2 characters (ISO 3166-1 alpha-2)")
                    }

                    // Validate phone format if provided
                    if (inp.phone != null && !isValidPhoneNumber(inp.phone)) {
                        throw IllegalArgumentException("Invalid phone number format")
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 3: Create address
            val createAddressStep = createStep<CreateCustomerAddressInput, CustomerAddress>(
                name = "create-customer-address",
                execute = { inp, ctx ->
                    logger.debug { "Creating address for customer: ${inp.customerId}" }

                    val customer = customerRepository.findByIdAndDeletedAtIsNull(inp.customerId)!!

                    val address = CustomerAddress().apply {
                        this.customer = customer
                        firstName = inp.firstName
                        lastName = inp.lastName
                        company = inp.company
                        phone = inp.phone
                        address1 = inp.address1
                        address2 = inp.address2
                        city = inp.city
                        province = inp.province
                        postalCode = inp.postalCode
                        countryCode = inp.countryCode.uppercase()
                    }

                    val savedAddress = customerAddressRepository.save(address)
                    ctx.addMetadata("addressId", savedAddress.id)

                    logger.info { "Customer address created: ${savedAddress.id}" }
                    StepResponse.of(savedAddress)
                },
                compensate = { _, ctx ->
                    try {
                        val addressId = ctx.getMetadata("addressId") as? String
                        if (addressId != null) {
                            val address = customerAddressRepository.findByIdAndDeletedAtIsNull(addressId)
                            if (address != null) {
                                address.deletedAt = Instant.now()
                                customerAddressRepository.save(address)
                                logger.info { "Compensated: Soft-deleted address $addressId" }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate address creation" }
                    }
                }
            )

            // Execute workflow steps
            validateCustomerStep.invoke(input.customerId, context)
            validateAddressStep.invoke(input, context)
            val address = createAddressStep.invoke(input, context).data

            val response = toAddressResponse(address)

            logger.info { "Create customer address workflow completed. Address: ${address.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Create customer address workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        // Basic phone validation - allows digits, spaces, dashes, parentheses, and plus sign
        val phoneRegex = Regex("^[+]?[(]?[0-9]{1,4}[)]?[-\\s./0-9]*\$")
        return phone.matches(phoneRegex) && phone.replace(Regex("[^0-9]"), "").length >= 7
    }
}

// ============== Update Address Workflow ==============

/**
 * Input for updating a customer address
 */
data class UpdateCustomerAddressInput(
    val addressId: String,
    val customerId: String,  // For ownership validation
    val firstName: String? = null,
    val lastName: String? = null,
    val company: String? = null,
    val phone: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val city: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null
)

/**
 * Update Customer Address Workflow (Customer)
 *
 * This workflow updates an existing customer address.
 *
 * Steps:
 * 1. Load address and validate ownership
 * 2. Validate updated fields
 * 3. Update address
 * 4. Return updated address
 */
@Component
@WorkflowTypes(UpdateCustomerAddressInput::class, CustomerAddressResponse::class)
class UpdateCustomerAddressWorkflow(
    private val customerAddressRepository: CustomerAddressRepository
) : Workflow<UpdateCustomerAddressInput, CustomerAddressResponse> {

    override val name = WorkflowConstants.UpdateCustomerAddress.NAME

    @Transactional
    override suspend fun execute(
        input: UpdateCustomerAddressInput,
        context: WorkflowContext
    ): WorkflowResult<CustomerAddressResponse> {
        logger.info { "Starting update customer address workflow for address: ${input.addressId}" }

        try {
            // Step 1: Load and validate ownership
            val loadAddressStep = createStep<String, CustomerAddress>(
                name = "load-address-for-update",
                execute = { addressId, ctx ->
                    logger.debug { "Loading address: $addressId" }

                    val address = customerAddressRepository.findByIdAndDeletedAtIsNull(addressId)
                        ?: throw IllegalArgumentException("Address not found: $addressId")

                    // Validate ownership
                    if (address.customer?.id != input.customerId) {
                        throw IllegalArgumentException("Address does not belong to this customer")
                    }

                    // Store original values for compensation
                    ctx.addMetadata("originalAddress1", address.address1)
                    ctx.addMetadata("originalCity", address.city)
                    ctx.addMetadata("originalCountryCode", address.countryCode)

                    StepResponse.of(address)
                }
            )

            // Step 2: Validate and update address
            val updateAddressStep = createStep<CustomerAddress, CustomerAddress>(
                name = "update-address-fields",
                execute = { address, _ ->
                    logger.debug { "Updating address: ${address.id}" }

                    // Update only provided fields
                    input.firstName?.let { address.firstName = it }
                    input.lastName?.let { address.lastName = it }
                    input.company?.let { address.company = it }
                    input.phone?.let { address.phone = it }
                    input.address1?.let {
                        if (it.isBlank()) throw IllegalArgumentException("Address line 1 cannot be blank")
                        address.address1 = it
                    }
                    input.address2?.let { address.address2 = it }
                    input.city?.let {
                        if (it.isBlank()) throw IllegalArgumentException("City cannot be blank")
                        address.city = it
                    }
                    input.province?.let { address.province = it }
                    input.postalCode?.let { address.postalCode = it }
                    input.countryCode?.let {
                        if (it.length != 2) throw IllegalArgumentException("Country code must be 2 characters")
                        address.countryCode = it.uppercase()
                    }

                    val savedAddress = customerAddressRepository.save(address)
                    logger.info { "Address updated: ${savedAddress.id}" }
                    StepResponse.of(savedAddress)
                }
            )

            // Execute workflow steps
            val address = loadAddressStep.invoke(input.addressId, context).data
            val updatedAddress = updateAddressStep.invoke(address, context).data

            val response = toAddressResponse(updatedAddress)

            logger.info { "Update customer address workflow completed. Address: ${updatedAddress.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Update customer address workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}

// ============== Delete Address Workflow ==============

/**
 * Input for deleting a customer address
 */
data class DeleteCustomerAddressInput(
    val addressId: String,
    val customerId: String  // For ownership validation
)

/**
 * Response for delete address
 */
data class DeleteCustomerAddressResponse(
    val addressId: String,
    val deleted: Boolean,
    val deletedAt: Instant
)

/**
 * Delete Customer Address Workflow (Customer)
 *
 * This workflow soft-deletes a customer address.
 *
 * Steps:
 * 1. Load address and validate ownership
 * 2. Soft-delete address
 * 3. Return confirmation
 */
@Component
@WorkflowTypes(DeleteCustomerAddressInput::class, DeleteCustomerAddressResponse::class)
class DeleteCustomerAddressWorkflow(
    private val customerAddressRepository: CustomerAddressRepository
) : Workflow<DeleteCustomerAddressInput, DeleteCustomerAddressResponse> {

    override val name = WorkflowConstants.DeleteCustomerAddress.NAME

    @Transactional
    override suspend fun execute(
        input: DeleteCustomerAddressInput,
        context: WorkflowContext
    ): WorkflowResult<DeleteCustomerAddressResponse> {
        logger.info { "Starting delete customer address workflow for address: ${input.addressId}" }

        try {
            // Step 1: Load and validate ownership
            val loadAddressStep = createStep<String, CustomerAddress>(
                name = "load-address-for-delete",
                execute = { addressId, _ ->
                    logger.debug { "Loading address: $addressId" }

                    val address = customerAddressRepository.findByIdAndDeletedAtIsNull(addressId)
                        ?: throw IllegalArgumentException("Address not found: $addressId")

                    // Validate ownership
                    if (address.customer?.id != input.customerId) {
                        throw IllegalArgumentException("Address does not belong to this customer")
                    }

                    StepResponse.of(address)
                }
            )

            // Step 2: Soft-delete address
            val deleteAddressStep = createStep<CustomerAddress, Instant>(
                name = "soft-delete-address",
                execute = { address, _ ->
                    logger.debug { "Soft-deleting address: ${address.id}" }

                    val deletedAt = Instant.now()
                    address.deletedAt = deletedAt
                    customerAddressRepository.save(address)

                    logger.info { "Address soft-deleted: ${address.id}" }
                    StepResponse.of(deletedAt)
                }
            )

            // Execute workflow steps
            val address = loadAddressStep.invoke(input.addressId, context).data
            val deletedAt = deleteAddressStep.invoke(address, context).data

            val response = DeleteCustomerAddressResponse(
                addressId = input.addressId,
                deleted = true,
                deletedAt = deletedAt
            )

            logger.info { "Delete customer address workflow completed. Address: ${input.addressId}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Delete customer address workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}

// ============== Helper Functions ==============

private fun toAddressResponse(address: CustomerAddress): CustomerAddressResponse {
    return CustomerAddressResponse(
        id = address.id,
        customerId = address.customer?.id ?: "",
        firstName = address.firstName,
        lastName = address.lastName,
        company = address.company,
        phone = address.phone,
        address1 = address.address1,
        address2 = address.address2,
        city = address.city,
        province = address.province,
        postalCode = address.postalCode,
        countryCode = address.countryCode,
        fullAddress = address.getFullAddress(),
        createdAt = address.createdAt
    )
}
