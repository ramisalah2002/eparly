-- ============================================================================
-- Migration V6 : Stockage persistant pour le Rate Limiting multi-instances
-- ============================================================================

CREATE TABLE rate_limit_entries (
    entry_key VARCHAR(160) PRIMARY KEY,
    attempt_count INT NOT NULL DEFAULT 1,
    window_start TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

-- Index pour accélérer le nettoyage planifié des fenêtres expirées
CREATE INDEX idx_rate_limit_entries_expires_at ON rate_limit_entries (expires_at);
