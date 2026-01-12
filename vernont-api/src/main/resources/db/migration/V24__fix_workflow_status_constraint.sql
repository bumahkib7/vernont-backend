-- V24: Fix workflow status constraint - remove duplicate constraints
-- V23 added chk_workflow_status but didn't drop the original workflow_executions_status_check

-- Drop the original inline constraint (without CLEANED_UP)
ALTER TABLE workflow_executions DROP CONSTRAINT IF EXISTS workflow_executions_status_check;

-- Drop the V23 constraint if it exists (we'll recreate it to ensure clean state)
ALTER TABLE workflow_executions DROP CONSTRAINT IF EXISTS chk_workflow_status;

-- Add the single correct constraint with all valid status values
ALTER TABLE workflow_executions
    ADD CONSTRAINT chk_workflow_status
    CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'COMPENSATED', 'PAUSED', 'CANCELLED', 'TIMEOUT', 'CLEANED_UP'));
