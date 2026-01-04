package com.vernont.workflow.flows.cart

import com.vernont.domain.cart.Cart
import com.vernont.domain.cart.CartLineItem
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
import com.vernont.workflow.flows.cart.dto.CartDto
import com.vernont.workflow.flows.cart.dto.CartResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.min

private val logger = KotlinLogging.logger {}

/**
 * Input for removing line items from cart
 * Matches Medusa's DeleteLineItemsWorkflowInputDTO
 */
data class RemoveLineItemFromCartInput(
    val cartId: String,
    val itemIds: List<String>,
    val additionalData: Map<String, Any>? = null
)

/**
 * Remove Line Item From Cart Workflow - Exact replication of Medusa's deleteLineItemsWorkflow
 *
 * This workflow removes one or more line items from a cart.
 * It releases inventory reservations and recalculates cart totals.
 *
 * Steps (matching Medusa):
 * 1. Acquire lock
 * 2. Load cart
 * 3. Validate cart (not completed/deleted)
 * 4. Find line items to remove
 * 5. Release inventory reservations (REAL InventoryLevel + InventoryReservation)
 * 6. Soft delete line items
 * 7. Refresh cart totals
 * 8. Save cart
 * 9. Release lock
 *
 * @see https://docs.medusajs.com/api/store#carts_deletecartsidlineitemsline_id
 */
@Component
@WorkflowTypes(input = RemoveLineItemFromCartInput::class, output = CartResponse::class)
class RemoveLineItemFromCartWorkflow(
    private val cartRepository: CartRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val inventoryReservationRepository: InventoryReservationRepository
) : Workflow<RemoveLineItemFromCartInput, CartResponse> {

    override val name = WorkflowConstants.RemoveLineItemFromCart.NAME

    @Transactional
    override suspend fun execute(
        input: RemoveLineItemFromCartInput,
        context: WorkflowContext
    ): WorkflowResult<CartResponse> {
        logger.info { "Starting remove line items workflow for cart: ${input.cartId}, items: ${input.itemIds}" }

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
                        throw IllegalStateException("Cannot remove items from completed cart: ${cart.id}")
                    }

                    if (cart.deletedAt != null) {
                        throw IllegalStateException("Cannot remove items from deleted cart: ${cart.id}")
                    }

                    logger.debug { "Cart ${cart.id} is valid for removing items" }
                    StepResponse.of(Unit)
                }
            )

            // Step 4: Find line items to remove
            val findItemsStep = createStep<RemoveLineItemFromCartInput, List<CartLineItem>>(
                name = "find-line-items",
                execute = { inp, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    val itemsToRemove = mutableListOf<CartLineItem>()
                    val notFoundIds = mutableListOf<String>()

                    inp.itemIds.forEach { itemId ->
                        val item = cart.items.find { it.id == itemId && it.deletedAt == null }
                        if (item != null) {
                            itemsToRemove.add(item)
                        } else {
                            notFoundIds.add(itemId)
                        }
                    }

                    if (notFoundIds.isNotEmpty()) {
                        logger.warn { "Line items not found: $notFoundIds" }
                    }

                    ctx.addMetadata("itemsToRemove", itemsToRemove)
                    logger.debug { "Found ${itemsToRemove.size} line items to remove" }
                    StepResponse.of(itemsToRemove)
                }
            )

            // Step 5: Release inventory reservations (REAL inventory logic)
            val releaseInventoryStep = createStep<List<CartLineItem>, Unit>(
                name = "release-inventory-reservations",
                execute = { items, ctx ->
                    val releasedReservations = mutableListOf<Pair<String, Int>>() // levelId to quantity

                    items.forEach { item ->
                        val variant = productVariantRepository.findById(item.variantId).orElse(null)

                        if (variant != null && variant.manageInventory && variant.inventoryItems.isNotEmpty()) {
                            val variantInventoryItem = variant.inventoryItems.firstOrNull()
                            if (variantInventoryItem != null) {
                                val inventoryItem = inventoryItemRepository.findWithLevelsById(variantInventoryItem.inventoryItemId)
                                if (inventoryItem != null) {
                                    val inventoryLevel = inventoryItem.inventoryLevels.firstOrNull { it.deletedAt == null }
                                    if (inventoryLevel != null) {
                                        val quantityToRelease = item.quantity * variantInventoryItem.requiredQuantity

                                        // Release reservations for this item/level, but cap at what is actually reserved
                                        val reservations = inventoryReservationRepository
                                            .findByOrderIdAndDeletedAtIsNullAndReleasedAtIsNull(item.cart?.id ?: "")
                                            .filter { it.lineItemId == item.id && it.inventoryLevelId == inventoryLevel.id }

                                        val totalReservedForItem = reservations.sumOf { it.quantity }
                                        val releasableQuantity = min(
                                            quantityToRelease,
                                            min(totalReservedForItem, inventoryLevel.reservedQuantity)
                                        )

                                        if (releasableQuantity <= 0) {
                                            logger.warn {
                                                "No releasable quantity for item ${item.id} on level ${inventoryLevel.id} " +
                                                "(requested $quantityToRelease, reserved ${inventoryLevel.reservedQuantity}, itemReserved $totalReservedForItem)"
                                            }
                                        } else {
                                            // Release inventory counts
                                            inventoryLevel.releaseReservation(releasableQuantity)
                                            inventoryLevelRepository.save(inventoryLevel)

                                            // Release reservation records up to releasableQuantity
                                            var remainingToRelease = releasableQuantity
                                            reservations.forEach { reservation ->
                                                if (remainingToRelease > 0) {
                                                    if (reservation.quantity <= remainingToRelease) {
                                                        remainingToRelease -= reservation.quantity
                                                        reservation.release()
                                                        inventoryReservationRepository.save(reservation)
                                                    } else {
                                                        reservation.quantity -= remainingToRelease
                                                        inventoryReservationRepository.save(reservation)
                                                        remainingToRelease = 0
                                                    }
                                                }
                                            }

                                            releasedReservations.add(Pair(inventoryLevel.id, releasableQuantity))
                                        }

                                        logger.info {
                                            "Released $releasableQuantity units of ${variant.sku} for item ${item.id}, " +
                                            "released ${reservations.size} reservations"
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ctx.addMetadata("releasedReservations", releasedReservations)
                    StepResponse.of(Unit)
                },
                compensate = { items, ctx ->
                    @Suppress("UNCHECKED_CAST")
                    val releasedReservations = ctx.getMetadata("releasedReservations") as? List<Pair<String, Int>>

                    releasedReservations?.forEach { (levelId, quantity) ->
                        val inventoryLevel = inventoryLevelRepository.findById(levelId).orElse(null)
                        if (inventoryLevel != null) {
                            // Re-reserve the inventory
                            inventoryLevel.reserve(quantity)
                            inventoryLevelRepository.save(inventoryLevel)

                            logger.info { "Rolled back release of $quantity units for level $levelId" }
                        }
                    }

                    // Re-mark reservations as active
                    items.forEach { item ->
                        val reservations = inventoryReservationRepository
                            .findByOrderIdAndDeletedAtIsNullAndReleasedAtIsNull(item.cart?.id ?: "")
                            .filter { it.lineItemId == item.id && it.isReleased() }

                        reservations.forEach { reservation ->
                            reservation.releasedAt = null
                            inventoryReservationRepository.save(reservation)
                        }
                    }
                }
            )

            // Step 6: Soft delete line items
            val deleteItemsStep = createStep<List<CartLineItem>, Unit>(
                name = "delete-line-items",
                execute = { items, ctx ->
                    val cart = ctx.getMetadata("cart") as Cart

                    items.forEach { item ->
                        item.deletedAt = Instant.now()
                        cart.removeItem(item)
                        logger.debug { "Soft deleted line item: ${item.id}" }
                    }

                    ctx.addMetadata("deletedItemIds", items.map { it.id })
                    logger.info { "Soft deleted ${items.size} line items" }
                    StepResponse.of(Unit)
                },
                compensate = { items, ctx ->
                    items.forEach { item ->
                        // Restore item
                        item.deletedAt = null
                        val cart = ctx.getMetadata("cart") as Cart
                        cart.addItem(item)
                        logger.info { "Restored line item: ${item.id}" }
                    }
                }
            )

            // Step 7: Refresh cart totals
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
            val itemsToRemove = findItemsStep.invoke(input, context).data

            if (itemsToRemove.isNotEmpty()) {
                releaseInventoryStep.invoke(itemsToRemove, context)
                deleteItemsStep.invoke(itemsToRemove, context)
                val finalCart = refreshCartStep.invoke(input.cartId, context).data

                logger.info { "Remove line items workflow completed for cart: ${finalCart.id}" }

                return WorkflowResult.success(CartDto.from(finalCart, context.correlationId))
            } else {
                logger.info { "No items to remove, returning cart as is" }
                return WorkflowResult.success(CartDto.from(cart, context.correlationId))
            }

        } catch (e: Exception) {
            logger.error(e) { "Remove line items workflow failed: ${e.message}" }
            return WorkflowResult.failure(e)
        }
    }
}
