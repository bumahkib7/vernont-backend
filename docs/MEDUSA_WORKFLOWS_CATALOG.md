# Medusa.js Workflows - Complete Catalog for Replication

Total workflows in Medusa: **297**

## Summary by Category

| Category | Count | Priority |
|----------|-------|----------|
| order | 96 | P1 - CRITICAL |
| cart | 21 | P1 - CRITICAL |
| product | 28 | P2 - HIGH |
| draft-order | 19 | P2 - HIGH |
| fulfillment | 17 | P2 - HIGH |
| promotion | 12 | P3 - MEDIUM |
| tax | 9 | P3 - MEDIUM |
| customer | 8 | P2 - HIGH |
| inventory | 8 | P2 - HIGH |
| price-list | 7 | P3 - MEDIUM |
| payment-collection | 6 | P1 - CRITICAL |
| api-key | 5 | P4 - LOW |
| customer-group | 5 | P3 - MEDIUM |
| stock-location | 5 | P2 - HIGH |
| user | 5 | P4 - LOW |
| common | 4 | P1 - CRITICAL |
| invite | 4 | P4 - LOW |
| payment | 4 | P1 - CRITICAL |
| reservation | 4 | P2 - HIGH |
| return-reason | 3 | P3 - MEDIUM |
| pricing | 3 | P3 - MEDIUM |
| product-category | 3 | P3 - MEDIUM |
| region | 3 | P3 - MEDIUM |
| sales-channel | 4 | P3 - MEDIUM |
| shipping-options | 3 | P2 - HIGH |
| store | 3 | P3 - MEDIUM |
| file | 2 | P4 - LOW |
| settings | 2 | P4 - LOW |
| auth | 1 | P1 - CRITICAL |
| defaults | 1 | P4 - LOW |
| line-item | 1 | P2 - HIGH |
| shipping-profile | 1 | P3 - MEDIUM |

## PRIORITY 1 - CRITICAL (Core Commerce Flows)

### Cart Workflows (21)
1. **complete-cart** ⭐ MUST HAVE - Completes cart and creates order
2. **create-carts** - Create new cart
3. **update-cart** - Update cart details
4. **add-to-cart** - Add items to cart
5. **update-line-item-in-cart** - Update item quantity
6. **add-shipping-method-to-cart** - Add shipping to cart
7. **list-shipping-options-for-cart** - Get available shipping
8. **list-shipping-options-for-cart-with-pricing** - Get shipping with pricing
9. **refresh-cart-items** - Recalculate cart items
10. **refresh-cart-shipping-methods** - Recalculate shipping
11. **update-cart-promotions** - Apply/remove promotions
12. **update-tax-lines** - Update tax calculations
13. **upsert-tax-lines** - Create/update tax lines
14. **create-payment-collection-for-cart** - Initialize payment
15. **refresh-payment-collection** - Update payment collection
16. **confirm-variant-inventory** - Check stock availability
17. **transfer-cart-customer** - Assign cart to customer
18. **get-variants-and-items-with-prices** - Get pricing info
19. **create-cart-credit-lines** - Add credit/discounts
20. **delete-cart-credit-lines** - Remove credits
21. **refund-payment-recreate-payment-session** - Handle payment retry

### Order Workflows (96 total, key ones listed)
**Core Order Operations:**
1. **create-order** ⭐ MUST HAVE - Create order from cart
2. **cancel-order** ⭐ MUST HAVE - Cancel order with compensation
3. **complete-orders** - Mark order complete
4. **update-order** - Update order details
5. **archive-orders** - Archive old orders
6. **get-order-detail** - Retrieve full order
7. **get-orders-list** - List/search orders

**Fulfillment:**
8. **create-fulfillment** ⭐ MUST HAVE - Create fulfillment
9. **create-shipment** ⭐ MUST HAVE - Create shipment with tracking
10. **cancel-order-fulfillment** - Cancel fulfillment
11. **mark-order-fulfillment-as-delivered** - Mark delivered

**Payment:**
12. **create-order-payment-collection** ⭐ MUST HAVE
13. **create-or-update-order-payment-collection** ⭐ MUST HAVE
14. **delete-order-payment-collection**
15. **mark-payment-collection-as-paid** ⭐ MUST HAVE

**Returns (subset):**
16. **begin-return** - Initiate return
17. **create-complete-return** - Process return
18. **confirm-return-request** - Approve return
19. **cancel-return** - Cancel return
20. **receive-complete-return** - Receive returned items

**Exchanges (subset):**
21. **begin-order-exchange** - Start exchange
22. **confirm-exchange-request** - Approve exchange
23. **cancel-exchange** - Cancel exchange

**Order Edit (subset):**
24. **begin-order-edit** - Start order modification
25. **confirm-order-edit-request** - Apply edits
26. **cancel-begin-order-edit** - Cancel edit

**Refunds:**
27. **refund-captured-payments** - Process refund
28. **create-order-refund-credit-lines** - Create refund credits

**Shipping:**
29. **list-shipping-options-for-order** - Get shipping options
30. **fetch-shipping-option** - Get single option
31. **maybe-refresh-shipping-methods** - Auto-refresh if needed

**Line Items:**
32. **add-line-items** - Add items to order

**Tax:**
33. **update-tax-lines** - Recalculate taxes

**Order Changes:**
34. **create-order-change** - Create change record
35. **create-order-change-actions** - Record change actions
36. **update-order-change-actions** - Modify actions
37. **delete-order-change-actions** - Remove actions
38. **cancel-order-change** - Cancel change
39. **decline-order-change** - Decline change
40. **update-order-changes** - Bulk update changes

**Credits:**
41. **create-order-credit-lines** - Add credits to order

**Transfer:**
42. **request-order-transfer** - Request ownership transfer
43. **accept-order-transfer** - Accept transfer
44. **decline-order-transfer** - Decline transfer
45. **cancel-order-transfer** - Cancel transfer

### Payment Workflows (4)
1. **authorize-payment-session** ⭐ MUST HAVE
2. **capture-payment** ⭐ MUST HAVE
3. **refund-payment** ⭐ MUST HAVE
4. **cancel-payment** ⭐ MUST HAVE

### Payment Collection Workflows (6)
1. **create-payment-session** ⭐ MUST HAVE
2. **create-refund-reasons**
3. **delete-payment-sessions**
4. **delete-refund-reasons**
5. **mark-payment-collection-as-paid** ⭐ MUST HAVE
6. **process-payment**

### Auth Workflows (1)
1. **generate-reset-password-token** ⭐ MUST HAVE

### Common Workflows (4)
1. **create-remote-link** - Link entities across modules
2. **dismiss-remote-link** - Remove links
3. **emit-event** - Publish events
4. **use-query-graph** - Query data

## PRIORITY 2 - HIGH (Essential Features)

### Product Workflows (28)
1. **create-products**
2. **update-products**
3. **delete-products**
4. **create-product-variants**
5. **update-product-variants**
6. **delete-product-variants**
7. **batch-products**
8. **batch-product-variants**
9. **create-product-options**
10. **update-product-options**
11. **delete-product-options**
12. **create-collections**
13. **update-collections**
14. **delete-collections**
15. **link-products-to-collection**
16. **create-product-tags**
17. **create-product-types**
18. **export-products**
19. **import-products**
20. **update-product-tags**
21. **update-product-types**
22. **upsert-variant-prices**
23. **batch-link-products-collection**
24. **batch-link-products-in-category**
25. **create-variant-pricing-link**
26. **generate-product-csv**
27. **wait-confirmation-product-import**
28. **create-products-prepare-data**

### Customer Workflows (8)
1. **create-customers**
2. **update-customers**
3. **delete-customers**
4. **create-customer-addresses**
5. **update-customer-addresses**
6. **delete-customer-addresses**
7. **create-addresses**
8. **update-addresses**

### Inventory Workflows (8)
1. **create-inventory-items**
2. **update-inventory-items**
3. **delete-inventory-items**
4. **create-inventory-levels**
5. **update-inventory-levels**
6. **delete-inventory-levels**
7. **bulk-create-delete-levels**
8. **adjust-inventory-levels**

### Draft Order Workflows (19)
1. **create-draft-orders**
2. **update-draft-orders**
3. **delete-draft-orders**
4. **complete-draft-order**
5. **add-draft-order-line-items**
6. **update-draft-order-line-items**
7. **delete-draft-order-line-items**
8. **create-draft-order-adjustments**
9. **set-draft-order-tax-lines**
And 10 more...

### Fulfillment Workflows (17)
1. **create-fulfillment**
2. **cancel-fulfillment**
3. **create-shipment**
4. **cancel-shipment**
5. **create-shipping-options**
6. **update-shipping-options**
7. **delete-shipping-options**
8. **batch-shipping-options**
9. **create-fulfillment-sets**
10. **update-fulfillment-sets**
11. **delete-fulfillment-sets**
12. **create-service-zones**
13. **update-service-zones**
14. **delete-service-zones**
15. **upsert-shipping-options**
16. **cancel-validation-step**
17. **mark-fulfillment-as-delivered**

### Stock Location Workflows (5)
1. **create-stock-locations**
2. **update-stock-locations**
3. **delete-stock-locations**
4. **link-sales-channels-to-stock-location**
5. **create-location-fulfillment-set**

### Reservation Workflows (4)
1. **create-reservations**
2. **update-reservations**
3. **delete-reservations**
4. **bulk-create-delete-reservations**

### Shipping Options Workflows (3)
1. **create-shipping-options-workflow**
2. **update-shipping-options-workflow**
3. **delete-shipping-options-workflow**

### Line Item Workflows (1)
1. **list-line-items-workflow**

## PRIORITY 3 - MEDIUM (Advanced Features)

### Promotion Workflows (12)
1. **create-promotions**
2. **update-promotions**
3. **delete-promotions**
4. **update-promotions-status**
5. **create-campaigns**
6. **update-campaigns**
7. **delete-campaigns**
8. **add-or-remove-campaign-promotions**
9. **create-promotion-rules**
10. **update-promotion-rules**
11. **delete-promotion-rules**
12. **batch-promotion-rules**

### Tax Workflows (9)
1. **create-tax-rates**
2. **create-tax-rate-rules**
3. **create-tax-regions**
4. **delete-tax-rates**
5. **delete-tax-rate-rules**
6. **delete-tax-regions**
7. **list-tax-rate-ids**
8. **list-tax-rate-rule-ids**
9. **set-tax-rate-rules**

### Price List Workflows (7)
1. **create-price-lists**
2. **update-price-lists**
3. **remove-price-lists**
4. **batch-price-lists**
5. **create-price-list-prices**
6. **remove-price-list-prices**
7. **update-price-list-prices**

### Pricing Workflows (3)
1. **create-price-preferences**
2. **delete-price-preferences**
3. **update-price-preferences**

### Customer Group Workflows (5)
1. **create-customer-groups**
2. **delete-customer-groups**
3. **link-customers-customer-group**
4. **update-customer-groups**
5. **batch-link-customer-groups**

### Product Category Workflows (3)
1. **create-product-categories**
2. **update-product-categories**
3. **delete-product-categories**

### Region Workflows (3)
1. **create-regions**
2. **update-regions**
3. **delete-regions**

### Sales Channel Workflows (4)
1. **create-sales-channels**
2. **delete-sales-channels**
3. **link-products-to-sales-channel**
4. **update-sales-channels**

### Return Reason Workflows (3)
1. **create-return-reasons**
2. **delete-return-reasons**
3. **update-return-reasons**

### Store Workflows (3)
1. **create-store**
2. **update-stores**
3. **delete-stores**

### Shipping Profile Workflows (1)
1. **update-shipping-profile**

## PRIORITY 4 - LOW (Admin/Utility Features)

### User Workflows (5)
1. **create-user-account**
2. **update-users**
3. **delete-users**
4. **remove-user-account**
5. **create-users**

### API Key Workflows (5)
1. **create-api-keys**
2. **delete-api-keys**
3. **link-sales-channels-to-api-key**
4. **revoke-api-keys**
5. **update-api-keys**

### Invite Workflows (4)
1. **accept-invite**
2. **create-invites**
3. **delete-invites**
4. **refresh-invite-tokens**

### File Workflows (2)
1. **delete-files**
2. **upload-files**

### Settings Workflows (2)
1. **create-workflow-execution**
2. **subscribe-to-workflow-execution**

### Defaults Workflows (1)
1. **create-default-store**

## Implementation Status

### ✅ Completed (4)
- [x] CreateOrderWorkflow - Full implementation with multi-location inventory, compensation

### ⏳ Partially Created (3 - currently broken, need to be fixed)
- [ ] CompleteCheckoutWorkflow
- [ ] CancelOrderWorkflow
- [ ] CapturePaymentWorkflow

### 🎯 Next Steps - Must Implement (Priority 1)

**Cart Workflows (21 workflows):**
1. complete-cart ⭐
2. create-carts
3. update-cart
4. add-to-cart
5. update-line-item-in-cart
6. [... all 21 cart workflows]

**Order Workflows (top 20 critical ones):**
1. cancel-order ⭐
2. create-fulfillment ⭐
3. create-shipment ⭐
4. create-order-payment-collection ⭐
5. mark-payment-collection-as-paid ⭐
6. refund-captured-payments ⭐
7. begin-return
8. confirm-return-request
9. begin-order-exchange
10. confirm-exchange-request
11. [... remaining critical order workflows]

**Payment Workflows (4 workflows):**
1. authorize-payment-session ⭐
2. capture-payment ⭐
3. refund-payment ⭐
4. cancel-payment ⭐

**Payment Collection (6 workflows)**
**Common (4 workflows)**
**Auth (1 workflow)**

## Key Patterns from Medusa Workflows

### completeCartWorkflow Pattern:
```javascript
1. acquireLockStep - Prevent concurrent completion
2. useQueryGraphStep - Load cart with all relations
3. validateCartPaymentsStep - Verify payment exists
4. compensatePaymentIfNeededStep - Setup compensation
5. createHook("validate") - Custom validation hook
6. when() - Conditional: only if order doesn't exist yet
   a. validateShippingStep - Verify shipping
   b. prepareLineItemData - Transform cart items to order items
   c. createOrdersStep - Create order entity
   d. parallelize:
      - createRemoteLinkStep - Link cart->order
      - updateCartsStep - Mark cart completed
      - reserveInventoryStep - Reserve stock
      - registerUsageStep - Track promotion usage
      - emitEventStep - Publish ORDER_PLACED event
   e. createHook("beforePaymentAuthorization")
   f. authorizePaymentSessionStep - Authorize payment
   g. addOrderTransactionStep - Record transaction
   h. parallelize:
      - createRemoteLinkStep - Link payment->order
      - emitEventStep - Publish PAYMENT_AUTHORIZED
7. releaseLockStep - Release lock
8. return order ID
```

### Compensation Pattern:
- Every workflow step can have compensation logic
- Executed in reverse order if workflow fails
- Example: reserveInventoryStep compensates by releasing reservation
- Example: authorizePaymentSessionStep compensates by voiding authorization

### Key Medusa Concepts to Replicate:
1. **Locks** - Prevent race conditions
2. **Hooks** - Allow customization points
3. **Remote Links** - Cross-module relationships
4. **Parallelize** - Run independent steps concurrently
5. **Transform** - Data transformation between steps
6. **When/Conditional** - Branching logic
7. **Query Graph** - Efficient data loading with relations
8. **Compensation** - Automatic rollback

## Total Scope
- **Total workflows to replicate**: 297
- **Priority 1 (CRITICAL)**: ~130 workflows
- **Priority 2 (HIGH)**: ~90 workflows
- **Priority 3 (MEDIUM)**: ~50 workflows
- **Priority 4 (LOW)**: ~27 workflows

## Immediate Action Plan

1. ✅ Fix build errors (DONE)
2. ✅ Catalog all workflows (DONE - this document)
3. 🎯 Implement all 21 cart workflows
4. 🎯 Implement top 30 order workflows
5. 🎯 Implement all 4 payment workflows
6. 🎯 Implement all 6 payment-collection workflows
7. 🎯 Implement all 4 common workflows
8. 🎯 Continue with Priority 2 workflows
9. 🎯 Continue with Priority 3 workflows
10. 🎯 Complete Priority 4 workflows

**Estimated Total**: 297 workflows to implement for COMPLETE replication
