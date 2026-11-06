-- ============================================================================
-- Migration V13 : Plan d'actions et suivi des économies (étape 11A)
-- ============================================================================

CREATE TABLE savings_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE RESTRICT,
    alternative_offer_id UUID NOT NULL REFERENCES commercial_offers(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'NOT_VERIFIED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    committed_at TIMESTAMPTZ,
    declared_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    horizon_months INT NOT NULL DEFAULT 12,
    estimate_snapshot JSONB,
    CONSTRAINT chk_savings_actions_status CHECK (
        status IN ('PLANNED', 'COMMITTED', 'DECLARED', 'CANCELED')
    ),
    CONSTRAINT chk_savings_actions_verification_status CHECK (
        verification_status IN ('NOT_VERIFIED', 'PARTIALLY_VERIFIED', 'VERIFIED_OBSERVED')
    ),
    CONSTRAINT chk_savings_actions_horizon CHECK (horizon_months > 0)
);

CREATE INDEX idx_savings_actions_user ON savings_actions (user_id);
CREATE INDEX idx_savings_actions_subscription ON savings_actions (subscription_id);
CREATE INDEX idx_savings_actions_status ON savings_actions (status);
CREATE INDEX idx_savings_actions_alternative_offer ON savings_actions (alternative_offer_id);
CREATE INDEX idx_savings_actions_created_at ON savings_actions (created_at);

-- Empêcher plusieurs actions actives pour la même opportunité (même abonnement + même offre)
-- Une action est "active" si son statut est PLANNED, COMMITTED ou DECLARED
CREATE UNIQUE INDEX idx_savings_actions_unique_active
    ON savings_actions (subscription_id, alternative_offer_id)
    WHERE status IN ('PLANNED', 'COMMITTED', 'DECLARED');

CREATE TABLE savings_action_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_id UUID NOT NULL REFERENCES savings_actions(id) ON DELETE CASCADE,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(50) NOT NULL,
    details VARCHAR(1000),
    CONSTRAINT chk_savings_action_history_previous CHECK (
        previous_status IS NULL OR previous_status IN ('PLANNED', 'COMMITTED', 'DECLARED', 'CANCELED')
    ),
    CONSTRAINT chk_savings_action_history_new CHECK (
        new_status IN ('PLANNED', 'COMMITTED', 'DECLARED', 'CANCELED')
    )
);

CREATE INDEX idx_savings_action_history_action ON savings_action_history (action_id);
CREATE INDEX idx_savings_action_history_created_at ON savings_action_history (created_at);
