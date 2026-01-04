# CJ Affiliate Product Feed Ingestion Guide

## Overview

Your Vernont platform has a **production-ready CJ feed ingestion system** that automatically processes Google Shopping XML feeds from CJ Affiliate.

## Your Feed Subscription Details

| Property | Value |
|----------|-------|
| **CID** | 7811141 |
| **Company Name** | Neoxus |
| **Subscription ID** | 311919 |
| **Feed Format** | Shopping (Google Format) |
| **Data Format** | XML |
| **Transfer Method** | CJ HTTP |
| **Transfer Schedule** | When Updates Occur |
| **File Name Pattern** | `AdvertiserName-FeedName-shopping.xml.zip` |

## Credentials (Already Configured)

The following credentials have been added to your `.env` files:

```bash
CJ_CID=7811141
CJ_FEED_USERNAME=7811141
CJ_FEED_PASSWORD=CqjH9na=
```

## How Product Ingestion Works

```
1. CJ sends notification when advertiser updates feed
   ↓
2. Download ZIP file from CJ HTTP URL
   ↓
3. Extract and parse Google Shopping XML
   ↓
4. Transform to canonical product format
   ↓
5. Upsert products + offers into database
   ↓
6. Index products in Elasticsearch
   ↓
7. Products visible in catalog
```

## Data Flow

**Product Deduplication:**
- External Key: `CJ:{advertiserId}:{sha256(sku|url|title)}`
- Prevents duplicate products from same advertiser

**Product Filtering:**
- All products from all regions are ingested
- Invalid products skipped with warning logs

**Offer Management:**
- Each product can have multiple offers from different CJ advertisers
- Offers automatically deactivated if missing from subsequent feed syncs
- Tracking URLs generated with your CJ affiliate parameters

## Methods to Ingest Feeds

### Method 1: Manual API Trigger (Recommended for Testing)

**Endpoint:** `POST /api/v1/admin/cj/feed/ingest`

**Example Request:**
```bash
curl -X POST http://localhost:8080/api/v1/admin/cj/feed/ingest \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "advertiserId": 123456,
    "url": "https://datafeed.cjaffiliates.com/your-feed-url.xml.zip",
    "format": "xml",
    "dryRun": false
  }'
```

**Response:**
```json
{
  "status": "started",
  "runId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Parameters:**
- `advertiserId` - CJ advertiser ID (from feed notification email)
- `url` - Full HTTP URL to download the ZIP feed
- `format` - "xml" or "googleshopping" (both supported)
- `dryRun` - Set to `true` to validate without saving to database

### Method 2: Manual File Upload (For Testing Downloaded Feeds)

**Endpoint:** `POST /api/v1/admin/cj/feed/upload/{advertiserId}`

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/admin/cj/feed/upload/123456 \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN" \
  -F "file=@AdvertiserName-FeedName-shopping.xml.zip" \
  -F "dryRun=false"
```

### Method 3: Feed Configuration (Recommended for Production)

Set up automatic feed syncing:

**1. Create Feed Configuration:**

You can create a feed config entry in the database or use the admin UI:

```sql
INSERT INTO cj_feed_config (
    advertiser_id,
    advertiser_name,
    feed_name,
    feed_url,
    feed_format,
    compression_type,
    enabled,
    language,
    region,
    priority,
    created_at,
    updated_at
) VALUES (
    123456,                     -- Replace with actual CJ advertiser ID
    'Example Advertiser',       -- Replace with advertiser name
    'shopping-feed',
    'https://datafeed.cjaffiliates.com/your-feed-url.xml.zip',  -- Replace with actual URL
    'GOOGLE_SHOPPING',
    'ZIP',
    true,
    'en',
    'GB',
    1,
    NOW(),
    NOW()
);
```

**2. Sync Specific Feed:**

```bash
curl -X POST http://localhost:8080/api/v1/admin/cj/feed/configs/{configId}/sync \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"dryRun": false}'
```

**3. Sync All Enabled Feeds:**

```bash
curl -X POST http://localhost:8080/api/v1/admin/cj/feed/configs/sync-all \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
```

**4. List All Feed Configs:**

```bash
curl -X GET http://localhost:8080/api/v1/admin/cj/feed/configs \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
```

## Monitoring Feed Runs

### Check Feed Run Status

**Endpoint:** `GET /api/v1/admin/cj/feed/runs/{runId}`

```bash
curl -X GET http://localhost:8080/api/v1/admin/cj/feed/runs/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
```

**Response:**
```json
{
  "runId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "advertiserId": 123456,
  "status": "COMPLETED",
  "processed": 15234,
  "total": 15234,
  "startedAt": "2025-12-27T10:00:00Z",
  "finishedAt": "2025-12-27T10:15:00Z"
}
```

**Status Values:**
- `STARTED` - Feed download started
- `RUNNING` - Actively processing products
- `COMPLETED` - Successfully finished
- `FAILED` - Encountered errors
- `CANCELLING` - Cancellation requested
- `CANCELLED` - Successfully cancelled

### List All Runs

```bash
curl -X GET http://localhost:8080/api/v1/admin/cj/feed/runs \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
```

Filter by advertiser:
```bash
curl -X GET "http://localhost:8080/api/v1/admin/cj/feed/runs?advertiserId=123456" \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
```

### Cancel a Running Feed

```bash
curl -X POST http://localhost:8080/api/v1/admin/cj/feed/runs/{runId}/cancel \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
```

## Real-time Progress Tracking

The system publishes Kafka events for real-time progress updates:

**Topic:** `cj.feed.status`

**Event Example:**
```json
{
  "runId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "advertiserId": 123456,
  "phase": "PROCESSING",
  "processed": 5000,
  "total": 15234,
  "status": "RUNNING",
  "message": "Processing products..."
}
```

You can consume these events in your admin dashboard for live progress bars.

## Product Data Model

### Product Entity
Each ingested product has:
- `externalKey` - Unique identifier: `CJ:{advertiserId}:{hash}`
- `source` - AFFILIATE (vs OWNED for your own products)
- `status` - DRAFT, PROPOSED, PUBLISHED, REJECTED
- `canonicalUrl` - Merchant's product URL (without tracking)
- `title`, `description`, `brandName`
- `extractedSize`, `sizeType` - Auto-extracted from title
- `images[]` - Product images
- `categories[]` - Auto-classified categories

### Affiliate Offer Entity
Each merchant offer has:
- `externalOfferId` - CJ product ID
- `rawUrl` - Original merchant URL
- `affiliateUrl` - URL with your CJ tracking parameters
- `price`, `salePrice`, `currency`
- `active` - Automatically set to false if missing from feed
- `lastSeenAt` - Timestamp of last feed appearance

## Elasticsearch Indexing

Products are automatically indexed with:
- **Full-text search:** Title, description
- **Autocomplete:** Brand, category names
- **Faceted filters:** Brand, category, price range, size, gender
- **Custom scoring:** Recency decay + offer count boost

**Index:** `vernont-catalog`

**Search Example:**
```bash
curl -X POST http://localhost:8080/api/v1/search/products/faceted \
  -H "Content-Type: application/json" \
  -d '{
    "query": "nike shoes",
    "filters": {
      "brands": ["Nike"],
      "priceMin": 50,
      "priceMax": 150
    }
  }'
```

## Configuration

### Environment Variables

All configuration is via environment variables (already set in `.env`):

```bash
# CJ API Configuration
CJ_API_KEY=aYhFnLClyHUl-DizXVVBLWSgNg
CJ_WEBSITE_ID=101611687
CJ_CID=7811141

# CJ Feed Credentials
CJ_FEED_USERNAME=7811141
CJ_FEED_PASSWORD=CqjH9na=

# Optional: Override defaults
CJ_BASE_URL=https://ads.api.cj.com/query
CJ_SERVICEABLE_AREA=GB
CJ_CURRENCY=GBP
```

### Application Configuration

In `application.yml`:

```yaml
cj:
  base-url: ${CJ_BASE_URL:https://ads.api.cj.com/query}
  api-key: ${CJ_API_KEY:}
  default-company-id: ${CJ_COMPANY_ID:${CJ_WEBSITE_ID:}}
  default-serviceable-area: ${CJ_SERVICEABLE_AREA:GB}
  default-currency: ${CJ_CURRENCY:GBP}
  rate-limit:
    requests-per-second: 3
    timeout: PT20S
  sync:
    enable-scheduling: true
    cron: "0 0 3 * * ?"  # 3 AM daily
```

## Scheduled Automatic Sync (Optional)

To enable automatic feed syncing:

**1. Add scheduler configuration to `application.yml`:**

```yaml
spring:
  task:
    scheduling:
      enabled: true
```

**2. Create a scheduled task (if not already present):**

```kotlin
@Component
@EnableScheduling
class CjFeedScheduler(
    private val feedConfigService: CjFeedConfigService
) {
    @Scheduled(cron = "\${cj.sync.cron:0 0 3 * * ?}")
    fun syncAllEnabledFeeds() {
        feedConfigService.syncAllEnabledFeeds()
    }
}
```

This will automatically sync all enabled feeds at 3 AM daily.

## Troubleshooting

### Issue: "Feed URL required for CJ feed ingest"

**Solution:** Either provide `url` in the request OR configure a feed entry in `cj_feed_config` table with `enabled=true`.

### Issue: "No products ingested"

**Possible Causes:**
1. Products missing required fields (title, link, price)
2. Network issue downloading feed

**Check Logs:**
```bash
tail -f vernont-api/logs/application.log | grep "CJ ingest"
```

### Issue: Products not appearing in search

**Solution:** Manually trigger reindex:

```bash
curl -X POST http://localhost:8080/api/v1/admin/search/reindex \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
```

### Issue: Feed download fails with authentication error

**Solution:** Verify CJ_FEED_USERNAME and CJ_FEED_PASSWORD are correct in `.env` file.

## Performance Considerations

**Large Feeds (>100k products):**
- Ingestion is **streaming** - no memory limits, handles millions of products
- Progress updates every 1000 products
- Uses bulk upsert with batch size 50
- Elasticsearch indexing deferred until after ingestion
- All regions and currencies supported

**Concurrent Feed Runs:**
- Multiple advertisers can be ingested simultaneously
- Each run has independent progress tracking
- Database uses optimistic locking to prevent conflicts

**Deactivation of Missing Offers:**
- After successful feed sync, offers not in feed are marked `active=false`
- Prevents stale offers from appearing in search results

## API Rate Limiting

CJ feed ingestion endpoints are **admin-only** and not rate-limited (except general admin limits from security config).

## Next Steps

1. **Get Feed URL from CJ:**
   - Wait for CJ's email notification with feed export details
   - CJ will provide an HTTP URL to download the ZIP file

2. **Test with Manual Trigger:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/admin/cj/feed/ingest \
     -H "Authorization: Bearer YOUR_ADMIN_JWT" \
     -H "Content-Type: application/json" \
     -d '{
       "advertiserId": ADVERTISER_ID_FROM_EMAIL,
       "url": "FEED_URL_FROM_EMAIL",
       "dryRun": true
     }'
   ```

3. **Verify Products Ingested:**
   ```bash
   curl -X GET "http://localhost:8080/api/v1/search/products?query=*&limit=10" \
     -H "Authorization: Bearer YOUR_JWT"
   ```

4. **Set Up Feed Config for Automation:**
   - Create feed config entries for each advertiser
   - Enable scheduling to automatically sync feeds

## Support

For issues or questions:
- Check logs: `vernont-api/logs/application.log`
- View feed runs: `GET /api/v1/admin/cj/feed/runs`
- Monitor Kafka topic: `cj.feed.status`

## Security Notes

- All feed endpoints require **ADMIN** role
- Feed credentials stored in `.env` (gitignored)
- Affiliate URLs include your CJ tracking parameters
- Products are soft-deleted (never permanently removed)
