# CJ Feed Ingestion - Remaining Hardening Tasks (Jira Tickets)

## Epic: CJ Feed Ingestion Platform Hardening

**Epic Description:**
Enhance the CJ feed ingestion pipeline with production-ready features including security, monitoring, automation, and operational improvements.

---

## Ticket 1: Implement Security Authorization for CJ Feed Admin API

**Type:** Task
**Priority:** High
**Story Points:** 2

**Description:**
Currently, the CJ feed admin API endpoints are using `permitAll()` in the security configuration. We need to restrict these endpoints to users with ADMIN role.

**Acceptance Criteria:**
- [ ] Update `SecurityConfig.kt` to remove `permitAll` for `/api/v1/admin/cj/**`
- [ ] Add `@PreAuthorize("hasRole('ADMIN')")` to all admin endpoints in `CjFeedController`
- [ ] Test that unauthorized users receive 403 Forbidden
- [ ] Test that users with ADMIN role can access endpoints
- [ ] Update API documentation with authentication requirements

**Technical Notes:**
- Review existing admin endpoints for authorization patterns
- Ensure consistent security across all admin API routes
- Consider adding API key authentication as alternative to session-based auth

**Files to Modify:**
- `vernont-api/src/main/kotlin/com/vernont/api/config/SecurityConfig.kt`
- `vernont-api/src/main/kotlin/com/vernont/api/controller/admin/CjFeedController.kt`

---

## Ticket 2: Enhance DLQ Monitoring and Alerting

**Type:** Improvement
**Priority:** Medium
**Story Points:** 3

**Description:**
Add comprehensive monitoring and alerting for the CJ feed DLQ to ensure failed items are detected and reviewed promptly.

**Acceptance Criteria:**
- [ ] Create Prometheus alert for DLQ message rate > 100/min
- [ ] Create Prometheus alert for DLQ message count > 1000
- [ ] Add Grafana dashboard panel for DLQ metrics
- [ ] Implement scheduled job to report DLQ statistics daily
- [ ] Document DLQ review and replay procedures
- [ ] Add DLQ message retention policy (e.g., 30 days)

**Technical Notes:**
- Alert should route to #platform-alerts Slack channel
- Dashboard should show DLQ volume trends and error patterns
- Consider adding DLQ replay API endpoint for admin users

**Dependencies:**
- Prometheus/Grafana infrastructure
- Slack integration for alerts

---

## Ticket 3: Add Merchant Mapping Configuration

**Type:** Enhancement
**Priority:** Medium
**Story Points:** 3

**Description:**
Implement configurable merchant mapping to deterministically resolve CJ advertiser IDs to internal merchant records, avoiding name-based heuristics.

**Acceptance Criteria:**
- [ ] Create `cj_advertiser_merchant_mapping` database table
- [ ] Add repository and service layer for mappings
- [ ] Create admin API endpoints to manage mappings (CRUD)
- [ ] Update `CjFeedIngestionService` to use mappings for merchant resolution
- [ ] Add admin UI for managing advertiser-to-merchant mappings
- [ ] Document mapping configuration process
- [ ] Migrate existing CJ advertisers to mapping table

**Technical Notes:**
- Table schema: `(advertiser_id BIGINT, merchant_id VARCHAR, network VARCHAR DEFAULT 'CJ')`
- Fallback to current logic if mapping not found
- Support bulk import from CSV

**Files to Create:**
- `vernont-domain/src/main/kotlin/com/vernont/domain/cj/CjAdvertiserMerchantMapping.kt`
- `vernont-domain/src/main/kotlin/com/vernont/repository/cj/CjAdvertiserMerchantMappingRepository.kt`

---

## Ticket 4: Implement Incremental Feed Support

**Type:** Feature
**Priority:** Low
**Story Points:** 5

**Description:**
Add support for incremental/delta feeds to reduce processing time and resource usage for advertisers that provide partial updates.

**Acceptance Criteria:**
- [ ] Add `feedType` parameter to start API (FULL or INCREMENTAL)
- [ ] Skip deactivation pass for incremental feeds
- [ ] Track last full feed timestamp per advertiser
- [ ] Add validation to reject incremental if no prior full feed
- [ ] Update metrics to distinguish full vs incremental runs
- [ ] Document incremental feed usage and limitations

**Technical Notes:**
- Incremental feeds only upsert items, no deactivation
- Require at least one successful full feed before allowing incremental
- Consider TTL for requiring periodic full feed refresh

**Dependencies:**
- Advertiser must provide incremental feed format

---

## Ticket 5: Add Automated Feed Scheduling

**Type:** Feature
**Priority:** Medium
**Story Points:** 5

**Description:**
Implement scheduled feed ingestion with configurable frequency per advertiser to automate catalog refresh without manual intervention.

**Acceptance Criteria:**
- [ ] Create `cj_feed_schedule` table (advertiser_id, feed_url, cron_expression, enabled)
- [ ] Add repository and service for schedule management
- [ ] Implement scheduled job to check and trigger feeds
- [ ] Add admin API for schedule CRUD operations
- [ ] Add admin UI for schedule management
- [ ] Support pause/resume of schedules
- [ ] Send notifications on schedule execution failures
- [ ] Add metrics for scheduled vs manual runs

**Technical Notes:**
- Use Spring `@Scheduled` or Quartz for execution
- Support cron expressions for flexible scheduling
- Handle overlapping runs (skip if previous run still in progress)

**Files to Create:**
- `vernont-domain/src/main/kotlin/com/vernont/domain/cj/CjFeedSchedule.kt`
- `vernont-application/src/main/kotlin/com/vernont/application/affiliate/cj/CjFeedScheduler.kt`

---

## Ticket 6: Implement Feed Validation and Schema Checking

**Type:** Improvement
**Priority:** Medium
**Story Points:** 3

**Description:**
Add upfront feed validation to detect format issues early and provide clear error messages before processing large feeds.

**Acceptance Criteria:**
- [ ] Download first 100 rows and validate schema before full processing
- [ ] Check for required columns (SKU, buyUrl, name, price)
- [ ] Validate data types and format (price as number, URLs as valid)
- [ ] Return validation errors in start API response if validation fails
- [ ] Add `validateOnly` parameter to start API for schema check without ingestion
- [ ] Log validation warnings for missing optional fields
- [ ] Document expected feed schema and validation rules

**Technical Notes:**
- Validation should be fast (< 10 seconds)
- Consider sampling approach for very large feeds
- Add validation metrics and error types

---

## Ticket 7: Enhanced Retry Logic with Exponential Backoff

**Type:** Improvement
**Priority:** Medium
**Story Points:** 3

**Description:**
Implement intelligent retry logic for transient failures with exponential backoff to improve resilience against temporary issues.

**Acceptance Criteria:**
- [ ] Add retry configuration (max attempts, initial delay, multiplier)
- [ ] Implement exponential backoff in `CjFeedUpsertConsumer`
- [ ] Track retry attempts in metrics
- [ ] Send to DLQ only after max retries exhausted
- [ ] Add retry metadata to DLQ messages (attempt count, last error)
- [ ] Support different retry strategies for different error types
- [ ] Document retry behavior and configuration

**Technical Notes:**
- Use Spring Retry or custom implementation
- Distinguish between retryable (network timeout) and non-retryable (validation error) failures
- Avoid blocking consumer for too long during retries

---

## Ticket 8: Real-time Feed Availability Monitoring

**Type:** Feature
**Priority:** Low
**Story Points:** 3

**Description:**
Implement proactive monitoring of CJ feed URLs to detect availability issues before scheduled ingestion runs.

**Acceptance Criteria:**
- [ ] Create scheduled job to check feed URL availability (HEAD request)
- [ ] Track feed URL response time and status codes
- [ ] Alert if feed URL returns errors or slow response
- [ ] Add metrics for feed availability per advertiser
- [ ] Create Grafana dashboard for feed health monitoring
- [ ] Support testing custom feed URLs via admin API

**Technical Notes:**
- Use lightweight HEAD requests to avoid downloading full feeds
- Check every hour or configurable interval
- Alert on consecutive failures (3+ in a row)

---

## Ticket 9: Webhook Notifications for Run Completion

**Type:** Feature
**Priority:** Low
**Story Points:** 3

**Description:**
Add webhook support to notify external systems when feed ingestion runs complete, allowing integration with other workflows.

**Acceptance Criteria:**
- [ ] Add webhook configuration to `cj_feed_schedule` or separate table
- [ ] Support POST webhook with run details on completion
- [ ] Include run status, counts, and duration in webhook payload
- [ ] Implement retry logic for webhook delivery failures
- [ ] Add webhook delivery metrics and logging
- [ ] Support multiple webhook URLs per advertiser
- [ ] Document webhook payload format and authentication

**Technical Notes:**
- Use async webhook delivery to avoid blocking run completion
- Support webhook authentication (API key, HMAC signature)
- Consider using a webhook delivery service/library

---

## Ticket 10: Admin UI Improvements for Run Management

**Type:** Improvement
**Priority:** Medium
**Story Points:** 5

**Description:**
Enhance the admin UI with improved run management features including real-time updates, filtering, run cancellation, and detailed status views.

**Acceptance Criteria:**
- [ ] Add run cancellation button to UI
- [ ] Implement API endpoint for canceling in-progress runs
- [ ] Show real-time progress bar with processed/total counts
- [ ] Add filtering by advertiser, status, date range
- [ ] Display run duration and performance metrics
- [ ] Add export functionality for run history (CSV)
- [ ] Show deactivation counts and impact summary
- [ ] Add run comparison view (before/after stats)

**Technical Notes:**
- Use WebSocket or polling for real-time updates
- Graceful cancellation (stop processing, mark as CANCELLED)
- Consider pagination for large run history

**Dependencies:**
- Admin UI framework (Next.js)

---

## Ticket 11: Database Migration for cj_feed_run Table

**Type:** Task
**Priority:** High
**Story Points:** 1

**Description:**
Create database migration script for the `cj_feed_run` table to ensure consistent schema across environments.

**Acceptance Criteria:**
- [ ] Create Liquibase/Flyway migration script
- [ ] Add indexes on `advertiser_id` and `started_at` for query performance
- [ ] Test migration on dev/staging environments
- [ ] Document rollback procedure
- [ ] Add to deployment checklist
- [ ] Verify table created successfully in all environments

**Technical Notes:**
- Use migration tool consistent with existing schema management
- Consider partitioning strategy for large run history

**SQL:**
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

CREATE INDEX idx_cj_feed_run_advertiser ON cj_feed_run(advertiser_id);
CREATE INDEX idx_cj_feed_run_started_at ON cj_feed_run(started_at DESC);
```

---

## Summary

**Total Tickets:** 11
**Total Story Points:** 36
**Priority Breakdown:**
- High: 2 tickets (3 points)
- Medium: 6 tickets (21 points)
- Low: 3 tickets (12 points)

**Recommended Sprint Allocation:**
- Sprint 1 (High Priority): Tickets #1, #11 - Security and DB migration
- Sprint 2 (Core Improvements): Tickets #2, #3, #6 - Monitoring, mapping, validation
- Sprint 3 (Features): Tickets #5, #10 - Scheduling and UI improvements
- Backlog: Tickets #4, #7, #8, #9 - Nice-to-have enhancements

---

**Created:** 2025-12-23
**Author:** Platform Team
**Epic Link:** NEXUS-XXX (TBD)
