-- ============================================================================
-- Migration V11 : Abonnements confirmes / declares par l'utilisateur
-- ============================================================================

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    recurring_series_id UUID REFERENCES recurring_series(id) ON DELETE SET NULL,
    name VARCHAR(200) NOT NULL,
    provider VARCHAR(200),
    declared_amount_minor BIGINT,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    declared_frequency VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    next_due_date DATE,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(1000),
    declared_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    origin VARCHAR(20) NOT NULL DEFAULT 'USER_DECLARED',
    status_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_subscriptions_frequency CHECK (
        declared_frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'UNKNOWN')
    ),
    CONSTRAINT chk_subscriptions_status CHECK (
        declared_status IN ('ACTIVE', 'PAUSED', 'CANCELED', 'UNKNOWN')
    ),
    CONSTRAINT chk_subscriptions_origin CHECK (
        origin IN ('AUTO_DETECTED', 'USER_DECLARED')
    ),
    CONSTRAINT chk_subscriptions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_subscriptions_amount CHECK (declared_amount_minor IS NULL OR declared_amount_minor >= 0),
    CONSTRAINT uq_subscriptions_recurring_series UNIQUE (recurring_series_id)
);

CREATE INDEX idx_subscriptions_user ON subscriptions (user_id);
CREATE INDEX idx_subscriptions_account ON subscriptions (account_id);
CREATE INDEX idx_subscriptions_status ON subscriptions (declared_status);

CREATE TABLE subscription_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(500),
    CONSTRAINT chk_subscription_status_history_from CHECK (
        from_status IS NULL OR from_status IN ('ACTIVE', 'PAUSED', 'CANCELED', 'UNKNOWN')
    ),
    CONSTRAINT chk_subscription_status_history_to CHECK (
        to_status IN ('ACTIVE', 'PAUSED', 'CANCELED', 'UNKNOWN')
    )
);

CREATE INDEX idx_subscription_status_history_subscription ON subscription_status_history (subscription_id);
