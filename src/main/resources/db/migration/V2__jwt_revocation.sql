CREATE TABLE identity_revoked_tokens (
    token_id VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_revoked_token_expiry ON identity_revoked_tokens(expires_at);
