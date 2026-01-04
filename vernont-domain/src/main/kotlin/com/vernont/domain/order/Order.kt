package com.vernont.domain.order

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

@Entity
@Table(name = "orders")
@NamedEntityGraph(
        name = "Order.full",
        attributeNodes =
                [
                        NamedAttributeNode(value = "items", subgraph = "itemsSubgraph"),
                        NamedAttributeNode("shippingAddress"),
                        NamedAttributeNode("billingAddress")],
        subgraphs =
                [
                        NamedSubgraph(
                                name = "itemsSubgraph",
                                attributeNodes = [
                                        // NamedAttributeNode("variant")
                                        ]
                        )]
)
@NamedEntityGraph(name = "Order.withItems", attributeNodes = [NamedAttributeNode("items")])
@NamedEntityGraph(
        name = "Order.withAddresses",
        attributeNodes =
                [NamedAttributeNode("shippingAddress"), NamedAttributeNode("billingAddress")]
)
@NamedEntityGraph(name = "Order.summary", attributeNodes = [])
class Order : BaseEntity() {

    @Column(nullable = false, unique = true) var displayId: Int = 0

    @Column(name = "customer_id") var customerId: String? = null

    @NotBlank @Column(nullable = false) var email: String = ""

    @Column(name = "cart_id") var cartId: String? = null

    @Column(name = "region_id", nullable = false) var regionId: String = ""

    @NotBlank
    @Column(name = "currency_code", nullable = false, length = 3)
    var currencyCode: String = ""

    @Column(precision = 19, scale = 4, nullable = false) var subtotal: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false) var tax: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false) var shipping: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false) var discount: BigDecimal = BigDecimal.ZERO

    @Column(precision = 19, scale = 4, nullable = false) var total: BigDecimal = BigDecimal.ZERO

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PENDING

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false)
    var fulfillmentStatus: FulfillmentStatus = FulfillmentStatus.NOT_FULFILLED

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    var paymentStatus: PaymentStatus = PaymentStatus.NOT_PAID

    @OneToMany(
            mappedBy = "order",
            cascade = [CascadeType.ALL],
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    var items: MutableSet<OrderLineItem> = mutableSetOf()

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id")
    var shippingAddress: OrderAddress? = null

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_address_id")
    var billingAddress: OrderAddress? = null

    @Column var shippingMethodId: String? = null

    @Column var paymentMethodId: String? = null

    @Column(columnDefinition = "TEXT") var note: String? = null

    @Column var canceledAt: String? = null

    fun addItem(item: OrderLineItem) {
        items.add(item)
        item.order = this
        recalculateTotals()
    }

    fun removeItem(item: OrderLineItem) {
        items.remove(item)
        item.order = null
        recalculateTotals()
    }

    fun recalculateTotals() {
        subtotal = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.total) }
        total = subtotal.add(tax).add(shipping).subtract(discount)
    }

    fun cancel() {
        require(status.canCancel()) { "Order cannot be canceled in current status: $status" }
        this.status = OrderStatus.CANCELED
        this.canceledAt = java.time.Instant.now().toString()
    }

    fun complete() {
        this.status = OrderStatus.COMPLETED
    }

    fun archive() {
        this.status = OrderStatus.ARCHIVED
    }

    fun markAsPaid() {
        this.paymentStatus = PaymentStatus.PAID
    }

    fun markAsPartiallyPaid() {
        this.paymentStatus = PaymentStatus.PARTIALLY_PAID
    }

    fun markAsRefunded() {
        this.paymentStatus = PaymentStatus.REFUNDED
    }

    fun markAsFulfilled() {
        this.fulfillmentStatus = FulfillmentStatus.FULFILLED
    }

    fun markAsPartiallyFulfilled() {
        this.fulfillmentStatus = FulfillmentStatus.PARTIALLY_FULFILLED
    }

    fun markAsShipped() {
        this.fulfillmentStatus = FulfillmentStatus.SHIPPED
    }

    fun isCanceled(): Boolean = status == OrderStatus.CANCELED

    fun isCompleted(): Boolean = status == OrderStatus.COMPLETED

    fun isPaid(): Boolean = paymentStatus == PaymentStatus.PAID

    fun isFulfilled(): Boolean = fulfillmentStatus == FulfillmentStatus.FULFILLED
}

enum class OrderStatus {
    PENDING,
    COMPLETED,
    ARCHIVED,
    CANCELED,
    REQUIRES_ACTION;

    fun canCancel(): Boolean {
        return this in listOf(PENDING, REQUIRES_ACTION)
    }
}

enum class FulfillmentStatus {
    NOT_FULFILLED,
    PARTIALLY_FULFILLED,
    FULFILLED,
    PARTIALLY_SHIPPED,
    SHIPPED,
    PARTIALLY_RETURNED,
    RETURNED,
    CANCELED,
    REQUIRES_ACTION
}

enum class PaymentStatus {
    NOT_PAID,
    AWAITING,
    CAPTURED,
    PARTIALLY_PAID,
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED,
    CANCELED,
    REQUIRES_ACTION
}
