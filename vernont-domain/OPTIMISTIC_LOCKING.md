# Optimistic Locking with @Version

## Overview

All entities in the vernont-backend project now have optimistic locking implemented via JPA's `@Version` annotation. This prevents lost updates and ensures data consistency in concurrent environments.

## Implementation Strategy

### ✅ **BaseEntity Approach**
Most entities extend `BaseEntity` which now includes:
```kotlin
@Version
@Column(nullable = false)
open var version: Long = 0
```

### ✅ **Direct Implementation**
Entities that don't extend `BaseEntity` have `@Version` added directly:
- `AuditLog` - Has its own `@Version` field
- `WorkflowExecution` - Extends BaseEntity, inherits versioning

## Entities with Optimistic Locking

### **Core Domain Entities** (via BaseEntity)
- Product, ProductVariant, ProductCategory, ProductOption, etc.
- Cart, CartLineItem
- Order, OrderLineItem, OrderAddress
- Customer, CustomerAddress, CustomerGroup
- Payment, PaymentSession, PaymentCollection, Refund
- Inventory entities (InventoryItem, InventoryLevel, StockLocation, etc.)
- Fulfillment entities (Fulfillment, FulfillmentItem, ShippingOption, etc.)
- Store entities (Store, ApiKey, SalesChannel)
- Region entities (Region, Country, Currency, TaxRate)
- Auth entities (User, Role, Permission)
- Promotion entities (Promotion, Discount, PromotionRule)

### **Audit & Workflow Entities** (direct implementation)
- `AuditLog` - Direct `@Version` field
- `WorkflowExecution` - Inherits from BaseEntity

## Database Schema

### **New Tables**
```sql
-- workflow_executions table includes version
CREATE TABLE workflow_executions (
    -- ... other columns ...
    version BIGINT NOT NULL DEFAULT 0
);
```

### **Existing Tables**
```sql
-- Add version to audit_log (Migration V11)
ALTER TABLE audit_log ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

## Usage Examples

### **Preventing Lost Updates**
```kotlin
// Service method with optimistic locking
@Transactional
fun updateCart(cartId: String, updateData: CartUpdateData): Cart {
    val cart = cartRepository.findById(cartId).orElseThrow()
    
    // Make changes
    cart.apply {
        // ... update fields ...
    }
    
    // Save - will throw OptimisticLockingFailureException if version mismatch
    return cartRepository.save(cart)
}
```

### **Handling Version Conflicts**
```kotlin
@Service
class CartService {
    
    @Retryable(
        value = [OptimisticLockingFailureException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100)
    )
    fun updateCartWithRetry(cartId: String, update: CartUpdate): Cart {
        val cart = cartRepository.findById(cartId).orElseThrow()
        cart.apply {
            // Apply updates
        }
        return cartRepository.save(cart)
    }
}
```

## Workflow Engine Integration

The WorkflowEngine benefits from optimistic locking:

```kotlin
// Workflow executions are protected from concurrent modifications
val execution = workflowExecutionService.getExecution(executionId)
execution.markAsCompleted(output)
workflowExecutionService.updateExecution(execution) // Version checked automatically
```

## Benefits

### **Data Consistency**
- Prevents lost updates in high-concurrency scenarios
- Ensures cart operations, payments, and inventory updates are atomic
- Protects workflow execution state changes

### **Performance**
- No database-level pessimistic locks needed
- Lightweight version checking
- Better scalability than row-level locking

### **Error Handling**
- Clear exceptions when conflicts occur (`OptimisticLockingFailureException`)
- Allows for intelligent retry strategies
- Maintains data integrity without blocking operations

## Production Considerations

### **Retry Strategies**
```kotlin
@Retryable(
    value = [OptimisticLockingFailureException::class],
    maxAttempts = 3,
    backoff = Backoff(delay = 100, multiplier = 2.0)
)
```

### **User Experience**
```kotlin
try {
    cartService.addToCart(cartId, item)
} catch (OptimisticLockingFailureException e) {
    // Refresh cart and show user that cart was updated by another process
    return "Cart updated by another operation, please review and try again"
}
```

### **Monitoring**
- Monitor `OptimisticLockingFailureException` rates
- Alert on high retry rates (indicates contention)
- Track version conflict patterns

## Summary

✅ **All entities protected** with optimistic locking  
✅ **Database migrations** ready for production  
✅ **Workflow engine integration** complete  
✅ **Production-ready** error handling patterns  

The vernont-backend platform now has enterprise-grade concurrency control! 🚀