-- V4: Create Refresh Tokens table
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) UNIQUE NOT NULL,
    token_family UUID NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    user_agent TEXT,
    ip_hash VARCHAR(128)
);

CREATE INDEX idx_refresh_tokens_user_id_expires_at ON refresh_tokens (user_id, expires_at);
CREATE INDEX idx_refresh_tokens_token_family ON refresh_tokens (token_family);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_revoked_at ON refresh_tokens (revoked_at) WHERE revoked_at IS NOT NULL;

-- Clean up expired/revoked tokens periodically (can be run as a scheduled job)
-- DELETE FROM refresh_tokens WHERE expires_at < NOW() OR revoked_at IS NOT NULL;