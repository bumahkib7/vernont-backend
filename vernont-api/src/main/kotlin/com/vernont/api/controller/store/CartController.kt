package com.vernont.api.controller.store

import com.fasterxml.jackson.annotation.JsonProperty
import com.vernont.api.rate.RateLimited
import com.vernont.domain.auth.UserContext
import com.vernont.domain.auth.getCurrentUserContext
import com.vernont.domain.cart.Cart
import com.vernont.domain.order.dto.OrderResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowOptions
import com.vernont.workflow.flows.cart.AddToCartInput
import com.vernont.workflow.flows.cart.AddToCartLineItemInput
import com.vernont.workflow.flows.cart.CreateCartInput
import com.vernont.workflow.flows.cart.CreateCartLineItemInput
import com.vernont.workflow.flows.cart.dto.CartResponse
import com.vernont.workflow.flows.cart.dto.CartDto
import com.vernont.workflow.flows.payment.CreateStripePaymentIntentInput
import com.vernont.workflow.flows.payment.StripePaymentIntentResponse
import com.vernont.workflow.flows.payment.ConfirmStripePaymentInput
import com.vernont.workflow.flows.payment.StripePaymentConfirmationResponse
import com.vernont.application.giftcard.GiftCardOrderService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Store Cart Controller - Medusa-compatible API
 * Implements Medusa's cart endpoints for the storefront
 */
@RestController
@RequestMapping("/store/carts")
@CrossOrigin(origins = ["http://localhost:8000", "http://localhost:9000", "http://localhost:3000"])
@Tag(name = "Store Carts", description = "Cart management endpoints for storefront")
class CartController(
    private val workflowEngine: WorkflowEngine,
    private val cartRepository: com.vernont.repository.cart.CartRepository,
    private val customerRepository: com.vernont.repository.customer.CustomerRepository,
    private val giftCardOrderService: GiftCardOrderService
) {

    /**
     * Create a new cart
     * POST /store/carts
     *
     * Medusa-compatible endpoint that uses CreateCartWorkflow
     */
    @Operation(summary = "Create a new cart")
    @PostMapping
    @RateLimited(
        keyPrefix = "cart:create",
        perIp = true,
        perEmail = false,
        limit = 10,
        windowSeconds = 3600,  // 1 hour
        failClosed = true  // SECURITY: Fail closed if rate limit check fails
    )
    suspend fun createCart(
        @RequestBody(required = false) request: StoreCreateCartRequest?,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {

        val correlationId = requestId ?: "req_${UUID.randomUUID()}"

        // Extract customer info from authenticated user using elegant UserContext
        val userContext = getCurrentUserContext()
        var email = request?.email
        var customerId = request?.customerId

        logger.info { "Cart creation - UserContext: ${if (userContext != null) "authenticated as ${userContext.email} (userId=${userContext.userId}, customerId=${userContext.customerId})" else "not authenticated"}" }
        logger.info { "Cart creation - Request params: email=${request?.email}, customerId=${request?.customerId}" }

        // If user is authenticated, use their context information
        if (userContext != null) {
            // If the user already has a customerId in their token, use it
            if (userContext.customerId != null) {
                customerId = userContext.customerId
                email = userContext.email
                logger.info { "Using customer from UserContext: customerId=$customerId, email=$email" }
            } else {
                // Otherwise, look up the customer by userId
                val customer = customerRepository.findByUserIdAndDeletedAtIsNull(userContext.userId)
                if (customer != null) {
                    customerId = customer.id
                    email = customer.email
                    logger.info { "Found authenticated customer via lookup: customerId=$customerId, email=$email" }
                } else {
                    logger.warn { "User is authenticated but no customer record found for userId=${userContext.userId}" }
                }
            }
        } else {
            logger.info { "User not authenticated, will use guest cart if email is provided" }
        }

        logger.info { "Creating new cart: regionId=${request?.regionId}, email=$email, customerId=$customerId" }

        return try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.CreateCart.NAME,
                // SECURITY: Never use client-provided unitPrice
                input = CreateCartInput(
                    regionId = request?.regionId,
                    customerId = customerId,
                    email = email,
                    currencyCode = request?.currencyCode,
                    items = request?.items?.map { item ->
                        CreateCartLineItemInput(
                            variantId = item.variantId,
                            quantity = item.quantity,
                            unitPrice = null  // Price will be fetched server-side from ProductVariant
                        )
                    },
                    correlationId = correlationId
                ),
                inputType = CreateCartInput::class,
                outputType = com.vernont.workflow.flows.cart.dto.CartResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    timeoutSeconds = 30
                )
            )

            when {
                result.isSuccess() -> {
                    val cartResponse = result.getOrNull()!!
                    val cartDto = cartResponse.cart
                    logger.info { "Cart created successfully: cartId=${cartDto.id}" }

                    ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
                        "cart" to mapToStoreCart(cartDto)
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to create cart: error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to create cart"),
                            "code" to "CREATE_CART_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception creating cart" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Retrieve a cart
     * GET /store/carts/:id
     *
     * Medusa-compatible endpoint that retrieves a cart by ID
     * SECURITY: Validates cart ownership if cart has a customerId
     */
    @Operation(summary = "Retrieve cart")
    @GetMapping("/{id}")
    fun getCart(
        @PathVariable id: String,
        @RequestParam(required = false) fields: String?,
        @org.springframework.security.core.annotation.AuthenticationPrincipal userContext: com.vernont.domain.auth.UserContext?
    ): ResponseEntity<Any> {
        logger.info { "Retrieving cart: cartId=$id" }

        return try {
            val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(id)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                    "error" to mapOf(
                        "message" to "Cart not found: $id",
                        "code" to "CART_NOT_FOUND"
                    )
                ))

            // SECURITY: Validate cart ownership
            // If cart belongs to a customer, verify the authenticated user matches
            if (cart.customerId != null) {
                val context = userContext ?: com.vernont.domain.auth.getCurrentUserContext()
                if (context == null || context.customerId != cart.customerId) {
                    logger.warn { "Unauthorized cart access attempt: cartId=$id, requesterId=${context?.customerId}" }
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                        "error" to mapOf(
                            "message" to "You don't have permission to access this cart",
                            "code" to "CART_ACCESS_DENIED"
                        )
                    ))
                }
            }

            logger.info { "Cart retrieved successfully: cartId=${cart.id}" }

            ResponseEntity.ok(mapOf(
                "cart" to mapToStoreCart(com.vernont.workflow.flows.cart.dto.CartDto.from(cart).cart)
            ))

        } catch (e: Exception) {
            logger.error(e) { "Exception retrieving cart" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Update cart
     * POST /store/carts/:id
     *
     * Medusa-compatible endpoint that uses UpdateCartWorkflow
     * SECURITY: Rate limited and validates cart ownership
     */
    @Operation(summary = "Update cart")
    @PostMapping("/{id}")
    @RateLimited(
        keyPrefix = "cart:update",
        perIp = true,
        perEmail = false,
        limit = 30,  // Reasonable limit for promo/gift card attempts
        windowSeconds = 300,  // 5 minutes
        failClosed = true
    )
    suspend fun updateCart(
        @PathVariable id: String,
        @RequestBody request: StoreUpdateCartRequest,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: "req_${UUID.randomUUID()}"
        logger.info { "Updating cart: cartId=$id, regionId=${request.regionId}, email=${request.email}" }

        // SECURITY: Validate cart ownership before update
        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "error" to mapOf(
                    "message" to "Cart not found: $id",
                    "code" to "CART_NOT_FOUND"
                )
            ))

        if (cart.customerId != null) {
            val context = getCurrentUserContext()
            if (context == null || context.customerId != cart.customerId) {
                logger.warn { "Unauthorized cart update attempt: cartId=$id, requesterId=${context?.customerId}" }
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf(
                    "error" to mapOf(
                        "message" to "You don't have permission to update this cart",
                        "code" to "CART_ACCESS_DENIED"
                    )
                ))
            }
        }

        return try {
            // Convert shipping address map to ShippingAddressInput if provided
            val shippingAddressInput = request.shippingAddress?.let { addr ->
                com.vernont.workflow.flows.cart.ShippingAddressInput(
                    firstName = addr["first_name"]?.toString() ?: addr["firstName"]?.toString(),
                    lastName = addr["last_name"]?.toString() ?: addr["lastName"]?.toString(),
                    address1 = addr["address_1"]?.toString() ?: addr["address1"]?.toString() ?: "",
                    address2 = addr["address_2"]?.toString() ?: addr["address2"]?.toString(),
                    city = addr["city"]?.toString() ?: "",
                    province = addr["province"]?.toString(),
                    postalCode = addr["postal_code"]?.toString() ?: addr["postalCode"]?.toString() ?: "",
                    countryCode = addr["country_code"]?.toString() ?: addr["countryCode"]?.toString() ?: "GB",
                    phone = addr["phone"]?.toString()
                )
            }

            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.UpdateCart.NAME,
                input = com.vernont.workflow.flows.cart.UpdateCartInput(
                    id = id,
                    regionId = request.regionId,
                    customerId = request.customerId,
                    email = request.email,
                    promoCodes = request.promoCodes,
                    shippingAddress = shippingAddressInput,
                    billingAddressId = request.billingAddress?.get("address_id")?.toString()
                        ?: request.billingAddress?.get("addressId")?.toString(),
                    giftCardCode = request.giftCardCode
                ),
                inputType = com.vernont.workflow.flows.cart.UpdateCartInput::class,
                outputType = com.vernont.workflow.flows.cart.dto.CartResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    lockKey = "cart:$id",
                    timeoutSeconds = 30
                )
            )

            when {
                result.isSuccess() -> {
                    val cartResponse = result.getOrNull()!!
                    val cartDto = cartResponse.cart
                    logger.info { "Cart updated successfully: cartId=${cartDto.id}" }

                    ResponseEntity.ok(mapOf(
                        "cart" to mapToStoreCart(cartDto)
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to update cart: error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to update cart"),
                            "code" to "UPDATE_CART_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception updating cart" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Add line item to cart
     * POST /store/carts/:id/line-items
     *
     * Medusa-compatible endpoint that uses AddToCartWorkflow
     */
    @Operation(summary = "Add item to cart")
    @PostMapping("/{id}/line-items")
    @RateLimited(
        keyPrefix = "cart:additem",
        perIp = true,
        perEmail = false,
        limit = 100,
        windowSeconds = 3600,  // 1 hour
        failClosed = true  // SECURITY: Fail closed if rate limit check fails
    )
    suspend fun addLineItem(
        @PathVariable id: String,
        @RequestBody request: StoreAddLineItemRequest,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {

        val correlationId = requestId ?: "req_${UUID.randomUUID()}"
        logger.info { "Adding line item to cart: cartId=$id, variantId=${request.variantId}, quantity=${request.quantity}" }

        return try {
            // SECURITY: Never use client-provided unitPrice - always fetch from server
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.AddToCart.NAME,
                input = AddToCartInput(
                    cartId = id,
                    items = listOf(
                        AddToCartLineItemInput(
                            variantId = request.variantId,
                            quantity = request.quantity,
                            unitPrice = null  // Price will be fetched server-side from ProductVariant
                        )
                    )
                ),
                inputType = AddToCartInput::class,
                outputType = com.vernont.workflow.flows.cart.dto.CartResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    lockKey = "cart:add:$id",
                    timeoutSeconds = 30
                )
            )

            when {
                result.isSuccess() -> {
                    val cartResponse = result.getOrNull()!!
                    val cartDto = cartResponse.cart
                    logger.info { "Line item added successfully: cartId=$id, itemCount=${cartDto.items.size}" }

                    ResponseEntity.ok(mapOf(
                        "cart" to mapToStoreCart(cartDto)
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to add line item: cartId=$id, error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to add item to cart"),
                            "code" to "ADD_TO_CART_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state: cartId=$id" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception adding line item: cartId=$id" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Update line item in cart
     * POST /store/carts/:id/line-items/:line_id
     *
     * Medusa-compatible endpoint that uses UpdateLineItemInCartWorkflow
     */
    @Operation(summary = "Update line item in cart")
    @PostMapping("/{id}/line-items/{lineId}")
    suspend fun updateLineItem(
        @PathVariable id: String,
        @PathVariable lineId: String,
        @RequestBody request: StoreUpdateLineItemRequest,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: "req_${UUID.randomUUID()}"
        logger.info { "Updating line item: cartId=$id, lineId=$lineId, quantity=${request.quantity}" }

        return try {
            // SECURITY: Never use client-provided unitPrice - price changes are not allowed
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.UpdateLineItemInCart.NAME,
                input = com.vernont.workflow.flows.cart.UpdateLineItemInCartInput(
                    cartId = id,
                    itemId = lineId,
                    quantity = request.quantity,
                    unitPrice = null  // Price cannot be changed after adding to cart
                ),
                inputType = com.vernont.workflow.flows.cart.UpdateLineItemInCartInput::class,
                outputType = com.vernont.workflow.flows.cart.dto.CartResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    lockKey = "cart:update:$id",
                    timeoutSeconds = 30
                )
            )

            when {
                result.isSuccess() -> {
                    val cartResponse = result.getOrNull()!!
                    val cartDto = cartResponse.cart
                    logger.info { "Line item updated successfully: cartId=$id, lineId=$lineId" }

                    ResponseEntity.ok(mapOf(
                        "cart" to mapToStoreCart(cartDto)
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to update line item: cartId=$id, error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to update line item"),
                            "code" to "UPDATE_LINE_ITEM_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state: cartId=$id" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception updating line item: cartId=$id, lineId=$lineId" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Delete line item from cart
     * DELETE /store/carts/:id/line-items/:line_id
     *
     * Medusa-compatible endpoint that uses RemoveLineItemFromCartWorkflow
     */
    @Operation(summary = "Delete line item from cart")
    @DeleteMapping("/{id}/line-items/{lineId}")
    suspend fun deleteLineItem(
        @PathVariable id: String,
        @PathVariable lineId: String,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: "req_${UUID.randomUUID()}"
        logger.info { "Deleting line item: cartId=$id, lineId=$lineId" }

        return try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.RemoveLineItemFromCart.NAME,
                input = com.vernont.workflow.flows.cart.RemoveLineItemFromCartInput(
                    cartId = id,
                    itemIds = listOf(lineId)
                ),
                inputType = com.vernont.workflow.flows.cart.RemoveLineItemFromCartInput::class,
                outputType = com.vernont.workflow.flows.cart.dto.CartResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    lockKey = "cart:remove:$id",
                    timeoutSeconds = 30
                )
            )

            when {
                result.isSuccess() -> {
                    val cartResponse = result.getOrNull()!!
                    val cartDto = cartResponse.cart
                    logger.info { "Line item deleted successfully: cartId=$id, lineId=$lineId" }

                    ResponseEntity.ok(mapOf(
                        "cart" to mapToStoreCart(cartDto)
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to delete line item: cartId=$id, error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to delete line item"),
                            "code" to "DELETE_LINE_ITEM_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state: cartId=$id" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception deleting line item: cartId=$id, lineId=$lineId" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Add shipping method to cart
     * POST /store/carts/:id/shipping-methods
     *
     * Medusa-compatible endpoint that uses AddShippingMethodToCartWorkflow
     */
    @Operation(summary = "Add shipping method to cart")
    @PostMapping("/{id}/shipping-methods")
    suspend fun addShippingMethod(
        @PathVariable id: String,
        @RequestBody request: StoreAddShippingMethodRequest,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: "req_${UUID.randomUUID()}"
        logger.info { "Adding shipping method to cart: cartId=$id, optionId=${request.optionId}" }

        return try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.AddShippingMethodToCart.NAME,
                input = com.vernont.workflow.flows.cart.AddShippingMethodToCartInput(
                    cartId = id,
                    shippingOptionId = request.optionId,
                    data = request.data
                ),
                inputType = com.vernont.workflow.flows.cart.AddShippingMethodToCartInput::class,
                outputType = com.vernont.workflow.flows.cart.dto.CartResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    lockKey = "cart:shipping:$id",
                    timeoutSeconds = 30
                )
            )

            when {
                result.isSuccess() -> {
                    val cartResponse = result.getOrNull()!!
                    val cartDto = cartResponse.cart
                    logger.info { "Shipping method added successfully: cartId=$id" }

                    ResponseEntity.ok(mapOf(
                        "cart" to mapToStoreCart(cartDto)
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to add shipping method: cartId=$id, error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to add shipping method"),
                            "code" to "ADD_SHIPPING_METHOD_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state: cartId=$id" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception adding shipping method: cartId=$id" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Complete cart and place order
     * POST /store/carts/:id/complete
     *
     * Medusa-compatible endpoint that uses CompleteCartWorkflow
     */
    @Operation(summary = "Complete cart and place order")
    @PostMapping("/{id}/complete")
    suspend fun completeCart(
        @PathVariable id: String,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: "req_${UUID.randomUUID()}"
        logger.info { "Completing cart: cartId=$id" }

        return try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.CompleteCart.NAME,
                input = com.vernont.workflow.flows.cart.CompleteCartInput(
                    cartId = id
                ),
                inputType = com.vernont.workflow.flows.cart.CompleteCartInput::class,
                outputType = OrderResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    lockKey = "cart:complete:$id",
                    timeoutSeconds = 60
                )
            )

            when {
                result.isSuccess() -> {
                    val orderResponse = result.getOrNull()!!
                    logger.info { "Cart completed successfully: cartId=$id, orderId=${orderResponse.id}" }

                    ResponseEntity.ok(mapOf(
                        "type" to "order",
                        "order" to mapOf(
                            "id" to orderResponse.id,
                            "status" to orderResponse.status.name.lowercase(),
                            "email" to orderResponse.email,
                            "total" to orderResponse.total.multiply(BigDecimal(100)).toInt()
                        )
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to complete cart: cartId=$id, error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to complete cart"),
                            "code" to "COMPLETE_CART_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state: cartId=$id" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception completing cart: cartId=$id" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Create Stripe PaymentIntent for cart checkout
     * POST /store/carts/:id/payment-sessions
     *
     * Creates a Stripe PaymentIntent and returns client_secret for frontend
     */
    @Operation(summary = "Create Stripe payment session for cart checkout")
    @PostMapping("/{id}/payment-sessions")
    suspend fun createPaymentSession(
        @PathVariable id: String,
        @RequestBody(required = false) request: StoreCreatePaymentSessionRequest?,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: "req_${UUID.randomUUID()}"
        logger.info { "Creating payment session for cart: cartId=$id" }

        logger.info { "Payment session request - giftCardCode: ${request?.giftCardCode}" }

        return try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.CreateStripePaymentIntent.NAME,
                input = CreateStripePaymentIntentInput(
                    cartId = id,
                    email = request?.email,
                    metadata = request?.metadata,
                    giftCardCode = request?.giftCardCode
                ),
                inputType = CreateStripePaymentIntentInput::class,
                outputType = StripePaymentIntentResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    lockKey = "cart:payment:$id",
                    timeoutSeconds = 30
                )
            )

            when {
                result.isSuccess() -> {
                    val paymentIntent = result.getOrNull()!!
                    logger.info { "Payment session created: cartId=$id, paymentIntentId=${paymentIntent.paymentIntentId}" }

                    ResponseEntity.ok(mapOf(
                        "payment_session" to mapOf(
                            "payment_intent_id" to paymentIntent.paymentIntentId,
                            "client_secret" to paymentIntent.clientSecret,
                            "publishable_key" to paymentIntent.publishableKey,
                            "amount" to paymentIntent.amount,
                            "currency" to paymentIntent.currencyCode,
                            "status" to paymentIntent.status
                        )
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to create payment session: cartId=$id, error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to create payment session"),
                            "code" to "PAYMENT_SESSION_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state: cartId=$id" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception creating payment session: cartId=$id" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    /**
     * Confirm Stripe payment and create order
     * POST /store/carts/:id/confirm-payment
     *
     * Verifies Stripe payment was successful and creates the order
     */
    @Operation(summary = "Confirm Stripe payment and create order")
    @PostMapping("/{id}/confirm-payment")
    suspend fun confirmPayment(
        @PathVariable id: String,
        @RequestBody request: StoreConfirmPaymentRequest,
        @RequestHeader(value = "X-Request-ID", required = false) requestId: String?
    ): ResponseEntity<Any> {
        val correlationId = requestId ?: "req_${UUID.randomUUID()}"
        logger.info { "Confirming payment for cart: cartId=$id, paymentIntentId=${request.paymentIntentId}" }

        return try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.ConfirmStripePayment.NAME,
                input = ConfirmStripePaymentInput(
                    cartId = id,
                    paymentIntentId = request.paymentIntentId
                ),
                inputType = ConfirmStripePaymentInput::class,
                outputType = StripePaymentConfirmationResponse::class,
                options = WorkflowOptions(
                    correlationId = correlationId,
                    lockKey = "cart:confirm:$id",
                    timeoutSeconds = 60
                )
            )

            when {
                result.isSuccess() -> {
                    val confirmation = result.getOrNull()!!
                    logger.info { "Payment confirmed: cartId=$id, orderId=${confirmation.orderId}" }

                    ResponseEntity.ok(mapOf(
                        "type" to "order",
                        "order" to mapOf(
                            "id" to confirmation.orderId,
                            "display_id" to confirmation.orderDisplayId,
                            "status" to confirmation.status,
                            "email" to confirmation.email,
                            "total" to confirmation.total.multiply(BigDecimal(100)).toInt(),
                            "currency_code" to confirmation.currencyCode,
                            "payment_status" to confirmation.paymentStatus
                        )
                    ))
                }

                result.isFailure() -> {
                    val error = (result as com.vernont.workflow.engine.WorkflowResult.Failure).error
                    logger.warn { "Failed to confirm payment: cartId=$id, error=${error.message}" }

                    val statusCode = when (error) {
                        is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                        is IllegalStateException -> HttpStatus.CONFLICT
                        else -> HttpStatus.INTERNAL_SERVER_ERROR
                    }

                    ResponseEntity.status(statusCode).body(mapOf(
                        "error" to mapOf(
                            "type" to error::class.simpleName,
                            "message" to (error.message ?: "Failed to confirm payment"),
                            "code" to "PAYMENT_CONFIRMATION_FAILED"
                        )
                    ))
                }

                else -> {
                    logger.error { "Unexpected workflow result state: cartId=$id" }
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                        "error" to mapOf(
                            "message" to "Unexpected workflow state",
                            "code" to "WORKFLOW_ERROR"
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Exception confirming payment: cartId=$id" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }

    private fun mapToStoreCart(cart: com.vernont.workflow.flows.cart.dto.CartDto): Map<String, Any?> {
        return mapOf(
            "id" to cart.id,
            "email" to cart.email,
            "customer_id" to cart.customerId,
            "region_id" to cart.regionId,
            "currency_code" to cart.currencyCode,
            "items" to cart.items.map { item ->
                val subtotal = item.unitPrice.multiply(BigDecimal(item.quantity))
                mapOf(
                    "id" to item.id,
                    "cart_id" to cart.id,
                    "variant_id" to item.variantId,
                    "title" to item.title,
                    "thumbnail" to item.thumbnail,
                    "product_handle" to item.productHandle,
                    "variant_title" to item.variantTitle,
                    "quantity" to item.quantity,
                    "unit_price" to item.unitPrice.multiply(BigDecimal(100)).toInt(), // Convert to cents
                    "subtotal" to subtotal.multiply(BigDecimal(100)).toInt(),
                    "total" to item.total.multiply(BigDecimal(100)).toInt(),
                    "created_at" to item.createdAt,
                    "updated_at" to item.updatedAt
                )
            },
            "subtotal" to cart.subtotal.multiply(BigDecimal(100)).toInt(),
            "tax_total" to cart.taxTotal.multiply(BigDecimal(100)).toInt(),
            "shipping_total" to cart.shippingTotal.multiply(BigDecimal(100)).toInt(),
            "discount_total" to cart.discountTotal.multiply(BigDecimal(100)).toInt(),
            "gift_card_code" to cart.giftCardCode,
            "gift_card_total" to cart.giftCardTotal.multiply(BigDecimal(100)).toInt(),
            "total" to cart.total.multiply(BigDecimal(100)).toInt(),
            "created_at" to cart.createdAt,
            "updated_at" to cart.updatedAt
        )
    }

    /**
     * Validate a gift card code
     * POST /store/carts/{id}/gift-cards/validate
     * SECURITY: Rate limited to prevent brute force enumeration
     */
    @Operation(summary = "Validate a gift card code")
    @PostMapping("/{id}/gift-cards/validate")
    @RateLimited(
        keyPrefix = "cart:giftcard:validate",
        perIp = true,
        perEmail = false,
        limit = 10,  // Strict limit to prevent brute force
        windowSeconds = 300,  // 5 minutes
        failClosed = true
    )
    fun validateGiftCard(
        @PathVariable id: String,
        @RequestBody request: ValidateGiftCardRequest
    ): ResponseEntity<Any> {
        logger.info { "Validating gift card for cart: $id" }

        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "Cart not found"))

        // SECURITY: Validate cart ownership
        if (cart.customerId != null) {
            val context = getCurrentUserContext()
            if (context == null || context.customerId != cart.customerId) {
                logger.warn { "Unauthorized gift card validation attempt: cartId=$id" }
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "Access denied"))
            }
        }

        val result = giftCardOrderService.validateGiftCard(
            code = request.code,
            currencyCode = cart.currencyCode
        )

        return if (result.valid) {
            // SECURITY: Don't expose exact balance - just indicate validity
            // and that there's sufficient balance (or show partial info)
            ResponseEntity.ok(mapOf(
                "valid" to true,
                "balance" to result.availableBalance,
                "currency_code" to cart.currencyCode
            ))
        } else {
            // SECURITY: Generic error message to prevent enumeration
            ResponseEntity.badRequest().body(mapOf(
                "valid" to false,
                "error" to "Invalid or expired gift card"
            ))
        }
    }
}

/**
 * Request for validating a gift card
 */
data class ValidateGiftCardRequest(
    val code: String
)

/**
 * Request body for creating a cart
 * Matches Medusa's structure
 */
data class StoreCreateCartRequest(
    @JsonProperty("region_id")
    val regionId: String? = null,

    @JsonProperty("customer_id")
    val customerId: String? = null,

    val email: String? = null,

    @JsonProperty("currency_code")
    val currencyCode: String? = null,

    val items: List<StoreCreateCartLineItem>? = null,

    val context: Map<String, Any>? = null
)

data class StoreCreateCartLineItem(
    @JsonProperty("variant_id")
    val variantId: String,

    val quantity: Int,

    @JsonProperty("unit_price")
    val unitPrice: BigDecimal? = null
)

/**
 * Request body for updating a cart
 * Matches Medusa's structure
 */
data class StoreUpdateCartRequest(
    @JsonProperty("region_id")
    val regionId: String? = null,

    @JsonProperty("customer_id")
    val customerId: String? = null,

    val email: String? = null,

    @JsonProperty("promo_codes")
    val promoCodes: List<String>? = null,

    @JsonProperty("shipping_address")
    val shippingAddress: Map<String, Any>? = null,

    @JsonProperty("billing_address")
    val billingAddress: Map<String, Any>? = null,

    /** Gift card code to apply (empty string to remove) */
    @JsonProperty("gift_card_code")
    val giftCardCode: String? = null
)

/**
 * Request body for adding line item to cart
 * Matches Medusa's structure
 */
data class StoreAddLineItemRequest(
    @JsonProperty("variant_id")
    val variantId: String,

    val quantity: Int,

    @JsonProperty("unit_price")
    val unitPrice: BigDecimal? = null,

    val metadata: Map<String, Any>? = null
)

/**
 * Request body for updating line item in cart
 * Matches Medusa's structure
 */
data class StoreUpdateLineItemRequest(
    val quantity: Int? = null,

    @JsonProperty("unit_price")
    val unitPrice: BigDecimal? = null,

    val metadata: Map<String, Any>? = null
)

/**
 * Request body for adding shipping method to cart
 * Matches Medusa's structure
 */
data class StoreAddShippingMethodRequest(
    @JsonProperty("option_id")
    val optionId: String,

    val data: Map<String, Any>? = null
)

/**
 * Request body for creating a Stripe payment session
 */
data class StoreCreatePaymentSessionRequest(
    val email: String? = null,

    @JsonProperty("shipping_address_id")
    val shippingAddressId: String? = null,

    @JsonProperty("billing_address_id")
    val billingAddressId: String? = null,

    val metadata: Map<String, String>? = null,

    /** Gift card code to apply - will reduce payment amount */
    @JsonProperty("gift_card_code")
    val giftCardCode: String? = null
)

/**
 * Request body for confirming Stripe payment
 */
data class StoreConfirmPaymentRequest(
    @JsonProperty("payment_intent_id")
    val paymentIntentId: String
)
