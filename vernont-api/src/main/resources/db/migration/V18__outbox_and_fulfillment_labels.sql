-- V18: Add outbox table and fulfillment label fields for production-safe shipping

-- ============================================================================
-- OUTBOX TABLE - Transactional outbox for reliable event delivery
-- ============================================================================

CREATE TABLE IF NOT EXISTS outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    -- Outbox specific fields
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    published_at TIMESTAMP,
    correlation_id VARCHAR(36)
);

-- Index for polling pending events (critical for publisher performance)
CREATE INDEX IF NOT EXISTS idx_outbox_event_status_next_attempt
    ON outbox_event (status, next_attempt_at)
    WHERE status = 'PENDING';

-- Index for finding events by aggregate
CREATE INDEX IF NOT EXISTS idx_outbox_event_aggregate
    ON outbox_event (aggregate_id);

-- Index for monitoring by event type
CREATE INDEX IF NOT EXISTS idx_outbox_event_type_created
    ON outbox_event (event_type, created_at);

-- ============================================================================
-- FULFILLMENT LABEL FIELDS - For idempotent label purchases
-- ============================================================================

-- Add label-related columns to fulfillment table
ALTER TABLE fulfillment
    ADD COLUMN IF NOT EXISTS label_idempotency_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS label_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS label_status VARCHAR(20) DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS label_url TEXT,
    ADD COLUMN IF NOT EXISTS label_cost BIGINT,
    ADD COLUMN IF NOT EXISTS carrier_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS service_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS label_void_error TEXT,
    ADD COLUMN IF NOT EXISTS label_purchased_at TIMESTAMP;

-- Index for finding fulfillments by label status (for ops monitoring)
CREATE INDEX IF NOT EXISTS idx_fulfillment_label_status
    ON fulfillment (label_status)
    WHERE label_status IN ('PENDING_PURCHASE', 'VOID_FAILED');

-- Index for finding fulfillments by idempotency key (for duplicate prevention)
CREATE INDEX IF NOT EXISTS idx_fulfillment_label_idempotency_key
    ON fulfillment (label_idempotency_key)
    WHERE label_idempotency_key IS NOT NULL;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE outbox_event IS 'Transactional outbox for reliable event delivery - events written in same tx as business state';
COMMENT ON COLUMN outbox_event.aggregate_type IS 'Type of aggregate (fulfillment, order, etc.)';
COMMENT ON COLUMN outbox_event.status IS 'PENDING, PUBLISHED, or FAILED';
COMMENT ON COLUMN outbox_event.next_attempt_at IS 'When to attempt next publish (exponential backoff)';

COMMENT ON COLUMN fulfillment.label_idempotency_key IS 'Idempotency key for label purchase - prevents double-buy on retry';
COMMENT ON COLUMN fulfillment.label_status IS 'NONE, PENDING_PURCHASE, PURCHASED, VOIDED, VOID_FAILED';
COMMENT ON COLUMN fulfillment.label_void_error IS 'Error message if label void failed - requires ops attention';
