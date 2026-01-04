package com.vernont.api.controller.store

import com.vernont.domain.auth.UserContext
import com.vernont.domain.auth.getCurrentUserContext
import com.vernont.api.dto.store.*
import com.vernont.api.toOffsetDateTime
import com.vernont.application.customer.CustomerService
import com.vernont.application.order.OrderService
import com.vernont.domain.customer.dto.CreateAddressRequest
import com.vernont.domain.customer.dto.RegisterCustomerRequest
import com.vernont.domain.customer.dto.UpdateAddressRequest
import com.vernont.domain.customer.dto.UpdateCustomerRequest
import com.vernont.domain.customer.dto.CustomerResponse
import com.vernont.domain.customer.dto.CustomerAddressResponse
import com.vernont.workflow.engine.WorkflowEngine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/store/customers")
@Tag(name = "Store Customers", description = "Customer management endpoints")
class CustomerController(
    private val customerService: CustomerService,
    private val orderService: OrderService,
    private val workflowEngine: WorkflowEngine
) {

    @Operation(summary = "Create/Update Customer Profile")
    @PostMapping
    fun createCustomer(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestBody req: StoreRegisterCustomerRequest
    ): ResponseEntity<StoreCustomerResponse> {
        logger.info { "POST /store/customers called with email: ${req.email}" }

        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        // Try to get existing customer (should exist from registration)
        val existingCustomer = try {
            customerService.getCustomerByEmail(context.email)
        } catch (e: Exception) {
            logger.debug { "Customer not found for email: ${context.email}, will return existing profile" }
            null
        }

        if (existingCustomer != null) {
            // Customer already exists (normal case), update with additional info if provided
            if (req.firstName != null || req.lastName != null || req.phone != null) {
                val updateRequest = UpdateCustomerRequest(
                    firstName = req.firstName,
                    lastName = req.lastName,
                    phone = req.phone
                )
                val updated = customerService.updateCustomer(existingCustomer.id, updateRequest)
                logger.info { "Updated existing customer profile: ${updated.id}" }
                return ResponseEntity.ok(StoreCustomerResponse(mapToStoreCustomer(updated)))
            } else {
                // No updates needed, return existing customer
                logger.info { "Returning existing customer profile: ${existingCustomer.id}" }
                return ResponseEntity.ok(StoreCustomerResponse(mapToStoreCustomer(existingCustomer)))
            }
        } else {
            // Customer doesn't exist (shouldn't happen, but return profile anyway)
            logger.warn { "Customer profile not found for authenticated user: ${context.email}" }
            return ResponseEntity.status(404).build()
        }
    }

    @Operation(summary = "Get Logged-in Customer")
    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestParam(required = false) fields: String?
    ): ResponseEntity<StoreCustomerResponse> {
        logger.info { "GET /store/customers/me called" }
        logger.info { "UserContext from @AuthenticationPrincipal: $userContext" }

        // Use UserContext instead of Jwt
        val context = userContext ?: getCurrentUserContext()

        logger.info { "UserContext after getCurrentUserContext(): $context" }

        if (context == null) {
            logger.warn { "No authentication context found, returning 401" }
            return ResponseEntity.status(401).build()
        }

        logger.info { "Fetching customer for email: ${context.email}" }
        val customer = customerService.getCustomerByEmail(context.email)

        val includeOrders = fields?.contains("orders") == true
        val orders = if (includeOrders) {
            val pageable = org.springframework.data.domain.PageRequest.of(0, 100)
            orderService.listCustomerOrders(customer.id, pageable).content.map { order ->
                StoreOrderSummary(
                    id = order.id,
                    displayId = order.displayId,
                    status = order.status.name.lowercase(),
                    fulfillmentStatus = order.fulfillmentStatus.name.lowercase().replace("_", " "),
                    paymentStatus = order.paymentStatus.name.lowercase().replace("_", " "),
                    total = order.total.multiply(java.math.BigDecimal(100)).toInt(),
                    currencyCode = order.currencyCode,
                    createdAt = order.createdAt.atOffset(ZoneOffset.UTC),
                    updatedAt = order.updatedAt.atOffset(ZoneOffset.UTC)
                )
            }
        } else null

        // Always include addresses by default (or check fields parameter)
        val addresses = customerService.getCustomerAddresses(customer.id).map { address ->
            mapToStoreAddress(address, customer.id)
        }

        return ResponseEntity.ok(StoreCustomerResponse(mapToStoreCustomer(customer, orders, addresses)))
    }

    @Operation(summary = "Update Customer")
    @PostMapping("/me")
    fun updateMe(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestBody req: StorePostCustomerReq
    ): ResponseEntity<StoreCustomerResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val currentCustomer = customerService.getCustomerByEmail(context.email)
        
        val updateRequest = UpdateCustomerRequest(
            firstName = req.firstName,
            lastName = req.lastName,
            phone = req.phone
        )
        
        val updated = customerService.updateCustomer(currentCustomer.id, updateRequest)
        return ResponseEntity.ok(StoreCustomerResponse(mapToStoreCustomer(updated)))
    }

    @Operation(summary = "List Addresses")
    @GetMapping("/me/addresses")
    fun listAddresses(@AuthenticationPrincipal userContext: UserContext?): ResponseEntity<StoreCustomerAddressesResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val customer = customerService.getCustomerByEmail(context.email)
        val addresses = customerService.getCustomerAddresses(customer.id)
        
        val storeAddresses = addresses.map { address ->
            StoreCustomerAddress(
                id = address.id,
                customerId = customer.id,
                company = address.company,
                firstName = address.firstName,
                lastName = address.lastName,
                address1 = address.address1,
                address2 = address.address2,
                city = address.city,
                countryCode = address.countryCode,
                province = address.province,
                postalCode = address.postalCode,
                phone = address.phone,
                createdAt = address.createdAt.toOffsetDateTime(),
                updatedAt = address.updatedAt.toOffsetDateTime(),
                deletedAt = null,
                metadata = null
            )
        }
        
        return ResponseEntity.ok(StoreCustomerAddressesResponse(
            addresses = storeAddresses,
            count = storeAddresses.size,
            offset = 0,
            limit = storeAddresses.size
        ))
    }

    @Operation(summary = "Create Address")
    @PostMapping("/me/addresses")
    fun createAddress(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestBody req: StorePostCustomerAddressReq
    ): ResponseEntity<StoreCustomerResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val customer = customerService.getCustomerByEmail(context.email)
        
        val createRequest = CreateAddressRequest(
            firstName = req.firstName ?: "",
            lastName = req.lastName ?: "",
            company = req.company,
            address1 = req.address1 ?: "",
            address2 = req.address2,
            city = req.city ?: "",
            countryCode = req.countryCode ?: "",
            province = req.province ?: "",
            postalCode = req.postalCode ?: "",
            phone = req.phone ?: ""
        )
        
        customerService.addAddress(customer.id, createRequest)

        // Return updated customer
        return getMe(userContext, null)
    }

    @Operation(summary = "Update Address")
    @PostMapping("/me/addresses/{address_id}")
    fun updateAddress(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable("address_id") addressId: String,
        @RequestBody req: StorePostCustomerAddressReq
    ): ResponseEntity<StoreCustomerResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val customer = customerService.getCustomerByEmail(context.email)
        
        val updateRequest = UpdateAddressRequest(
            firstName = req.firstName,
            lastName = req.lastName,
            company = req.company,
            address1 = req.address1,
            address2 = req.address2,
            city = req.city,
            countryCode = req.countryCode,
            province = req.province,
            postalCode = req.postalCode,
            phone = req.phone
        )
        
        customerService.updateAddress(customer.id, addressId, updateRequest)

        return getMe(userContext, null)
    }

    @Operation(summary = "Delete Address")
    @DeleteMapping("/me/addresses/{address_id}")
    fun deleteAddress(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable("address_id") addressId: String
    ): ResponseEntity<StoreCustomerAddressResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val customer = customerService.getCustomerByEmail(context.email)
        
        // Fetch address before deleting to return it (simulated since service doesn't return it on delete)
        // In a real scenario we might want to change service to return deleted item or fetch first
        // For now, let's just fetch it first.
        val addresses = customerService.getCustomerAddresses(customer.id)
        val addressToDelete = addresses.find { it.id == addressId }
        
        customerService.removeAddress(customer.id, addressId)
        
        return if (addressToDelete != null) {
            ResponseEntity.ok(StoreCustomerAddressResponse(mapToStoreAddress(addressToDelete, customer.id)))
        } else {
            ResponseEntity.ok().build()
        }
    }

    private fun mapToStoreCustomer(
        customer: CustomerResponse,
        orders: List<StoreOrderSummary>? = null,
        addresses: List<StoreCustomerAddress>? = null
    ): StoreCustomer {
        return StoreCustomer(
            id = customer.id,
            email = customer.email,
            firstName = customer.firstName,
            lastName = customer.lastName,
            billingAddressId = customer.billingAddressId,
            phone = customer.phone,
            hasAccount = customer.hasAccount,
            orders = orders,
            addresses = addresses,
            createdAt = customer.createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = customer.updatedAt.atOffset(ZoneOffset.UTC),
            deletedAt = null,
            metadata = null
        )
    }

    private fun mapToStoreAddress(address: CustomerAddressResponse, customerId: String): StoreCustomerAddress {
        return StoreCustomerAddress(
            id = address.id,
            customerId = customerId,
            company = address.company,
            firstName = address.firstName,
            lastName = address.lastName,
            address1 = address.address1,
            address2 = address.address2,
            city = address.city,
            countryCode = address.countryCode,
            province = address.province,
            postalCode = address.postalCode,
            phone = address.phone,
            createdAt = address.createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = address.updatedAt.atOffset(ZoneOffset.UTC),
            deletedAt = null,
            metadata = null
        )
    }
}
