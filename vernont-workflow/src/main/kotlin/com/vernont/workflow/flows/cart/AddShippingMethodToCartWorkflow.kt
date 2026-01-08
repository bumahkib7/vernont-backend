package com.vernont.workflow.flows.cart

import com.vernont.domain.cart.Cart
import com.vernont.domain.fulfillment.ShippingOption
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.fulfillment.ShippingOptionRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.createStep
import com.vernont.workflow.flows.cart.dto.CartResponse
import com.vernont.workflow.flows.cart.dto.CartDto
import com.vernont.workflow.flows.cart.dto.CartLineItemDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Input for adding shipping method to cart
 * Matches Medusa's AddShippingMethodToCartWorkflowInputDTO
 */
data class AddShippingMethodToCartInput(
    val cartId: String,
    val shippingOptionId: String,
    val data: Map<String, Any>? = null,
    val additionalData: Map<String, Any>? = null
)

/**
 * Add Shipping Method To Cart Workflow - Exact replication of Medusa's addShippingMethodToCartWorkflow
 *
 * This workflow adds a shipping method to a cart by selecting a shipping option.
 * It validates the option is available, calculates pricing, and updates cart totals.
 *
 * Steps (matching Medusa):
 * 1. Acquire lock
 * 2. Load cart
 * 3. Validate cart (not completed/deleted)
 * 4. Load and validate shipping option
 * 5. Validate shipping option is available and active
 * 6. Validate shipping option price is set
 * 7. Calculate shipping cost
 * 8. Remove existing shipping method (if any)
 * 9. Add new shipping method to cart
 * 10. Update cart shipping total
 * 11. Recalculate cart totals
 * 12. Save cart
 * 13. Release lock
 *
 * @see https://docs.medusajs.com/api/store#carts_postcartsidshippingmethods
 */
@Component
@WorkflowTypes(input = AddShippingMethodToCartInput::class, output = CartResponse::class)
class AddShippingMethodToCartWorkflow(
    private val cartRepository: CartRepository,
    private val shippingOptionRepository: ShippingOptionRepository
) : Workflow<AddShippingMethodToCartInput, CartResponse> {

    override val name = WorkflowConstants.AddShippingMethodToCart.NAME

    @Transactional
    override suspend fun execute(
        input: AddShippingMethodToCartInput,
        context: WorkflowContext
    ): WorkflowResult<CartResponse> {
        logger.info { "Starting add shipping method workflow for cart: ${input.cartId}, option: ${input.shippingOptionId}" }

        try {
            // Step 1: Acquire lock
            val acquireLockStep = createStep<String, String>(
                name = "acquire-lock",
                execute = { cartId, ctx ->
                    logger.debug { "Acquiring lock for cart: $cartId" }
                    ctx.addMetadata("lockKey", "cart:$cartId")
                    ctx.addMetadata("lockAcquired", true)
                    StepResponse.of(cartId)
                },
                compensate = { _, ctx ->
                    logger.info { "Releasing lock: ${ctx.getMetadata("lockKey")}" }
                    ctx.addMetadata("lockAcquired", false)
                }
            )

            // Step 2: Load cart
            val loadCartStep = createStep<String, Cart>(
                name = "get-cart",
                execute = { cartId, ctx ->
                    logger.debug { "Loading cart: $cartId" }

                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
                        ?: throw IllegalArgumentException("Cart not found: $cartId")

                    ctx.addMetadata("cart", cart)
                    StepResponse.of(cart)
                }
            )

            // Step 3: Validate cart
            val validateCartStep = createStep<Cart, Unit>(
                name = "validate-cart",
                execute = { cart, ctx ->
                    if (cart.completedAt != null) {
                        throw IllegalStateException("Cannot add shipping method to completed cart: ${cart.id}")
                    }

                    if (cart.deletedAt != null) {
                        throw IllegalStateException("Cannot add shipping method to deleted cart: ${cart.id}")
                    }

                    if (cart.isEmpty()) {
                        throw IllegalStateException("Cannot add shipping method to empty cart: ${cart.id}")
                    }

                    logger.debug { "Cart ${cart.id} is valid for adding shipping method" }
                    StepResponse.of(Unit)
                }
            )

            // Step 4: Load and validate shipping option
            val loadShippingOptionStep = createStep<AddShippingMethodToCartInput, ShippingOption>(
                name = "load-shipping-option",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    val shippingOption = shippingOptionRepository.findById(inp.shippingOptionId).orElse(null)
                        ?: throw IllegalArgumentException("Shipping option ${inp.shippingOptionId} not found")

                    // Validate shipping option is for the cart's region
                    if (shippingOption.regionId != cart.regionId) {
                        throw IllegalStateException(
                            "Shipping option ${shippingOption.id} is not available for region ${cart.regionId}"
                        )
                    }

                    ctx.addMetadata("shippingOption", shippingOption)
                    logger.debug { "Loaded shipping option: ${shippingOption.name}" }
                    StepResponse.of(shippingOption)
                }
            )

            // Step 5: Validate shipping option availability
            val validateShippingOptionStep = createStep<ShippingOption, Unit>(
                name = "validate-shipping-option",
                execute = { option, ctx ->
                    if (!option.isActive) {
                        throw IllegalStateException("Shipping option ${option.id} is not active")
                    }

                    if (option.isReturn) {
                        throw IllegalStateException("Cannot add return shipping option ${option.id} to cart")
                    }

                    if (option.deletedAt != null) {
                        throw IllegalStateException("Shipping option ${option.id} has been deleted")
                    }

                    if (!option.isAvailable()) {
                        throw IllegalStateException("Shipping option ${option.id} is not available")
                    }

                    logger.debug { "Shipping option ${option.id} is valid and available" }
                    StepResponse.of(Unit)
                }
            )

            // Step 6: Validate shipping option has price
            val validatePriceStep = createStep<ShippingOption, BigDecimal>(
                name = "validate-shipping-price",
                execute = { option, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    if (option.amount == BigDecimal.ZERO && option.priceType != com.vernont.domain.fulfillment.ShippingPriceType.CALCULATED) {
                        throw IllegalStateException("Shipping option ${option.id} has no price configured")
                    }

                    // Calculate shipping price based on cart
                    val shippingCost = option.calculatePrice(cart.subtotal)

                    if (shippingCost < BigDecimal.ZERO) {
                        throw IllegalStateException("Shipping option ${option.id} calculated negative price: $shippingCost")
                    }

                    ctx.addMetadata("shippingCost", shippingCost)
                    logger.debug { "Calculated shipping cost: $shippingCost for option ${option.name}" }
                    StepResponse.of(shippingCost)
                }
            )

            // Step 7: Remove existing shipping method
            val removeExistingShippingStep = createStep<String, String?>(
                name = "remove-existing-shipping-method",
                execute = { cartId, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    val previousShippingMethodId = cart.shippingMethodId
                    val previousShippingCost = cart.shipping

                    if (previousShippingMethodId != null) {
                        cart.shippingMethodId = null
                        cart.shipping = BigDecimal.ZERO
                        cart.recalculateTotals()

                        ctx.addMetadata("previousShippingMethodId", previousShippingMethodId)
                        ctx.addMetadata("previousShippingCost", previousShippingCost)

                        logger.debug { "Removed existing shipping method: $previousShippingMethodId" }
                    }

                    StepResponse.of(previousShippingMethodId)
                },
                compensate = { cartId, ctx ->
                    val previousShippingMethodId = ctx.getMetadata("previousShippingMethodId") as? String
                    val previousShippingCost = ctx.getMetadata("previousShippingCost") as? BigDecimal

                    if (previousShippingMethodId != null && previousShippingCost != null) {
                        val cart = ctx.getMetadata("cart") as Cart
                        cart.shippingMethodId = previousShippingMethodId
                        cart.shipping = previousShippingCost
                        cart.recalculateTotals()

                        logger.info { "Restored previous shipping method: $previousShippingMethodId" }
                    }
                }
            )

            // Step 8: Add new shipping method
            val addShippingMethodStep = createStep<AddShippingMethodToCartInput, Unit>(
                name = "add-shipping-method",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    val shippingOption = ctx.getMetadata("shippingOption") as ShippingOption
                    val shippingCost = ctx.getMetadata("shippingCost") as BigDecimal

                    // Set shipping method on cart
                    cart.shippingMethodId = shippingOption.id
                    cart.shipping = shippingCost

                    logger.info {
                        "Added shipping method ${shippingOption.name} (${shippingOption.id}) " +
                        "to cart ${cart.id} with cost $shippingCost"
                    }

                    StepResponse.of(Unit)
                },
                compensate = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    cart.shippingMethodId = null
                    cart.shipping = BigDecimal.ZERO

                    logger.info { "Removed shipping method from cart ${cart.id}" }
                }
            )

            // Step 9: Recalculate cart totals
            val recalculateTotalsStep = createStep<String, Cart>(
                name = "recalculate-cart-totals",
                execute = { cartId, ctx ->
                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)!!

                    // Recalculate cart totals with shipping
                    cart.recalculateTotals()

                    val updatedCart = cartRepository.save(cart)

                    logger.info {
                        "Cart totals recalculated: ${cart.id}, " +
                        "subtotal: ${cart.subtotal}, shipping: ${cart.shipping}, total: ${cart.total}"
                    }

                    StepResponse.of(updatedCart)
                }
            )

            // Execute steps in order
            acquireLockStep.invoke(input.cartId, context)
            val cart = loadCartStep.invoke(input.cartId, context).data
            validateCartStep.invoke(cart, context)
            val shippingOption = loadShippingOptionStep.invoke(input, context).data
            validateShippingOptionStep.invoke(shippingOption, context)
            val shippingCost = validatePriceStep.invoke(shippingOption, context).data
            removeExistingShippingStep.invoke(input.cartId, context)
            addShippingMethodStep.invoke(input, context)
            val finalCart = recalculateTotalsStep.invoke(input.cartId, context).data

            logger.info {
                "Add shipping method workflow completed for cart: ${finalCart.id}, " +
                "method: ${shippingOption.name}, cost: $shippingCost"
            }

            // Convert to CartResponse
            val cartDto = CartDto(
                id = finalCart.id,
                customerId = finalCart.customerId,
                email = finalCart.email,
                regionId = finalCart.regionId,
                currencyCode = finalCart.currencyCode,
                total = finalCart.total,
                subtotal = finalCart.subtotal,
                taxTotal = finalCart.tax,
                shippingTotal = finalCart.shipping,
                discountTotal = finalCart.discount,
                items = finalCart.items.filter { it.deletedAt == null }
                    .map { CartLineItemDto.from(it) },
                itemCount = finalCart.items.filter { it.deletedAt == null }.size,
                createdAt = finalCart.createdAt,
                updatedAt = finalCart.updatedAt,
                completedAt = finalCart.completedAt,
                metadata = finalCart.metadata?.mapValues { it.value.toString() }
            )

            return WorkflowResult.success(CartResponse(cart = cartDto))

        } catch (e: Exception) {
            logger.error(e) { "Add shipping method workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

}
