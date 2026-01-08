-- Add invitation tracking fields to app_user table
ALTER TABLE app_user
    ADD COLUMN invite_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN invited_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN invite_accepted_at TIMESTAMP WITH TIME ZONE;

-- Index for filtering by invite status
CREATE INDEX idx_user_invite_status ON app_user(invite_status) WHERE deleted_at IS NULL;

-- Update existing users to have NONE status (they were created directly)
-- Already handled by DEFAULT 'NONE'
