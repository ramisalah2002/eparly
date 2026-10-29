CREATE TABLE catalog_sync_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID REFERENCES offer_providers(id) ON DELETE RESTRICT,
    source_name VARCHAR(200) NOT NULL,
    official_domain VARCHAR(253) NOT NULL,
    source_url VARCHAR(2000) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    sector VARCHAR(30) NOT NULL,
    country VARCHAR(2) NOT NULL DEFAULT 'FR',
    collection_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    permission_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    permission_reason VARCHAR(1000),
    permission_updated_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    permission_updated_at TIMESTAMPTZ,
    robots_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    extractor_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_checked_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    etag VARCHAR(1000),
    last_modified VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_catalog_sync_source_type CHECK (
        source_type IN ('OFFICIAL_API', 'JSON_FEED', 'CSV_FEED', 'SITEMAP', 'JSON_LD', 'HTML_PAGE')
    ),
    CONSTRAINT chk_catalog_sync_source_sector CHECK (
        sector IN ('STREAMING', 'MOBILE_PLAN', 'INTERNET_BOX')
    ),
    CONSTRAINT chk_catalog_sync_source_country CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_catalog_sync_source_permission CHECK (
        permission_status IN ('PENDING', 'AUTHORIZED', 'DENIED')
    ),
    CONSTRAINT chk_catalog_sync_source_enabled_authorized CHECK (
        collection_enabled = FALSE OR permission_status = 'AUTHORIZED'
    ),
    CONSTRAINT chk_catalog_sync_source_robots CHECK (
        robots_status IN ('UNKNOWN', 'ALLOWED', 'DISALLOWED', 'ERROR')
    ),
    CONSTRAINT chk_catalog_sync_source_https CHECK (
        source_url ~ '^(https://|http://(localhost|127[.]0[.]0[.]1)(:|/))'
    ),
    CONSTRAINT uq_catalog_sync_source_url UNIQUE (source_url)
);

CREATE INDEX idx_catalog_sync_sources_eligible
    ON catalog_sync_sources (collection_enabled, permission_status, robots_status);
CREATE INDEX idx_catalog_sync_sources_provider ON catalog_sync_sources (provider_id);

CREATE TABLE catalog_sync_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL,
    sources_configured INTEGER NOT NULL DEFAULT 0,
    sources_processed INTEGER NOT NULL DEFAULT 0,
    pages_fetched INTEGER NOT NULL DEFAULT 0,
    pages_unsupported INTEGER NOT NULL DEFAULT 0,
    offers_discovered INTEGER NOT NULL DEFAULT 0,
    offers_updated INTEGER NOT NULL DEFAULT 0,
    offers_unchanged INTEGER NOT NULL DEFAULT 0,
    offers_rejected INTEGER NOT NULL DEFAULT 0,
    errors_count INTEGER NOT NULL DEFAULT 0,
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT chk_catalog_sync_run_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL_FAILURE', 'FAILED', 'SKIPPED')
    ),
    CONSTRAINT chk_catalog_sync_run_counts CHECK (
        sources_configured >= 0 AND sources_processed >= 0 AND pages_fetched >= 0
        AND pages_unsupported >= 0
        AND offers_discovered >= 0 AND offers_updated >= 0 AND offers_unchanged >= 0
        AND offers_rejected >= 0 AND errors_count >= 0
    )
);
CREATE INDEX idx_catalog_sync_runs_started ON catalog_sync_runs (started_at DESC);

CREATE TABLE catalog_offer_observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES catalog_sync_sources(id) ON DELETE RESTRICT,
    provider_id UUID REFERENCES offer_providers(id) ON DELETE RESTRICT,
    offer_external_key VARCHAR(1000) NOT NULL,
    canonical_url VARCHAR(2000),
    observed_at TIMESTAMPTZ NOT NULL,
    last_observed_at TIMESTAMPTZ NOT NULL,
    normalized_data JSONB NOT NULL,
    content_hash CHAR(64) NOT NULL,
    confidence NUMERIC(4,3) NOT NULL,
    validation_status VARCHAR(30) NOT NULL,
    validation_reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT chk_catalog_observation_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT chk_catalog_observation_status CHECK (
        validation_status IN ('EXTRACTED', 'VALIDATED_STRUCTURALLY', 'NEEDS_REVIEW', 'REJECTED', 'UNSUPPORTED')
    ),
    CONSTRAINT uq_catalog_observation_identity_hash UNIQUE
        (source_id, offer_external_key, content_hash)
);
CREATE INDEX idx_catalog_observations_source_seen
    ON catalog_offer_observations (source_id, last_observed_at DESC);
CREATE INDEX idx_catalog_observations_provider ON catalog_offer_observations (provider_id);

CREATE TABLE catalog_offer_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES catalog_sync_sources(id) ON DELETE RESTRICT,
    provider_id UUID REFERENCES offer_providers(id) ON DELETE RESTRICT,
    existing_offer_id UUID REFERENCES commercial_offers(id) ON DELETE RESTRICT,
    latest_observation_id UUID NOT NULL REFERENCES catalog_offer_observations(id) ON DELETE RESTRICT,
    candidate_status VARCHAR(20) NOT NULL DEFAULT 'NEEDS_REVIEW',
    change_type VARCHAR(30) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_catalog_candidate_status CHECK (
        candidate_status IN ('NEEDS_REVIEW', 'ACCEPTED', 'REJECTED', 'SUPERSEDED')
    ),
    CONSTRAINT chk_catalog_candidate_change CHECK (
        change_type IN ('NEW_OFFER', 'PRICE_CHANGED', 'DETAILS_CHANGED')
    ),
    CONSTRAINT uq_catalog_candidate_observation UNIQUE (latest_observation_id)
);
CREATE INDEX idx_catalog_candidates_review
    ON catalog_offer_candidates (candidate_status, detected_at DESC);
CREATE INDEX idx_catalog_candidates_existing_offer ON catalog_offer_candidates (existing_offer_id);
