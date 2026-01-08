package com.vernont.domain.cart

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "cart",
    indexes = [
        Index(name = "idx_cart_customer_id", columnList = "customer_id"),
        Index(name = "idx_cart_email", columnList = "email"),
        Index(name = "idx_cart_region_id", columnList = "region_id"),
        Index(name = "idx_cart_completed_at", columnList = "completed_at"),
        Index(name = "idx_cart_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Cart.full",
    attributeNodes = [
        NamedAttributeNode(value = "items", subgraph = "itemsSubgraph")
    ],
    subgraphs = [
        NamedSubgraph(
            name = "itemsSubgraph",
            attributeNodes = [
                // NamedAttributeNode("variant") // Removed as CartLineItem does not have a 'variant' attribute
            ]
        )
    ]
)
@NamedEntityGraph(
    name = "Cart.withItems",
    attributeNodes = [
        NamedAttributeNode("items")
    ]
)
@NamedEntityGraph(
    name = "Cart.summary",
    attributeNodes = []
)
class Cart : BaseEntity() {

    @Column(name = "customer_id")
    var customerId: String? = null

    @Column
    var email: String? = null

    @Column(name = "region_id", nullable = false)
    var regionId: String = ""

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(precision = 19, scale = 4, nullable = false)
    var subtotal: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false)
    var tax: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false)
    var shipping: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false)
    var discount: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false)
    var total: BigDecimal = BigDecimal.ZERO

    // Additional properties required by workflows
    @Column(precision = 19, scale = 4, nullable = false)
    var taxAmount: BigDecimal = BigDecimal.ZERO

    @Column(precision = 4, scale = 4, nullable = false)
    var taxRate: BigDecimal = BigDecimal.ZERO

    @Column(name = "tax_code")
    var taxCode: String? = null

    @Column(precision = 19, scale = 4, nullable = false)
    var discountTotal: BigDecimal = BigDecimal.ZERO

    /** Applied gift card code */
    @Column(name = "gift_card_code", length = 19)
    var giftCardCode: String? = null

    /** Gift card amount to deduct (in major currency units) */
    @Column(name = "gift_card_total", precision = 19, scale = 4, nullable = false)
    var giftCardTotal: BigDecimal = BigDecimal.ZERO

    @OneToMany(mappedBy = "cart", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var items: MutableSet<CartLineItem> = mutableSetOf()

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "firstName", column = Column(name = "first_name", nullable = true)),
        AttributeOverride(name = "lastName", column = Column(name = "last_name", nullable = true)),
        AttributeOverride(name = "address1", column = Column(name = "address_1", nullable = true)),
        AttributeOverride(name = "address2", column = Column(name = "address_2", nullable = true)),
        AttributeOverride(name = "city", column = Column(name = "city", nullable = true)),
        AttributeOverride(name = "countryCode", column = Column(name = "country_code", nullable = true)),
        AttributeOverride(name = "province", column = Column(name = "province", nullable = true)),
        AttributeOverride(name = "postalCode", column = Column(name = "postal_code", nullable = true)),
        AttributeOverride(name = "phone", column = Column(name = "phone", nullable = true))
    )
    var shippingAddress: com.vernont.domain.common.Address? = null

    @Column
    var billingAddressId: String? = null

    @Column
    var shippingMethodId: String? = null

    @Column
    var paymentMethodId: String? = null

    @Column
    var completedAt: Instant? = null

    @Column(columnDefinition = "TEXT")
    var note: String? = null

    fun addItem(item: CartLineItem) {
        items.add(item)
        item.cart = this
        recalculateTotals()
    }

    fun removeItem(item: CartLineItem) {
        items.remove(item)
        item.cart = null
        recalculateTotals()
    }

    fun findItem(variantId: String): CartLineItem? {
        return items.find { it.variantId == variantId }
    }

    fun updateItemQuantity(variantId: String, quantity: Int) {
        require(quantity > 0) { "Quantity must be greater than 0" }
        val item = findItem(variantId) ?: throw IllegalArgumentException("Item not found in cart")
        item.quantity = quantity
        item.recalculateTotal()
        recalculateTotals()
    }

    fun recalculateTotals() {
        subtotal = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.total) }
        // Total = subtotal + tax + shipping - discount - gift card
        total = subtotal.add(tax).add(shipping).subtract(discount).subtract(giftCardTotal)
        // Ensure total doesn't go negative
        if (total < BigDecimal.ZERO) {
            total = BigDecimal.ZERO
        }
    }

    fun clear() {
        items.clear()
        recalculateTotals()
    }

    fun complete() {
        this.completedAt = Instant.now()
    }

    fun isCompleted(): Boolean = completedAt != null

    fun isEmpty(): Boolean = items.isEmpty()

    fun getItemCount(): Int = items.sumOf { it.quantity }

    fun hasItem(variantId: String): Boolean = items.any { it.variantId == variantId }

    fun setCustomer(customerId: String, email: String) {
        this.customerId = customerId
        this.email = email
    }

    fun removeCustomer() {
        this.customerId = null
        this.email = null
    }

    fun hasCustomer(): Boolean = customerId != null
}
