-- ============================================================================
-- Migration V30 : suivi des abonnements et des resiliations. Additive.
-- Eparly n'envoie aucune demande au fournisseur : ces tables decrivent ce que l'utilisateur declare.
-- ============================================================================

ALTER TABLE subscriptions ADD COLUMN commitment_end_date DATE;
ALTER TABLE subscriptions ADD COLUMN no_longer_needed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscriptions ADD COLUMN archived_at TIMESTAMPTZ;

-- Series supplementaires d'un meme abonnement, par exemple apres un changement de compte bancaire.
CREATE TABLE subscription_linked_series (
    series_id UUID PRIMARY KEY REFERENCES recurring_series(id) ON DELETE CASCADE,
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_subscription_linked_series_subscription ON subscription_linked_series (subscription_id);

CREATE TABLE subscription_cancellations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    requested_on DATE,
    channel VARCHAR(30),
    request_reference VARCHAR(200),
    provider_end_date DATE,
    confirmed_on DATE,
    confirmation_source VARCHAR(30),
    confirmation_reference VARCHAR(200),
    -- Aucune integration fournisseur n'existe : reste FALSE, aucune API ne le modifie.
    provider_verified BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(1000),
    -- Hypotheses des economies, figees a la decision.
    baseline_amount_minor BIGINT,
    baseline_frequency VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    baseline_currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_subscription_cancellation_status CHECK (
        status IN ('PLANNED', 'REQUESTED', 'CONFIRMED', 'ENDED', 'FAILED', 'ABANDONED')
    ),
    CONSTRAINT chk_subscription_cancellation_channel CHECK (
        channel IS NULL OR channel IN ('ONLINE_ACCOUNT', 'EMAIL', 'POSTAL_MAIL', 'PHONE', 'IN_STORE', 'OTHER')
    ),
    CONSTRAINT chk_subscription_cancellation_source CHECK (
        confirmation_source IS NULL OR confirmation_source IN ('USER_DECLARED', 'USER_HOLDS_PROVIDER_DOCUMENT')
    )
);
-- Une seule demarche ouverte par abonnement, garantie aussi en cas de requetes concurrentes.
CREATE UNIQUE INDEX uq_subscription_open_cancellation ON subscription_cancellations (subscription_id)
    WHERE status IN ('PLANNED', 'REQUESTED', 'CONFIRMED');

-- Historique et evenements metier ; notified_at servira au futur moteur de notifications.
CREATE TABLE subscription_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    details JSONB,
    dedup_key VARCHAR(200),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notified_at TIMESTAMPTZ,
    CONSTRAINT uq_subscription_events_dedup UNIQUE (dedup_key)
);
CREATE INDEX idx_subscription_events_subscription ON subscription_events (subscription_id, occurred_at DESC);
