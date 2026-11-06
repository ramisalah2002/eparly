-- Preserve the ownership and financial facts on which savings verification relies.

ALTER TABLE savings_actions
    ADD CONSTRAINT uq_savings_actions_id_user UNIQUE (id, user_id);

ALTER TABLE savings_action_verifications
    ADD CONSTRAINT fk_savings_verifications_action_user
    FOREIGN KEY (action_id, user_id)
    REFERENCES savings_actions (id, user_id)
    ON DELETE CASCADE;

CREATE OR REPLACE FUNCTION trg_check_evidence_user_consistency()
RETURNS TRIGGER AS $$
DECLARE
    v_action_user_id UUID;
    v_subscription_account_id UUID;
    v_tx_user_id UUID;
    v_tx_account_id UUID;
BEGIN
    SELECT sa.user_id, s.account_id
    INTO v_action_user_id, v_subscription_account_id
    FROM savings_actions sa
    JOIN subscriptions s ON s.id = sa.subscription_id
    WHERE sa.id = NEW.action_id;

    IF v_action_user_id IS NULL OR v_action_user_id <> NEW.user_id THEN
        RAISE EXCEPTION 'Action user mismatch in savings_action_evidences: action % belongs to % but evidence user is %',
            NEW.action_id, v_action_user_id, NEW.user_id;
    END IF;

    SELECT a.user_id, t.account_id
    INTO v_tx_user_id, v_tx_account_id
    FROM transactions t
    JOIN accounts a ON a.id = t.account_id
    WHERE t.id = NEW.transaction_id;

    IF v_tx_user_id IS NULL OR v_tx_user_id <> NEW.user_id THEN
        RAISE EXCEPTION 'Transaction user mismatch in savings_action_evidences: transaction % belongs to % but evidence user is %',
            NEW.transaction_id, v_tx_user_id, NEW.user_id;
    END IF;

    IF v_subscription_account_id IS NOT NULL AND v_tx_account_id <> v_subscription_account_id THEN
        RAISE EXCEPTION 'Transaction account mismatch in savings_action_evidences: transaction % is not from the subscription account',
            NEW.transaction_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION prevent_evidenced_transaction_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM savings_action_evidences e
        WHERE e.transaction_id = OLD.id
    ) AND (
        OLD.account_id IS DISTINCT FROM NEW.account_id
        OR OLD.import_job_id IS DISTINCT FROM NEW.import_job_id
        OR OLD.fingerprint IS DISTINCT FROM NEW.fingerprint
        OR OLD.booking_date IS DISTINCT FROM NEW.booking_date
        OR OLD.value_date IS DISTINCT FROM NEW.value_date
        OR OLD.amount_minor IS DISTINCT FROM NEW.amount_minor
        OR OLD.currency IS DISTINCT FROM NEW.currency
        OR OLD.raw_label IS DISTINCT FROM NEW.raw_label
        OR OLD.normalized_label IS DISTINCT FROM NEW.normalized_label
        OR OLD.transaction_type IS DISTINCT FROM NEW.transaction_type
    ) THEN
        RAISE EXCEPTION 'Financial facts of transaction % cannot change while it is used as savings evidence', OLD.id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_evidenced_transaction_changes
BEFORE UPDATE OF account_id, import_job_id, fingerprint, booking_date, value_date,
    amount_minor, currency, raw_label, normalized_label, transaction_type
ON transactions
FOR EACH ROW EXECUTE FUNCTION prevent_evidenced_transaction_changes();
