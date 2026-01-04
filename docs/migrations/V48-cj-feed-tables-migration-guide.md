# CJ Feed Tables Migration Guide

## Migration: V48__create_cj_feed_tables.sql

**Date:** 2025-12-23
**Author:** Platform Team
**Jira:** NEXUS-XXX (TBD)

## Overview

This migration creates the database tables required for the CJ (Commission Junction) feed ingestion pipeline:
- `cj_feed_run` - Tracks feed ingestion runs
- `cj_advertiser_config` - Stores per-advertiser feed configuration

## Prerequisites

- Database: PostgreSQL 12+
- Migration Tool: Flyway
- Required Extensions: None (JSONB is built-in)
- Estimated Execution Time: < 1 second
- Table Size: Empty initially

## Tables Created

### 1. cj_feed_run

Tracks feed ingestion runs with status and progress.

**Columns:**
- `run_id` (VARCHAR(255), PK) - Unique UUID for each run
- `advertiser_id` (BIGINT) - CJ advertiser ID
- `status` (VARCHAR(50)) - STARTED, RUNNING, COMPLETED, FAILED, CANCELLED
- `processed` (BIGINT) - Number of items processed
- `total` (BIGINT, nullable) - Total items (null if unknown)
- `started_at` (TIMESTAMP) - When the run started
- `finished_at` (TIMESTAMP, nullable) - When the run finished
- `last_updated_at` (TIMESTAMP) - Last progress update
- `created_at` (TIMESTAMP) - Record creation timestamp
- `updated_at` (TIMESTAMP) - Record update timestamp
- `version` (INTEGER) - Optimistic locking version

**Indexes:**
- `idx_cj_feed_run_advertiser_id` - Query runs by advertiser
- `idx_cj_feed_run_started_at` - Query recent runs (DESC)
- `idx_cj_feed_run_status` - Query by status

### 2. cj_advertiser_config

Stores feed configuration per advertiser.

**Columns:**
- `id` (BIGSERIAL, PK) - Auto-increment primary key
- `advertiser_id` (BIGINT, UNIQUE) - CJ advertiser ID
- `advertiser_name` (VARCHAR(255), nullable) - Human-readable name
- `feed_url` (VARCHAR(2048), nullable) - Feed download URL
- `feed_format` (VARCHAR(50)) - tsv, csv, xml, json (default: tsv)
- `compression_type` (VARCHAR(50), nullable) - gzip, zip, none
- `enabled` (BOOLEAN) - Whether config is active (default: true)
- `schedule_cron` (VARCHAR(255), nullable) - Cron expression for scheduling
- `metadata` (JSONB, nullable) - Additional configuration
- `created_at` (TIMESTAMP) - Record creation timestamp
- `updated_at` (TIMESTAMP) - Record update timestamp
- `version` (INTEGER) - Optimistic locking version

**Indexes:**
- `idx_cj_advertiser_config_advertiser_id` - UNIQUE, lookup by advertiser
- `idx_cj_advertiser_config_enabled` - Partial index on enabled configs

## Running the Migration

### Development

```bash
# Run migration
./gradlew flywayMigrate

# Check migration status
./gradlew flywayInfo

# Validate migration
./gradlew flywayValidate
```

### Staging/Production

```bash
# 1. Backup database
pg_dump -h <host> -U <user> -d vernont_db -F c -f backup_pre_v48.dump

# 2. Run migration with Flyway
flyway -url=jdbc:postgresql://<host>:5432/vernont_db \
       -user=<user> \
       -password=<password> \
       migrate

# 3. Verify migration
flyway -url=jdbc:postgresql://<host>:5432/vernont_db \
       -user=<user> \
       info

# 4. Verify tables created
psql -h <host> -U <user> -d vernont_db -c "\d cj_feed_run"
psql -h <host> -U <user> -d vernont_db -c "\d cj_advertiser_config"
```

## Rollback

If you need to rollback this migration:

```bash
# Using Flyway undo (if configured)
flyway -url=jdbc:postgresql://<host>:5432/vernont_db \
       -user=<user> \
       -password=<password> \
       undo

# Or manually execute the undo script
psql -h <host> -U <user> -d vernont_db -f U48__drop_cj_feed_tables.sql
```

## Post-Migration Steps

### 1. Verify Table Creation

```sql
-- Check tables exist
SELECT table_name FROM information_schema.tables
WHERE table_name IN ('cj_feed_run', 'cj_advertiser_config');

-- Check indexes
SELECT indexname FROM pg_indexes
WHERE tablename IN ('cj_feed_run', 'cj_advertiser_config');
```

### 2. Insert Test Data (Optional)

```sql
-- Add a test advertiser config
INSERT INTO cj_advertiser_config (
    advertiser_id,
    advertiser_name,
    feed_url,
    feed_format,
    compression_type,
    enabled
) VALUES (
    12345,
    'Test Advertiser',
    'https://example.com/feeds/12345.tsv.gz',
    'tsv',
    'gzip',
    false  -- Disabled for testing
);

-- Verify insert
SELECT * FROM cj_advertiser_config;
```

### 3. Grant Permissions

```sql
-- Grant permissions to application user
GRANT SELECT, INSERT, UPDATE, DELETE ON cj_feed_run TO vernont_app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON cj_advertiser_config TO vernont_app_user;
GRANT USAGE, SELECT ON SEQUENCE cj_advertiser_config_id_seq TO vernont_app_user;
```

### 4. Configure Monitoring

Add alerts for:
- Failed runs: `SELECT COUNT(*) FROM cj_feed_run WHERE status = 'FAILED'`
- Long-running jobs: `SELECT COUNT(*) FROM cj_feed_run WHERE status = 'RUNNING' AND started_at < NOW() - INTERVAL '2 hours'`

## Validation Queries

### Check Migration Success

```sql
-- Verify tables created with correct columns
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'cj_feed_run'
ORDER BY ordinal_position;

-- Verify indexes created
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'cj_feed_run';

-- Test insert/update/delete
BEGIN;
  INSERT INTO cj_feed_run (run_id, advertiser_id, status, started_at, last_updated_at)
  VALUES ('test-uuid', 99999, 'STARTED', NOW(), NOW());

  UPDATE cj_feed_run SET status = 'COMPLETED' WHERE run_id = 'test-uuid';

  DELETE FROM cj_feed_run WHERE run_id = 'test-uuid';
ROLLBACK;
```

### Performance Check

```sql
-- Explain query plan for common queries
EXPLAIN ANALYZE
SELECT * FROM cj_feed_run
WHERE advertiser_id = 12345
ORDER BY started_at DESC
LIMIT 10;

-- Should use idx_cj_feed_run_advertiser_id index
```

## Troubleshooting

### Migration Fails

**Error:** "relation already exists"
```sql
-- Check if tables exist from previous attempt
SELECT * FROM pg_tables WHERE tablename LIKE 'cj_%';

-- Drop and re-run if needed
DROP TABLE IF EXISTS cj_advertiser_config CASCADE;
DROP TABLE IF EXISTS cj_feed_run CASCADE;
```

**Error:** "permission denied"
```sql
-- Grant schema permissions
GRANT ALL ON SCHEMA public TO <migration_user>;
```

### Rollback Issues

**Error:** "cannot drop table because other objects depend on it"
```sql
-- Use CASCADE to drop dependencies
DROP TABLE cj_feed_run CASCADE;
DROP TABLE cj_advertiser_config CASCADE;
```

## Impact Assessment

- **Downtime Required:** No
- **Application Restart Required:** Yes (to pick up new entity mappings)
- **Backward Compatible:** Yes (new tables, no schema changes to existing)
- **Data Loss Risk:** None (creating new tables)

## References

- PR: https://github.com/bumahkib7/vernont-ecommerce/pull/1
- Runbook: `/docs/cj-feed-ingestion-runbook.md`
- Entity: `/vernont-domain/src/main/kotlin/com/vernont/domain/cj/`
- Repository: `/vernont-domain/src/main/kotlin/com/vernont/repository/cj/`

## Approval

- [ ] Code Review: _______________
- [ ] DBA Review: _______________
- [ ] Staging Tested: _______________
- [ ] Production Ready: _______________

---

**Migration Checklist:**
- [ ] Backup completed
- [ ] Migration executed successfully
- [ ] Tables created and verified
- [ ] Indexes created and verified
- [ ] Permissions granted
- [ ] Test data inserted (if applicable)
- [ ] Application restarted
- [ ] Smoke tests passed
- [ ] Monitoring configured
