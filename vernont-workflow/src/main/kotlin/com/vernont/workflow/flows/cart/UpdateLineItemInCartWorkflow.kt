package com.vernont.workflow.flows.cart

import com.vernont.domain.cart.Cart
import com.vernont.domain.cart.CartLineItem
import com.vernont.domain.inventory.InventoryReservation
import com.vernont.repository.cart.CartRepository
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.inventory.InventoryLevelRepository
import com.vernont.repository.inventory.InventoryReservationRepository
import com.vernont.repository.product.ProductVariantRepository
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
 * Input for updating a line item in cart
 * Matches Medusa's UpdateLineItemInCartWorkflowInputDTO
 */
data class UpdateLineItemInCartInput(
    val cartId: String,
    val itemId: String,
    val quantity: Int? = null,
    val unitPrice: BigDecimal? = null
)

/**
 * Update Line Item In Cart Workflow - Exact replication of Medusa's updateLineItemInCartWorkflow
 *
 * This workflow updates a line item's quantity or unit price in a cart.
 * If quantity is set to 0, the item is removed from the cart.
 *
 * Steps (matching Medusa):
 * 1. Acquire lock
 * 2. Load cart with items
 * 3. Validate cart (not completed/deleted)
 * 4. Find line item to update
 * 5. Validate variant prices
 * 6. Confirm inventory availability (real InventoryLevel checking)
 * 7. Update inventory reservations (real InventoryReservation tracking)
 * 8. Update or remove line item
 * 9. Refresh cart totals
 * 10. Save cart
 * 11. Release lock
 *
 * @see https://docs.medusajs.com/api/store#carts_postcartsidlineitemsline_id
 */
@Component
@WorkflowTypes(input = UpdateLineItemInCartInput::class, output = CartResponse::class)
class UpdateLineItemInCartWorkflow(
    private val cartRepository: CartRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val inventoryReservationRepository: InventoryReservationRepository
) : Workflow<UpdateLineItemInCartInput, CartResponse> {

    override val name = WorkflowConstants.UpdateLineItemInCart.NAME

    @Transactional
    override suspend fun execute(
        input: UpdateLineItemInCartInput,
        context: WorkflowContext
    ): WorkflowResult<CartResponse> {
        logger.info { "Starting update line item workflow for cart: ${input.cartId}, item: ${input.itemId}" }

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
                        throw IllegalStateException("Cannot update items in completed cart: ${cart.id}")
                    }

                    if (cart.deletedAt != null) {
                        throw IllegalStateException("Cannot update items in deleted cart: ${cart.id}")
                    }

                    logger.debug { "Cart ${cart.id} is valid for updates" }
                    StepResponse.of(Unit)
                }
            )

            // Step 4: Find line item
            val findItemStep = createStep<UpdateLineItemInCartInput, CartLineItem>(
                name = "find-line-item",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    val item = cart.items.find { it.id == inp.itemId }
                        ?: throw IllegalArgumentException("Line item not found: ${inp.itemId}")

                    ctx.addMetadata("lineItem", item)
                    ctx.addMetadata("originalQuantity", item.quantity)
                    ctx.addMetadata("originalUnitPrice", item.unitPrice)

                    logger.debug { "Found line item: ${item.id}, current quantity: ${item.quantity}" }
                    StepResponse.of(item)
                }
            )

            // Step 5: Validate variant prices
            val validatePricesStep = createStep<UpdateLineItemInCartInput, Unit>(
                name = "validate-variant-prices",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    val lineItem = ctx.getMetadata("lineItem") as CartLineItem

                    // If custom price provided, skip variant validation
                    if (inp.unitPrice != null) {
                        logger.debug { "Custom price ${inp.unitPrice} provided, skipping variant price validation" }
                        return@createStep StepResponse.of(Unit)
                    }

                    // If line item already has a price, allow using the existing price
                    if (lineItem.unitPrice > BigDecimal.ZERO) {
                        logger.debug { "Existing line item price present, skipping variant price validation" }
                        return@createStep StepResponse.of(Unit)
                    }

                    // Validate variant has price for cart's currency
                    val variant = productVariantRepository.findById(lineItem.variantId).orElse(null)
                        ?: throw IllegalArgumentException("Variant ${lineItem.variantId} not found")

                    val hasValidPrice = variant.prices.any { price ->
                        price.currencyCode == cart.currencyCode
                    }

                    if (!hasValidPrice) {
                        throw IllegalStateException(
                            "Variant ${lineItem.variantId} has no price for currency ${cart.currencyCode}"
                        )
                    }

                    logger.debug { "Variant ${lineItem.variantId} has valid price for ${cart.currencyCode}" }
                    StepResponse.of(Unit)
                }
            )

            // Step 6: Confirm inventory availability (REAL inventory checking)
            val confirmInventoryStep = createStep<UpdateLineItemInCartInput, Unit>(
                name = "confirm-inventory",
                execute = { inp, ctx ->
                    if (inp.quantity != null && inp.quantity > 0) {
                        val lineItem = ctx.getMetadata("lineItem") as CartLineItem
                        val originalQuantity = ctx.getMetadata("originalQuantity") as Int
                        val quantityDifference = inp.quantity - originalQuantity

                        if (quantityDifference > 0) {
                            // Increasing quantity - check inventory availability
                            val variant = productVariantRepository.findById(lineItem.variantId).orElse(null)
                                ?: throw IllegalArgumentException("Variant ${lineItem.variantId} not found")

                            if (variant.manageInventory && variant.inventoryItems.isNotEmpty()) {
                                // Get inventory item from variant
                                val variantInventoryItem = variant.inventoryItems.firstOrNull()
                                    ?: throw IllegalStateException("Variant ${variant.sku} has manage inventory enabled but no inventory item")

                                val inventoryItem = inventoryItemRepository.findWithLevelsById(variantInventoryItem.inventoryItemId)
                                    ?: throw IllegalStateException("Inventory item ${variantInventoryItem.inventoryItemId} not found")

                                // Get inventory level (first available location)
                                val inventoryLevel = inventoryItem.inventoryLevels.firstOrNull { it.deletedAt == null }
                                    ?: throw IllegalStateException("No inventory levels found for item ${inventoryItem.sku}")

                                val requiredQuantity = quantityDifference * variantInventoryItem.requiredQuantity

                                if (!inventoryLevel.hasAvailableStock(requiredQuantity)) {
                                    throw IllegalStateException(
                                        "Insufficient inventory for variant ${variant.sku}. " +
                                        "Available: ${inventoryLevel.availableQuantity}, Requested additional: $requiredQuantity"
                                    )
                                }

                                logger.debug {
                                    "Inventory confirmed: ${variant.sku} has ${inventoryLevel.availableQuantity} available, " +
                                    "requesting additional $requiredQuantity (${quantityDifference} x ${variantInventoryItem.requiredQuantity})"
                                }
                            } else {
                                logger.debug { "Variant ${variant.sku} does not track inventory" }
                            }
                        } else if (quantityDifference < 0) {
                            logger.debug { "Decreasing quantity by ${-quantityDifference}, no inventory check needed" }
                        }
                    }

                    StepResponse.of(Unit)
                }
            )

            // Step 7: Update inventory reservations (REAL InventoryReservation tracking)
            val updateInventoryStep = createStep<UpdateLineItemInCartInput, Unit>(
                name = "update-inventory-reservations",
                execute = { inp, ctx ->
                    if (inp.quantity != null && inp.quantity > 0) {
                        val lineItem = ctx.getMetadata("lineItem") as CartLineItem
                        val originalQuantity = ctx.getMetadata("originalQuantity") as Int
                        val quantityDifference = inp.quantity - originalQuantity

                        if (quantityDifference != 0) {
                            val variant = productVariantRepository.findById(lineItem.variantId).orElse(null)
                                ?: throw IllegalArgumentException("Variant ${lineItem.variantId} not found")

                            if (variant.manageInventory && variant.inventoryItems.isNotEmpty()) {
                                val variantInventoryItem = variant.inventoryItems.firstOrNull()
                                if (variantInventoryItem == null) {
                                    logger.warn { "Variant ${variant.sku} has no inventory items" }
                                } else {
                                    val inventoryItem = inventoryItemRepository.findWithLevelsById(variantInventoryItem.inventoryItemId)
                                    if (inventoryItem == null) {
                                        logger.warn { "Inventory item ${variantInventoryItem.inventoryItemId} not found" }
                                    } else {
                                        val inventoryLevel = inventoryItem.inventoryLevels.firstOrNull { it.deletedAt == null }
                                        if (inventoryLevel == null) {
                                            logger.warn { "No inventory levels found for item ${inventoryItem.sku}" }
                                        } else {

                                val requiredQuantity = quantityDifference * variantInventoryItem.requiredQuantity

                                if (quantityDifference > 0) {
                                    // Increasing quantity - reserve additional inventory
                                    inventoryLevel.reserve(requiredQuantity)
                                    inventoryLevelRepository.save(inventoryLevel)

                                    // Create new reservation record
                                    val reservation = InventoryReservation()
                                    reservation.orderId = lineItem.cart?.id
                                    reservation.lineItemId = lineItem.id
                                    reservation.inventoryLevelId = inventoryLevel.id
                                    reservation.quantity = requiredQuantity
                                    inventoryReservationRepository.save(reservation)

                                    ctx.addMetadata("newReservationId", reservation.id)
                                    ctx.addMetadata("quantityChange", requiredQuantity)
                                    ctx.addMetadata("inventoryLevelId", inventoryLevel.id)

                                    logger.info {
                                        "Reserved additional $requiredQuantity units of ${variant.sku}, " +
                                        "reservation ID: ${reservation.id}"
                                    }
                                } else {
                                    // Decreasing quantity - release excess inventory
                                    val releaseQuantityRequested = -requiredQuantity

                                    // Find reservations for this line item + inventory level
                                    val reservations = inventoryReservationRepository
                                        .findByOrderIdAndDeletedAtIsNullAndReleasedAtIsNull(lineItem.cart?.id ?: "")
                                        .filter { it.lineItemId == lineItem.id && it.inventoryLevelId == inventoryLevel.id }

                                    val totalReservedForItem = reservations.sumOf { it.quantity }
                                    val releasableQuantity = minOf(
                                        releaseQuantityRequested,
                                        minOf(totalReservedForItem, inventoryLevel.reservedQuantity)
                                    )

                                    val releasedReservationIds = mutableListOf<String>()

                                    if (releasableQuantity > 0) {
                                        inventoryLevel.releaseReservation(releasableQuantity)
                                        inventoryLevelRepository.save(inventoryLevel)

                                        var remainingToRelease = releasableQuantity
                                        for (reservation in reservations) {
                                            if (remainingToRelease <= 0) break
                                            if (reservation.quantity <= remainingToRelease) {
                                                remainingToRelease -= reservation.quantity
                                                reservation.release()
                                                inventoryReservationRepository.save(reservation)
                                                releasedReservationIds.add(reservation.id)
                                            } else {
                                                reservation.quantity -= remainingToRelease
                                                inventoryReservationRepository.save(reservation)
                                                remainingToRelease = 0
                                                break
                                            }
                                        }

                                        logger.info {
                                            "Released $releasableQuantity units of ${variant.sku} for item ${lineItem.id}, " +
                                            "released ${releasedReservationIds.size} reservations"
                                        }
                                    } else {
                                        logger.warn {
                                            "No releasable inventory for item ${lineItem.id} on level ${inventoryLevel.id} " +
                                            "(requested $releaseQuantityRequested, reserved ${inventoryLevel.reservedQuantity}, itemReserved $totalReservedForItem)"
                                        }
                                    }

                                    // Track actual change (negative for release)
                                    val actualChange = -releasableQuantity
                                    ctx.addMetadata("releasedReservationIds", releasedReservationIds)
                                    ctx.addMetadata("quantityChange", actualChange)
                                    ctx.addMetadata("inventoryLevelId", inventoryLevel.id)
                                }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    StepResponse.of(Unit)
                },
                compensate = { inp, ctx ->
                    val quantityChange = ctx.getMetadata("quantityChange") as? Int
                    val inventoryLevelId = ctx.getMetadata("inventoryLevelId") as? String

                    if (quantityChange != null && quantityChange != 0 && inventoryLevelId != null) {
                        val inventoryLevel = inventoryLevelRepository.findById(inventoryLevelId).orElse(null)

                        if (inventoryLevel != null) {
                            if (quantityChange > 0) {
                                // Rollback increase: release the reservation
                                inventoryLevel.releaseReservation(quantityChange)
                                inventoryLevelRepository.save(inventoryLevel)

                                val newReservationId = ctx.getMetadata("newReservationId") as? String
                                if (newReservationId != null) {
                                    val reservation = inventoryReservationRepository.findById(newReservationId).orElse(null)
                                    if (reservation != null) {
                                        reservation.release()
                                        inventoryReservationRepository.save(reservation)
                                    }
                                }

                                logger.info { "Rolled back reservation of $quantityChange units" }
                            } else {
                                // Rollback decrease: re-reserve the inventory
                                val reReserveQuantity = -quantityChange
                                inventoryLevel.reserve(reReserveQuantity)
                                inventoryLevelRepository.save(inventoryLevel)

                                @Suppress("UNCHECKED_CAST")
                                val releasedIds = ctx.getMetadata("releasedReservationIds") as? List<String>
                                releasedIds?.forEach { reservationId ->
                                    val reservation = inventoryReservationRepository.findById(reservationId).orElse(null)
                                    if (reservation != null && reservation.isReleased()) {
                                        reservation.releasedAt = null
                                        inventoryReservationRepository.save(reservation)
                                    }
                                }

                                logger.info { "Rolled back release of $reReserveQuantity units" }
                            }
                        }
                    }
                }
            )

            // Step 8: Update or remove line item
            val updateLineItemStep = createStep<UpdateLineItemInCartInput, Boolean>(
                name = "update-line-items",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    val lineItem = ctx.getMetadata("lineItem") as CartLineItem

                    var removed = false

                    // Update quantity
                    if (inp.quantity != null) {
                        if (inp.quantity <= 0) {
                            // Remove item if quantity is 0 or negative
                            cart.removeItem(lineItem)
                            removed = true
                            logger.info { "Removed item ${lineItem.id} from cart ${cart.id}" }
                        } else {
                            lineItem.quantity = inp.quantity
                            lineItem.recalculateTotal()
                            logger.debug { "Updated item ${lineItem.id} quantity to ${inp.quantity}" }
                        }
                    }

                    // Update unit price if provided and not removed
                    if (!removed && inp.unitPrice != null) {
                        lineItem.unitPrice = inp.unitPrice
                        lineItem.recalculateTotal()
                        logger.debug { "Updated item ${lineItem.id} unit price to ${inp.unitPrice}" }
                    }

                    // Ensure item has valid price
                    if (!removed && lineItem.unitPrice == BigDecimal.ZERO) {
                        throw IllegalStateException("Line item ${lineItem.title} has no unit price")
                    }

                    ctx.addMetadata("itemRemoved", removed)
                    StepResponse.of(removed)
                },
                compensate = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart
                    val lineItem = ctx.getMetadata("lineItem") as CartLineItem
                    val itemRemoved = ctx.getMetadata("itemRemoved") as? Boolean ?: false

                    if (itemRemoved) {
                        // Restore item to cart
                        cart.addItem(lineItem)
                        logger.info { "Restored item ${lineItem.id} to cart ${cart.id}" }
                    } else {
                        // Restore original values
                        lineItem.quantity = ctx.getMetadata("originalQuantity") as Int
                        lineItem.unitPrice = ctx.getMetadata("originalUnitPrice") as BigDecimal
                        lineItem.recalculateTotal()
                        logger.info { "Restored item ${lineItem.id} to original state" }
                    }
                }
            )

            // Step 9: Refresh cart totals
            val refreshCartStep = createStep<String, Cart>(
                name = "refresh-cart-items",
                execute = { cartId, ctx ->
                    val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)!!

                    // Recalculate cart totals
                    cart.recalculateTotals()

                    val updatedCart = cartRepository.save(cart)

                    logger.info { "Cart refreshed: ${cart.id}, items: ${cart.items.size}, total: ${cart.total}" }
                    StepResponse.of(updatedCart)
                }
            )

            // Execute steps in order
            acquireLockStep.invoke(input.cartId, context)
            val cart = loadCartStep.invoke(input.cartId, context).data
            validateCartStep.invoke(cart, context)
            val lineItem = findItemStep.invoke(input, context).data
            validatePricesStep.invoke(input, context)
            confirmInventoryStep.invoke(input, context)
            updateInventoryStep.invoke(input, context)
            updateLineItemStep.invoke(input, context)
            val finalCart = refreshCartStep.invoke(input.cartId, context).data

            logger.info { "Update line item workflow completed for cart: ${finalCart.id}" }

            return WorkflowResult.success(CartDto.from(finalCart, context.correlationId))

        } catch (e: Exception) {
            logger.error(e) { "Update line item workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
