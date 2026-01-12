-- V22: Add unique constraint for workflow step events
-- This constraint ensures idempotent step event recording (one row per execution+step)

-- Add unique constraint if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_workflow_step_event_execution_step'
    ) THEN
        ALTER TABLE workflow_step_event
            ADD CONSTRAINT uq_workflow_step_event_execution_step
            UNIQUE (execution_id, step_index);
    END IF;
END $$;

-- Add composite indexes for common query patterns if they don't exist
CREATE INDEX IF NOT EXISTS idx_workflow_step_event_workflow_started
    ON workflow_step_event(workflow_name, started_at);

CREATE INDEX IF NOT EXISTS idx_workflow_step_event_workflow_status_started
    ON workflow_step_event(workflow_name, status, started_at);
