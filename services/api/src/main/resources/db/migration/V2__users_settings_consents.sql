-- ============================================================================
-- Migration V2 : Modélisation des utilisateurs, préférences et consentements
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Table users (Comptes utilisateurs Eparly)
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED'))
);

-- Index unique insensible à la casse pour les adresses email
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));

-- ----------------------------------------------------------------------------
-- 2. Table user_settings (Préférences régionales et financières de l'utilisateur)
-- ----------------------------------------------------------------------------
-- Politique de suppression : ON DELETE RESTRICT pour interdire toute suppression
-- accidentelle en cascade. La fermeture de compte doit faire l'objet d'un processus métier.
CREATE TABLE user_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    country VARCHAR(2) NOT NULL DEFAULT 'FR',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Europe/Paris',
    monthly_savings_target_cents BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_settings_savings_target CHECK (monthly_savings_target_cents >= 0)
);

-- ----------------------------------------------------------------------------
-- 3. Table consent_events (Journal immuable de traçabilité RGPD / Consentements)
-- ----------------------------------------------------------------------------
-- Enregistre chaque accord et retrait de consentement avec version des CGU/politiques.
-- Politique de suppression : ON DELETE RESTRICT pour garantir l'intégrité de la piste d'audit.
CREATE TABLE consent_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    consent_type VARCHAR(100) NOT NULL,
    information_version VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_consent_events_status CHECK (status IN ('GRANTED', 'REVOKED'))
);

CREATE INDEX idx_consent_events_user_id ON consent_events (user_id);
CREATE INDEX idx_consent_events_user_type ON consent_events (user_id, consent_type);
