-- ============================================================================
-- Migration V22 : collecte multi-categories et multi-sources
-- Strictement additive pour les donnees : aucune ligne existante n'est modifiee.
-- Seules des contraintes CHECK sont elargies et l'unicite des candidats est
-- restreinte aux candidats ouverts (un meme contenu peut etre revu a nouveau).
-- ============================================================================

-- Les trois secteurs historiques conservent leur nom : les comparaisons mobile et
-- streaming restent inchangees. Un secteur sans strategie de comparaison repond deja
-- 404 (precedent INTERNET_BOX), aucun nouveau secteur n'entre donc dans le comparateur.
ALTER TABLE offer_providers DROP CONSTRAINT chk_offer_providers_sector;
ALTER TABLE offer_providers ADD CONSTRAINT chk_offer_providers_sector CHECK (
    sector IN ('STREAMING', 'MOBILE_PLAN', 'INTERNET_BOX', 'MUSIC_STREAMING', 'CLOUD_STORAGE',
               'SOFTWARE_SAAS', 'GAMING', 'ELECTRICITY', 'GAS', 'INSURANCE', 'BANKING',
               'SPORT_FITNESS', 'PRESS', 'TRANSPORT', 'OTHER_RECURRING')
);

ALTER TABLE commercial_offers DROP CONSTRAINT chk_commercial_offers_sector;
ALTER TABLE commercial_offers ADD CONSTRAINT chk_commercial_offers_sector CHECK (
    sector IN ('STREAMING', 'MOBILE_PLAN', 'INTERNET_BOX', 'MUSIC_STREAMING', 'CLOUD_STORAGE',
               'SOFTWARE_SAAS', 'GAMING', 'ELECTRICITY', 'GAS', 'INSURANCE', 'BANKING',
               'SPORT_FITNESS', 'PRESS', 'TRANSPORT', 'OTHER_RECURRING')
);

ALTER TABLE catalog_sync_sources DROP CONSTRAINT chk_catalog_sync_source_sector;
ALTER TABLE catalog_sync_sources ADD CONSTRAINT chk_catalog_sync_source_sector CHECK (
    sector IN ('STREAMING', 'MOBILE_PLAN', 'INTERNET_BOX', 'MUSIC_STREAMING', 'CLOUD_STORAGE',
               'SOFTWARE_SAAS', 'GAMING', 'ELECTRICITY', 'GAS', 'INSURANCE', 'BANKING',
               'SPORT_FITNESS', 'PRESS', 'TRANSPORT', 'OTHER_RECURRING')
);

-- connector_key NULL : lecteur generique selon source_type (comportement V20).
ALTER TABLE catalog_sync_sources
    ADD COLUMN connector_key VARCHAR(60),
    ADD COLUMN consecutive_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN circuit_open_until TIMESTAMPTZ,
    ADD CONSTRAINT chk_catalog_sync_source_failures CHECK (consecutive_failures >= 0);

-- SUCCEEDED et PARTIAL_FAILURE restent admis pour les lignes historiques.
ALTER TABLE catalog_sync_runs DROP CONSTRAINT chk_catalog_sync_run_status;
ALTER TABLE catalog_sync_runs ADD CONSTRAINT chk_catalog_sync_run_status CHECK (
    status IN ('RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'SKIPPED',
               'SUCCEEDED', 'PARTIAL_FAILURE')
);
ALTER TABLE catalog_sync_runs
    ADD COLUMN sources_failed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN sources_skipped INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN sources_not_modified INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN offers_collected INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN offers_duplicates INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN offers_not_returned INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_catalog_sync_run_counts_v22 CHECK (
        sources_failed >= 0 AND sources_skipped >= 0 AND sources_not_modified >= 0
        AND offers_collected >= 0 AND offers_duplicates >= 0 AND offers_not_returned >= 0
    );

-- Bilan par source d'une execution.
CREATE TABLE catalog_sync_run_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES catalog_sync_runs(id) ON DELETE RESTRICT,
    source_id UUID NOT NULL REFERENCES catalog_sync_sources(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    pages_fetched INTEGER NOT NULL DEFAULT 0,
    offers_collected INTEGER NOT NULL DEFAULT 0,
    offers_discovered INTEGER NOT NULL DEFAULT 0,
    offers_updated INTEGER NOT NULL DEFAULT 0,
    offers_unchanged INTEGER NOT NULL DEFAULT 0,
    offers_duplicates INTEGER NOT NULL DEFAULT 0,
    offers_rejected INTEGER NOT NULL DEFAULT 0,
    offers_not_returned INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(40),
    error_message VARCHAR(1000),
    CONSTRAINT chk_catalog_run_source_status CHECK (
        status IN ('SUCCESS', 'NOT_MODIFIED', 'FAILED', 'SKIPPED_ROBOTS',
                   'SKIPPED_NOT_CONFIGURED', 'SKIPPED_CIRCUIT_OPEN')
    ),
    CONSTRAINT uq_catalog_run_source UNIQUE (run_id, source_id)
);
CREATE INDEX idx_catalog_run_sources_source
    ON catalog_sync_run_sources (source_id, started_at DESC);

-- Identite d'une offre independamment de la source qui l'a rapportee.
CREATE TABLE catalog_offer_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identity_key CHAR(64) NOT NULL,
    sector VARCHAR(30) NOT NULL,
    provider_key VARCHAR(300) NOT NULL,
    plan_key VARCHAR(300) NOT NULL,
    variant_key VARCHAR(500) NOT NULL DEFAULT '',
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_catalog_offer_identity UNIQUE (identity_key)
);
CREATE INDEX idx_catalog_identities_sector ON catalog_offer_identities (sector);

ALTER TABLE catalog_offer_observations
    ADD COLUMN run_id UUID REFERENCES catalog_sync_runs(id) ON DELETE RESTRICT,
    ADD COLUMN identity_id UUID REFERENCES catalog_offer_identities(id) ON DELETE RESTRICT,
    ADD COLUMN sector VARCHAR(30),
    ADD COLUMN price_kind VARCHAR(30),
    ADD COLUMN pricing_fingerprint CHAR(64),
    ADD COLUMN comparability_status VARCHAR(30),
    ADD COLUMN comparison_group VARCHAR(500),
    ADD COLUMN raw_payload JSONB,
    ADD CONSTRAINT chk_catalog_observation_price_kind CHECK (
        price_kind IS NULL OR price_kind IN
            ('MISSING', 'FREE', 'FIXED', 'PROMOTIONAL', 'VARIABLE', 'PROFILE_DEPENDENT')
    ),
    ADD CONSTRAINT chk_catalog_observation_comparability CHECK (
        comparability_status IS NULL OR comparability_status IN
            ('COMPARABLE_WITHIN_GROUP', 'INSUFFICIENT_ATTRIBUTES', 'NOT_COMPARABLE')
    );
CREATE INDEX idx_catalog_observations_identity ON catalog_offer_observations (identity_id);

-- Etat courant d'une offre pour une source : detection des retours arriere de prix
-- et des offres non retournees. Ne pilote jamais d'archivage.
CREATE TABLE catalog_source_offer_states (
    source_id UUID NOT NULL REFERENCES catalog_sync_sources(id) ON DELETE RESTRICT,
    offer_external_key VARCHAR(1000) NOT NULL,
    identity_id UUID REFERENCES catalog_offer_identities(id) ON DELETE RESTRICT,
    latest_observation_id UUID NOT NULL REFERENCES catalog_offer_observations(id) ON DELETE RESTRICT,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_run_id UUID REFERENCES catalog_sync_runs(id) ON DELETE RESTRICT,
    missing_since TIMESTAMPTZ,
    PRIMARY KEY (source_id, offer_external_key)
);
CREATE INDEX idx_catalog_source_states_identity ON catalog_source_offer_states (identity_id);

-- Le contenu d'une observation peut redevenir courant (A -> B -> A) : l'unicite ne
-- porte plus que sur les candidats ouverts, l'historique de revue est preserve.
ALTER TABLE catalog_offer_candidates DROP CONSTRAINT uq_catalog_candidate_observation;
CREATE UNIQUE INDEX uq_catalog_candidate_open_observation
    ON catalog_offer_candidates (latest_observation_id)
    WHERE candidate_status = 'NEEDS_REVIEW';

ALTER TABLE catalog_offer_candidates
    ADD COLUMN identity_id UUID REFERENCES catalog_offer_identities(id) ON DELETE RESTRICT,
    ADD COLUMN sector VARCHAR(30);
CREATE INDEX idx_catalog_candidates_identity ON catalog_offer_candidates (identity_id, candidate_status);

ALTER TABLE catalog_offer_candidates DROP CONSTRAINT chk_catalog_candidate_status;
ALTER TABLE catalog_offer_candidates ADD CONSTRAINT chk_catalog_candidate_status CHECK (
    candidate_status IN ('NEEDS_REVIEW', 'ACCEPTED', 'REJECTED', 'SUPERSEDED', 'DRAFT_CREATED')
);
ALTER TABLE catalog_offer_candidates DROP CONSTRAINT chk_catalog_candidate_change;
ALTER TABLE catalog_offer_candidates ADD CONSTRAINT chk_catalog_candidate_change CHECK (
    change_type IN ('NEW_OFFER', 'PRICE_CHANGED', 'DETAILS_CHANGED',
                    'PROMOTION_CHANGED', 'SOURCE_CONFLICT')
);

-- Historique des prix OBSERVES, en insertion seule. Distinct de offer_price_history,
-- qui trace les tarifs du catalogue valides par une personne.
CREATE TABLE catalog_observed_price_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES catalog_sync_sources(id) ON DELETE RESTRICT,
    offer_external_key VARCHAR(1000) NOT NULL,
    identity_id UUID REFERENCES catalog_offer_identities(id) ON DELETE RESTRICT,
    observation_id UUID NOT NULL REFERENCES catalog_offer_observations(id) ON DELETE RESTRICT,
    run_id UUID REFERENCES catalog_sync_runs(id) ON DELETE RESTRICT,
    change_kind VARCHAR(30) NOT NULL,
    old_amount_minor BIGINT,
    new_amount_minor BIGINT,
    old_snapshot JSONB,
    new_snapshot JSONB NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_catalog_price_history_kind CHECK (
        change_kind IN ('FIRST_OBSERVED', 'PRICE_CHANGED', 'PROMOTION_CHANGED')
    )
);
CREATE INDEX idx_catalog_price_history_offer
    ON catalog_observed_price_history (source_id, offer_external_key, detected_at DESC);
CREATE INDEX idx_catalog_price_history_identity
    ON catalog_observed_price_history (identity_id, detected_at DESC);
