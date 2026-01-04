package com.vernont.application.cart

import com.vernont.domain.cart.dto.*
import com.vernont.domain.cart.Cart
import com.vernont.domain.cart.CartLineItem
import com.vernont.events.CartCreated
import com.vernont.events.CartItemAdded
import com.vernont.events.CartItemRemoved
import com.vernont.events.EventPublisher
import com.vernont.repository.cart.CartLineItemRepository
import com.vernont.repository.cart.CartRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class CartService(
    private val cartRepository: CartRepository,
    private val cartLineItemRepository: CartLineItemRepository,
    private val eventPublisher: EventPublisher
) {

    /**
     * Create a new shopping cart
     */
    fun createCart(request: CreateCartRequest): CartResponse {
        logger.info { "Creating cart for customer: ${request.customerId ?: request.email ?: "guest"}" }

        // Check if customer already has an active cart
        val existingCart = request.customerId?.let { 
            cartRepository.findActiveCartByCustomerId(it)
        } ?: request.email?.let {
            cartRepository.findActiveCartByEmail(it)
        }

        if (existingCart != null) {
            logger.info { "Customer already has active cart: ${existingCart.id}" }
            return CartResponse.from(existingCart)
        }

        val cart = Cart().apply {
            customerId = request.customerId
            email = request.email
            regionId = request.regionId
            currencyCode = request.currencyCode
        }

        val saved = cartRepository.save(cart)

        eventPublisher.publish(
            CartCreated(
                aggregateId = saved.id,
                customerId = saved.customerId ?: ""
            )
        )

        logger.info { "Cart created: ${saved.id}" }
        return CartResponse.from(saved)
    }

    /**
     * Get cart by ID
     */
    @Transactional(readOnly = true)
    fun getCart(id: String): CartResponse {
        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(id)
            ?: throw CartNotFoundException("Cart not found: $id")

        return CartResponse.from(cart)
    }

    /**
     * Get active cart by customer ID
     */
    @Transactional(readOnly = true)
    fun getActiveCartByCustomerId(customerId: String): CartResponse {
        val cart = cartRepository.findActiveCartByCustomerId(customerId)
            ?: throw CartNotFoundException("No active cart found for customer: $customerId")

        return CartResponse.from(cart)
    }

    /**
     * Get active cart by email
     */
    @Transactional(readOnly = true)
    fun getActiveCartByEmail(email: String): CartResponse {
        val cart = cartRepository.findActiveCartByEmail(email)
            ?: throw CartNotFoundException("No active cart found for email: $email")

        return CartResponse.from(cart)
    }

    /**
     * Add item to cart with inventory validation
     */
    fun addItemToCart(cartId: String, request: AddCartItemRequest): CartResponse {
        logger.info { "Adding item to cart $cartId: variant ${request.variantId}" }

        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        if (cart.isCompleted()) {
            throw CartCompletedException("Cannot add items to completed cart: $cartId")
        }

        // Check if item already exists in cart
        val existingItem = cart.findItem(request.variantId)
        if (existingItem != null) {
            // Update quantity of existing item
            existingItem.increaseQuantity(request.quantity)
            cartLineItemRepository.save(existingItem)
            
            logger.info { "Increased quantity for existing item: ${existingItem.id}" }
        } else {
            // Create new item
            val item = CartLineItem().apply {
                variantId = request.variantId
                title = request.title
                description = request.description
                thumbnail = request.thumbnail
                quantity = request.quantity
                unitPrice = request.unitPrice
                currencyCode = request.currencyCode
                isGiftcard = request.isGiftcard
                allowDiscounts = request.allowDiscounts
                hasShipping = request.hasShipping
                recalculateTotal()
            }

            cart.addItem(item)
            cartLineItemRepository.save(item)

            eventPublisher.publish(
                CartItemAdded(
                    aggregateId = cartId,
                    customerId = cart.customerId ?: "",
                    productId = request.variantId,
                    quantity = request.quantity,
                    unitPrice = request.unitPrice,
                    totalPrice = item.total
                )
            )

            logger.info { "Added new item to cart: ${item.id}" }
        }

        val updated = cartRepository.save(cart)
        return CartResponse.from(updated)
    }

    /**
     * Update cart item quantity
     */
    fun updateCartItemQuantity(
        cartId: String, 
        itemId: String, 
        request: UpdateCartItemQuantityRequest
    ): CartResponse {
        logger.info { "Updating cart item $itemId quantity to ${request.quantity}" }

        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        if (cart.isCompleted()) {
            throw CartCompletedException("Cannot update completed cart: $cartId")
        }

        val item = cart.items.find { it.id == itemId }
            ?: throw CartItemNotFoundException("Item not found in cart: $itemId")

        item.updateQuantity(request.quantity)
        cartLineItemRepository.save(item)

        cart.recalculateTotals()
        val updated = cartRepository.save(cart)

        logger.info { "Cart item quantity updated: $itemId" }
        return CartResponse.from(updated)
    }

    /**
     * Remove item from cart
     */
    fun removeItemFromCart(cartId: String, itemId: String): CartResponse {
        logger.info { "Removing item $itemId from cart $cartId" }

        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        if (cart.isCompleted()) {
            throw CartCompletedException("Cannot remove items from completed cart: $cartId")
        }

        val item = cart.items.find { it.id == itemId }
            ?: throw CartItemNotFoundException("Item not found in cart: $itemId")

        val variantId = item.variantId
        val quantity = item.quantity
        val totalPrice = item.total

        cart.removeItem(item)
        item.softDelete()
        cartLineItemRepository.save(item)

        eventPublisher.publish(
            CartItemRemoved(
                aggregateId = cartId,
                customerId = cart.customerId ?: "",
                productId = variantId,
                quantity = quantity,
                totalPrice = totalPrice
            )
        )

        val updated = cartRepository.save(cart)

        logger.info { "Item removed from cart: $itemId" }
        return CartResponse.from(updated)
    }

    /**
     * Apply discount to cart item
     */
    fun applyItemDiscount(
        cartId: String, 
        itemId: String, 
        request: ApplyCartItemDiscountRequest
    ): CartResponse {
        logger.info { "Applying discount to cart item $itemId: ${request.discountAmount}" }

        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        val item = cart.items.find { it.id == itemId }
            ?: throw CartItemNotFoundException("Item not found in cart: $itemId")

        item.applyDiscount(request.discountAmount)
        cartLineItemRepository.save(item)

        cart.recalculateTotals()
        val updated = cartRepository.save(cart)

        logger.info { "Discount applied to cart item: $itemId" }
        return CartResponse.from(updated)
    }

    /**
     * Remove discount from cart item
     */
    fun removeItemDiscount(cartId: String, itemId: String): CartResponse {
        logger.info { "Removing discount from cart item $itemId" }

        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        val item = cart.items.find { it.id == itemId }
            ?: throw CartItemNotFoundException("Item not found in cart: $itemId")

        item.removeDiscount()
        cartLineItemRepository.save(item)

        cart.recalculateTotals()
        val updated = cartRepository.save(cart)

        logger.info { "Discount removed from cart item: $itemId" }
        return CartResponse.from(updated)
    }

    /**
     * Update cart details (addresses, shipping, payment methods)
     */
    fun updateCart(cartId: String, request: UpdateCartRequest): CartResponse {
        logger.info { "Updating cart details: $cartId" }

        val cart = cartRepository.findByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        if (cart.isCompleted()) {
            throw CartCompletedException("Cannot update completed cart: $cartId")
        }

        cart.apply {
            request.shippingMethodId?.let { shippingMethodId = it }
            request.paymentMethodId?.let { paymentMethodId = it }
            request.note?.let { note = it }
            
            // Update customer info if provided
            if (request.customerId != null && request.email != null) {
                setCustomer(request.customerId!!, request.email!!)
            }
        }

        val updated = cartRepository.save(cart)

        logger.info { "Cart updated: $cartId" }
        return CartResponse.from(updated)
    }

    /**
     * Clear all items from cart
     */
    fun clearCart(cartId: String): CartResponse {
        logger.info { "Clearing cart: $cartId" }

        val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        if (cart.isCompleted()) {
            throw CartCompletedException("Cannot clear completed cart: $cartId")
        }

        cart.items.forEach { it.softDelete() }
        cart.clear()

        val updated = cartRepository.save(cart)

        logger.info { "Cart cleared: $cartId" }
        return CartResponse.from(updated)
    }

    /**
     * Mark cart as completed (typically when converted to order)
     */
    fun completeCart(cartId: String): CartResponse {
        logger.info { "Completing cart: $cartId" }

        val cart = cartRepository.findByIdAndDeletedAtIsNull(cartId)
            ?: throw CartNotFoundException("Cart not found: $cartId")

        if (cart.isCompleted()) {
            throw CartCompletedException("Cart already completed: $cartId")
        }

        if (cart.isEmpty()) {
            throw EmptyCartException("Cannot complete empty cart: $cartId")
        }

        cart.complete()
        val completed = cartRepository.save(cart)

        logger.info { "Cart completed: $cartId" }
        return CartResponse.from(completed)
    }

    /**
     * Delete cart (soft delete)
     */
    fun deleteCart(cartId: String) {
        logger.info { "Deleting cart: $cartId" }

        val cart = cartRepository.findById(cartId)
            .orElseThrow { CartNotFoundException("Cart not found: $cartId") }

        cart.softDelete()
        cartRepository.save(cart)

        logger.info { "Cart deleted: $cartId" }
    }

    /**
     * List active carts with pagination
     */
    @Transactional(readOnly = true)
    fun listActiveCarts(pageable: Pageable): Page<CartSummaryResponse> {
        val page = cartRepository.findAll(pageable)
        return page.map { CartSummaryResponse.from(it) }
    }

    /**
     * Get cart statistics
     */
    @Transactional(readOnly = true)
    fun getCartStatistics(): CartStatistics {
        val activeCount = cartRepository.countActiveCarts()
        val allCarts = cartRepository.findAllActive()
        
        val totalValue = allCarts
            .fold(BigDecimal.ZERO) { acc, cart -> acc.add(cart.total) }
        
        val averageValue = if (activeCount > 0) {
            totalValue.divide(BigDecimal(activeCount), 2, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return CartStatistics(
            activeCartsCount = activeCount,
            totalValue = totalValue,
            averageCartValue = averageValue
        )
    }
}

/**
 * Cart statistics data class
 */
data class CartStatistics(
    val activeCartsCount: Long,
    val totalValue: BigDecimal,
    val averageCartValue: BigDecimal
)

// Custom exceptions
class CartNotFoundException(message: String) : RuntimeException(message)
class CartCompletedException(message: String) : RuntimeException(message)
class CartItemNotFoundException(message: String) : RuntimeException(message)
class EmptyCartException(message: String) : RuntimeException(message)
