-- V19: Product Workflow Refactor - Phased Architecture Support
-- Adds idempotency support, pending image uploads, and human intervention queue

-- ============================================================================
-- WORKFLOW EXECUTION IDEMPOTENCY SUPPORT
-- ============================================================================

-- Add idempotency columns to workflow_executions
ALTER TABLE workflow_executions
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS result_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS result_payload JSONB,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- Unique constraint for idempotency (key + workflow type)
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_idempotency
    ON workflow_executions (idempotency_key, workflow_name)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

-- Index for finding stale in-progress executions
CREATE INDEX IF NOT EXISTS idx_workflow_stale_running
    ON workflow_executions (status, created_at)
    WHERE status = 'RUNNING';

-- Index for cleanup of expired executions
CREATE INDEX IF NOT EXISTS idx_workflow_expires
    ON workflow_executions (expires_at)
    WHERE expires_at IS NOT NULL;

-- ============================================================================
-- PENDING IMAGE UPLOAD TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS pending_image_upload (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    -- Upload specific fields
    product_id VARCHAR(36) NOT NULL,
    source_url VARCHAR(2000) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_url VARCHAR(500),
    error_message VARCHAR(500),
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    last_attempt_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    CONSTRAINT fk_pending_upload_product
        FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

-- Index for finding pending uploads by product
CREATE INDEX IF NOT EXISTS idx_pending_upload_product
    ON pending_image_upload (product_id)
    WHERE deleted_at IS NULL;

-- Index for processing pending uploads
CREATE INDEX IF NOT EXISTS idx_pending_upload_status
    ON pending_image_upload (status, created_at)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

-- Index for cleanup of failed uploads
CREATE INDEX IF NOT EXISTS idx_pending_upload_failed
    ON pending_image_upload (status, last_attempt_at)
    WHERE status = 'FAILED';

-- ============================================================================
-- HUMAN INTERVENTION QUEUE
-- ============================================================================

CREATE TABLE IF NOT EXISTS human_intervention_queue (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    -- Intervention specific fields
    intervention_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    title VARCHAR(255) NOT NULL,
    description TEXT,
    error_message TEXT,
    context_data JSONB,
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(255),
    resolution_notes TEXT,
    auto_retry_count INT NOT NULL DEFAULT 0,
    max_auto_retries INT NOT NULL DEFAULT 3,
    next_auto_retry_at TIMESTAMPTZ
);

-- Index for pending interventions dashboard
CREATE INDEX IF NOT EXISTS idx_intervention_pending
    ON human_intervention_queue (status, severity, created_at)
    WHERE status = 'PENDING';

-- Index for finding interventions by entity
CREATE INDEX IF NOT EXISTS idx_intervention_entity
    ON human_intervention_queue (entity_type, entity_id);

-- Index for auto-retry processing
CREATE INDEX IF NOT EXISTS idx_intervention_retry
    ON human_intervention_queue (status, next_auto_retry_at)
    WHERE status = 'PENDING' AND next_auto_retry_at IS NOT NULL;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE pending_image_upload IS 'Tracks image uploads during product creation - allows retry and progress tracking';
COMMENT ON COLUMN pending_image_upload.status IS 'PENDING, IN_PROGRESS, COMPLETED, FAILED';
COMMENT ON COLUMN pending_image_upload.source_url IS 'Original image URL or identifier to upload';
COMMENT ON COLUMN pending_image_upload.result_url IS 'S3 URL after successful upload';

COMMENT ON TABLE human_intervention_queue IS 'Queue for operations requiring human review - failed cleanups, edge cases';
COMMENT ON COLUMN human_intervention_queue.intervention_type IS 'Type of intervention: FAILED_PRODUCT_CLEANUP, ORPHANED_IMAGES, etc';
COMMENT ON COLUMN human_intervention_queue.severity IS 'LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN human_intervention_queue.status IS 'PENDING, IN_PROGRESS, RESOLVED, IGNORED';

COMMENT ON COLUMN workflow_executions.idempotency_key IS 'Client-provided or deterministic key for duplicate prevention';
COMMENT ON COLUMN workflow_executions.result_id IS 'ID of created entity (e.g., product_id)';
COMMENT ON COLUMN workflow_executions.result_payload IS 'Cached response for idempotent replay';
