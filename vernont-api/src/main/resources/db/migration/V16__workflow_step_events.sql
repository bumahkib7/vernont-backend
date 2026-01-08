-- Workflow step events table for tracking individual step executions
-- Used for historical analysis and replay capabilities

CREATE TABLE workflow_step_event (
    id VARCHAR(36) PRIMARY KEY,
    execution_id VARCHAR(36) NOT NULL,
    workflow_name VARCHAR(255) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_index INTEGER NOT NULL,
    total_steps INTEGER,
    status VARCHAR(50) NOT NULL,
    input_data TEXT,
    output_data TEXT,
    error_message TEXT,
    error_type VARCHAR(255),
    duration_ms BIGINT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_step_event_execution
        FOREIGN KEY (execution_id)
        REFERENCES workflow_executions(id)
        ON DELETE CASCADE
);

-- Index for querying steps by execution
CREATE INDEX idx_step_event_execution ON workflow_step_event(execution_id);

-- Index for querying recent step events
CREATE INDEX idx_step_event_started ON workflow_step_event(started_at DESC);

-- Index for querying by workflow name
CREATE INDEX idx_step_event_workflow ON workflow_step_event(workflow_name);

-- Index for querying by status (to find failed steps)
CREATE INDEX idx_step_event_status ON workflow_step_event(status);

-- Composite index for workflow + status queries
CREATE INDEX idx_step_event_workflow_status ON workflow_step_event(workflow_name, status);

COMMENT ON TABLE workflow_step_event IS 'Stores individual workflow step execution events for monitoring and debugging';
COMMENT ON COLUMN workflow_step_event.input_data IS 'JSON serialized input data passed to the step';
COMMENT ON COLUMN workflow_step_event.output_data IS 'JSON serialized output data returned from the step';
COMMENT ON COLUMN workflow_step_event.step_index IS 'Zero-based index of the step within the workflow execution';
