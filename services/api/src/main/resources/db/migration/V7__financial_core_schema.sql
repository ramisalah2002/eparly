-- ============================================================================
-- Migration V7 : Modélisation du cœur financier PostgreSQL (Comptes, Imports, Catégories, Transactions)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Table accounts (Comptes financiers des utilisateurs)
-- ----------------------------------------------------------------------------
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_accounts_source_type CHECK (source_type IN ('CSV', 'BANK_API')),
    CONSTRAINT uq_accounts_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

-- ----------------------------------------------------------------------------
-- 2. Table import_jobs (Opérations de traitement et traçabilité des imports)
-- ----------------------------------------------------------------------------
CREATE TABLE import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    file_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    inserted_rows INT NOT NULL DEFAULT 0,
    skipped_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error_summary VARCHAR(1000),
    CONSTRAINT chk_import_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_import_jobs_counters CHECK (
        total_rows >= 0 AND inserted_rows >= 0 AND skipped_rows >= 0 AND failed_rows >= 0
    )
);

CREATE INDEX idx_import_jobs_user_id ON import_jobs (user_id);
CREATE INDEX idx_import_jobs_account_id ON import_jobs (account_id);
CREATE INDEX idx_import_jobs_account_file_hash ON import_jobs (account_id, file_hash);

-- ----------------------------------------------------------------------------
-- 3. Table categories (Catégories hiérarchiques de dépenses et revenus)
-- ----------------------------------------------------------------------------
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    parent_id UUID REFERENCES categories(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    system_flag BOOLEAN NOT NULL DEFAULT FALSE,
    category_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_categories_system_owner CHECK (
        (system_flag = TRUE AND user_id IS NULL) OR
        (system_flag = FALSE AND user_id IS NOT NULL)
    ),
    CONSTRAINT chk_categories_type CHECK (category_type IN ('EXPENSE', 'INCOME', 'BOTH'))
);

CREATE INDEX idx_categories_user_id ON categories (user_id);
CREATE INDEX idx_categories_parent_id ON categories (parent_id);
CREATE INDEX idx_categories_system ON categories (system_flag);

-- ----------------------------------------------------------------------------
-- 4. Table transactions (Opérations financières importées)
-- ----------------------------------------------------------------------------
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    import_job_id UUID NOT NULL REFERENCES import_jobs(id) ON DELETE RESTRICT,
    source_uid VARCHAR(128),
    fingerprint VARCHAR(64) NOT NULL,
    booking_date DATE NOT NULL,
    value_date DATE,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    raw_label VARCHAR(500) NOT NULL,
    normalized_label VARCHAR(500) NOT NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    custom_category_flag BOOLEAN NOT NULL DEFAULT FALSE,
    transaction_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_transactions_type CHECK (
        transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER', 'REFUND', 'FEE', 'UNKNOWN')
    ),
    CONSTRAINT uq_transactions_account_fingerprint UNIQUE (account_id, fingerprint)
);

CREATE INDEX idx_transactions_account_date ON transactions (account_id, booking_date);
CREATE INDEX idx_transactions_import_job ON transactions (import_job_id);
CREATE INDEX idx_transactions_category ON transactions (category_id);
