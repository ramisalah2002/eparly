-- ============================================================================
-- Migration V8 : Integrite du moteur d'import (additive, V1 a V7 inchangees)
--  - un import_job appartient au proprietaire du compte vise
--  - une transaction appartient au meme compte que son import_job
--  - une categorie privee ne peut etre utilisee que par son proprietaire
--  - un source_uid est unique dans le perimetre d'un compte
--  - les compteurs d'un import_job sont coherents
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. import_jobs.user_id doit etre le proprietaire de import_jobs.account_id
-- ----------------------------------------------------------------------------
ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_id_user UNIQUE (id, user_id);

ALTER TABLE import_jobs
    ADD CONSTRAINT fk_import_jobs_account_owner
        FOREIGN KEY (account_id, user_id) REFERENCES accounts (id, user_id) ON DELETE RESTRICT;

-- ----------------------------------------------------------------------------
-- 2. transactions.account_id doit etre le compte de transactions.import_job_id
-- ----------------------------------------------------------------------------
ALTER TABLE import_jobs
    ADD CONSTRAINT uq_import_jobs_id_account UNIQUE (id, account_id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_job_account
        FOREIGN KEY (import_job_id, account_id) REFERENCES import_jobs (id, account_id) ON DELETE RESTRICT;

-- ----------------------------------------------------------------------------
-- 3. Unicite de l'identifiant source (quand il existe) dans le perimetre du compte
-- ----------------------------------------------------------------------------
CREATE UNIQUE INDEX uq_transactions_account_source_uid
    ON transactions (account_id, source_uid)
    WHERE source_uid IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 4. Coherence des compteurs d'un import_job
-- ----------------------------------------------------------------------------
ALTER TABLE import_jobs
    ADD CONSTRAINT chk_import_jobs_row_sum
        CHECK (inserted_rows + skipped_rows + failed_rows <= total_rows);

-- ----------------------------------------------------------------------------
-- 5. Visibilite des categories : systeme, ou privee du proprietaire du compte
-- ----------------------------------------------------------------------------
CREATE FUNCTION check_transaction_category_visibility() RETURNS trigger AS $$
DECLARE
    category_owner UUID;
    category_is_system BOOLEAN;
    account_owner UUID;
BEGIN
    IF NEW.category_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT user_id, system_flag INTO category_owner, category_is_system
    FROM categories WHERE id = NEW.category_id;

    IF NOT FOUND OR category_is_system THEN
        RETURN NEW;
    END IF;

    SELECT user_id INTO account_owner FROM accounts WHERE id = NEW.account_id;

    IF category_owner IS DISTINCT FROM account_owner THEN
        RAISE EXCEPTION 'Category is not visible for the owner of the account'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_category_visibility
    BEFORE INSERT OR UPDATE OF category_id, account_id ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION check_transaction_category_visibility();
