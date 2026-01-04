package com.vernont.application.customer

import com.vernont.domain.customer.dto.*
import com.vernont.domain.customer.Customer
import com.vernont.domain.customer.CustomerAddress
import com.vernont.events.CustomerRegistered
import com.vernont.events.CustomerUpdated
import com.vernont.events.EventPublisher
import com.vernont.repository.customer.CustomerRepository
import com.vernont.repository.customer.CustomerAddressRepository
import com.vernont.repository.customer.CustomerGroupRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import jakarta.validation.Valid

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val customerAddressRepository: CustomerAddressRepository,
    private val customerGroupRepository: CustomerGroupRepository,
    private val eventPublisher: EventPublisher
) {

    /**
     * Register a new customer
     */
    fun registerCustomer(@Valid request: RegisterCustomerRequest): CustomerResponse {
        logger.info { "Registering new customer: ${request.email}" }

        // Check if email already exists
        if (customerRepository.existsByEmail(request.email)) {
            throw CustomerEmailAlreadyExistsException("Customer with email ${request.email} already exists")
        }

        val customer = Customer().apply {
            email = request.email
            firstName = request.firstName
            lastName = request.lastName
            phone = request.phone
            hasAccount = request.hasAccount
        }

        val saved = customerRepository.save(customer)

        eventPublisher.publish(
            CustomerRegistered(
                aggregateId = saved.id,
                email = saved.email!!,
                firstName = saved.firstName!!,
                lastName = saved.lastName!!,
                phone = saved.phone
            )
        )

        logger.info { "Customer registered successfully: ${saved.id}" }
        return CustomerResponse.from(saved)
    }

    /**
     * Update customer profile
     */
    fun updateCustomer(customerId: String, @Valid request: UpdateCustomerRequest): CustomerResponse {
        logger.info { "Updating customer: $customerId" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        customer.apply {
            request.firstName?.let { firstName = it }
            request.lastName?.let { lastName = it }
            request.phone?.let { phone = it }
        }

        val updated = customerRepository.save(customer)

        eventPublisher.publish(
            CustomerUpdated(
                aggregateId = updated.id,
                email = updated.getEffectiveEmail(),
                firstName = updated.getEffectiveFirstName() ?: "",
                lastName = updated.getEffectiveLastName() ?: "",
                phone = updated.phone,
                address = null,
                isActive = updated.deletedAt == null
            )
        )

        logger.info { "Customer updated successfully: $customerId" }
        return CustomerResponse.from(updated)
    }

    /**
     * Get customer by ID
     */
    @Transactional(readOnly = true)
    fun getCustomer(customerId: String): CustomerResponse {
        val customer = customerRepository.findWithAddressesByIdAndDeletedAtIsNull(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        return CustomerResponse.from(customer)
    }

    /**
     * Get customer by email
     */
    @Transactional(readOnly = true)
    fun getCustomerByEmail(email: String): CustomerResponse {
        val customer = customerRepository.findWithFullDetailsByEmail(email)
            ?: throw CustomerNotFoundException("Customer not found with email: $email")

        return CustomerResponse.from(customer)
    }

    /**
     * Search customers
     */
    @Transactional(readOnly = true)
    fun searchCustomers(searchTerm: String): List<CustomerSummaryResponse> {
        return customerRepository.searchCustomers(searchTerm)
            .map { CustomerSummaryResponse.from(it) }
    }

    /**
     * List customers with pagination
     */
    @Transactional(readOnly = true)
    fun listCustomers(pageable: Pageable): Page<CustomerSummaryResponse> {
        return customerRepository.findAll(pageable)
            .map { CustomerSummaryResponse.from(it) }
    }

    /**
     * Delete customer (soft delete)
     */
    fun deleteCustomer(customerId: String) {
        logger.info { "Deleting customer: $customerId" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        customer.softDelete()
        customerRepository.save(customer)

        logger.info { "Customer deleted successfully: $customerId" }
    }

    /**
     * Add address to customer
     */
    fun addAddress(customerId: String, @Valid request: CreateAddressRequest): CustomerAddressResponse {
        logger.info { "Adding address to customer: $customerId" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        val address = CustomerAddress().apply {
            this.customer = customer
            firstName = request.firstName
            lastName = request.lastName
            company = request.company
            phone = request.phone
            address1 = request.address1
            address2 = request.address2
            city = request.city
            province = request.province
            postalCode = request.postalCode
            countryCode = request.countryCode
        }

        val saved = customerAddressRepository.save(address)

        logger.info { "Address added successfully: ${saved.id}" }
        return CustomerAddressResponse.from(saved)
    }

    /**
     * Update customer address
     */
    fun updateAddress(customerId: String, addressId: String, @Valid request: UpdateAddressRequest): CustomerAddressResponse {
        logger.info { "Updating address: $addressId for customer: $customerId" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        val address = customerAddressRepository.findByIdAndDeletedAtIsNull(addressId)
            ?: throw CustomerAddressNotFoundException("Address not found: $addressId")

        // Verify address belongs to customer
        if (address.customer?.id != customerId) {
            throw CustomerAddressNotFoundException("Address does not belong to customer: $customerId")
        }

        address.apply {
            request.firstName?.let { firstName = it }
            request.lastName?.let { lastName = it }
            request.company?.let { company = it }
            request.phone?.let { phone = it }
            request.address1?.let { address1 = it }
            request.address2?.let { address2 = it }
            request.city?.let { city = it }
            request.province?.let { province = it }
            request.postalCode?.let { postalCode = it }
            request.countryCode?.let { countryCode = it }
        }

        val updated = customerAddressRepository.save(address)

        logger.info { "Address updated successfully: $addressId" }
        return CustomerAddressResponse.from(updated)
    }

    /**
     * Remove customer address
     */
    fun removeAddress(customerId: String, addressId: String) {
        logger.info { "Removing address: $addressId from customer: $customerId" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        val address = customerAddressRepository.findByIdAndDeletedAtIsNull(addressId)
            ?: throw CustomerAddressNotFoundException("Address not found: $addressId")

        // Verify address belongs to customer
        if (address.customer?.id != customerId) {
            throw CustomerAddressNotFoundException("Address does not belong to customer: $customerId")
        }

        // Check if this is the billing address
        if (customer.billingAddressId == addressId) {
            customer.billingAddressId = null
            customerRepository.save(customer)
        }

        address.softDelete()
        customerAddressRepository.save(address)

        logger.info { "Address removed successfully: $addressId" }
    }

    /**
     * Set billing address
     */
    fun setBillingAddress(customerId: String, addressId: String): CustomerResponse {
        logger.info { "Setting billing address: $addressId for customer: $customerId" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        val address = customerAddressRepository.findByIdAndDeletedAtIsNull(addressId)
            ?: throw CustomerAddressNotFoundException("Address not found: $addressId")

        // Verify address belongs to customer
        if (address.customer?.id != customerId) {
            throw CustomerAddressNotFoundException("Address does not belong to customer: $customerId")
        }

        customer.billingAddressId = addressId
        val updated = customerRepository.save(customer)

        logger.info { "Billing address set successfully: $addressId" }
        return CustomerResponse.from(updated)
    }

    /**
     * Get customer addresses
     */
    @Transactional(readOnly = true)
    fun getCustomerAddresses(customerId: String): List<CustomerAddressResponse> {
        val customer = customerRepository.findByIdAndDeletedAtIsNull(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        return customerAddressRepository.findByCustomerIdAndDeletedAtIsNull(customerId)
            .map { CustomerAddressResponse.from(it) }
    }

    /**
     * Add customer to group
     */
    fun addToGroup(customerId: String, groupId: String): CustomerResponse {
        logger.info { "Adding customer: $customerId to group: $groupId" }

        val customer = customerRepository.findWithGroupsById(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        val group = customerGroupRepository.findById(groupId)
            .orElseThrow { CustomerGroupNotFoundException("Customer group not found: $groupId") }

        if (!customer.groups.contains(group)) {
            customer.groups.add(group)
            customerRepository.save(customer)
        }

        logger.info { "Customer added to group successfully: $customerId -> $groupId" }
        return CustomerResponse.from(customer)
    }

    /**
     * Remove customer from group
     */
    fun removeFromGroup(customerId: String, groupId: String): CustomerResponse {
        logger.info { "Removing customer: $customerId from group: $groupId" }

        val customer = customerRepository.findWithGroupsById(customerId)
            ?: throw CustomerNotFoundException("Customer not found: $customerId")

        val group = customerGroupRepository.findById(groupId)
            .orElseThrow { CustomerGroupNotFoundException("Customer group not found: $groupId") }

        customer.groups.remove(group)
        val updated = customerRepository.save(customer)

        logger.info { "Customer removed from group successfully: $customerId -> $groupId" }
        return CustomerResponse.from(updated)
    }

    /**
     * Create customer record for a user who just registered
     */
    fun createCustomerForUser(user: com.vernont.domain.auth.User): Customer {
        logger.info { "Creating customer record for user: ${user.id}" }

        val customer = Customer().apply {
            this.user = user
            email = user.email
            firstName = user.firstName
            lastName = user.lastName
            hasAccount = true
        }

        val saved = customerRepository.save(customer)

        eventPublisher.publish(
            CustomerRegistered(
                aggregateId = saved.id,
                email = saved.email!!,
                firstName = saved.firstName ?: "",
                lastName = saved.lastName ?: "",
                phone = saved.phone
            )
        )

        logger.info { "Customer record created successfully for user: ${user.id}, customerId: ${saved.id}" }
        return saved
    }
}

// Exception classes
class CustomerNotFoundException(message: String) : RuntimeException(message)
class CustomerEmailAlreadyExistsException(message: String) : RuntimeException(message)
class CustomerAddressNotFoundException(message: String) : RuntimeException(message)
class CustomerGroupNotFoundException(message: String) : RuntimeException(message)