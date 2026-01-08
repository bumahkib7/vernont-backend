package com.vernont.api.controller.store

import com.vernont.repository.cart.CartRepository
import com.vernont.repository.fulfillment.ShippingOptionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

/**
 * Store Shipping Controller - Medusa-compatible API
 * Implements Medusa's shipping endpoints for the storefront
 */
@RestController
@RequestMapping("/store")
@CrossOrigin(origins = ["http://localhost:8000", "http://localhost:9000", "http://localhost:3000"])
@Tag(name = "Store Shipping", description = "Shipping management endpoints for storefront")
class ShippingController(
    private val shippingOptionRepository: ShippingOptionRepository,
    private val cartRepository: CartRepository
) {

    /**
     * List shipping options for a cart
     * GET /store/shipping-options?cart_id=:id
     *
     * Medusa-compatible endpoint that returns available shipping options for a cart
     */
    @Operation(summary = "List shipping options")
    @GetMapping("/shipping-options")
    fun listShippingOptions(
        @RequestParam(name = "cart_id", required = false) cartId: String?
    ): ResponseEntity<Any> {
        logger.info { "Listing shipping options for cart: $cartId" }

        return try {
            // If cart ID is provided, get shipping options for the cart's region
            val shippingOptions = if (cartId != null) {
                val cart = cartRepository.findWithItemsByIdAndDeletedAtIsNull(cartId)
                if (cart == null) {
                    logger.warn { "Cart not found: $cartId" }
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                        "error" to mapOf(
                            "message" to "Cart not found: $cartId",
                            "code" to "CART_NOT_FOUND"
                        )
                    ))
                }

                // Get shipping options for the cart's region (with provider eagerly loaded)
                shippingOptionRepository.findActiveByRegionId(cart.regionId)
                    .filter { !it.isReturn } // Exclude return shipping options
                    .filter { it.isAvailable() }
            } else {
                // No cart ID provided, return all active shipping options
                shippingOptionRepository.findByIsActiveAndDeletedAtIsNull(true)
                    .filter { !it.isReturn }
                    .filter { it.isAvailable() }
            }

            val shippingOptionDtos = shippingOptions.map { option ->
                mapOf(
                    "id" to option.id,
                    "name" to option.name,
                    "price_type" to option.priceType.name.lowercase(),
                    "amount" to option.amount.multiply(BigDecimal(100)).toInt(), // Convert to cents for storefront compatibility
                    "is_return" to option.isReturn,
                    "admin_only" to false, // Not tracked in current model
                    "provider_id" to option.provider?.id,
                    "region_id" to option.regionId,
                    "data" to option.data,
                    "metadata" to option.metadata
                )
            }

            logger.info { "Found ${shippingOptionDtos.size} shipping options" }

            ResponseEntity.ok(mapOf(
                "shipping_options" to shippingOptionDtos
            ))

        } catch (e: Exception) {
            logger.error(e) { "Exception listing shipping options" }

            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "error" to mapOf(
                    "message" to "Internal server error",
                    "code" to "INTERNAL_ERROR"
                )
            ))
        }
    }
}
