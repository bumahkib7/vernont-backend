package com.vernont.repository.cart

import com.vernont.domain.cart.CartLineItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CartLineItemRepository : JpaRepository<CartLineItem, String> {

    fun findByCartId(cartId: String): List<CartLineItem>

    fun findByCartIdAndDeletedAtIsNull(cartId: String): List<CartLineItem>

    fun findByVariantId(variantId: String): List<CartLineItem>

    fun findByVariantIdAndDeletedAtIsNull(variantId: String): List<CartLineItem>

    fun findByIdAndDeletedAtIsNull(id: String): CartLineItem?

    fun findByDeletedAtIsNull(): List<CartLineItem>

    @Query("SELECT cli FROM CartLineItem cli WHERE cli.cart.id = :cartId AND cli.variantId = :variantId AND cli.deletedAt IS NULL")
    fun findByCartIdAndVariantId(@Param("cartId") cartId: String, @Param("variantId") variantId: String): CartLineItem?

    @Query("SELECT COUNT(cli) FROM CartLineItem cli WHERE cli.cart.id = :cartId AND cli.deletedAt IS NULL")
    fun countByCartId(@Param("cartId") cartId: String): Long

    @Query("SELECT SUM(cli.quantity) FROM CartLineItem cli WHERE cli.cart.id = :cartId AND cli.deletedAt IS NULL")
    fun sumQuantityByCartId(@Param("cartId") cartId: String): Long?

    @Query("SELECT SUM(cli.quantity) FROM CartLineItem cli WHERE cli.variantId = :variantId AND cli.deletedAt IS NULL")
    fun sumQuantityByVariantId(@Param("variantId") variantId: String): Long?

    @Query("SELECT cli FROM CartLineItem cli WHERE cli.cart.customerId = :customerId AND cli.deletedAt IS NULL")
    fun findByCustomerId(@Param("customerId") customerId: String): List<CartLineItem>
}
