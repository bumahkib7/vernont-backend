# CJ Feed Ingestion Pipeline - Documentation & Runbook

## Overview

The CJ (Commission Junction) Feed Ingestion Pipeline is a complete system for importing product catalog feeds from CJ advertisers into the Nexus commerce platform. It provides streaming feed processing, run tracking, automatic deactivation of stale offers, and comprehensive monitoring through Prometheus metrics.

## Architecture

### Components

1. **CjFeedIngestionService** - Main orchestration service
   - Downloads feed files (TSV format, supports gzip compression)
   - Streams and parses feed data
   - Tracks run state and progress in database
   - Triggers deactivation pass after successful runs

2. **Kafka Pipeline**
   - **CjFeedProducer** - Publishes feed items and status updates
   - **CjFeedUpsertConsumer** - Consumes items and upserts products/offers
   - **Topics**:
     - `cj.feed.items` - Feed item messages
     - `cj.feed.status` - Run status updates
     - `cj.feed.dlq` - Dead letter queue for failed items

3. **Database Entities**
   - **CjFeedRun** - Tracks ingestion runs with status, counts, timestamps
   - **AffiliateMerchant** - CJ merchants (network="CJ", programId=advertiserId)
   - **AffiliateOffer** - Product offers from CJ

4. **Admin API**
   - `POST /api/v1/admin/cj/feed/start` - Start feed ingestion
   - `GET /api/v1/admin/cj/feed/runs` - List recent runs
   - `GET /api/v1/admin/cj/feed/runs/{runId}` - Get run details

### Data Flow

```
CJ Feed URL
    ↓
CjFeedIngestionService (download + parse)
    ↓
Kafka Topic: cj.feed.items
    ↓
CjFeedUpsertConsumer
    ↓
AffiliateIngestService (product/offer upsert)
    ↓
Database (products, offers, merchants)
    ↓
Deactivation Pass (mark stale offers inactive)
```

## Configuration

### Environment Variables

- `SPRING_KAFKA_BOOTSTRAP_SERVERS` - Kafka broker address (default: `redpanda:9092`)
- `SPRING_KAFKA_CONSUMER_GROUP_ID` - Consumer group ID (default: `fo-sync`)

### Kafka Topics

Ensure these topics exist before deployment:
- `cj.feed.items`
- `cj.feed.status`
- `cj.feed.dlq`

### Database Schema

```sql
CREATE TABLE cj_feed_run (
    run_id VARCHAR(255) PRIMARY KEY,
    advertiser_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    processed BIGINT DEFAULT 0,
    total BIGINT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    last_updated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

## API Usage

### Start Feed Ingestion

```bash
curl -X POST http://localhost:8080/api/v1/admin/cj/feed/start \
  -H "Content-Type: application/json" \
  -d '{
    "advertiserId": 12345,
    "url": "https://example.com/feeds/advertiser-12345.tsv.gz",
    "format": "tsv",
    "compression": "gzip",
    "verifyChecksum": false,
    "dryRun": false
  }'
```

**Parameters:**
- `advertiserId` (required) - CJ advertiser ID
- `url` (required) - Feed download URL
- `format` (optional) - Feed format (default: "tsv")
- `compression` (optional) - Compression type (default: auto-detect from URL)
- `verifyChecksum` (optional) - Verify file checksum (default: false)
- `dryRun` (optional) - Skip deactivation pass (default: false)

**Response:**
```json
{
  "runId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "advertiserId": 12345,
  "status": "STARTED"
}
```

### List Recent Runs

```bash
curl http://localhost:8080/api/v1/admin/cj/feed/runs?advertiserId=12345
```

**Response:**
```json
[
  {
    "runId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "advertiserId": 12345,
    "status": "RUNNING",
    "processed": 15000,
    "total": null
  }
]
```

### Get Run Details

```bash
curl http://localhost:8080/api/v1/admin/cj/feed/runs/f47ac10b-58cc-4372-a567-0e02b2c3d479
```

## Monitoring

### Run States

- **STARTED** - Run initiated, download starting
- **RUNNING** - Processing feed items (updated every 1000 items)
- **COMPLETED** - All items processed successfully, deactivation pass complete
- **FAILED** - Error occurred during processing

### Prometheus Metrics

Monitor these metrics in Grafana/Prometheus:

**Batch Metrics:**
- `cj_feed_upsert_batches_received_total` - Total batches received
- `cj_feed_upsert_batches_failed_total` - Failed batches

**Item Metrics:**
- `cj_feed_upsert_items_received_total` - Total items received
- `cj_feed_upsert_items_processed_total` - Successfully processed
- `cj_feed_upsert_items_failed_total` - Failed items
- `cj_feed_upsert_items_sent_to_dlq_total` - Items sent to DLQ

**Product/Offer Metrics:**
- `cj_feed_upsert_products_created_total` - New products created
- `cj_feed_upsert_products_updated_total` - Products updated
- `cj_feed_upsert_offers_created_total` - New offers created
- `cj_feed_upsert_offers_updated_total` - Offers updated

**Duration Metrics:**
- `cj_feed_upsert_item_duration_seconds` (tags: `status=success|failed`) - Item processing time

### Recommended Alerts

1. **High DLQ Volume**
   - Alert when `cj_feed_upsert_items_sent_to_dlq_total` rate > 100/min
   - Action: Check logs and investigate failures

2. **Run Stuck**
   - Alert when run in RUNNING state for > 2 hours
   - Action: Check worker health and Kafka consumer lag

3. **High Failure Rate**
   - Alert when `cj_feed_upsert_items_failed_total` / `cj_feed_upsert_items_received_total` > 5%
   - Action: Review error logs and validate feed format

## Troubleshooting

### Run Stuck in RUNNING

**Symptoms:** Run status shows RUNNING but processed count hasn't increased

**Possible Causes:**
- Kafka consumer down or lagging
- Database connection issues
- Feed URL unreachable

**Resolution:**
```bash
# Check Kafka consumer lag
kafka-consumer-groups --bootstrap-server redpanda:9092 --describe --group cj-feed-upserts

# Check application logs
kubectl logs -f deployment/vernont-backend | grep "CJ Feed"

# Restart consumers if needed
kubectl rollout restart deployment/vernont-backend
```

### High DLQ Volume

**Symptoms:** Many items being sent to DLQ

**Possible Causes:**
- Invalid feed data format
- Database constraints violated
- External service timeouts

**Resolution:**
```bash
# Inspect DLQ messages
kafka-console-consumer --bootstrap-server redpanda:9092 \
  --topic cj.feed.dlq \
  --from-beginning \
  --max-messages 10

# Check application error logs
kubectl logs deployment/vernont-backend | grep "sent to DLQ"

# Fix data issues and replay DLQ if needed
```

### Deactivation Pass Not Running

**Symptoms:** Stale offers remain active after feed completion

**Possible Causes:**
- Run completed with errors
- Merchant not found for advertiser
- dryRun mode enabled

**Resolution:**
```bash
# Check run status and logs
curl http://localhost:8080/api/v1/admin/cj/feed/runs/{runId}

# Verify merchant exists
SELECT * FROM affiliate_merchant WHERE network='CJ' AND program_id={advertiserId};

# Manually trigger deactivation if needed (via SQL or admin API)
```

### Feed Parse Errors

**Symptoms:** Run fails immediately after start

**Possible Causes:**
- Invalid feed URL
- Unsupported feed format
- Network/download issues

**Resolution:**
- Verify feed URL is accessible
- Check feed format matches expected TSV structure
- Review download logs for HTTP errors

## Operational Procedures

### Starting a New Feed

1. Obtain feed URL from CJ account
2. Identify advertiser ID
3. Call start API endpoint
4. Monitor run status via list/get endpoints
5. Watch Prometheus metrics for errors
6. Verify offers created/updated in database

### Handling Failed Runs

1. Check run details to identify error phase
2. Review application logs for stack traces
3. Inspect DLQ messages for failed items
4. Fix underlying issue (data format, constraints, etc.)
5. Re-run feed ingestion
6. Consider manual cleanup if partial data imported

### Scheduled Feed Refresh

For regular feed updates:
1. Set up cron job or scheduled task
2. Call start API endpoint with latest feed URL
3. Monitor success rate and duration trends
4. Alert on anomalies (duration spikes, high failures)

## External ID Mapping

The pipeline uses consistent external ID generation:

```
externalId = advertiserId:SKU
```

If SKU is missing or empty:
```
externalId = advertiserId:hash(canonicalUrl)
```

This ensures:
- Deduplication across runs
- Accurate deactivation of missing items
- Consistent offer identification

## Best Practices

1. **Always test with dryRun first** - Validate feed format without affecting production data
2. **Monitor DLQ regularly** - Set up alerts and review failed items weekly
3. **Track deactivation counts** - Log deactivated offer counts after each run
4. **Use gzip feeds** - Compressed feeds reduce download time and bandwidth
5. **Stagger large feeds** - For multiple advertisers, spread out start times
6. **Maintain feed URL history** - Document feed URL changes and migration dates

## Security Considerations

- Admin API endpoints should be protected with authentication
- Feed URLs may contain sensitive parameters - avoid logging full URLs
- DLQ messages may contain customer data - implement retention policies
- Monitor for unusual advertiser IDs or feed volumes

## Future Enhancements

Potential improvements documented in Jira:
- Incremental feed support (delta updates)
- Automated feed scheduling per advertiser
- Feed validation and schema checking
- Enhanced retry logic with exponential backoff
- Real-time feed availability monitoring
- Webhook notifications for run completion
- Admin UI improvements for run management

## Support

For issues or questions:
- Check application logs: `kubectl logs -f deployment/vernont-backend`
- Monitor Kafka topics: `kafka-console-consumer --topic cj.feed.items`
- Review database: `SELECT * FROM cj_feed_run ORDER BY started_at DESC LIMIT 10`
- Contact: Platform Team (#platform-support)

---

**Last Updated:** 2025-12-23
**Document Owner:** Platform Team
**Review Cadence:** Quarterly
