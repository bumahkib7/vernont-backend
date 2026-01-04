# CJ Product Ingestion (Search API)

CJ ingestion uses the Product Search API (v2) and follows the same Kafka + Redis pattern as FlexOffers.

## Configuration

Environment variables (or `application.yml`):

- `CJ_API_KEY`: CJ personal access token (server-side only).
- `CJ_COMPANY_ID`: CJ company ID (publisher company ID).
- `CJ_PID`: CJ PID for link generation (optional).
- `CJ_SERVICEABLE_AREA`: Default service area (e.g., `GB`).
- `CJ_CURRENCY`: Default currency (e.g., `GBP`).
- `CJ_SYNC_ENABLED`: Enable scheduler (`true|false`).
- `CJ_SYNC_ADVERTISERS`: Comma-separated advertiser IDs.
- `CJ_SYNC_KEYWORDS`: Comma-separated keywords (brands/models).
- `CJ_SYNC_PAGE_SIZE`: Records per page (up to 1000).
- `CJ_SYNC_MAX_PAGES`: Max pages per keyword/advertiser.
- `CJ_RETRY_MAX_ATTEMPTS`: Max retries for a task.
- `CJ_RECONCILE_INTERVAL_MS`: Requeue interval for stale tasks.
- `CJ_RECONCILE_MAX_AGE_MINUTES`: Stale threshold.

## Manual ingestion (admin)

POST `/api/v1/admin/cj/product-ingest`

Payload:
```json
{
  "advertiserIds": [12345],
  "keywords": ["Lyle & Scott"],
  "pageSize": 100,
  "maxPages": 50,
  "companyId": 999,
  "pid": "9999999",
  "currency": "GBP",
  "serviceableArea": "GB"
}
```

Status:

GET `/api/v1/admin/cj/status`

## Notes

- CJ is search-based (no full catalog dump). You must provide keywords.
- Keys are stable via SKU or buy URL hash.
- Tokens must never be embedded in code or client-side apps.
