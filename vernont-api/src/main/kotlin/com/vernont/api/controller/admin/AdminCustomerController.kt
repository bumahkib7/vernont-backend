package com.vernont.api.controller.admin

import com.vernont.api.dto.admin.*
import com.vernont.domain.customer.*
import com.vernont.repository.customer.CustomerActivityLogRepository
import com.vernont.repository.customer.CustomerGroupRepository
import com.vernont.repository.customer.CustomerRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.flows.customer.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/customers")
@Tag(name = "Admin Customers", description = "Customer management endpoints")
class AdminCustomerController(
    private val customerRepository: CustomerRepository,
    private val customerGroupRepository: CustomerGroupRepository,
    private val customerActivityLogRepository: CustomerActivityLogRepository,
    private val orderRepository: OrderRepository,
    private val workflowEngine: WorkflowEngine
) {

    // =========================================================================
    // Customer CRUD
    // =========================================================================

    @Operation(summary = "List customers with filters")
    @GetMapping
    fun listCustomers(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) tier: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) groupId: String?,
        @RequestParam(required = false) hasAccount: Boolean?
    ): ResponseEntity<AdminCustomersResponse> {
        logger.info { "GET /admin/customers - limit=$limit, offset=$offset, q=$q, tier=$tier, status=$status" }

        // Get all customers with filters
        var customers = customerRepository.findByDeletedAtIsNull()

        // Apply search filter
        if (!q.isNullOrBlank()) {
            val searchTerm = q.lowercase()
            customers = customers.filter { customer ->
                customer.getEffectiveEmail().lowercase().contains(searchTerm) ||
                customer.getFullName().lowercase().contains(searchTerm) ||
                customer.phone?.lowercase()?.contains(searchTerm) == true
            }
        }

        // Apply tier filter
        if (!tier.isNullOrBlank()) {
            val tierEnum = try {
                CustomerTier.valueOf(tier.uppercase())
            } catch (e: IllegalArgumentException) { null }
            if (tierEnum != null) {
                customers = customers.filter { it.tier == tierEnum }
            }
        }

        // Apply status filter
        if (!status.isNullOrBlank()) {
            val statusEnum = try {
                CustomerStatus.valueOf(status.uppercase())
            } catch (e: IllegalArgumentException) { null }
            if (statusEnum != null) {
                customers = customers.filter { it.status == statusEnum }
            }
        }

        // Apply group filter
        if (!groupId.isNullOrBlank()) {
            customers = customers.filter { customer ->
                customer.groups.any { it.id == groupId }
            }
        }

        // Apply hasAccount filter
        if (hasAccount != null) {
            customers = customers.filter { it.hasAccount == hasAccount }
        }

        // Sort by most recent first
        customers = customers.sortedByDescending { it.createdAt }

        val count = customers.size.toLong()
        val paginatedCustomers = customers.drop(offset).take(limit.coerceAtMost(100))

        return ResponseEntity.ok(AdminCustomersResponse(
            customers = paginatedCustomers.map { AdminCustomerSummary.from(it) },
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get customer by ID")
    @GetMapping("/{id}")
    fun getCustomer(@PathVariable id: String): ResponseEntity<AdminCustomerResponse> {
        val customer = customerRepository.findWithFullDetailsByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(AdminCustomerResponse(customer = AdminCustomer.from(customer)))
    }

    @Operation(summary = "Create customer")
    @PostMapping
    @Transactional
    fun createCustomer(@RequestBody request: CreateCustomerRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/customers - email=${request.email}" }

        // Check if email already exists
        if (customerRepository.existsByEmail(request.email)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Customer with this email already exists"
            ))
        }

        val customer = Customer().apply {
            email = request.email
            firstName = request.firstName
            lastName = request.lastName
            phone = request.phone
            hasAccount = false
            internalNotes = request.internalNotes

            // Set tier if specified
            if (!request.tier.isNullOrBlank()) {
                try {
                    tier = CustomerTier.valueOf(request.tier.uppercase())
                    tierOverride = true
                } catch (e: IllegalArgumentException) {
                    // Invalid tier, use default
                }
            }
        }

        val savedCustomer = customerRepository.save(customer)

        // Add to groups if specified
        request.groupIds?.forEach { groupId ->
            customerGroupRepository.findByIdAndDeletedAtIsNull(groupId)?.let { group ->
                savedCustomer.addToGroup(group)
            }
        }

        customerRepository.save(savedCustomer)

        // Log activity
        customerActivityLogRepository.save(
            CustomerActivityLog.accountCreated(savedCustomer.id, "admin")
        )

        return ResponseEntity.status(201).body(AdminCustomerResponse(
            customer = AdminCustomer.from(savedCustomer)
        ))
    }

    @Operation(summary = "Update customer")
    @PutMapping("/{id}")
    @Transactional
    fun updateCustomer(
        @PathVariable id: String,
        @RequestBody request: UpdateCustomerRequest
    ): ResponseEntity<Any> {
        logger.info { "PUT /admin/customers/$id" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Update fields (only for guest customers, registered customers update via User)
        if (!customer.hasAccount) {
            request.firstName?.let { customer.firstName = it }
            request.lastName?.let { customer.lastName = it }
        }
        request.phone?.let { customer.phone = it }
        request.internalNotes?.let { customer.internalNotes = it }

        val savedCustomer = customerRepository.save(customer)

        // Log activity
        customerActivityLogRepository.save(CustomerActivityLog.create(
            customerId = id,
            activityType = CustomerActivityType.PROFILE_UPDATED,
            description = "Customer profile updated by admin",
            performedBy = "admin"
        ))

        return ResponseEntity.ok(AdminCustomerResponse(customer = AdminCustomer.from(savedCustomer)))
    }

    @Operation(summary = "Delete customer")
    @DeleteMapping("/{id}")
    @Transactional
    fun deleteCustomer(@PathVariable id: String): ResponseEntity<Any> {
        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        customer.deletedAt = Instant.now()
        customerRepository.save(customer)

        logger.info { "Deleted customer: $id" }
        return ResponseEntity.ok(mapOf("message" to "Customer deleted", "id" to id))
    }

    // =========================================================================
    // Customer Actions
    // =========================================================================

    @Operation(summary = "Send email to customer")
    @PostMapping("/{id}/send-email")
    suspend fun sendEmail(
        @PathVariable id: String,
        @RequestBody request: SendEmailRequest
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/customers/$id/send-email - subject=${request.subject}" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val input = SendCustomerEmailInput(
            customerId = id,
            email = customer.getEffectiveEmail(),
            subject = request.subject,
            body = request.body
        )

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.Customer.SEND_EMAIL,
            input = input,
            inputType = SendCustomerEmailInput::class,
            outputType = SendCustomerEmailOutput::class,
            context = WorkflowContext()
        )

        return when (result) {
            is WorkflowResult.Success -> {
                // Log activity
                customerActivityLogRepository.save(
                    CustomerActivityLog.emailSent(id, request.subject, "admin")
                )
                ResponseEntity.ok(mapOf("message" to "Email sent successfully"))
            }
            is WorkflowResult.Failure -> {
                ResponseEntity.badRequest().body(mapOf(
                    "message" to "Failed to send email",
                    "error" to (result.error.message ?: "Unknown error")
                ))
            }
        }
    }

    @Operation(summary = "Send gift card to customer")
    @PostMapping("/{id}/send-gift-card")
    suspend fun sendGiftCard(
        @PathVariable id: String,
        @RequestBody request: SendGiftCardRequest
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/customers/$id/send-gift-card - amount=${request.amount}" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val input = SendGiftCardInput(
            customerId = id,
            customerEmail = customer.getEffectiveEmail(),
            customerName = customer.getFullName(),
            amount = request.amount,
            currencyCode = request.currencyCode,
            message = request.message
        )

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.Customer.SEND_GIFT_CARD,
            input = input,
            inputType = SendGiftCardInput::class,
            outputType = SendGiftCardOutput::class,
            context = WorkflowContext()
        )

        return when (result) {
            is WorkflowResult.Success -> {
                val data = result.data
                // Log activity
                customerActivityLogRepository.save(
                    CustomerActivityLog.giftCardSent(id, request.amount, data.giftCardId, "admin")
                )
                ResponseEntity.ok(mapOf(
                    "message" to "Gift card sent successfully",
                    "gift_card_id" to data.giftCardId,
                    "gift_card_code" to data.giftCardCode
                ))
            }
            is WorkflowResult.Failure -> {
                ResponseEntity.badRequest().body(mapOf(
                    "message" to "Failed to send gift card",
                    "error" to (result.error.message ?: "Unknown error")
                ))
            }
        }
    }

    @Operation(summary = "Change customer tier")
    @PostMapping("/{id}/change-tier")
    @Transactional
    fun changeTier(
        @PathVariable id: String,
        @RequestBody request: ChangeTierRequest
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/customers/$id/change-tier - tier=${request.tier}" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val newTier = try {
            CustomerTier.valueOf(request.tier.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Invalid tier",
                "valid_tiers" to CustomerTier.entries.map { it.name }
            ))
        }

        val previousTier = customer.tier
        customer.setTierManually(newTier)
        customerRepository.save(customer)

        // Log activity
        customerActivityLogRepository.save(
            CustomerActivityLog.tierChanged(id, previousTier, newTier, request.reason, "admin")
        )

        return ResponseEntity.ok(mapOf(
            "message" to "Tier changed successfully",
            "previous_tier" to previousTier.name,
            "new_tier" to newTier.name
        ))
    }

    @Operation(summary = "Suspend customer account")
    @PostMapping("/{id}/suspend")
    @Transactional
    fun suspendCustomer(
        @PathVariable id: String,
        @RequestBody request: SuspendCustomerRequest
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/customers/$id/suspend" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        if (customer.status == CustomerStatus.SUSPENDED) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Customer is already suspended"
            ))
        }

        customer.suspend(request.reason)
        customerRepository.save(customer)

        // Log activity
        customerActivityLogRepository.save(
            CustomerActivityLog.accountSuspended(id, request.reason, "admin")
        )

        return ResponseEntity.ok(mapOf(
            "message" to "Customer suspended",
            "suspended_at" to customer.suspendedAt.toString()
        ))
    }

    @Operation(summary = "Activate customer account")
    @PostMapping("/{id}/activate")
    @Transactional
    fun activateCustomer(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "POST /admin/customers/$id/activate" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        if (customer.status == CustomerStatus.ACTIVE) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Customer is already active"
            ))
        }

        customer.activate()
        customerRepository.save(customer)

        // Log activity
        customerActivityLogRepository.save(
            CustomerActivityLog.accountActivated(id, "admin")
        )

        return ResponseEntity.ok(mapOf("message" to "Customer activated"))
    }

    @Operation(summary = "Trigger password reset email")
    @PostMapping("/{id}/reset-password")
    suspend fun resetPassword(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "POST /admin/customers/$id/reset-password" }

        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        if (!customer.hasAccount) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Customer does not have an account"
            ))
        }

        val input = ResetCustomerPasswordInput(
            customerId = id,
            email = customer.getEffectiveEmail()
        )

        val result = workflowEngine.execute(
            workflowName = WorkflowConstants.Customer.RESET_PASSWORD,
            input = input,
            inputType = ResetCustomerPasswordInput::class,
            outputType = ResetCustomerPasswordOutput::class,
            context = WorkflowContext()
        )

        return when (result) {
            is WorkflowResult.Success -> {
                // Log activity
                customerActivityLogRepository.save(
                    CustomerActivityLog.passwordResetRequested(id, "admin")
                )
                ResponseEntity.ok(mapOf("message" to "Password reset email sent"))
            }
            is WorkflowResult.Failure -> {
                ResponseEntity.badRequest().body(mapOf(
                    "message" to "Failed to send password reset email",
                    "error" to (result.error.message ?: "Unknown error")
                ))
            }
        }
    }

    // =========================================================================
    // Customer Orders & Activity
    // =========================================================================

    @Operation(summary = "Get customer orders")
    @GetMapping("/{id}/orders")
    fun getCustomerOrders(
        @PathVariable id: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<Any> {
        val customer = customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtMost(100))
        val orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(id, pageable)

        return ResponseEntity.ok(mapOf(
            "orders" to orders.content,
            "count" to orders.totalElements,
            "offset" to offset,
            "limit" to limit
        ))
    }

    @Operation(summary = "Get customer activity log")
    @GetMapping("/{id}/activity")
    fun getCustomerActivity(
        @PathVariable id: String,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) activityType: String?
    ): ResponseEntity<AdminCustomerActivitiesResponse> {
        customerRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtMost(100))

        val activityTypeEnum = activityType?.let {
            try { CustomerActivityType.valueOf(it.uppercase()) } catch (e: IllegalArgumentException) { null }
        }

        val activities = customerActivityLogRepository.findByCustomerIdAndOptionalType(
            customerId = id,
            activityType = activityTypeEnum,
            pageable = pageable
        )

        return ResponseEntity.ok(AdminCustomerActivitiesResponse(
            activities = activities.content.map { AdminCustomerActivity.from(it) },
            count = activities.totalElements,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get customer addresses")
    @GetMapping("/{id}/addresses")
    fun getCustomerAddresses(@PathVariable id: String): ResponseEntity<AdminCustomerAddressesResponse> {
        val customer = customerRepository.findWithAddressesByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val addresses = customer.addresses
            .filter { it.deletedAt == null }
            .map { AdminCustomerAddress.from(it, customer.billingAddressId) }

        return ResponseEntity.ok(AdminCustomerAddressesResponse(addresses = addresses))
    }

    // =========================================================================
    // Customer Groups
    // =========================================================================

    @Operation(summary = "List customer groups")
    @GetMapping("/groups")
    fun listGroups(): ResponseEntity<AdminCustomerGroupsResponse> {
        val groups = customerGroupRepository.findByDeletedAtIsNull()
        return ResponseEntity.ok(AdminCustomerGroupsResponse(
            groups = groups.map { AdminCustomerGroup.from(it) },
            count = groups.size
        ))
    }

    @Operation(summary = "Get customer group by ID")
    @GetMapping("/groups/{groupId}")
    fun getGroup(@PathVariable groupId: String): ResponseEntity<AdminCustomerGroupResponse> {
        val group = customerGroupRepository.findByIdAndDeletedAtIsNull(groupId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(AdminCustomerGroupResponse(group = AdminCustomerGroup.from(group)))
    }

    @Operation(summary = "Create customer group")
    @PostMapping("/groups")
    @Transactional
    fun createGroup(@RequestBody request: CreateCustomerGroupRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/customers/groups - name=${request.name}" }

        if (customerGroupRepository.existsByName(request.name)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Group with this name already exists"
            ))
        }

        val group = CustomerGroup().apply {
            name = request.name
            description = request.description
        }

        val savedGroup = customerGroupRepository.save(group)
        return ResponseEntity.status(201).body(AdminCustomerGroupResponse(
            group = AdminCustomerGroup.from(savedGroup)
        ))
    }

    @Operation(summary = "Update customer group")
    @PutMapping("/groups/{groupId}")
    @Transactional
    fun updateGroup(
        @PathVariable groupId: String,
        @RequestBody request: UpdateCustomerGroupRequest
    ): ResponseEntity<Any> {
        val group = customerGroupRepository.findByIdAndDeletedAtIsNull(groupId)
            ?: return ResponseEntity.notFound().build()

        request.name?.let {
            if (customerGroupRepository.existsByNameAndIdNot(it, groupId)) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "Group with this name already exists"
                ))
            }
            group.name = it
        }
        request.description?.let { group.description = it }

        val savedGroup = customerGroupRepository.save(group)
        return ResponseEntity.ok(AdminCustomerGroupResponse(group = AdminCustomerGroup.from(savedGroup)))
    }

    @Operation(summary = "Delete customer group")
    @DeleteMapping("/groups/{groupId}")
    @Transactional
    fun deleteGroup(@PathVariable groupId: String): ResponseEntity<Any> {
        val group = customerGroupRepository.findByIdAndDeletedAtIsNull(groupId)
            ?: return ResponseEntity.notFound().build()

        group.deletedAt = Instant.now()
        customerGroupRepository.save(group)

        return ResponseEntity.ok(mapOf("message" to "Group deleted", "id" to groupId))
    }

    @Operation(summary = "Add customer to group")
    @PostMapping("/{id}/groups/{groupId}")
    @Transactional
    fun addToGroup(
        @PathVariable id: String,
        @PathVariable groupId: String
    ): ResponseEntity<Any> {
        val customer = customerRepository.findWithGroupsByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val group = customerGroupRepository.findByIdAndDeletedAtIsNull(groupId)
            ?: return ResponseEntity.notFound().build()

        if (customer.groups.any { it.id == groupId }) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Customer is already in this group"
            ))
        }

        customer.addToGroup(group)
        customerRepository.save(customer)

        // Log activity
        customerActivityLogRepository.save(
            CustomerActivityLog.addedToGroup(id, groupId, group.name, "admin")
        )

        return ResponseEntity.ok(mapOf("message" to "Customer added to group"))
    }

    @Operation(summary = "Remove customer from group")
    @DeleteMapping("/{id}/groups/{groupId}")
    @Transactional
    fun removeFromGroup(
        @PathVariable id: String,
        @PathVariable groupId: String
    ): ResponseEntity<Any> {
        val customer = customerRepository.findWithGroupsByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val group = customer.groups.find { it.id == groupId }
            ?: return ResponseEntity.badRequest().body(mapOf(
                "message" to "Customer is not in this group"
            ))

        customer.removeFromGroup(group)
        customerRepository.save(customer)

        // Log activity
        customerActivityLogRepository.save(
            CustomerActivityLog.removedFromGroup(id, groupId, group.name, "admin")
        )

        return ResponseEntity.ok(mapOf("message" to "Customer removed from group"))
    }

    // =========================================================================
    // Stats
    // =========================================================================

    @Operation(summary = "Get customer statistics")
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<CustomerStatsResponse> {
        val allCustomers = customerRepository.findByDeletedAtIsNull()

        val now = Instant.now()
        val startOfMonth = now.minus(30, ChronoUnit.DAYS)

        val stats = CustomerStats(
            totalCustomers = allCustomers.size.toLong(),
            activeCustomers = allCustomers.count { it.status == CustomerStatus.ACTIVE }.toLong(),
            suspendedCustomers = allCustomers.count { it.status == CustomerStatus.SUSPENDED }.toLong(),
            customersWithAccounts = allCustomers.count { it.hasAccount }.toLong(),
            vipCustomers = allCustomers.count { it.tier == CustomerTier.GOLD || it.tier == CustomerTier.PLATINUM }.toLong(),
            newThisMonth = allCustomers.count { it.createdAt.isAfter(startOfMonth) }.toLong()
        )

        return ResponseEntity.ok(CustomerStatsResponse(stats = stats))
    }

    @Operation(summary = "Get available tiers")
    @GetMapping("/tiers")
    fun getTiers(): ResponseEntity<Any> {
        val tiers = CustomerTier.entries.map { mapOf(
            "value" to it.name,
            "label" to it.displayName,
            "spend_threshold" to it.spendThreshold,
            "discount_percent" to it.discountPercent,
            "free_shipping" to it.freeShipping
        )}
        return ResponseEntity.ok(mapOf("tiers" to tiers))
    }

    @Operation(summary = "Get available statuses")
    @GetMapping("/statuses")
    fun getStatuses(): ResponseEntity<Any> {
        val statuses = CustomerStatus.entries.map { mapOf(
            "value" to it.name,
            "label" to it.displayName,
            "can_order" to it.canOrder
        )}
        return ResponseEntity.ok(mapOf("statuses" to statuses))
    }

    @Operation(summary = "Get activity types")
    @GetMapping("/activity-types")
    fun getActivityTypes(): ResponseEntity<Any> {
        val types = CustomerActivityType.entries.map { mapOf(
            "value" to it.name,
            "label" to it.displayName,
            "category" to it.category
        )}
        return ResponseEntity.ok(mapOf("activity_types" to types))
    }
}
