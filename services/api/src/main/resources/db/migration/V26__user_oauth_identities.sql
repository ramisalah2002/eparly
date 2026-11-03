-- ============================================================================
-- Migration V26 : identites externes et comptes sans mot de passe local.
-- Aucune ligne existante n'est modifiee. Aucun jeton Google n'est stocke.
-- ============================================================================

-- NULL = aucun mot de passe local : la connexion par mot de passe echoue toujours pour ce compte.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE user_oauth_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email_at_link_time VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_oauth_provider CHECK (provider IN ('GOOGLE')),
    -- Ces deux index couvrent aussi les recherches par identite et par utilisateur.
    CONSTRAINT uq_user_oauth_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_user_oauth_user_provider UNIQUE (user_id, provider)
);
