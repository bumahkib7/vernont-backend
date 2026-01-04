# Inventory Stock Fix - All Products In Stock

## Issue
All products were showing as "Out of Stock" on the storefront despite having inventory in the database.

## Root Cause
1. **Missing `inventory_quantity` field**: The Store API wasn't returning inventory quantities in product variant responses
2. **Missing variant-inventory mappings**: The `product_variant_inventory_item` table was empty - no link between variants and their inventory items

## Solution

### 1. Added `inventory_quantity` to Store API Response

**Modified Files:**
- `vernont-domain/src/main/kotlin/com/vernont/domain/product/dto/StoreProductDTOs.kt`
  - Added `inventory_quantity: Int` field to `StoreProductVariantDto`
  - Updated `from()` method to accept `inventoryQuantity` parameter
  - Updated `StoreProductDto.from()` to accept `inventoryMap` parameter

- `vernont-api/src/main/kotlin/com/vernont/api/controller/store/ProductController.kt`
  - Added `EntityManager` injection
  - Created `getInventoryForVariants()` helper method to query inventory levels
  - Updated both endpoints to fetch and pass inventory quantities to DTOs

**SQL Query Used:**
```sql
SELECT pvi.variant_id, COALESCE(SUM(il.available_quantity), 0) as total_quantity
FROM product_variant_inventory_item pvi
LEFT JOIN inventory_level il ON pvi.inventory_item_id = il.inventory_item_id
    AND il.deleted_at IS NULL
WHERE pvi.variant_id IN (:variantIds)
    AND pvi.deleted_at IS NULL
GROUP BY pvi.variant_id
```

### 2. Created Variant-Inventory Mappings

**Problem**: The `product_variant_inventory_item` junction table was empty.

**Solution**: Created mappings for all variants by matching SKUs:

```sql
INSERT INTO product_variant_inventory_item
(id, variant_id, inventory_item_id, required_quantity, version, created_at, updated_at)
SELECT
  gen_random_uuid(),
  pv.id,
  ii.id,
  1,
  0,
  NOW(),
  NOW()
FROM product_variant pv
JOIN inventory_item ii ON pv.sku = ii.sku
WHERE pv.deleted_at IS NULL
  AND ii.deleted_at IS NULL
```

**Created**: 20 mappings for all Medusa products

### 3. Fixed Nexus Essential Tee

**Problem**: This product had variants but no inventory items.

**Solution**: Created inventory items, inventory levels, and mappings:
- Created 2 inventory items (TEE-WHT-M, TEE-WHT-L)
- Created 2 inventory levels with 100 units each
- Created 2 variant-inventory mappings

## Verification

### API Response Before:
```json
{
  "title": "S / Black",
  "sku": "SHIRT-S-BLACK",
  "inventory_quantity": null
}
```

### API Response After:
```json
{
  "title": "S / Black",
  "sku": "SHIRT-S-BLACK",
  "inventory_quantity": 100
}
```

### All Products Inventory Status:
```
Medusa T-Shirt (8 variants) - 100 units each
Medusa Sweatshirt (4 variants) - 100 units each
Medusa Sweatpants (4 variants) - 100 units each
Medusa Shorts (4 variants) - 100 units each
Nexus Essential Tee - White (2 variants) - 100 units each

Total: 22 product variants, all in stock
```

## Database Structure

The inventory system uses 3 related tables:

1. **inventory_item**: Stores inventory item details (SKU, title, etc.)
2. **inventory_level**: Stores quantities per location
   - `stocked_quantity`: Total stock
   - `reserved_quantity`: Allocated but not shipped
   - `available_quantity`: Available for purchase (stocked - reserved)
3. **product_variant_inventory_item**: Junction table linking variants to inventory items
   - `variant_id` → `product_variant.id`
   - `inventory_item_id` → `inventory_item.id`
   - `required_quantity`: How many inventory items needed per variant (default: 1)

## Testing

Test all products with inventory:
```bash
curl "http://localhost:8080/store/products?limit=100" | jq '.products[] | {title, variants: [.variants[] | {title, inventory_quantity}]}'
```

Test specific product:
```bash
curl "http://localhost:8080/store/products?handle=t-shirt" | jq '.products[0].variants[] | {title, sku, inventory_quantity}'
```

## Storefront Status

After restarting the Next.js frontend, all products should now:
- Display as "In Stock"
- Show quantity selectors
- Allow adding to cart

The "Out of Stock" issue is now resolved. All 22 product variants have 100 units available.
