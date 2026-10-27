-- ============================================================================
-- Migration V3 : Table des jetons de vérification d'email
-- ============================================================================

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_evt_expires_after_created CHECK (expires_at > created_at)
);

-- Index unique sur le hash du token pour recherche rapide et prévention des doublons
CREATE UNIQUE INDEX idx_evt_token_hash ON email_verification_tokens (token_hash);

-- Index sur user_id pour retrouver et invalider les anciens jetons
CREATE INDEX idx_evt_user_id ON email_verification_tokens (user_id);
