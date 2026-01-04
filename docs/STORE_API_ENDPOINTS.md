# Vernont - Store API Endpoints

**Base URL**: `http://localhost:8080`
**Frontend Ports**: 8000 (store), 9000 (admin)

## Implemented Endpoints

### Products
✅ `GET /store/products` - List products
- Query params: `offset`, `limit`, `handle`, `region_id`, `fields`
- Response:
```json
{
  "products": [...],
  "count": 10,
  "offset": 0,
  "limit": 20
}
```

✅ `GET /store/products/{id}` - Get single product
- Response:
```json
{
  "product": {
    "id": "...",
    "title": "...",
    "handle": "...",
    "images": [{"id": "...", "url": "...", "rank": 0}],
    "variants": [...],
    "options": [...],
    "created_at": "...",
    "updated_at": "..."
  }
}
```

### Regions
✅ `GET /store/regions` - List all regions
- Response:
```json
{
  "regions": [
    {
      "id": "...",
      "name": "...",
      "currency_code": "USD",
      "tax_rate": 0.0,
      "countries": [{"iso_2": "US", "name": "United States"}]
    }
  ]
}
```

✅ `GET /store/regions/{id}` - Get single region

### Collections
✅ `GET /store/collections` - List collections
- Query params: `offset`, `limit`, `handle`
- Response:
```json
{
  "collections": [...],
  "count": 5,
  "offset": 0,
  "limit": 20
}
```

✅ `GET /store/collections/{id}` - Get single collection

### Product Categories
✅ `GET /store/product-categories` - List categories
- Query params: `offset`, `limit`, `handle`, `parent_category_id`
- Response:
```json
{
  "product_categories": [...],
  "count": 10,
  "offset": 0,
  "limit": 20
}
```

✅ `GET /store/product-categories/{id}` - Get single category

## Key Differences from Medusa

1. **Field Names**: All use snake_case (Medusa-compatible)
2. **Image Structure**: `{id, url, rank}` - exactly matches Medusa
3. **CORS**: Enabled for ports 8000, 9000, 3000
4. **Pagination**: Uses `offset/limit` (Medusa-compatible)

## Frontend Integration

The frontend at `/Users/kibuka/IdeaProjects/neoxus-aff-store-front` should work with:
- `sdk.client.fetch('/store/products', ...)` 
- Response structure matches Medusa's `HttpTypes.StoreProduct`
- Images array will now be properly populated with `id`, `url`, `rank`

## Next Endpoints to Implement

Priority based on storefront needs:
1. Carts (`POST /store/carts`, `POST /store/carts/{id}/line-items`)
2. Customers (`POST /store/customers`, `GET /store/customers/me`)
3. Orders (`GET /store/orders`, `GET /store/orders/{id}`)
4. Auth (`POST /auth/customer/{provider}`)

## Testing

```bash
# Test products endpoint
curl http://localhost:8080/store/products?limit=5

# Test regions
curl http://localhost:8080/store/regions

# Test collections
curl http://localhost:8080/store/collections

# Test categories
curl http://localhost:8080/store/product-categories
```
