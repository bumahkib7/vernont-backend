-- Add workflow_executions table for workflow engine
CREATE TABLE IF NOT EXISTS workflow_executions (
    id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255),
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    deleted_by VARCHAR(255),
    metadata JSONB,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL,
    workflow_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'COMPENSATED', 'PAUSED', 'CANCELLED', 'TIMEOUT')),
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    error_message TEXT,
    error_stack_trace TEXT,
    input_data TEXT,
    output_data TEXT,
    context_data TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    timeout_seconds BIGINT,
    parent_execution_id VARCHAR(36),
    correlation_id VARCHAR(255),
    execution_version INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_name ON workflow_executions (workflow_name);
CREATE INDEX IF NOT EXISTS idx_workflow_status ON workflow_executions (status);
CREATE INDEX IF NOT EXISTS idx_workflow_created ON workflow_executions (created_at);
CREATE INDEX IF NOT EXISTS idx_workflow_parent ON workflow_executions (parent_execution_id);
CREATE INDEX IF NOT EXISTS idx_workflow_correlation ON workflow_executions (correlation_id);
