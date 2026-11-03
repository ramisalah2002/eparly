-- ============================================================================
-- Migration V27 : challenges a usage unique de la connexion Google.
-- Seule l'empreinte SHA-256 du nonce est conservee. Aucun jeton Google n'est stocke.
-- ============================================================================

CREATE TABLE google_auth_challenges (
    id UUID PRIMARY KEY,
    nonce_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(10) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT uq_google_challenge_nonce_hash UNIQUE (nonce_hash),
    CONSTRAINT chk_google_challenge_purpose CHECK (purpose IN ('LOGIN', 'LINK')),
    -- Un challenge LINK appartient a un utilisateur ; un challenge LOGIN a aucun.
    CONSTRAINT chk_google_challenge_owner CHECK ((purpose = 'LINK') = (user_id IS NOT NULL)),
    CONSTRAINT chk_google_challenge_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_google_challenge_expires_at ON google_auth_challenges (expires_at);
CREATE INDEX idx_google_challenge_user_id ON google_auth_challenges (user_id) WHERE user_id IS NOT NULL;
