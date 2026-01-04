package com.vernont.workflow.flows.cart

import com.vernont.domain.cart.Cart
import com.vernont.domain.promotion.Discount
import com.vernont.events.EventPublisher
import com.vernont.events.CartPromotionAppliedEvent
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.promotion.DiscountRepository
import com.vernont.repository.promotion.PromotionRepository
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import com.vernont.workflow.common.WorkflowConstants
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Input for updating cart promotions
 */
data class UpdateCartPromotionsInput(
    val cartId: String,
    val promoCodes: List<String>? = null,
    val correlationId: String? = null
)

/**
 * Output with applied promotions
 */
data class UpdateCartPromotionsOutput(
    val cartId: String,
    val appliedPromotions: List<AppliedPromotion>,
    val discountTotal: BigDecimal,
    val invalidCodes: List<InvalidPromotionCode>
)

data class AppliedPromotion(
    val code: String,
    val type: String,
    val value: Double,
    val amount: BigDecimal
)

data class InvalidPromotionCode(
    val code: String,
    val reason: String
)

/**
 * Update Cart Promotions Workflow - Production Ready
 *
 * This workflow applies promotion codes to a cart, validates them, calculates discounts,
 * and updates the cart total. It handles multiple promo codes and validates them against
 * business rules.
 *
 * Based on Medusa's updateCartPromotionsWorkflow
 *
 * Steps:
 * 1. Fetch cart and validate it exists and is not completed
 * 2. Fetch and validate promotion codes
 * 3. Remove existing cart discounts
 * 4. Apply valid promotions to cart
 * 5. Calculate discount amounts based on cart subtotal
 * 6. Update cart discount total and recalculate cart totals
 * 7. Emit promotion applied events
 * 8. Return applied promotions with discount details
 *
 * Business Rules:
 * - Cart must exist and not be completed
 * - Promotion codes must be valid (active, not expired, not disabled)
 * - Promotions must apply to the cart's region
 * - Usage limits are enforced
 * - Multiple promotions can be applied (stacking rules apply)
 * - Invalid codes are tracked and returned for user feedback
 *
 * @see https://docs.medusajs.com/resources/commerce-modules/cart/promotions
 */
@Component
@WorkflowTypes(input = UpdateCartPromotionsInput::class, output = UpdateCartPromotionsOutput::class)
class UpdateCartPromotionsWorkflow(
    private val cartRepository: CartRepository,
    private val promotionRepository: PromotionRepository,
    private val discountRepository: DiscountRepository,
    private val eventPublisher: EventPublisher
) : Workflow<UpdateCartPromotionsInput, UpdateCartPromotionsOutput> {

    override val name = WorkflowConstants.UpdateCartPromotions.NAME

    @Transactional
    override suspend fun execute(
        input: UpdateCartPromotionsInput,
        context: WorkflowContext
    ): WorkflowResult<UpdateCartPromotionsOutput> {
        logger.info { "Starting update cart promotions workflow for cart: ${input.cartId}" }

        try {
            // Step 1: Fetch and validate cart
            val fetchCartStep = createStep<String, Cart>(
                name = "fetch-and-validate-cart",
                execute = { cartId, ctx ->
                    logger.debug { "Fetching cart: $cartId" }

                    val cart = cartRepository.findById(cartId).orElseThrow {
                        CartNotFoundException("Cart not found: $cartId")
                    }

                    if (cart.completedAt != null) {
                        throw IllegalStateException("Cannot apply promotions to completed cart: $cartId")
                    }

                    if (cart.deletedAt != null) {
                        throw IllegalStateException("Cannot apply promotions to deleted cart: $cartId")
                    }

                    ctx.addMetadata("cart", cart)
                    ctx.addMetadata("originalDiscountTotal", cart.discountTotal)
                    logger.debug { "Cart fetched. Subtotal: ${cart.subtotal}, Current discount: ${cart.discountTotal}" }

                    StepResponse.of(cart, cart.id)
                }
            )

            // Step 2: Validate promotion codes
            val validatePromotionsStep = createStep<List<String>, ValidatedPromotions>(
                name = "validate-promotion-codes",
                execute = { promoCodes, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    logger.debug { "Validating ${promoCodes.size} promotion codes for cart: ${cart.id}" }

                    val validPromotions = mutableListOf<com.vernont.domain.promotion.Promotion>()
                    val invalidCodes = mutableListOf<InvalidPromotionCode>()

                    promoCodes.forEach { code ->
                        try {
                            val promotion = promotionRepository.findByCodeAndDeletedAtIsNull(code)

                            if (promotion == null) {
                                invalidCodes.add(InvalidPromotionCode(
                                    code = code,
                                    reason = "Promotion code not found"
                                ))
                                logger.warn { "Promotion code not found: $code" }
                                return@forEach
                            }

                            // Validate promotion
                            val validationResult = validatePromotion(promotion, cart)
                            if (validationResult.isValid) {
                                validPromotions.add(promotion)
                                logger.debug { "Promotion code valid: $code" }
                            } else {
                                invalidCodes.add(InvalidPromotionCode(
                                    code = code,
                                    reason = validationResult.reason ?: "Invalid promotion"
                                ))
                                logger.warn { "Promotion code invalid: $code - ${validationResult.reason}" }
                            }

                        } catch (e: Exception) {
                            logger.error(e) { "Error validating promotion code: $code" }
                            invalidCodes.add(InvalidPromotionCode(
                                code = code,
                                reason = "Error validating promotion: ${e.message}"
                            ))
                        }
                    }

                    ctx.addMetadata("validPromotions", validPromotions)
                    ctx.addMetadata("invalidCodes", invalidCodes)

                    logger.info {
                        "Promotion validation complete. Valid: ${validPromotions.size}, Invalid: ${invalidCodes.size}"
                    }

                    StepResponse.of(ValidatedPromotions(validPromotions, invalidCodes))
                }
            )

            // Step 3: Remove existing cart discounts
            val removeExistingDiscountsStep = createStep<Cart, List<String>>(
                name = "remove-existing-discounts",
                execute = { cart, ctx ->
                    logger.debug { "Removing existing discounts from cart: ${cart.id}" }

                    val existingDiscounts = discountRepository.findByCartIdAndDeletedAtIsNull(cart.id)
                    val removedDiscountIds = existingDiscounts.map { it.id }

                    existingDiscounts.forEach { discount ->
                        // Decrement usage count if promotion exists
                        discount.promotion?.let { promotion ->
                            promotion.decrementUsage()
                            promotionRepository.save(promotion)
                        }

                        // Soft delete the discount
                        discountRepository.delete(discount)
                    }

                    logger.debug { "Removed ${existingDiscounts.size} existing discounts" }

                    StepResponse.of(removedDiscountIds, existingDiscounts)
                },
                compensate = { cart, ctx ->
                    // Compensation: Restore removed discounts
                    logger.warn { "Compensating: Restoring removed discounts for cart: ${cart.id}" }
                    @Suppress("UNCHECKED_CAST")
                    val removedDiscounts = ctx.getMetadata("removedDiscounts") as? List<Discount>
                    removedDiscounts?.forEach { discount ->
                        discount.deletedAt = null
                        discountRepository.save(discount)
                        discount.promotion?.let { promotion ->
                            promotion.incrementUsage()
                            promotionRepository.save(promotion)
                        }
                    }
                }
            )

            // Step 4: Apply valid promotions and calculate discounts
            val applyPromotionsStep = createStep<ValidatedPromotions, List<Discount>>(
                name = "apply-promotions-and-calculate",
                execute = { validated, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    logger.debug { "Applying ${validated.valid.size} valid promotions to cart: ${cart.id}" }

                    val appliedDiscounts = mutableListOf<Discount>()

                    validated.valid.forEach { promotion ->
                        try {
                            // Create discount from promotion
                            val discount = Discount.fromPromotion(promotion).apply {
                                this.cartId = cart.id
                                this.isValid = true
                                this.isApplied = false
                            }

                            // Calculate discount amount based on cart subtotal
                            discount.updateAmount(cart.subtotal)

                            // Save discount
                            val savedDiscount = discountRepository.save(discount)

                            // Mark discount as applied
                            savedDiscount.apply()
                            discountRepository.save(savedDiscount)

                            // Increment promotion usage
                            promotion.incrementUsage()
                            promotionRepository.save(promotion)

                            appliedDiscounts.add(savedDiscount)

                            logger.debug {
                                "Applied promotion: ${promotion.code}, amount: ${savedDiscount.amount}"
                            }

                        } catch (e: Exception) {
                            logger.error(e) { "Error applying promotion: ${promotion.code}" }
                        }
                    }

                    ctx.addMetadata("appliedDiscounts", appliedDiscounts)
                    logger.info { "Applied ${appliedDiscounts.size} promotions to cart" }

                    StepResponse.of(appliedDiscounts, appliedDiscounts.map { it.id })
                },
                compensate = { validated, ctx ->
                    // Compensation: Remove applied discounts
                    logger.warn { "Compensating: Removing applied discounts" }
                    @Suppress("UNCHECKED_CAST")
                    val appliedDiscountIds = ctx.getMetadata("appliedDiscountIds") as? List<String>
                    appliedDiscountIds?.forEach { id ->
                        discountRepository.findById(id).ifPresent { discount ->
                            discount.promotion?.let { promotion ->
                                promotion.decrementUsage()
                                promotionRepository.save(promotion)
                            }
                            discountRepository.delete(discount)
                        }
                    }
                }
            )

            // Step 5: Update cart totals with new discounts
            val updateCartTotalsStep = createStep<List<Discount>, Cart>(
                name = "update-cart-totals",
                execute = { discounts, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    logger.debug { "Updating cart totals for cart: ${cart.id}" }

                    // Calculate total discount amount
                    val totalDiscount = discounts.sumOf { it.amount }

                    // Update cart discount fields
                    cart.discount = totalDiscount
                    cart.discountTotal = totalDiscount

                    // Recalculate cart total
                    cart.recalculateTotals()

                    // Save updated cart
                    val savedCart = cartRepository.save(cart)

                    logger.info {
                        "Cart totals updated. Discount: $totalDiscount, New total: ${savedCart.total}"
                    }

                    StepResponse.of(savedCart, ctx.getMetadata("originalDiscountTotal"))
                },
                compensate = { discounts, ctx ->
                    // Compensation: Restore original cart totals
                    logger.warn { "Compensating: Restoring original cart totals" }
                    val cart = ctx.getMetadata("cart") as Cart
                    val originalDiscount = ctx.getMetadata("originalDiscountTotal") as? BigDecimal ?: BigDecimal.ZERO
                    cart.discount = originalDiscount
                    cart.discountTotal = originalDiscount
                    cart.recalculateTotals()
                    cartRepository.save(cart)
                }
            )

            // Step 6: Emit promotion applied events
            val emitEventsStep = createStep<List<Discount>, List<Discount>>(
                name = "emit-promotion-events",
                execute = { discounts, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    discounts.forEach { discount ->
                        try {
                            eventPublisher.publish(
                                CartPromotionAppliedEvent(
                                    aggregateId = cart.id,
                                    cartId = cart.id,
                                    promotionCode = discount.code,
                                    discountAmount = discount.amount,
                                    timestamp = discount.createdAt
                                )
                            )
                            logger.debug { "Published CartPromotionApplied event for: ${discount.code}" }
                        } catch (e: Exception) {
                            logger.warn(e) { "Failed to publish event for promotion: ${discount.code}" }
                        }
                    }

                    StepResponse.of(discounts)
                }
            )

            // Execute all steps
            val cart = fetchCartStep.invoke(input.cartId, context).data

            // If no promo codes provided, just remove existing discounts and return
            if (input.promoCodes.isNullOrEmpty()) {
                removeExistingDiscountsStep.invoke(cart, context)
                cart.discount = BigDecimal.ZERO
                cart.discountTotal = BigDecimal.ZERO
                cart.recalculateTotals()
                cartRepository.save(cart)

                logger.info { "No promo codes provided. Removed all discounts from cart: ${cart.id}" }

                return WorkflowResult.success(
                    UpdateCartPromotionsOutput(
                        cartId = cart.id,
                        appliedPromotions = emptyList(),
                        discountTotal = BigDecimal.ZERO,
                        invalidCodes = emptyList()
                    )
                )
            }

            val validated = validatePromotionsStep.invoke(input.promoCodes, context).data
            removeExistingDiscountsStep.invoke(cart, context)
            val appliedDiscounts = applyPromotionsStep.invoke(validated, context).data
            val updatedCart = updateCartTotalsStep.invoke(appliedDiscounts, context).data
            emitEventsStep.invoke(appliedDiscounts, context)

            // Prepare output
            val appliedPromotions = appliedDiscounts.map { discount ->
                AppliedPromotion(
                    code = discount.code,
                    type = discount.type.name,
                    value = discount.value,
                    amount = discount.amount
                )
            }

            logger.info {
                "Update cart promotions workflow completed. " +
                "Cart: ${updatedCart.id}, Applied: ${appliedPromotions.size}, " +
                "Invalid: ${validated.invalid.size}, Total discount: ${updatedCart.discountTotal}"
            }

            return WorkflowResult.success(
                UpdateCartPromotionsOutput(
                    cartId = updatedCart.id,
                    appliedPromotions = appliedPromotions,
                    discountTotal = updatedCart.discountTotal,
                    invalidCodes = validated.invalid
                )
            )

        } catch (e: CartNotFoundException) {
            logger.error { "Cart not found: ${e.message}" }
            return WorkflowResult.failure(e)
        } catch (e: Exception) {
            logger.error(e) { "Update cart promotions workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }

    /**
     * Validate a promotion against business rules
     */
    private fun validatePromotion(
        promotion: com.vernont.domain.promotion.Promotion,
        cart: Cart
    ): PromotionValidationResult {
        // Check if promotion is valid (active, not expired, not disabled)
        if (!promotion.isValid()) {
            return PromotionValidationResult(
                isValid = false,
                reason = when {
                    promotion.isDisabled -> "Promotion is disabled"
                    !promotion.isActive -> "Promotion is not active"
                    promotion.hasEnded() -> "Promotion has expired"
                    !promotion.hasStarted() -> "Promotion has not started yet"
                    promotion.hasReachedUsageLimit() -> "Promotion has reached usage limit"
                    else -> "Promotion is not valid"
                }
            )
        }

        // Check if promotion applies to the cart's region
        if (!promotion.appliesToRegion(cart.regionId)) {
            return PromotionValidationResult(
                isValid = false,
                reason = "Promotion is not valid for this region"
            )
        }

        // Check if cart has items (for non-free-shipping promotions)
        if (cart.items.isEmpty() && promotion.type != com.vernont.domain.promotion.PromotionType.FREE_SHIPPING) {
            return PromotionValidationResult(
                isValid = false,
                reason = "Cart is empty"
            )
        }

        return PromotionValidationResult(isValid = true, reason = null)
    }
}

/**
 * Internal data class for validated promotions
 */
private data class ValidatedPromotions(
    val valid: List<com.vernont.domain.promotion.Promotion>,
    val invalid: List<InvalidPromotionCode>
)

/**
 * Internal data class for promotion validation result
 */
private data class PromotionValidationResult(
    val isValid: Boolean,
    val reason: String?
)

/**
 * Exception for cart not found
 */
class CartNotFoundException(message: String) : Exception(message)
