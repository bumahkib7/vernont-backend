-- ============================================================================
-- Sample Seed Data for CJ Feed Tables
-- Purpose: Test data for development and staging environments
-- Note: DO NOT run in production without modifications
-- ============================================================================

-- Sample CJ Advertiser Configurations
-- Replace with actual advertiser IDs and feed URLs from CJ account

INSERT INTO cj_advertiser_config (advertiser_id, advertiser_name, feed_url, feed_format, compression_type, enabled, schedule_cron, metadata)
VALUES
    (
        12345,
        'Example Retailer',
        'https://feeds.cj.com/advertiser/12345/products.tsv.gz',
        'tsv',
        'gzip',
        true,
        '0 2 * * *',  -- Daily at 2 AM
        '{"category": "electronics", "priority": "high"}'::jsonb
    ),
    (
        67890,
        'Fashion Brand Co',
        'https://feeds.cj.com/advertiser/67890/catalog.tsv.gz',
        'tsv',
        'gzip',
        true,
        '0 3 * * *',  -- Daily at 3 AM
        '{"category": "fashion", "priority": "medium"}'::jsonb
    ),
    (
        11111,
        'Sports Equipment Inc',
        'https://feeds.cj.com/advertiser/11111/inventory.tsv',
        'tsv',
        null,
        false,  -- Disabled for manual testing
        null,
        '{"category": "sports", "priority": "low", "test": true}'::jsonb
    )
ON CONFLICT (advertiser_id) DO NOTHING;

-- Sample Feed Run (for testing queries)
-- Note: These are historical examples, not active runs

INSERT INTO cj_feed_run (run_id, advertiser_id, status, processed, total, started_at, finished_at, last_updated_at)
VALUES
    (
        'aaaaaaaa-bbbb-cccc-dddd-000000000001',
        12345,
        'COMPLETED',
        50000,
        50000,
        NOW() - INTERVAL '7 days',
        NOW() - INTERVAL '7 days' + INTERVAL '15 minutes',
        NOW() - INTERVAL '7 days' + INTERVAL '15 minutes'
    ),
    (
        'aaaaaaaa-bbbb-cccc-dddd-000000000002',
        12345,
        'COMPLETED',
        52000,
        52000,
        NOW() - INTERVAL '6 days',
        NOW() - INTERVAL '6 days' + INTERVAL '18 minutes',
        NOW() - INTERVAL '6 days' + INTERVAL '18 minutes'
    ),
    (
        'aaaaaaaa-bbbb-cccc-dddd-000000000003',
        67890,
        'FAILED',
        1500,
        null,
        NOW() - INTERVAL '5 days',
        NOW() - INTERVAL '5 days' + INTERVAL '2 minutes',
        NOW() - INTERVAL '5 days' + INTERVAL '2 minutes'
    ),
    (
        'aaaaaaaa-bbbb-cccc-dddd-000000000004',
        67890,
        'COMPLETED',
        35000,
        35000,
        NOW() - INTERVAL '4 days',
        NOW() - INTERVAL '4 days' + INTERVAL '12 minutes',
        NOW() - INTERVAL '4 days' + INTERVAL '12 minutes'
    )
ON CONFLICT (run_id) DO NOTHING;

-- Verify seed data
SELECT 'Advertiser Configs Inserted:' as info, COUNT(*) as count FROM cj_advertiser_config;
SELECT 'Feed Runs Inserted:' as info, COUNT(*) as count FROM cj_feed_run;

-- Show sample data
SELECT
    advertiser_id,
    advertiser_name,
    enabled,
    schedule_cron,
    created_at
FROM cj_advertiser_config
ORDER BY advertiser_id;

SELECT
    run_id,
    advertiser_id,
    status,
    processed,
    total,
    EXTRACT(EPOCH FROM (finished_at - started_at))/60 as duration_minutes
FROM cj_feed_run
ORDER BY started_at DESC;
