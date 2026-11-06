-- ============================================================================
-- Migration V12 : Catalogue d'offres commerciales verifiees
-- ============================================================================

CREATE TABLE offer_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    sector VARCHAR(30) NOT NULL,
    official_website VARCHAR(500) NOT NULL,
    country VARCHAR(2) NOT NULL DEFAULT 'FR',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_offer_providers_sector CHECK (
        sector IN ('STREAMING', 'MOBILE_PLAN', 'INTERNET_BOX')
    ),
    CONSTRAINT chk_offer_providers_status CHECK (
        status IN ('ACTIVE', 'INACTIVE')
    ),
    CONSTRAINT chk_offer_providers_country CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_offer_providers_website CHECK (official_website ~ '^https://.+')
);

CREATE INDEX idx_offer_providers_sector ON offer_providers (sector);
CREATE INDEX idx_offer_providers_status ON offer_providers (status);
CREATE INDEX idx_offer_providers_name ON offer_providers (name);

CREATE TABLE commercial_offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL REFERENCES offer_providers(id) ON DELETE RESTRICT,
    commercial_name VARCHAR(200) NOT NULL,
    sector VARCHAR(30) NOT NULL,
    characteristics TEXT,
    eligibility_conditions TEXT,
    commitment VARCHAR(100),
    territory VARCHAR(100) NOT NULL DEFAULT 'FR',
    publication_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    valid_from DATE,
    valid_until DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_commercial_offers_sector CHECK (
        sector IN ('STREAMING', 'MOBILE_PLAN', 'INTERNET_BOX')
    ),
    CONSTRAINT chk_commercial_offers_status CHECK (
        publication_status IN ('DRAFT', 'VERIFIED', 'STALE', 'EXPIRED', 'ARCHIVED')
    ),
    CONSTRAINT chk_commercial_offers_validity CHECK (
        valid_from IS NULL OR valid_until IS NULL OR valid_from <= valid_until
    ),
    CONSTRAINT uq_commercial_offers_name_provider UNIQUE (commercial_name, provider_id)
);

CREATE INDEX idx_commercial_offers_provider ON commercial_offers (provider_id);
CREATE INDEX idx_commercial_offers_sector ON commercial_offers (sector);
CREATE INDEX idx_commercial_offers_status ON commercial_offers (publication_status);
CREATE INDEX idx_commercial_offers_territory ON commercial_offers (territory);
CREATE INDEX idx_commercial_offers_valid_until ON commercial_offers (valid_until);

CREATE TABLE offer_pricing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id UUID NOT NULL REFERENCES commercial_offers(id) ON DELETE CASCADE,
    amount_minor BIGINT,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    billing_period VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    activation_fee_minor BIGINT,
    other_fees TEXT,
    promotion_percent INTEGER,
    promotion_duration_months INTEGER,
    price_after_promotion_minor BIGINT,
    discount_conditions VARCHAR(500),
    taxes_included BOOLEAN NOT NULL DEFAULT TRUE,
    data_unknown BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_offer_pricing_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_offer_pricing_billing_period CHECK (
        billing_period IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'UNKNOWN')
    ),
    CONSTRAINT chk_offer_pricing_amount CHECK (amount_minor >= 0),
    CONSTRAINT chk_offer_pricing_activation_fee CHECK (activation_fee_minor IS NULL OR activation_fee_minor >= 0),
    CONSTRAINT chk_offer_pricing_promotion_percent CHECK (
        promotion_percent IS NULL OR (promotion_percent >= 0 AND promotion_percent <= 100)
    ),
    CONSTRAINT chk_offer_pricing_price_after_promo CHECK (
        price_after_promotion_minor IS NULL OR price_after_promotion_minor >= 0
    ),
    CONSTRAINT uq_offer_pricing_offer UNIQUE (offer_id)
);

CREATE INDEX idx_offer_pricing_offer ON offer_pricing (offer_id);

CREATE TABLE offer_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id UUID NOT NULL REFERENCES commercial_offers(id) ON DELETE CASCADE,
    official_url VARCHAR(1000) NOT NULL,
    consulted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    nature VARCHAR(200) NOT NULL,
    verified_summary VARCHAR(2000),
    expires_at TIMESTAMPTZ,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provenance VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_offer_evidence_status CHECK (
        verification_status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'INVALID')
    ),
    CONSTRAINT chk_offer_evidence_url CHECK (official_url ~ '^https://.+')
);

CREATE INDEX idx_offer_evidence_offer ON offer_evidence (offer_id);
CREATE INDEX idx_offer_evidence_status ON offer_evidence (verification_status);
CREATE INDEX idx_offer_evidence_verified_at ON offer_evidence (verified_at);
CREATE INDEX idx_offer_evidence_url ON offer_evidence (official_url);

CREATE TABLE offer_price_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id UUID NOT NULL REFERENCES commercial_offers(id) ON DELETE CASCADE,
    old_amount_minor BIGINT,
    new_amount_minor BIGINT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500)
);

CREATE INDEX idx_offer_price_history_offer ON offer_price_history (offer_id);
CREATE INDEX idx_offer_price_history_changed_at ON offer_price_history (changed_at);
