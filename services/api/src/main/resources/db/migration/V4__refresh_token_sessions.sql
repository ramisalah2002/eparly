-- Chaque ligne représente une génération d'un refresh token opaque.
-- Les lignes consommées sont conservées afin de détecter les réutilisations.
CREATE TABLE refresh_token_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL,
    family_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    replaced_by_session_id UUID REFERENCES refresh_token_sessions(session_id) ON DELETE RESTRICT,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(32),
    CONSTRAINT uq_refresh_token_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_sessions_expiration CHECK (expires_at > created_at),
    CONSTRAINT chk_refresh_sessions_consumed CHECK (consumed_at IS NULL OR consumed_at >= created_at),
    CONSTRAINT chk_refresh_sessions_revoked CHECK (
        (revoked_at IS NULL AND revoked_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_reason IN ('LOGOUT', 'TOKEN_REUSE', 'ACCOUNT_DISABLED', 'EXPIRED'))
    )
);

CREATE INDEX idx_refresh_sessions_user_id ON refresh_token_sessions (user_id);
CREATE INDEX idx_refresh_sessions_family_id ON refresh_token_sessions (family_id);
CREATE INDEX idx_refresh_sessions_expiration ON refresh_token_sessions (expires_at);

-- Une seule génération non consommée et non révoquée peut exister par famille.
CREATE UNIQUE INDEX uq_refresh_sessions_active_family
    ON refresh_token_sessions (family_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
