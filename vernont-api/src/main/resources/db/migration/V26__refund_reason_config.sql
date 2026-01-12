-- V26: Add refund_reason_config table for configurable refund reasons

-- ============================================================================
-- REFUND REASON CONFIG TABLE - Configurable refund reasons for order refunds
-- ============================================================================

CREATE TABLE IF NOT EXISTS refund_reason_config (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    -- Refund reason specific fields
    value VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    description TEXT,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    requires_note BOOLEAN NOT NULL DEFAULT FALSE
);

-- Unique constraint on value (only for non-deleted records)
CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_reason_value
    ON refund_reason_config (value)
    WHERE deleted_at IS NULL;

-- Index for listing active reasons
CREATE INDEX IF NOT EXISTS idx_refund_reason_value
    ON refund_reason_config (value);

-- Index for soft delete filtering
CREATE INDEX IF NOT EXISTS idx_refund_reason_deleted_at
    ON refund_reason_config (deleted_at);

-- Index for ordering
CREATE INDEX IF NOT EXISTS idx_refund_reason_display_order
    ON refund_reason_config (display_order)
    WHERE deleted_at IS NULL;

-- ============================================================================
-- SEED DEFAULT REFUND REASONS
-- ============================================================================

INSERT INTO refund_reason_config (id, value, label, description, display_order, is_active, requires_note)
VALUES
    (gen_random_uuid()::text, 'product_issue', 'Product Issue', 'Product defect or quality issue', 1, TRUE, FALSE),
    (gen_random_uuid()::text, 'shipping_damage', 'Shipping Damage', 'Item damaged during shipping', 2, TRUE, FALSE),
    (gen_random_uuid()::text, 'wrong_item_shipped', 'Wrong Item Shipped', 'Incorrect item was shipped', 3, TRUE, FALSE),
    (gen_random_uuid()::text, 'order_cancelled', 'Order Cancelled', 'Order was cancelled before fulfillment', 4, TRUE, FALSE),
    (gen_random_uuid()::text, 'customer_request', 'Customer Request', 'Customer requested refund', 5, TRUE, FALSE),
    (gen_random_uuid()::text, 'duplicate_order', 'Duplicate Order', 'Customer placed duplicate order', 6, TRUE, FALSE),
    (gen_random_uuid()::text, 'pricing_error', 'Pricing Error', 'Incorrect price was charged', 7, TRUE, FALSE),
    (gen_random_uuid()::text, 'other', 'Other', 'Other reason (please specify)', 99, TRUE, TRUE)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE refund_reason_config IS 'Configurable refund reasons that can be selected when processing order refunds';
COMMENT ON COLUMN refund_reason_config.value IS 'Machine-readable identifier (snake_case)';
COMMENT ON COLUMN refund_reason_config.label IS 'Human-readable display label';
COMMENT ON COLUMN refund_reason_config.description IS 'Detailed description of the reason';
COMMENT ON COLUMN refund_reason_config.display_order IS 'Order in which reasons are displayed to agents';
COMMENT ON COLUMN refund_reason_config.is_active IS 'Whether this reason is currently available for selection';
COMMENT ON COLUMN refund_reason_config.requires_note IS 'Whether agent must provide additional details when selecting this reason';
