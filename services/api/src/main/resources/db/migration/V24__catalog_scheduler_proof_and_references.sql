-- ============================================================================
-- Migration V24 : preuve d'execution du scheduler et protection des tarifs de reference.
-- ============================================================================

-- Une ligne par processus dont le scheduler est reellement charge dans le contexte Spring.
CREATE TABLE catalog_sync_scheduler_instances (
    instance_id UUID PRIMARY KEY,
    pid BIGINT NOT NULL,
    host VARCHAR(255) NOT NULL,
    cron VARCHAR(100) NOT NULL,
    zone VARCHAR(60) NOT NULL,
    business_ingestion BOOLEAN NOT NULL,
    next_run_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ NOT NULL,
    stopped_at TIMESTAMPTZ
);
CREATE INDEX idx_catalog_scheduler_heartbeat ON catalog_sync_scheduler_instances (last_heartbeat_at DESC);

-- Origine de chaque execution : "scheduler:<instance>" ou "manual".
ALTER TABLE catalog_sync_runs ADD COLUMN triggered_by VARCHAR(100);

-- Un tarif de reference ou un repere n'est pas une offre a proposer : aucune voie ne peut le publier.
ALTER TABLE commercial_offers ADD CONSTRAINT chk_commercial_offers_reference_not_published CHECK (
    publication_status <> 'VERIFIED' OR characteristics IS NULL
    OR position('"offerNature":"REFERENCE' IN characteristics) = 0
);
