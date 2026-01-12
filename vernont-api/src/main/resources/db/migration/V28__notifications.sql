-- V28: Notification System for Admin Panel

-- ============================================================================
-- NOTIFICATION PREFERENCE - Per-user notification settings
-- ============================================================================

CREATE TABLE IF NOT EXISTS notification_preference (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    user_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    browser_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_preference_user_event
    ON notification_preference (user_id, event_type)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notification_preference_user_id
    ON notification_preference (user_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE notification_preference IS 'Per-user notification settings for each event type';
COMMENT ON COLUMN notification_preference.event_type IS 'ORDER_CREATED, ORDER_PAID, CUSTOMER_REGISTERED, etc.';
COMMENT ON COLUMN notification_preference.browser_enabled IS 'Whether to show browser push notifications';
COMMENT ON COLUMN notification_preference.in_app_enabled IS 'Whether to show in-app toast notifications';

-- ============================================================================
-- NOTIFICATION - Notification history for bell icon
-- ============================================================================

CREATE TABLE IF NOT EXISTS notification (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    user_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    entity_type VARCHAR(50),
    entity_id VARCHAR(36),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_user_unread
    ON notification (user_id, is_read)
    WHERE deleted_at IS NULL AND is_read = FALSE;

CREATE INDEX IF NOT EXISTS idx_notification_user_created
    ON notification (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notification_entity
    ON notification (entity_type, entity_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE notification IS 'Notification history for the bell icon dropdown';
COMMENT ON COLUMN notification.event_type IS 'Type of event that triggered this notification';
COMMENT ON COLUMN notification.entity_type IS 'ORDER, CUSTOMER, PRODUCT, etc.';
COMMENT ON COLUMN notification.entity_id IS 'ID of the related entity for navigation';
