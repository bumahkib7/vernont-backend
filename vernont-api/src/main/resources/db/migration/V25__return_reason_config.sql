-- V25: Add return_reason_config table for configurable return reasons

-- ============================================================================
-- RETURN REASON CONFIG TABLE - Configurable return reasons for RMA
-- ============================================================================

CREATE TABLE IF NOT EXISTS return_reason_config (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    -- Return reason specific fields
    value VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    description TEXT,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    requires_note BOOLEAN NOT NULL DEFAULT FALSE
);

-- Unique constraint on value (only for non-deleted records)
CREATE UNIQUE INDEX IF NOT EXISTS uk_return_reason_value
    ON return_reason_config (value)
    WHERE deleted_at IS NULL;

-- Index for listing active reasons
CREATE INDEX IF NOT EXISTS idx_return_reason_value
    ON return_reason_config (value);

-- Index for soft delete filtering
CREATE INDEX IF NOT EXISTS idx_return_reason_deleted_at
    ON return_reason_config (deleted_at);

-- Index for ordering
CREATE INDEX IF NOT EXISTS idx_return_reason_display_order
    ON return_reason_config (display_order)
    WHERE deleted_at IS NULL;

-- ============================================================================
-- SEED DEFAULT RETURN REASONS
-- ============================================================================

INSERT INTO return_reason_config (id, value, label, description, display_order, is_active, requires_note)
VALUES
    (gen_random_uuid()::text, 'wrong_item', 'Wrong Item', 'Customer received the wrong item', 1, TRUE, FALSE),
    (gen_random_uuid()::text, 'damaged', 'Damaged', 'Item arrived damaged or defective', 2, TRUE, FALSE),
    (gen_random_uuid()::text, 'not_as_described', 'Not as Described', 'Item doesn''t match the description', 3, TRUE, FALSE),
    (gen_random_uuid()::text, 'size_too_small', 'Size Too Small', 'Item is too small', 4, TRUE, FALSE),
    (gen_random_uuid()::text, 'size_too_large', 'Size Too Large', 'Item is too large', 5, TRUE, FALSE),
    (gen_random_uuid()::text, 'changed_mind', 'Changed Mind', 'Customer no longer wants the item', 6, TRUE, FALSE),
    (gen_random_uuid()::text, 'quality_issue', 'Quality Issue', 'Item quality is not as expected', 7, TRUE, FALSE),
    (gen_random_uuid()::text, 'other', 'Other', 'Other reason (please specify)', 99, TRUE, TRUE)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE return_reason_config IS 'Configurable return reasons that customers can select when requesting a return';
COMMENT ON COLUMN return_reason_config.value IS 'Machine-readable identifier (snake_case)';
COMMENT ON COLUMN return_reason_config.label IS 'Human-readable display label';
COMMENT ON COLUMN return_reason_config.description IS 'Detailed description of the reason';
COMMENT ON COLUMN return_reason_config.display_order IS 'Order in which reasons are displayed to customers';
COMMENT ON COLUMN return_reason_config.is_active IS 'Whether this reason is currently available for selection';
COMMENT ON COLUMN return_reason_config.requires_note IS 'Whether customer must provide additional details when selecting this reason';
