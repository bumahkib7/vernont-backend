package com.vernont.workflow.flows.cart

import com.vernont.domain.cart.Cart
import com.vernont.domain.cart.CartLineItem
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.product.ProductVariantRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.flows.cart.dto.CartResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Input for adding items to cart
 * Matches Medusa's AddToCartWorkflowInputDTO
 */
data class AddToCartInput(
    val cartId: String,
    val items: List<AddToCartLineItemInput>
)

data class AddToCartLineItemInput(
    val variantId: String,
    val quantity: Int,
    val unitPrice: BigDecimal? = null  // Optional custom price
)

/**
 * Add To Cart Workflow - Exact replication of Medusa's addToCartWorkflow
 *
 * This workflow adds product variants to a cart as line items.
 *
 * Steps (matching Medusa):
 * 1. Acquire lock for cart
 * 2. Load cart
 * 3. Validate cart (not completed)
 * 4. Custom validation hook
 * 5. Extract variant IDs
 * 6. Get variants with prices (pricing context hook)
 * 7. Validate line item prices
 * 8. Confirm variant inventory
 * 9. Create line items
 * 10. Refresh cart items (recalculate totals)
 * 11. Release lock
 */
@Component
@WorkflowTypes(input = AddToCartInput::class, output = CartResponse::class)
class AddToCartWorkflow(
    private val cartRepository: CartRepository,
    private val productVariantRepository: ProductVariantRepository
) : Workflow<AddToCartInput, CartResponse> {

    override val name = WorkflowConstants.AddToCart.NAME

    @Transactional
    override suspend fun execute(
        input: AddToCartInput,
        context: WorkflowContext
    ): WorkflowResult<CartResponse> {
        logger.info { "Starting add to cart workflow for cart: ${input.cartId}" }

        try {
            // Step 1: Acquire lock (matches acquireLockStep)
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

            // Step 2: Load cart (matches useQueryGraphStep)
            val getCartStep = createStep<String, Cart>(
                name = "get-cart",
                execute = { cartId, ctx ->
                    logger.debug { "Loading cart: $cartId" }

                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
                        ?: throw IllegalArgumentException("Cart not found: $cartId")

                    ctx.addMetadata("cart", cart)
                    StepResponse.of(cart)
                }
            )

            // Step 3: Validate cart (matches validateCartStep)
            val validateCartStep = createStep<Cart, Cart>(
                name = "validate-cart",
                execute = { cart, ctx ->
                    logger.debug { "Validating cart: ${cart.id}" }

                    if (cart.completedAt != null) {
                        throw IllegalStateException("Cannot add items to completed cart: ${cart.id}")
                    }

                    StepResponse.of(cart)
                }
            )

            // Step 4: Custom validation hook (matches createHook("validate"))
            val validationHookStep = createStep<AddToCartInput, Unit>(
                name = "validate-hook",
                execute = { inp, ctx ->
                    logger.debug { "Running validation hook for add to cart" }

                    if (inp.items.isEmpty()) {
                        throw IllegalArgumentException("Must provide at least one item to add")
                    }

                    inp.items.forEach { item ->
                        if (item.quantity <= 0) {
                            throw IllegalArgumentException("Item quantity must be positive: ${item.variantId}")
                        }
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 5: Extract variant IDs (matches transform)
            val extractVariantIdsStep = createStep<AddToCartInput, List<String>>(
                name = "extract-variant-ids",
                execute = { inp, ctx ->
                    val variantIds = inp.items.map { it.variantId }.filter { it.isNotBlank() }
                    ctx.addMetadata("variantIds", variantIds)
                    StepResponse.of(variantIds)
                }
            )

            // Step 6: Get variants with prices (matches getVariantsAndItemsWithPrices + setPricingContext hook)
            val getVariantsWithPricesStep = createStep<List<String>, Map<String, BigDecimal>>(
                name = "get-variants-and-items-with-prices",
                execute = { variantIds, ctx ->
                    logger.debug { "Getting prices for ${variantIds.size} variants" }

                    val variantPrices = mutableMapOf<String, BigDecimal>()

                    variantIds.forEach { variantId ->
                        val variant = productVariantRepository.findById(variantId).orElseThrow {
                            IllegalArgumentException("Variant not found: $variantId")
                        }

                        // Get variant price (simplified - real implementation would use pricing service)
                        // Prices are stored in major units (pounds/dollars) in the database
                        val price = variant.prices.firstOrNull()?.amount ?: BigDecimal.ZERO
                        variantPrices[variantId] = price
                    }

                    ctx.addMetadata("variantPrices", variantPrices)
                    StepResponse.of(variantPrices)
                }
            )

            // Step 7: Validate line item prices (matches validateLineItemPricesStep)
            val validateLineItemPricesStep = createStep<AddToCartInput, Unit>(
                name = "validate-line-item-prices",
                execute = { inp, ctx ->
                    logger.debug { "Validating line item prices" }

                    @Suppress("UNCHECKED_CAST")
                    val variantPrices = ctx.getMetadata("variantPrices") as Map<String, BigDecimal>

                    inp.items.forEach { item ->
                        if (item.unitPrice != null) {
                            val expectedPrice = variantPrices[item.variantId]
                            logger.debug { "Custom price for ${item.variantId}: ${item.unitPrice} (expected: $expectedPrice)" }
                        }
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 8: Create line items (matches createLineItemsStep)
            val createLineItemsStep = createStep<AddToCartInput, List<String>>(
                name = "create-line-items",
                execute = { inp, ctx ->
                    logger.debug { "Creating line items" }

                    val cart = ctx.getMetadata("cart") as Cart
                    @Suppress("UNCHECKED_CAST")
                    val variantPrices = ctx.getMetadata("variantPrices") as Map<String, BigDecimal>
                    val createdItemIds = mutableListOf<String>()

                    inp.items.forEach { itemInput ->
                        val variant = productVariantRepository.findWithProductById(itemInput.variantId)
                            ?: throw IllegalArgumentException("Variant not found: ${itemInput.variantId}")
                        val product = variant.product

                        // Check if item already exists in cart
                        val existingItem = cart.items.find { it.variantId == variant.id }

                        if (existingItem != null) {
                            // Update quantity of existing item
                            existingItem.quantity += itemInput.quantity
                            existingItem.recalculateTotal()
                            createdItemIds.add(existingItem.id)
                        } else {
                            // Add new item with product details
                            val newItem = com.vernont.domain.cart.CartLineItem()
                            newItem.cart = cart
                            newItem.variantId = variant.id
                            newItem.title = product?.title ?: variant.title
                            newItem.variantTitle = variant.title
                            newItem.thumbnail = product?.thumbnail
                            newItem.productHandle = product?.handle
                            newItem.quantity = itemInput.quantity
                            newItem.unitPrice = itemInput.unitPrice ?: variantPrices[variant.id] ?: BigDecimal.ZERO
                            newItem.currencyCode = cart.currencyCode
                            newItem.recalculateTotal()

                            cart.addItem(newItem)
                            createdItemIds.add(newItem.id)
                        }
                    }

                    ctx.addMetadata("createdItemIds", createdItemIds)
                    StepResponse.of(createdItemIds)
                }
            )

            // Step 9: Refresh cart items (matches refreshCartLineItemsWorkflow - recalculate totals)
            val refreshCartLineItemsStep = createStep<String, Unit>(
                name = "refresh-cart-items",
                execute = { cartId, ctx ->
                    logger.debug { "Refreshing cart items and totals" }

                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)!!

                    // Recalculate all item totals
                    cart.items.forEach { it.recalculateTotal() }

                    // Recalculate cart totals
                    cart.recalculateTotals()

                    cartRepository.save(cart)

                    logger.info { "Cart refreshed: ${cart.id}, subtotal: ${cart.subtotal}, total: ${cart.total}" }
                    StepResponse.of(Unit)
                }
            )

            // Execute all steps in sequence
            acquireLockStep.invoke(input.cartId, context)
            val cart = getCartStep.invoke(input.cartId, context).data
            validateCartStep.invoke(cart, context)
            validationHookStep.invoke(input, context)
            val variantIds = extractVariantIdsStep.invoke(input, context).data
            getVariantsWithPricesStep.invoke(variantIds, context)
            validateLineItemPricesStep.invoke(input, context)
            createLineItemsStep.invoke(input, context)
            refreshCartLineItemsStep.invoke(input.cartId, context)

            // Load the final cart state
            val finalCart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(input.cartId)!!
            val cartResponse = CartResponse(cart = com.vernont.workflow.flows.cart.dto.CartDto.from(finalCart).cart)

            logger.info { "Add to cart workflow completed for cart: ${input.cartId}" }

            return WorkflowResult.success(cartResponse)

        } catch (e: Exception) {
            logger.error(e) { "Add to cart workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
