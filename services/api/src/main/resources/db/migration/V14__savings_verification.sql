-- ============================================================================
-- Migration V14 : Vérification des économies à partir des transactions (étape 11B.1)
-- ============================================================================

-- 1. Ajout de la date effective de changement déclarée par l'utilisateur
ALTER TABLE savings_actions ADD COLUMN effective_change_date DATE;

-- 2. Table des preuves de transactions associées aux actions d'économies
CREATE TABLE savings_action_evidences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action_id UUID NOT NULL REFERENCES savings_actions(id) ON DELETE CASCADE,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    provenance VARCHAR(30) NOT NULL DEFAULT 'USER_CONFIRMED',
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_savings_action_evidence_role CHECK (
        role IN ('OLD_SUBSCRIPTION_PAYMENT', 'NEW_SUBSCRIPTION_PAYMENT')
    ),
    CONSTRAINT chk_savings_action_evidence_provenance CHECK (
        provenance IN ('USER_CONFIRMED', 'SYSTEM_SUGGESTED')
    ),
    -- Une transaction bancaire ne peut être associée qu'une seule fois au maximum,
    -- toutes actions et tous rôles confondus, pour empêcher tout double comptage
    CONSTRAINT uq_savings_action_evidences_transaction UNIQUE (transaction_id)
);

CREATE INDEX idx_savings_action_evidences_action ON savings_action_evidences(action_id);
CREATE INDEX idx_savings_action_evidences_user ON savings_action_evidences(user_id);
CREATE INDEX idx_savings_action_evidences_transaction ON savings_action_evidences(transaction_id);

-- Déclencheur pour garantir l'intégrité multi-locataire absolue au niveau PostgreSQL :
-- NEW.user_id doit correspondre à la fois au propriétaire de l'action et au propriétaire du compte de la transaction.
CREATE OR REPLACE FUNCTION trg_check_evidence_user_consistency()
RETURNS TRIGGER AS $$
DECLARE
    v_action_user_id UUID;
    v_tx_user_id UUID;
BEGIN
    SELECT user_id INTO v_action_user_id FROM savings_actions WHERE id = NEW.action_id;
    IF v_action_user_id IS NULL OR v_action_user_id <> NEW.user_id THEN
        RAISE EXCEPTION 'Action user mismatch in savings_action_evidences: action % belongs to % but evidence user is %',
            NEW.action_id, v_action_user_id, NEW.user_id;
    END IF;

    SELECT a.user_id INTO v_tx_user_id
    FROM transactions t
    JOIN accounts a ON a.id = t.account_id
    WHERE t.id = NEW.transaction_id;

    IF v_tx_user_id IS NULL OR v_tx_user_id <> NEW.user_id THEN
        RAISE EXCEPTION 'Transaction user mismatch in savings_action_evidences: transaction % belongs to % but evidence user is %',
            NEW.transaction_id, v_tx_user_id, NEW.user_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_savings_action_evidences_user_check
BEFORE INSERT OR UPDATE ON savings_action_evidences
FOR EACH ROW EXECUTE FUNCTION trg_check_evidence_user_consistency();

-- 3. Table d'audit des évaluations de vérification
CREATE TABLE savings_action_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_id UUID NOT NULL REFERENCES savings_actions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_monthly_amount_minor BIGINT,
    new_monthly_amount_minor BIGINT,
    observed_difference_minor BIGINT,
    covered_from DATE,
    covered_to DATE,
    missing_information JSONB,
    contradictions JSONB,
    decision_reasons JSONB,
    CONSTRAINT chk_savings_verifications_status CHECK (
        status IN ('NOT_VERIFIED', 'PARTIALLY_VERIFIED', 'VERIFIED_OBSERVED')
    )
);

CREATE INDEX idx_savings_verifications_action ON savings_action_verifications(action_id);
CREATE INDEX idx_savings_verifications_user ON savings_action_verifications(user_id);
CREATE INDEX idx_savings_verifications_evaluated ON savings_action_verifications(evaluated_at);
