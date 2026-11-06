-- ============================================================================
-- Migration V10 : Series de paiements recurrents detectes (abonnements potentiels)
-- ============================================================================

CREATE TABLE recurring_series (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    normalized_label VARCHAR(500) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    frequency VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CANDIDATE',
    series_type VARCHAR(30) NOT NULL DEFAULT 'UNCLASSIFIED',
    usual_amount_minor BIGINT NOT NULL,
    min_amount_minor BIGINT NOT NULL,
    max_amount_minor BIGINT NOT NULL,
    occurrence_count INT NOT NULL,
    first_occurrence_date DATE NOT NULL,
    last_occurrence_date DATE NOT NULL,
    next_estimated_date DATE,
    monthly_equivalent_minor NUMERIC(19,4),
    confidence_score INT NOT NULL DEFAULT 0,
    dismissed_occurrence_count INT NOT NULL DEFAULT 0,
    last_analyzed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_decision_at TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_recurring_series_frequency CHECK (
        frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'UNKNOWN')
    ),
    CONSTRAINT chk_recurring_series_status CHECK (
        status IN ('CANDIDATE', 'CONFIRMED', 'DISMISSED')
    ),
    CONSTRAINT chk_recurring_series_type CHECK (
        series_type IN ('SUBSCRIPTION', 'RECURRING_BILL', 'OTHER_RECURRING', 'UNCLASSIFIED')
    ),
    CONSTRAINT chk_recurring_series_counts CHECK (occurrence_count >= 0),
    CONSTRAINT chk_recurring_series_confidence CHECK (confidence_score >= 0 AND confidence_score <= 100),
    CONSTRAINT uq_recurring_series_account_label_currency UNIQUE (account_id, normalized_label, currency)
);

CREATE INDEX idx_recurring_series_account ON recurring_series (account_id);
CREATE INDEX idx_recurring_series_status ON recurring_series (status);
CREATE INDEX idx_recurring_series_type ON recurring_series (series_type);

CREATE TABLE recurring_series_transactions (
    series_id UUID NOT NULL REFERENCES recurring_series(id) ON DELETE CASCADE,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    PRIMARY KEY (series_id, transaction_id)
);

CREATE INDEX idx_recurring_series_transactions_transaction ON recurring_series_transactions (transaction_id);

CREATE TABLE recurring_series_reasons (
    series_id UUID NOT NULL REFERENCES recurring_series(id) ON DELETE CASCADE,
    position INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (series_id, position)
);
