-- V23: Add CLEANED_UP status to workflow_executions status constraint
-- The FailedProductCleanupJob marks workflows as CLEANED_UP after resource cleanup

-- Drop the existing constraint and recreate with all valid status values
-- PostgreSQL inline CHECK constraints get auto-generated names, so we need to find and drop it

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    -- Find the check constraint on the status column
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY(con.conkey)
    WHERE rel.relname = 'workflow_executions'
      AND att.attname = 'status'
      AND con.contype = 'c';

    -- Drop the constraint if found
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE workflow_executions DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Add the new constraint with CLEANED_UP included
ALTER TABLE workflow_executions
    ADD CONSTRAINT chk_workflow_status
    CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'COMPENSATED', 'PAUSED', 'CANCELLED', 'TIMEOUT', 'CLEANED_UP'));
