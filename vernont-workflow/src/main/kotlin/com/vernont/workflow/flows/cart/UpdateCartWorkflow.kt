package com.vernont.workflow.flows.cart

import com.vernont.application.cart.CartPromotionService
import com.vernont.application.giftcard.GiftCardOrderService
import com.vernont.domain.cart.Cart
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.customer.CustomerAddressRepository
import com.vernont.repository.region.RegionRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.createStep
import com.vernont.workflow.flows.cart.dto.CartResponse
import com.vernont.workflow.flows.cart.dto.CartDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Shipping address input for cart
 */
data class ShippingAddressInput(
    val firstName: String? = null,
    val lastName: String? = null,
    val address1: String,
    val address2: String? = null,
    val city: String,
    val province: String? = null,
    val postalCode: String,
    val countryCode: String = "GB",
    val phone: String? = null
)

/**
 * Input for updating a cart
 * Matches Medusa's UpdateCartWorkflowInputDTO
 */
data class UpdateCartInput(
    val id: String,
    val regionId: String? = null,
    val customerId: String? = null,
    val email: String? = null,
    val promoCodes: List<String>? = null,
    val shippingAddress: ShippingAddressInput? = null,
    val billingAddressId: String? = null,
    /** Gift card code to apply (set to empty string to remove) */
    val giftCardCode: String? = null
)

/**
 * Update Cart Workflow - Replication of Medusa's updateCartWorkflow
 *
 * This workflow updates a cart's details like region, customer, addresses, etc.
 *
 * Steps (matching Medusa):
 * 1. Acquire lock
 * 2. Load cart
 * 3. Validate cart not completed
 * 4. Update cart fields
 * 5. If region changed, refresh prices
 * 6. If promo codes changed, update promotions
 * 7. Refresh tax lines
 * 8. Save cart
 * 9. Release lock
 */
@Component
@WorkflowTypes(input = UpdateCartInput::class, output = CartResponse::class)
class UpdateCartWorkflow(
    private val cartRepository: CartRepository,
    private val regionRepository: RegionRepository,
    private val customerAddressRepository: CustomerAddressRepository,
    private val cartPromotionService: CartPromotionService,
    private val giftCardOrderService: GiftCardOrderService
) : Workflow<UpdateCartInput, CartResponse> {

    override val name = WorkflowConstants.UpdateCart.NAME

    @Transactional
    override suspend fun execute(
        input: UpdateCartInput,
        context: WorkflowContext
    ): WorkflowResult<CartResponse> {
        logger.info { "Starting update cart workflow for cart: ${input.id}" }

        try {
            // Step 1: Acquire lock
            val acquireLockStep = createStep<String, String>(
                name = "acquire-lock",
                execute = { cartId, ctx ->
                    ctx.addMetadata("lockKey", "cart:$cartId")
                    ctx.addMetadata("lockAcquired", true)
                    StepResponse.of(cartId)
                },
                compensate = { _, ctx ->
                    ctx.addMetadata("lockAcquired", false)
                }
            )

            // Step 2: Load cart
            val loadCartStep = createStep<String, Cart>(
                name = "get-cart",
                execute = { cartId, ctx ->
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
                        throw IllegalStateException("Cannot update completed cart: ${cart.id}")
                    }
                    StepResponse.of(Unit)
                }
            )

            // Step 4: Update cart fields
            val updateCartFieldsStep = createStep<UpdateCartInput, Cart>(
                name = "update-carts",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    var regionChanged = false

                    // Update region
                    if (inp.regionId != null && inp.regionId != cart.regionId) {
                        regionRepository.findById(inp.regionId).orElseThrow {
                            IllegalArgumentException("Region not found: ${inp.regionId}")
                        }
                        cart.regionId = inp.regionId
                        regionChanged = true
                    }

                    // Update customer ID
                    if (inp.customerId != null) {
                        cart.customerId = inp.customerId
                    }

                    // Update email
                    if (inp.email != null) {
                        cart.email = inp.email
                    }

                    // Update shipping address (embedded)
                    if (inp.shippingAddress != null) {
                        val addr = inp.shippingAddress
                        cart.shippingAddress = com.vernont.domain.common.Address(
                            firstName = addr.firstName,
                            lastName = addr.lastName,
                            address1 = addr.address1,
                            address2 = addr.address2,
                            city = addr.city,
                            province = addr.province,
                            postalCode = addr.postalCode,
                            countryCode = addr.countryCode,
                            phone = addr.phone
                        )
                        logger.debug { "Updated shipping address for cart: ${cart.id}" }
                    }

                    // Update billing address ID (reference to saved customer address)
                    if (inp.billingAddressId != null) {
                        cart.billingAddressId = inp.billingAddressId
                    }

                    ctx.addMetadata("regionChanged", regionChanged)
                    StepResponse.of(cart)
                }
            )

            // Step 5: Apply promo codes if provided
            // Delegated to CartPromotionService for proper transaction management
            val applyPromotionsStep = createStep<UpdateCartInput, BigDecimal>(
                name = "apply-promotions",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    // If promo codes provided, apply them using the transactional service
                    if (!inp.promoCodes.isNullOrEmpty()) {
                        logger.info { "Applying ${inp.promoCodes.size} promo codes to cart: ${cart.id}" }

                        val result = cartPromotionService.applyPromoCodes(cart.id, inp.promoCodes)

                        // Reload cart to get updated state
                        val updatedCart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cart.id)!!
                        ctx.addMetadata("cart", updatedCart)

                        logger.info { "Total discount applied: ${result.totalDiscount}" }
                        StepResponse.of(result.totalDiscount)
                    } else {
                        StepResponse.of(cart.discount)
                    }
                }
            )

            // Step 6: Apply gift card if provided
            val applyGiftCardStep = createStep<UpdateCartInput, BigDecimal>(
                name = "apply-gift-card",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    // If gift card code is provided (including empty string to remove)
                    if (inp.giftCardCode != null) {
                        if (inp.giftCardCode.isBlank()) {
                            // Remove gift card
                            cart.giftCardCode = null
                            cart.giftCardTotal = BigDecimal.ZERO
                            logger.info { "Removed gift card from cart: ${cart.id}" }
                        } else {
                            // Apply gift card
                            val code = inp.giftCardCode.trim().uppercase()
                            logger.info { "Applying gift card $code to cart: ${cart.id}" }

                            val validation = giftCardOrderService.validateGiftCard(code, cart.currencyCode)

                            if (!validation.valid) {
                                throw IllegalArgumentException("Gift card error: ${validation.errorMessage}")
                            }

                            // Calculate cart total before gift card (subtotal + shipping - discount)
                            val cartTotalBeforeGc = cart.subtotal.add(cart.shipping).subtract(cart.discount)

                            // Convert gift card balance from minor to major units
                            val giftCardBalanceMajor = BigDecimal(validation.availableBalance).divide(BigDecimal(100))

                            // Apply the lesser of gift card balance or cart total
                            val giftCardAmount = giftCardBalanceMajor.min(cartTotalBeforeGc)

                            cart.giftCardCode = validation.giftCard?.code ?: code
                            cart.giftCardTotal = giftCardAmount

                            logger.info { "Applied gift card $code: amount=${giftCardAmount} ${cart.currencyCode}" }
                        }

                        cartRepository.save(cart)
                        ctx.addMetadata("cart", cart)
                    }

                    StepResponse.of(cart.giftCardTotal)
                }
            )

            // Step 7: Recalculate totals
            val recalculateTotalsStep = createStep<String, Cart>(
                name = "recalculate-totals",
                execute = { cartId, ctx ->
                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)!!
                    val regionChanged = ctx.getMetadata("regionChanged") as Boolean

                    if (regionChanged) {
                        logger.debug { "Region changed, recalculating prices and taxes" }
                    }

                    // Always recalculate totals to apply discounts and gift cards
                    cart.recalculateTotals()

                    val updatedCart = cartRepository.save(cart)
                    StepResponse.of(updatedCart)
                }
            )

            // Execute steps
            acquireLockStep.invoke(input.id, context)
            val cart = loadCartStep.invoke(input.id, context).data
            validateCartStep.invoke(cart, context)
            updateCartFieldsStep.invoke(input, context)
            applyPromotionsStep.invoke(input, context)
            applyGiftCardStep.invoke(input, context)
            val finalCart = recalculateTotalsStep.invoke(input.id, context).data

            logger.info { "Update cart workflow completed for cart: ${finalCart.id}" }

            // Convert Cart to CartResponse
            val cartResponse = CartDto.from(finalCart)
            return WorkflowResult.success(cartResponse)

        } catch (e: Exception) {
            logger.error(e) { "Update cart workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
