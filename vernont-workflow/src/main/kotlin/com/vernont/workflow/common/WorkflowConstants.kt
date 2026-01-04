package com.vernont.workflow.common

/**
 * Constants for workflow names used throughout the application.
 */
object WorkflowConstants {

    object Auth {
        const val GENERATE_EMAIL_VERIFICATION_TOKEN = "auth.generate-email-verification-token"
        const val VERIFY_EMAIL = "auth.verify-email"
        const val GENERATE_RESET_PASSWORD_TOKEN = "auth.generate-reset-password-token"
        const val INVITE_INTERNAL_USER = "auth.invite-internal-user"
        const val SET_AUTH_APP_METADATA = "auth.set-auth-app-metadata"
    }

    object AuthMetadata {
        const val NAME = "auth.set-auth-app-metadata"
        const val SETUP = "auth.set-auth-app-metadata"
    }

    object Cart {
        const val CREATE_CART = "cart.create"
        const val UPDATE_CART = "cart.update"
        const val ADD_TO_CART = "cart.add-item"
        const val UPDATE_LINE_ITEM = "cart.update-line-item"
        const val REMOVE_LINE_ITEM = "cart.remove-line-item"
        const val ADD_SHIPPING_METHOD = "cart.add-shipping-method"
        const val COMPLETE_CART = "cart.complete"
        const val CREATE_PAYMENT_COLLECTION = "cart.create-payment-collection"
        const val REFRESH_PAYMENT_COLLECTION = "cart.refresh-payment-collection"
        const val UPDATE_PROMOTIONS = "cart.update-promotions"
        const val CONFIRM_VARIANT_INVENTORY = "cart.confirm-variant-inventory"
        const val REFRESH_CART_ITEMS = "cart.refresh-items"
    }

    // Individual workflow name objects (matching Medusa patterns)
    object CreateCart {
        const val NAME = "cart.create"
    }
    object UpdateCart {
        const val NAME = "cart.update"
    }
    object AddToCart {
        const val NAME = "cart.add-item"
    }
    object UpdateLineItemInCart {
        const val NAME = "cart.update-line-item"
    }
    object RemoveLineItemFromCart {
        const val NAME = "cart.remove-line-item"
    }
    object AddShippingMethodToCart {
        const val NAME = "cart.add-shipping-method"
    }
    object CompleteCart {
        const val NAME = "cart.complete"
    }
    object CreatePaymentCollectionForCart {
        const val NAME = "cart.create-payment-collection"
    }
    object RefreshPaymentCollection {
        const val NAME = "cart.refresh-payment-collection"
    }
    object UpdateCartPromotions {
        const val NAME = "cart.update-promotions"
    }
    object ConfirmVariantInventory {
        const val NAME = "cart.confirm-variant-inventory"
    }
    object RefreshCartItems {
        const val NAME = "cart.refresh-items"
    }

    object Order {
        const val CREATE_ORDER = "order.create"
        const val CANCEL_ORDER = "order.cancel"
        const val FULFILL_ORDER = "order.fulfill"
        const val SHIP_ORDER = "order.ship"
        const val COMPLETE_ORDER = "order.complete"
    }
    object CreateOrder {
        const val NAME = "order.create"
    }
    object CancelOrder {
        const val NAME = "order.cancel"
    }
    object FulfillOrder {
        const val NAME = "order.fulfill"
    }
    object ShipOrder {
        const val NAME = "order.ship"
    }
    object CompleteOrder {
        const val NAME = "order.complete"
    }

    object Payment {
        const val PROCESS_PAYMENT = "payment.process"
        const val AUTHORIZE_PAYMENT = "payment.authorize"
        const val CAPTURE_PAYMENT = "payment.capture"
        const val REFUND_PAYMENT = "payment.refund"
        const val CREATE_STRIPE_PAYMENT_INTENT = "payment.stripe.create-intent"
        const val CONFIRM_STRIPE_PAYMENT = "payment.stripe.confirm"
    }
    object ProcessPayment {
        const val NAME = "payment.process"
    }
    object AuthorizePayment {
        const val NAME = "payment.authorize"
    }
    object CapturePayment {
        const val NAME = "payment.capture"
    }
    object RefundPayment {
        const val NAME = "payment.refund"
    }
    object CreateStripePaymentIntent {
        const val NAME = "payment.stripe.create-intent"
    }
    object ConfirmStripePayment {
        const val NAME = "payment.stripe.confirm"
    }

    object Fulfillment {
        const val CREATE_FULFILLMENT = "fulfillment.create"
        const val CREATE_SHIPMENT = "fulfillment.create-shipment"
    }
    object CreateFulfillment {
        const val NAME = "fulfillment.create"
    }
    object CreateShipment {
        const val NAME = "fulfillment.create-shipment"
    }

    object Product {
        const val CREATE_PRODUCT = "product.create"
        const val UPDATE_PRODUCT = "product.update"
        const val PUBLISH_PRODUCT = "product.publish"
        const val LIST_STOREFRONT_PRODUCTS = "product.list-storefront"
    }

    object Storefront {
        const val LIST_PRODUCTS = "product.list-storefront"
    }
    object CreateProduct {
        const val NAME = "product.create"
    }
    object UpdateProduct {
        const val NAME = "product.update"
    }
    object ProductLifecycle {
        const val PUBLISH = "product.publish"
        const val PUBLISH_PRODUCT = "product.publish"
    }

    object Collection {
        const val CREATE_COLLECTION = "collection.create"
        const val EDIT_COLLECTION = "collection.edit"
        const val PUBLISH_COLLECTION = "collection.publish"
    }
    object CreateCollection {
        const val NAME = "collection.create"
        const val CREATE_COLLECTION = "collection.create"
    }
    object EditCollection {
        const val NAME = "collection.edit"
        const val EDIT_COLLECTION = "collection.edit"
    }
    object CollectionLifecycle {
        const val PUBLISH = "collection.publish"
        const val PUBLISH_COLLECTION = "collection.publish"
    }

    object Customer {
        const val CREATE_CUSTOMER_ACCOUNT = "customer.create-account"
        const val CREATE_CUSTOMERS = "customer.create"
    }
    object CreateCustomerAccount {
        const val NAME = "customer.create-account"
    }
    object CreateCustomers {
        const val NAME = "customer.create"
    }

    object Region {
        const val CREATE_REGIONS = "region.create"
    }
    object CreateRegions {
        const val NAME = "region.create"
        const val CREATE_REGIONS = "region.create"
    }

    object Marketing {
        const val PRICE_DROP_ALERT = "marketing.price-drop-alert"
        const val NEW_ARRIVALS_ALERT = "marketing.new-arrivals-alert"
        const val WIN_BACK_CAMPAIGN = "marketing.win-back-campaign"
        const val WEEKLY_DIGEST = "marketing.weekly-digest"
    }

    object Return {
        const val REQUEST_RETURN = "return.request"
        const val RECEIVE_RETURN = "return.receive"
        const val PROCESS_REFUND = "return.process-refund"
        const val CANCEL_RETURN = "return.cancel"
        const val REJECT_RETURN = "return.reject"
    }
    object RequestReturn {
        const val NAME = "return.request"
    }
    object ReceiveReturn {
        const val NAME = "return.receive"
    }
    object ProcessReturnRefund {
        const val NAME = "return.process-refund"
    }
    object CancelReturn {
        const val NAME = "return.cancel"
    }
    object RejectReturn {
        const val NAME = "return.reject"
    }

    // Inventory Management
    object Inventory {
        const val ADJUST_INVENTORY = "inventory.adjust"
        const val CREATE_STOCK_LOCATION = "inventory.create-stock-location"
        const val UPDATE_STOCK_LOCATION = "inventory.update-stock-location"
        const val TRANSFER_INVENTORY = "inventory.transfer"
    }
    object AdjustInventory {
        const val NAME = "inventory.adjust"
    }
    object CreateStockLocation {
        const val NAME = "inventory.create-stock-location"
    }
    object UpdateStockLocation {
        const val NAME = "inventory.update-stock-location"
    }
    object TransferInventory {
        const val NAME = "inventory.transfer"
    }

    // Product Variant Management
    object ProductVariant {
        const val UPDATE_VARIANT = "product.update-variant"
        const val DELETE_VARIANT = "product.delete-variant"
        const val UPDATE_VARIANT_INVENTORY = "product.update-variant-inventory"
    }
    object UpdateProductVariant {
        const val NAME = "product.update-variant"
    }
    object DeleteProductVariant {
        const val NAME = "product.delete-variant"
    }

    // Exchange Management
    object Exchange {
        const val CREATE_EXCHANGE = "exchange.create"
        const val PROCESS_EXCHANGE = "exchange.process"
        const val CANCEL_EXCHANGE = "exchange.cancel"
    }
    object CreateExchange {
        const val NAME = "exchange.create"
    }
    object ProcessExchange {
        const val NAME = "exchange.process"
    }
    object CancelExchange {
        const val NAME = "exchange.cancel"
    }

    // Customer Address Management
    object CustomerAddress {
        const val CREATE_ADDRESS = "customer.create-address"
        const val UPDATE_ADDRESS = "customer.update-address"
        const val DELETE_ADDRESS = "customer.delete-address"
    }
    object CreateCustomerAddress {
        const val NAME = "customer.create-address"
    }
    object UpdateCustomerAddress {
        const val NAME = "customer.update-address"
    }
    object DeleteCustomerAddress {
        const val NAME = "customer.delete-address"
    }
}
