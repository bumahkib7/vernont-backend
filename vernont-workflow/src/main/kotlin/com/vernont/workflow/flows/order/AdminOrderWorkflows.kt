package com.vernont.workflow.flows.order

import com.vernont.domain.fulfillment.Fulfillment
import com.vernont.domain.fulfillment.FulfillmentItem
import com.vernont.domain.fulfillment.FulfillmentProvider
import com.vernont.domain.inventory.StockLocation
import com.vernont.domain.order.FulfillmentStatus
import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderStatus
import com.vernont.events.*
import com.vernont.application.order.OrderEventService
import com.vernont.application.shipping.CreateLabelRequest
import com.vernont.application.shipping.ShipEngineAddress
import com.vernont.application.shipping.ShipEngineLabelResult
import com.vernont.application.shipping.ShipEngineParcel
import com.vernont.application.shipping.ShipEngineService
import com.vernont.repository.fulfillment.FulfillmentProviderRepository
import com.vernont.repository.fulfillment.FulfillmentRepository
import com.vernont.repository.inventory.StockLocationRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.*
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

// ============================================================================
// INPUT/OUTPUT DTOs
// ============================================================================

/**
 * Input for fulfilling an order from admin
 */
data class FulfillOrderInput(
    val orderId: String,
    val locationId: String? = null,
    val providerId: String? = null,
    val noNotification: Boolean = false,
    val metadata: Map<String, Any>? = null
)

/**
 * Response after fulfilling an order
 */
data class FulfillOrderResponse(
    val orderId: String,
    val fulfillmentId: String,
    val fulfillmentStatus: String,
    val itemCount: Int,
    val message: String
)

/**
 * Input for shipping an order from admin
 */
data class ShipOrderInput(
    val orderId: String,
    val trackingNumber: String? = null,
    val carrier: String? = null,
    val trackingUrl: String? = null,
    val noNotification: Boolean = false,
    val metadata: Map<String, Any>? = null,
    // ShipEngine integration fields
    val useShipEngine: Boolean = false,
    val carrierId: String? = null,
    val serviceCode: String? = null,
    val packageWeight: Double? = null,
    val packageLength: Double? = null,
    val packageWidth: Double? = null,
    val packageHeight: Double? = null
)

/**
 * Response after shipping an order
 */
data class ShipOrderResponse(
    val orderId: String,
    val fulfillmentId: String,
    val fulfillmentStatus: String,
    val trackingNumbers: List<String>,
    val message: String,
    // ShipEngine integration fields
    val labelUrls: List<String> = emptyList(),
    val trackingUrls: List<String> = emptyList(),
    val shipEngineLabelId: String? = null,
    val carrier: String? = null,
    val shippingCost: String? = null
)

/**
 * Input for completing an order from admin
 */
data class CompleteOrderInput(
    val orderId: String,
    val metadata: Map<String, Any>? = null
)

/**
 * Response after completing an order
 */
data class CompleteOrderResponse(
    val orderId: String,
    val status: String,
    val message: String
)

// ============================================================================
// FULFILL ORDER WORKFLOW
// ============================================================================

/**
 * Admin Fulfill Order Workflow
 *
 * This workflow creates a fulfillment for an order, marking it as ready to ship.
 * It automatically fetches or creates the required stock location and fulfillment provider.
 *
 * Steps:
 * 1. Load and validate order (must exist, not canceled, not already fulfilled)
 * 2. Get or create default stock location
 * 3. Get or create default fulfillment provider
 * 4. Create fulfillment with all order items
 * 5. Update order fulfillment status
 * 6. Emit fulfillment created event
 *
 * SAGA Compensation:
 * - If any step fails, previous steps are compensated
 * - Fulfillment is marked as canceled
 * - Order status is reverted
 */
@Component
@WorkflowTypes(input = FulfillOrderInput::class, output = FulfillOrderResponse::class)
class FulfillOrderWorkflow(
    private val orderRepository: OrderRepository,
    private val fulfillmentRepository: FulfillmentRepository,
    private val fulfillmentProviderRepository: FulfillmentProviderRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val eventPublisher: EventPublisher,
    private val orderEventService: OrderEventService
) : Workflow<FulfillOrderInput, FulfillOrderResponse> {

    override val name = WorkflowConstants.FulfillOrder.NAME

    companion object {
        const val DEFAULT_LOCATION_NAME = "Default Warehouse"
        const val DEFAULT_PROVIDER_NAME = "manual"
    }

    @Transactional
    override suspend fun execute(
        input: FulfillOrderInput,
        context: WorkflowContext
    ): WorkflowResult<FulfillOrderResponse> {
        logger.info { "Starting fulfill order workflow for order: ${input.orderId}" }

        try {
            // Step 1: Load and validate order
            val loadOrderStep = createStep<String, Order>(
                name = "load-and-validate-order",
                execute = { orderId, ctx ->
                    logger.debug { "Loading order: $orderId" }

                    val order = orderRepository.findWithItemsById(orderId)
                        ?: throw IllegalArgumentException("Order not found: $orderId")

                    // Validate order state
                    if (order.status == OrderStatus.CANCELED) {
                        throw IllegalStateException("Cannot fulfill canceled order: $orderId")
                    }

                    if (order.fulfillmentStatus == FulfillmentStatus.FULFILLED) {
                        throw IllegalStateException("Order is already fulfilled: $orderId")
                    }

                    if (order.fulfillmentStatus == FulfillmentStatus.SHIPPED) {
                        throw IllegalStateException("Order is already shipped: $orderId")
                    }

                    if (order.items.isEmpty()) {
                        throw IllegalStateException("Order has no items to fulfill: $orderId")
                    }

                    // Store original status for compensation
                    ctx.addMetadata("originalFulfillmentStatus", order.fulfillmentStatus)
                    ctx.addMetadata("order", order)

                    logger.info { "Order ${order.id} validated for fulfillment with ${order.items.size} items" }
                    StepResponse.of(order)
                }
            )

            // Step 2: Get or create stock location
            val getLocationStep = createStep<FulfillOrderInput, StockLocation>(
                name = "get-or-create-stock-location",
                execute = { inp, ctx ->
                    val location = if (inp.locationId != null) {
                        // Use specified location
                        stockLocationRepository.findByIdAndDeletedAtIsNull(inp.locationId)
                            ?: throw IllegalArgumentException("Stock location not found: ${inp.locationId}")
                    } else {
                        // Get or create default location
                        var defaultLocation = stockLocationRepository.findByNameAndDeletedAtIsNull(DEFAULT_LOCATION_NAME)
                        if (defaultLocation == null) {
                            logger.info { "Creating default stock location: $DEFAULT_LOCATION_NAME" }
                            defaultLocation = StockLocation()
                            defaultLocation.name = DEFAULT_LOCATION_NAME
                            defaultLocation.address = "Default Warehouse Address"
                            defaultLocation.fulfillmentEnabled = true
                            defaultLocation = stockLocationRepository.save(defaultLocation)
                            ctx.addMetadata("createdDefaultLocation", true)
                        }
                        defaultLocation
                    }

                    if (!location.fulfillmentEnabled) {
                        throw IllegalStateException("Stock location ${location.id} is not enabled for fulfillment")
                    }

                    ctx.addMetadata("stockLocation", location)
                    logger.info { "Using stock location: ${location.id} (${location.name})" }
                    StepResponse.of(location)
                }
            )

            // Step 3: Get or create fulfillment provider
            val getProviderStep = createStep<FulfillOrderInput, FulfillmentProvider>(
                name = "get-or-create-fulfillment-provider",
                execute = { inp, ctx ->
                    val provider = if (inp.providerId != null) {
                        // Use specified provider
                        fulfillmentProviderRepository.findByIdAndDeletedAtIsNull(inp.providerId)
                            ?: throw IllegalArgumentException("Fulfillment provider not found: ${inp.providerId}")
                    } else {
                        // Get or create default manual provider
                        var defaultProvider = fulfillmentProviderRepository.findByNameAndDeletedAtIsNull(DEFAULT_PROVIDER_NAME)
                        if (defaultProvider == null) {
                            logger.info { "Creating default fulfillment provider: $DEFAULT_PROVIDER_NAME" }
                            defaultProvider = FulfillmentProvider()
                            defaultProvider.name = DEFAULT_PROVIDER_NAME
                            defaultProvider.providerId = DEFAULT_PROVIDER_NAME
                            defaultProvider.isActive = true
                            defaultProvider = fulfillmentProviderRepository.save(defaultProvider)
                            ctx.addMetadata("createdDefaultProvider", true)
                        }
                        defaultProvider
                    }

                    if (!provider.isActive) {
                        throw IllegalStateException("Fulfillment provider ${provider.id} is not active")
                    }

                    ctx.addMetadata("fulfillmentProvider", provider)
                    logger.info { "Using fulfillment provider: ${provider.id} (${provider.name})" }
                    StepResponse.of(provider)
                }
            )

            // Step 4: Create fulfillment with all order items
            val createFulfillmentStep = createStep<Order, Fulfillment>(
                name = "create-fulfillment",
                execute = { order, ctx ->
                    val location = ctx.getMetadata("stockLocation") as StockLocation
                    val provider = ctx.getMetadata("fulfillmentProvider") as FulfillmentProvider

                    logger.debug { "Creating fulfillment for order: ${order.id}" }

                    val fulfillment = Fulfillment()
                    fulfillment.orderId = order.id
                    fulfillment.provider = provider
                    fulfillment.locationId = location.id
                    fulfillment.noNotification = input.noNotification

                    // Create fulfillment items from order items
                    order.items.filter { it.deletedAt == null }.forEach { orderItem ->
                        val fulfillmentItem = FulfillmentItem()
                        fulfillmentItem.title = orderItem.title
                        fulfillmentItem.sku = orderItem.variantId ?: ""
                        fulfillmentItem.quantity = orderItem.quantity
                        fulfillmentItem.lineItemId = orderItem.id

                        fulfillment.addItem(fulfillmentItem)
                    }

                    // Store metadata
                    input.metadata?.let { metadata ->
                        fulfillment.data = metadata.toMap()
                    }

                    val savedFulfillment = fulfillmentRepository.save(fulfillment)
                    ctx.addMetadata("fulfillment", savedFulfillment)

                    // Record FULFILLMENT_CREATED event
                    orderEventService.recordFulfillmentCreated(
                        orderId = order.id,
                        fulfillmentId = savedFulfillment.id,
                        itemCount = savedFulfillment.items.size,
                        fulfilledBy = input.metadata?.get("fulfilled_by") as? String
                    )

                    logger.info { "Fulfillment created: ${savedFulfillment.id} with ${savedFulfillment.items.size} items" }
                    StepResponse.of(savedFulfillment)
                },
                compensate = { order, ctx ->
                    // Cancel the created fulfillment
                    val fulfillment = ctx.getMetadata("fulfillment") as? Fulfillment
                    if (fulfillment != null) {
                        try {
                            fulfillment.cancel()
                            fulfillmentRepository.save(fulfillment)
                            logger.info { "Compensated: Canceled fulfillment ${fulfillment.id}" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate fulfillment: ${fulfillment.id}" }
                        }
                    }
                }
            )

            // Step 5: Update order fulfillment status
            val updateOrderStatusStep = createStep<Order, Order>(
                name = "update-order-fulfillment-status",
                execute = { order, ctx ->
                    logger.debug { "Updating order fulfillment status: ${order.id}" }

                    order.fulfillmentStatus = FulfillmentStatus.FULFILLED

                    val savedOrder = orderRepository.save(order)
                    ctx.addMetadata("orderUpdated", true)

                    logger.info { "Order ${order.id} fulfillment status updated to FULFILLED" }
                    StepResponse.of(savedOrder)
                },
                compensate = { order, ctx ->
                    // Revert order fulfillment status
                    val originalStatus = ctx.getMetadata("originalFulfillmentStatus") as? FulfillmentStatus
                    if (originalStatus != null) {
                        try {
                            order.fulfillmentStatus = originalStatus
                            orderRepository.save(order)
                            logger.info { "Compensated: Reverted order ${order.id} fulfillment status to $originalStatus" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate order status: ${order.id}" }
                        }
                    }
                }
            )

            // Step 6: Emit fulfillment created event
            val emitEventStep = createStep<Fulfillment, Unit>(
                name = "emit-fulfillment-created-event",
                execute = { fulfillment, ctx ->
                    val order = ctx.getMetadata("order") as Order
                    val location = ctx.getMetadata("stockLocation") as StockLocation
                    val provider = ctx.getMetadata("fulfillmentProvider") as FulfillmentProvider

                    val event = FulfillmentCreated(
                        aggregateId = fulfillment.id,
                        orderId = order.id,
                        locationId = location.id,
                        providerId = provider.id,
                        status = "FULFILLED"
                    )

                    eventPublisher.publish(event)
                    logger.info { "Fulfillment created event emitted for: ${fulfillment.id}" }
                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val order = loadOrderStep.invoke(input.orderId, context).data
            getLocationStep.invoke(input, context)
            getProviderStep.invoke(input, context)
            val fulfillment = createFulfillmentStep.invoke(order, context).data
            val updatedOrder = updateOrderStatusStep.invoke(order, context).data
            emitEventStep.invoke(fulfillment, context)

            // Build response
            val response = FulfillOrderResponse(
                orderId = updatedOrder.id,
                fulfillmentId = fulfillment.id,
                fulfillmentStatus = updatedOrder.fulfillmentStatus.name.lowercase(),
                itemCount = fulfillment.items.size,
                message = "Order fulfilled successfully"
            )

            logger.info { "Fulfill order workflow completed. Order: ${order.id}, Fulfillment: ${fulfillment.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Fulfill order workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}

// ============================================================================
// SHIP ORDER WORKFLOW
// ============================================================================

/**
 * Admin Ship Order Workflow
 *
 * This workflow marks an order as shipped, adding tracking information.
 * It automatically uses existing fulfillment or creates one if needed.
 *
 * Steps:
 * 1. Load and validate order
 * 2. Get or create fulfillment
 * 3. Add tracking information
 * 4. Mark fulfillment as shipped
 * 5. Update order status
 * 6. Emit shipment event
 *
 * SAGA Compensation:
 * - Fulfillment shipping is reverted
 * - Tracking info is removed
 * - Order status is reverted
 */
@Component
@WorkflowTypes(input = ShipOrderInput::class, output = ShipOrderResponse::class)
class ShipOrderWorkflow(
    private val orderRepository: OrderRepository,
    private val fulfillmentRepository: FulfillmentRepository,
    private val fulfillmentProviderRepository: FulfillmentProviderRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val eventPublisher: EventPublisher,
    private val orderEventService: OrderEventService,
    private val shipEngineService: ShipEngineService
) : Workflow<ShipOrderInput, ShipOrderResponse> {

    override val name = WorkflowConstants.ShipOrder.NAME

    @Transactional
    override suspend fun execute(
        input: ShipOrderInput,
        context: WorkflowContext
    ): WorkflowResult<ShipOrderResponse> {
        logger.info { "Starting ship order workflow for order: ${input.orderId}" }

        try {
            // Step 1: Load and validate order
            val loadOrderStep = createStep<String, Order>(
                name = "load-and-validate-order",
                execute = { orderId, ctx ->
                    logger.debug { "Loading order: $orderId" }

                    val order = orderRepository.findWithItemsById(orderId)
                        ?: throw IllegalArgumentException("Order not found: $orderId")

                    // Validate order state
                    if (order.status == OrderStatus.CANCELED) {
                        throw IllegalStateException("Cannot ship canceled order: $orderId")
                    }

                    if (order.fulfillmentStatus == FulfillmentStatus.SHIPPED) {
                        throw IllegalStateException("Order is already shipped: $orderId")
                    }

                    // Store original status for compensation
                    ctx.addMetadata("originalFulfillmentStatus", order.fulfillmentStatus)
                    ctx.addMetadata("order", order)

                    logger.info { "Order ${order.id} validated for shipping" }
                    StepResponse.of(order)
                }
            )

            // Step 2: Get or create fulfillment
            val getFulfillmentStep = createStep<Order, Fulfillment>(
                name = "get-or-create-fulfillment",
                execute = { order, ctx ->
                    // Check for existing fulfillment
                    val existingFulfillments = fulfillmentRepository.findByOrderIdAndDeletedAtIsNull(order.id)
                    val activeFulfillment = existingFulfillments.firstOrNull { !it.isCanceled() }

                    val fulfillment = if (activeFulfillment != null) {
                        logger.info { "Using existing fulfillment: ${activeFulfillment.id}" }
                        ctx.addMetadata("existingFulfillment", true)
                        activeFulfillment
                    } else {
                        // Create fulfillment on the fly
                        logger.info { "No existing fulfillment found, creating one for order: ${order.id}" }

                        // Get or create default provider
                        var provider = fulfillmentProviderRepository.findByNameAndDeletedAtIsNull("manual")
                        if (provider == null) {
                            provider = FulfillmentProvider()
                            provider.name = "manual"
                            provider.providerId = "manual"
                            provider.isActive = true
                            provider = fulfillmentProviderRepository.save(provider)
                        }

                        // Get or create default location
                        var location = stockLocationRepository.findByNameAndDeletedAtIsNull("Default Warehouse")
                        if (location == null) {
                            location = StockLocation()
                            location.name = "Default Warehouse"
                            location.address = "Default Warehouse Address"
                            location.fulfillmentEnabled = true
                            location = stockLocationRepository.save(location)
                        }

                        val newFulfillment = Fulfillment()
                        newFulfillment.orderId = order.id
                        newFulfillment.provider = provider
                        newFulfillment.locationId = location.id
                        newFulfillment.noNotification = input.noNotification

                        // Create fulfillment items
                        order.items.filter { it.deletedAt == null }.forEach { orderItem ->
                            val fulfillmentItem = FulfillmentItem()
                            fulfillmentItem.title = orderItem.title
                            fulfillmentItem.sku = orderItem.variantId ?: ""
                            fulfillmentItem.quantity = orderItem.quantity
                            fulfillmentItem.lineItemId = orderItem.id
                            newFulfillment.addItem(fulfillmentItem)
                        }

                        val savedFulfillment = fulfillmentRepository.save(newFulfillment)
                        ctx.addMetadata("createdFulfillment", true)
                        savedFulfillment
                    }

                    ctx.addMetadata("fulfillment", fulfillment)
                    fulfillment.shippedAt?.let { ctx.addMetadata("originalShippedAt", it) }
                    fulfillment.trackingNumbers?.let { ctx.addMetadata("originalTrackingNumbers", it) }
                    fulfillment.trackingUrls?.let { ctx.addMetadata("originalTrackingUrls", it) }

                    StepResponse.of(fulfillment)
                },
                compensate = { order, ctx ->
                    val createdFulfillment = ctx.getMetadata("createdFulfillment") as? Boolean ?: false
                    val fulfillment = ctx.getMetadata("fulfillment") as? Fulfillment
                    if (createdFulfillment && fulfillment != null) {
                        try {
                            fulfillment.cancel()
                            fulfillmentRepository.save(fulfillment)
                            logger.info { "Compensated: Canceled created fulfillment ${fulfillment.id}" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate fulfillment: ${fulfillment.id}" }
                        }
                    }
                }
            )

            // Step 3: Create ShipEngine label (optional)
            val createShipEngineLabelStep = createStep<ShipOrderInput, ShipEngineLabelResult?>(
                name = "create-shipengine-label",
                execute = { inp, ctx ->
                    // Skip if ShipEngine not requested or not available
                    if (!inp.useShipEngine || !shipEngineService.isAvailable()) {
                        if (inp.useShipEngine) {
                            logger.warn { "ShipEngine requested but not configured, falling back to manual mode" }
                            ctx.addMetadata("shipEngineWarning", "ShipEngine not configured, using manual mode")
                        }
                        return@createStep StepResponse.of<ShipEngineLabelResult?>(null)
                    }

                    val order = ctx.getMetadata("order") as Order

                    try {
                        // Build shipping address
                        val shippingAddress = order.shippingAddress
                            ?: throw IllegalStateException("Order has no shipping address")

                        val toAddress = ShipEngineAddress(
                            name = listOfNotNull(shippingAddress.firstName, shippingAddress.lastName)
                                .joinToString(" ").ifBlank { "Customer" },
                            street1 = shippingAddress.address1 ?: "",
                            street2 = shippingAddress.address2,
                            city = shippingAddress.city ?: "",
                            stateProvince = shippingAddress.province,
                            postalCode = shippingAddress.postalCode ?: "",
                            countryCode = shippingAddress.countryCode ?: "GB",
                            phone = shippingAddress.phone?.ifBlank { null } ?: "+1 555 555 5555",
                            email = order.email
                        )

                        // Build parcel dimensions
                        val parcel = ShipEngineParcel(
                            length = inp.packageLength ?: 10.0,
                            width = inp.packageWidth ?: 10.0,
                            height = inp.packageHeight ?: 5.0,
                            weight = inp.packageWeight ?: (16.0 * order.items.sumOf { it.quantity })
                        )

                        // Calculate customs info from order
                        val totalQuantity = order.items.sumOf { it.quantity }
                        val customsDescription = order.items.firstOrNull()?.title ?: "Merchandise"
                        val customsValue = order.total?.toDouble() ?: 10.0

                        val request = CreateLabelRequest(
                            toAddress = toAddress,
                            parcel = parcel,
                            carrierId = inp.carrierId ?: "",
                            serviceCode = inp.serviceCode ?: shipEngineService.getConfig().defaultServiceCode,
                            customsDescription = customsDescription.take(50), // Limit to 50 chars
                            customsQuantity = totalQuantity,
                            customsValue = customsValue
                        )

                        val result = shipEngineService.createLabel(request)
                        ctx.addMetadata("shipEngineResult", result)

                        logger.info { "ShipEngine label created: ${result.labelId}, tracking: ${result.trackingNumber}" }
                        StepResponse.of(result)

                    } catch (e: Exception) {
                        logger.error(e) { "ShipEngine label creation failed: ${e.message}" }
                        // Don't fail silently - throw the error so the admin knows what went wrong
                        throw IllegalStateException("ShipEngine label creation failed: ${e.message}", e)
                    }
                }
            )

            // Step 4: Add tracking information and ship fulfillment
            val shipFulfillmentStep = createStep<ShipOrderInput, Fulfillment>(
                name = "ship-fulfillment",
                execute = { inp, ctx ->
                    val fulfillment = ctx.getMetadata("fulfillment") as Fulfillment
                    val shipEngineResult = ctx.getMetadata("shipEngineResult") as? ShipEngineLabelResult

                    logger.debug { "Adding tracking info and shipping fulfillment: ${fulfillment.id}" }

                    // Use ShipEngine tracking if available, otherwise use manual input
                    val trackingNumber = shipEngineResult?.trackingNumber ?: inp.trackingNumber
                    val trackingUrl = shipEngineResult?.trackingUrl ?: inp.trackingUrl
                    val carrier = shipEngineResult?.carrier ?: inp.carrier

                    // Add tracking number
                    if (!trackingNumber.isNullOrBlank()) {
                        fulfillment.addTrackingNumber(trackingNumber)
                    }

                    // Add tracking URL
                    if (!trackingUrl.isNullOrBlank()) {
                        fulfillment.addTrackingUrl(trackingUrl)
                    } else if (!trackingNumber.isNullOrBlank() && !carrier.isNullOrBlank()) {
                        val generatedUrl = generateTrackingUrl(carrier, trackingNumber)
                        if (generatedUrl != null) {
                            fulfillment.addTrackingUrl(generatedUrl)
                        }
                    }

                    // Store carrier and ShipEngine data
                    val currentData = fulfillment.data?.toMutableMap() ?: mutableMapOf()
                    if (!carrier.isNullOrBlank()) {
                        currentData["carrier"] = carrier
                    }
                    if (shipEngineResult != null) {
                        currentData["shipengine_label_id"] = shipEngineResult.labelId
                        currentData["shipengine_label_url"] = shipEngineResult.labelDownloadUrl
                        shipEngineResult.labelDownloadPng?.let { currentData["shipengine_label_png_url"] = it }
                        currentData["shipengine_carrier"] = shipEngineResult.carrier
                        currentData["shipengine_service"] = shipEngineResult.serviceCode
                        currentData["shipengine_cost"] = shipEngineResult.shipmentCost
                    }
                    fulfillment.data = currentData

                    // Mark as shipped
                    if (!fulfillment.isShipped()) {
                        fulfillment.ship()
                    }

                    val savedFulfillment = fulfillmentRepository.save(fulfillment)
                    ctx.addMetadata("shippedFulfillment", savedFulfillment)

                    // Record SHIPPED event
                    val order = ctx.getMetadata("order") as Order
                    orderEventService.recordShipped(
                        orderId = order.id,
                        fulfillmentId = savedFulfillment.id,
                        trackingNumber = trackingNumber,
                        carrier = carrier,
                        trackingUrl = savedFulfillment.getTrackingUrlsList().firstOrNull(),
                        shippedBy = inp.metadata?.get("shipped_by") as? String
                    )

                    logger.info { "Fulfillment ${savedFulfillment.id} shipped with tracking: ${savedFulfillment.trackingNumbers}" }
                    StepResponse.of(savedFulfillment)
                },
                compensate = { inp, ctx ->
                    val fulfillment = ctx.getMetadata("fulfillment") as? Fulfillment
                    val originalShippedAt = ctx.getMetadata("originalShippedAt") as? Instant
                    val originalTrackingNumbers = ctx.getMetadata("originalTrackingNumbers") as? String
                    val originalTrackingUrls = ctx.getMetadata("originalTrackingUrls") as? String

                    if (fulfillment != null) {
                        try {
                            fulfillment.shippedAt = originalShippedAt
                            fulfillment.trackingNumbers = originalTrackingNumbers
                            fulfillment.trackingUrls = originalTrackingUrls
                            fulfillmentRepository.save(fulfillment)
                            logger.info { "Compensated: Reverted fulfillment ${fulfillment.id} shipping state" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate fulfillment shipping: ${fulfillment.id}" }
                        }
                    }
                }
            )

            // Step 4: Update order status
            val updateOrderStep = createStep<Order, Order>(
                name = "update-order-status",
                execute = { order, ctx ->
                    logger.debug { "Updating order status: ${order.id}" }

                    order.fulfillmentStatus = FulfillmentStatus.SHIPPED

                    val savedOrder = orderRepository.save(order)

                    logger.info { "Order ${order.id} status updated to SHIPPED" }
                    StepResponse.of(savedOrder)
                },
                compensate = { order, ctx ->
                    val originalStatus = ctx.getMetadata("originalFulfillmentStatus") as? FulfillmentStatus
                    if (originalStatus != null) {
                        try {
                            order.fulfillmentStatus = originalStatus
                            orderRepository.save(order)
                            logger.info { "Compensated: Reverted order ${order.id} to $originalStatus" }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to compensate order status: ${order.id}" }
                        }
                    }
                }
            )

            // Step 5: Emit shipment event
            val emitEventStep = createStep<Fulfillment, Unit>(
                name = "emit-shipment-event",
                execute = { fulfillment, ctx ->
                    val order = ctx.getMetadata("order") as Order

                    val event = FulfillmentShipped(
                        aggregateId = fulfillment.id,
                        orderId = order.id,
                        locationId = fulfillment.locationId ?: "",
                        trackingNumbers = fulfillment.getTrackingNumbersList(),
                        shippedAt = fulfillment.shippedAt ?: Instant.now()
                    )

                    eventPublisher.publish(event)
                    logger.info { "Shipment event emitted for fulfillment: ${fulfillment.id}" }
                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val order = loadOrderStep.invoke(input.orderId, context).data
            getFulfillmentStep.invoke(order, context)
            createShipEngineLabelStep.invoke(input, context)
            val shippedFulfillment = shipFulfillmentStep.invoke(input, context).data
            val updatedOrder = updateOrderStep.invoke(order, context).data
            emitEventStep.invoke(shippedFulfillment, context)

            // Get ShipEngine result for response
            val shipEngineResult = context.getMetadata("shipEngineResult") as? ShipEngineLabelResult
            val shipEngineWarning = context.getMetadata("shipEngineWarning") as? String
            val shipEngineError = context.getMetadata("shipEngineError") as? String

            // Build response
            val response = ShipOrderResponse(
                orderId = updatedOrder.id,
                fulfillmentId = shippedFulfillment.id,
                fulfillmentStatus = updatedOrder.fulfillmentStatus.name.lowercase(),
                trackingNumbers = shippedFulfillment.getTrackingNumbersList(),
                message = buildMessage(shipEngineResult, shipEngineWarning, shipEngineError),
                labelUrls = listOfNotNull(shipEngineResult?.labelDownloadUrl, shipEngineResult?.labelDownloadPng),
                trackingUrls = shippedFulfillment.getTrackingUrlsList(),
                shipEngineLabelId = shipEngineResult?.labelId,
                carrier = shipEngineResult?.carrier ?: input.carrier,
                shippingCost = shipEngineResult?.let { "${it.shipmentCost} ${it.currency}" }
            )

            logger.info { "Ship order workflow completed. Order: ${order.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Ship order workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

    /**
     * Generate tracking URL based on carrier
     */
    private fun generateTrackingUrl(carrier: String, trackingNumber: String): String? {
        return when (carrier.lowercase()) {
            "ups" -> "https://www.ups.com/track?tracknum=$trackingNumber"
            "fedex" -> "https://www.fedex.com/apps/fedextrack/?tracknumbers=$trackingNumber"
            "usps" -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=$trackingNumber"
            "dhl" -> "https://www.dhl.com/en/express/tracking.html?AWB=$trackingNumber"
            "royal_mail", "royalmail" -> "https://www.royalmail.com/track-your-item#/$trackingNumber"
            "dpd" -> "https://www.dpd.co.uk/tracking/trackingSearch.do?parcelNumber=$trackingNumber"
            "hermes", "evri" -> "https://www.evri.com/track/parcel/$trackingNumber"
            "yodel" -> "https://www.yodel.co.uk/tracking/$trackingNumber"
            else -> null
        }
    }

    /**
     * Build response message based on ShipEngine result
     */
    private fun buildMessage(
        shipEngineResult: ShipEngineLabelResult?,
        warning: String?,
        error: String?
    ): String {
        return when {
            shipEngineResult != null -> "Order shipped with ShipEngine label generated"
            warning != null -> "Order shipped. Warning: $warning"
            error != null -> "Order shipped manually. ShipEngine failed: $error"
            else -> "Order shipped successfully"
        }
    }
}

// ============================================================================
// COMPLETE ORDER WORKFLOW
// ============================================================================

/**
 * Admin Complete Order Workflow
 *
 * This workflow marks an order as completed after all fulfillment is done.
 *
 * Steps:
 * 1. Load and validate order
 * 2. Validate order can be completed (has been shipped or fulfilled)
 * 3. Update order status to completed
 * 4. Emit order completed event
 *
 * SAGA Compensation:
 * - Order status is reverted to previous state
 */
@Component
@WorkflowTypes(input = CompleteOrderInput::class, output = CompleteOrderResponse::class)
class CompleteOrderWorkflow(
    private val orderRepository: OrderRepository,
    private val eventPublisher: EventPublisher,
    private val orderEventService: OrderEventService
) : Workflow<CompleteOrderInput, CompleteOrderResponse> {

    override val name = WorkflowConstants.CompleteOrder.NAME

    @Transactional
    override suspend fun execute(
        input: CompleteOrderInput,
        context: WorkflowContext
    ): WorkflowResult<CompleteOrderResponse> {
        logger.info { "Starting complete order workflow for order: ${input.orderId}" }

        try {
            // Step 1: Load and validate order
            val loadOrderStep = createStep<String, Order>(
                name = "load-and-validate-order",
                execute = { orderId, ctx ->
                    logger.debug { "Loading order: $orderId" }

                    val order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                        ?: throw IllegalArgumentException("Order not found: $orderId")

                    // Validate order state
                    if (order.status == OrderStatus.CANCELED) {
                        throw IllegalStateException("Cannot complete canceled order: $orderId")
                    }

                    if (order.status == OrderStatus.COMPLETED) {
                        throw IllegalStateException("Order is already completed: $orderId")
                    }

                    // Store original status for compensation
                    ctx.addMetadata("originalStatus", order.status)
                    ctx.addMetadata("originalFulfillmentStatus", order.fulfillmentStatus)
                    ctx.addMetadata("order", order)

                    logger.info { "Order ${order.id} validated for completion" }
                    StepResponse.of(order)
                }
            )

            // Step 2: Update order status to completed
            val completeOrderStep = createStep<Order, Order>(
                name = "complete-order",
                execute = { order, ctx ->
                    logger.debug { "Completing order: ${order.id}" }

                    order.status = OrderStatus.COMPLETED

                    // If not already marked as fulfilled/shipped, mark as fulfilled
                    if (order.fulfillmentStatus == FulfillmentStatus.NOT_FULFILLED) {
                        order.fulfillmentStatus = FulfillmentStatus.FULFILLED
                    }

                    val savedOrder = orderRepository.save(order)

                    // Record ORDER_COMPLETED event
                    orderEventService.recordOrderCompleted(
                        orderId = order.id,
                        completedBy = input.metadata?.get("completed_by") as? String
                    )

                    logger.info { "Order ${order.id} marked as COMPLETED" }
                    StepResponse.of(savedOrder)
                },
                compensate = { order, ctx ->
                    val originalStatus = ctx.getMetadata("originalStatus") as? OrderStatus
                    val originalFulfillmentStatus = ctx.getMetadata("originalFulfillmentStatus") as? FulfillmentStatus

                    try {
                        if (originalStatus != null) {
                            order.status = originalStatus
                        }
                        if (originalFulfillmentStatus != null) {
                            order.fulfillmentStatus = originalFulfillmentStatus
                        }
                        orderRepository.save(order)
                        logger.info { "Compensated: Reverted order ${order.id} status to $originalStatus" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to compensate order completion: ${order.id}" }
                    }
                }
            )

            // Step 3: Emit order completed event
            val emitEventStep = createStep<Order, Unit>(
                name = "emit-order-completed-event",
                execute = { order, ctx ->
                    val event = OrderCompleted(
                        aggregateId = order.id,
                        customerId = order.customerId ?: "",
                        totalAmount = order.total,
                        completedAt = Instant.now()
                    )

                    eventPublisher.publish(event)
                    logger.info { "Order completed event emitted for: ${order.id}" }
                    StepResponse.of(Unit)
                }
            )

            // Execute workflow steps
            val order = loadOrderStep.invoke(input.orderId, context).data
            val completedOrder = completeOrderStep.invoke(order, context).data
            emitEventStep.invoke(completedOrder, context)

            // Build response
            val response = CompleteOrderResponse(
                orderId = completedOrder.id,
                status = completedOrder.status.name.lowercase(),
                message = "Order completed successfully"
            )

            logger.info { "Complete order workflow completed. Order: ${order.id}" }
            return WorkflowResult.success(response)

        } catch (e: Exception) {
            logger.error(e) { "Complete order workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
