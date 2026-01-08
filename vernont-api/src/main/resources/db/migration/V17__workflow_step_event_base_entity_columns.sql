-- Add BaseEntity columns to workflow_step_event table

ALTER TABLE workflow_step_event
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN created_by VARCHAR(255),
    ADD COLUMN updated_by VARCHAR(255),
    ADD COLUMN deleted_by VARCHAR(255),
    ADD COLUMN metadata JSONB,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN workflow_step_event.updated_at IS 'Last update timestamp from BaseEntity';
COMMENT ON COLUMN workflow_step_event.version IS 'Optimistic locking version from BaseEntity';
