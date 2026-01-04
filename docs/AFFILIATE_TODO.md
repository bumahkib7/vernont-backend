## Affiliate & Storefront Plan

### Entities (now in code)
- **Shared**: `Product` (canonical), `ProductCategory`, `ProductCollection`, `Brand` (new), `UserFavorite` (new).
- **Affiliate-only**: `AffiliateMerchant`, `AffiliateOffer` (`AffiliateOffer.full` graph), feed/click models.
- **Storefront-only**: cart/order/payment remain unchanged but consume catalog read models.
- **Read models**: `CatalogResponse`, `ProductDetailView`, `BrandSummary`, `CategorySummary`, `FavoriteView`, `AffiliateOfferView`.

### Workloads to wire next (event-driven)
- Catalog read service that builds `CatalogResponse`/`ProductDetailView` from canonical product + active affiliate offers (bestOffer + offerCount), with caching.
- Brands/categories index endpoints for navigation (brands from DB; categories from product categories).
- Favorites service: persist favorites, emit `FavoriteCreated`/`FavoriteRemoved`, toggle alerts and thresholds.
- Alerting: subscribe to `AffiliateOfferPriceChanged`/`AffiliateOfferInventoryChanged` → fire `FavoriteAlertTriggered` jobs/notifications.
- Offer ingest: feed workflows publish `AffiliateOfferUpserted`/`AffiliateOfferDeactivated`/`AffiliateOfferPriceChanged`; click flow emits `AffiliateClickRecorded`.
- Currency/locale: normalize prices on ingest or query (fx rate lookup), expose currency on read models.

### Implementation checklist (next steps)
- [ ] Add mappers/query methods that hydrate `CatalogResponse`/`ProductDetailView` using `ProductRepository` + `AffiliateOfferRepository` (best offer, count, related items).
- [ ] Expose API controllers for catalog (`/api/affiliate/catalog`), detail (`/api/affiliate/products/{handle}`), brands (`/api/affiliate/brands`), categories (`/api/affiliate/categories`), favorites (`/api/affiliate/favorites`).
- [ ] Publish affiliate events from feed + click workflows (`AffiliateOfferUpserted`, `AffiliateOfferPriceChanged`, `AffiliateOfferDeactivated`, `AffiliateClickRecorded`).
- [ ] Wire favorites service to emit events and schedule alert notifications on price/inventory changes.
- [ ] Add caches (per-brand/category lists, product detail) and entity graphs where needed for JPA efficiency.
- [ ] Backfill/seed core brands in the DB for quick navigation fetches.
