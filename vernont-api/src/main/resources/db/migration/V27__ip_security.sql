-- V27: IP Security & Live User Tracking System for Admin Panel

-- ============================================================================
-- IP LIST ENTRY - Allowlist/blocklist for manual IP overrides
-- ============================================================================

CREATE TABLE IF NOT EXISTS ip_list_entry (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    ip_address VARCHAR(45) NOT NULL,
    list_type VARCHAR(20) NOT NULL,
    reason TEXT,
    expires_at TIMESTAMP,
    added_by_user_id VARCHAR(36)
);

CREATE INDEX IF NOT EXISTS idx_ip_list_entry_ip_address
    ON ip_list_entry (ip_address)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ip_list_entry_list_type
    ON ip_list_entry (list_type)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ip_list_entry_expires_at
    ON ip_list_entry (expires_at)
    WHERE deleted_at IS NULL AND expires_at IS NOT NULL;

COMMENT ON TABLE ip_list_entry IS 'Manual IP allowlist/blocklist entries for overriding automated decisions';
COMMENT ON COLUMN ip_list_entry.ip_address IS 'IPv4 or IPv6 address (supports CIDR notation)';
COMMENT ON COLUMN ip_list_entry.list_type IS 'ALLOWLIST or BLOCKLIST';
COMMENT ON COLUMN ip_list_entry.reason IS 'Reason for adding to list';
COMMENT ON COLUMN ip_list_entry.expires_at IS 'Optional expiration time for temporary entries';

-- ============================================================================
-- IP INTELLIGENCE CACHE - Cached IPQualityScore responses (24h TTL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS ip_intelligence_cache (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    ip_address VARCHAR(45) NOT NULL,
    fraud_score INT NOT NULL DEFAULT 0,
    is_vpn BOOLEAN NOT NULL DEFAULT FALSE,
    is_proxy BOOLEAN NOT NULL DEFAULT FALSE,
    is_tor BOOLEAN NOT NULL DEFAULT FALSE,
    is_datacenter BOOLEAN NOT NULL DEFAULT FALSE,
    is_bot BOOLEAN NOT NULL DEFAULT FALSE,
    is_crawler BOOLEAN NOT NULL DEFAULT FALSE,
    country_code VARCHAR(2),
    city VARCHAR(255),
    region VARCHAR(255),
    isp VARCHAR(255),
    organization VARCHAR(255),
    asn INT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    timezone VARCHAR(100),
    mobile BOOLEAN NOT NULL DEFAULT FALSE,
    host VARCHAR(255),
    raw_response JSONB,
    expires_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ip_intelligence_ip_address
    ON ip_intelligence_cache (ip_address)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ip_intelligence_expires_at
    ON ip_intelligence_cache (expires_at);

CREATE INDEX IF NOT EXISTS idx_ip_intelligence_fraud_score
    ON ip_intelligence_cache (fraud_score)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE ip_intelligence_cache IS 'Cached IP intelligence data from IPQualityScore API';
COMMENT ON COLUMN ip_intelligence_cache.fraud_score IS 'Risk score 0-100, higher is riskier';
COMMENT ON COLUMN ip_intelligence_cache.expires_at IS 'Cache expiration time (typically 24h after creation)';
COMMENT ON COLUMN ip_intelligence_cache.raw_response IS 'Full API response for debugging';

-- ============================================================================
-- ADMIN SESSION - Active admin user sessions for live tracking
-- ============================================================================

CREATE TABLE IF NOT EXISTS admin_session (
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
    session_token_hash VARCHAR(64) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    device_type VARCHAR(50),
    browser VARCHAR(100),
    browser_version VARCHAR(50),
    os VARCHAR(100),
    os_version VARCHAR(50),
    country_code VARCHAR(2),
    city VARCHAR(255),
    region VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    flagged_vpn BOOLEAN NOT NULL DEFAULT FALSE,
    flagged_proxy BOOLEAN NOT NULL DEFAULT FALSE,
    fraud_score INT,
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(36),
    revoke_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_admin_session_user_id
    ON admin_session (user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_admin_session_status
    ON admin_session (status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_admin_session_last_activity
    ON admin_session (last_activity_at)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_admin_session_token_hash
    ON admin_session (session_token_hash)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_admin_session_ip_address
    ON admin_session (ip_address)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE admin_session IS 'Active admin user sessions for live tracking and security monitoring';
COMMENT ON COLUMN admin_session.session_token_hash IS 'SHA-256 hash of the JWT token for lookup';
COMMENT ON COLUMN admin_session.status IS 'ACTIVE, EXPIRED, REVOKED';
COMMENT ON COLUMN admin_session.flagged_vpn IS 'Whether session IP was flagged as VPN';
COMMENT ON COLUMN admin_session.fraud_score IS 'Fraud score at time of session creation';

-- ============================================================================
-- SECURITY EVENT - Security audit log
-- ============================================================================

CREATE TABLE IF NOT EXISTS security_event (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    ip_address VARCHAR(45),
    user_id VARCHAR(36),
    user_email VARCHAR(255),
    session_id VARCHAR(36),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    request_path VARCHAR(500),
    request_method VARCHAR(10),
    user_agent TEXT,
    country_code VARCHAR(2),
    city VARCHAR(255),
    fraud_score INT,
    is_vpn BOOLEAN,
    is_proxy BOOLEAN,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(36),
    resolution_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_security_event_event_type
    ON security_event (event_type)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_security_event_severity
    ON security_event (severity)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_security_event_created_at
    ON security_event (created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_security_event_resolved
    ON security_event (resolved)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_security_event_user_id
    ON security_event (user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_security_event_ip_address
    ON security_event (ip_address)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE security_event IS 'Security audit log for tracking blocked access attempts and security events';
COMMENT ON COLUMN security_event.event_type IS 'VPN_BLOCKED, PROXY_BLOCKED, DATACENTER_BLOCKED, TOR_BLOCKED, HIGH_FRAUD_SCORE, BLOCKLIST_HIT, SESSION_CREATED, SESSION_REVOKED, etc.';
COMMENT ON COLUMN security_event.severity IS 'LOW, MEDIUM, HIGH, CRITICAL';

-- ============================================================================
-- SECURITY CONFIG - Singleton settings table
-- ============================================================================

CREATE TABLE IF NOT EXISTS security_config (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    block_vpn BOOLEAN NOT NULL DEFAULT TRUE,
    block_proxy BOOLEAN NOT NULL DEFAULT TRUE,
    block_datacenter BOOLEAN NOT NULL DEFAULT TRUE,
    block_tor BOOLEAN NOT NULL DEFAULT TRUE,
    block_bots BOOLEAN NOT NULL DEFAULT TRUE,
    fraud_score_threshold INT NOT NULL DEFAULT 75,
    session_timeout_minutes INT NOT NULL DEFAULT 30,
    max_sessions_per_user INT NOT NULL DEFAULT 5,
    ipqs_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    require_allowlist BOOLEAN NOT NULL DEFAULT FALSE
);

-- Insert default configuration
INSERT INTO security_config (id, block_vpn, block_proxy, block_datacenter, block_tor, block_bots, fraud_score_threshold, session_timeout_minutes, max_sessions_per_user, ipqs_enabled, require_allowlist)
VALUES ('default', TRUE, TRUE, TRUE, TRUE, TRUE, 75, 30, 5, TRUE, FALSE)
ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE security_config IS 'Singleton table for security configuration settings';
COMMENT ON COLUMN security_config.fraud_score_threshold IS 'Block requests with fraud score above this threshold (0-100)';
COMMENT ON COLUMN security_config.session_timeout_minutes IS 'Session expires after this many minutes of inactivity';
COMMENT ON COLUMN security_config.max_sessions_per_user IS 'Maximum concurrent sessions per user';
COMMENT ON COLUMN security_config.require_allowlist IS 'If true, only allowlisted IPs can access admin';
