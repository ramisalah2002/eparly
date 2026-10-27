-- ============================================================================
-- Migration V5 : Récupération de mot de passe et invalidation des sessions
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Table password_reset_tokens (Jetons à usage unique pour mot de passe)
-- ----------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_prt_expires_after_created CHECK (expires_at > created_at)
);

-- Index unique sur le hash du token pour recherche rapide et unicité stricte
CREATE UNIQUE INDEX idx_prt_token_hash ON password_reset_tokens (token_hash);

-- Index sur user_id pour retrouver et invalider les anciens jetons
CREATE INDEX idx_prt_user_id ON password_reset_tokens (user_id);

-- ----------------------------------------------------------------------------
-- 2. Champs de sécurité pour l'invalidation immédiate des JWT
-- ----------------------------------------------------------------------------
-- Permet de rejeter immédiatement tout access token émis avant une réinitialisation
ALTER TABLE users ADD COLUMN token_invalid_before TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 1;

-- ----------------------------------------------------------------------------
-- 3. Mise à jour des motifs de révocation des refresh tokens
-- ----------------------------------------------------------------------------
-- Ajout de 'PASSWORD_RESET' pour tracer la révocation globale des sessions
ALTER TABLE refresh_token_sessions DROP CONSTRAINT chk_refresh_sessions_revoked;
ALTER TABLE refresh_token_sessions ADD CONSTRAINT chk_refresh_sessions_revoked CHECK (
    (revoked_at IS NULL AND revoked_reason IS NULL)
    OR (revoked_at IS NOT NULL AND revoked_reason IN ('LOGOUT', 'TOKEN_REUSE', 'ACCOUNT_DISABLED', 'EXPIRED', 'PASSWORD_RESET'))
);
