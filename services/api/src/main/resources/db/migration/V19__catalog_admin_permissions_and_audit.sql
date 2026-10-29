CREATE TABLE catalog_admin_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    granted_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT chk_catalog_admin_permission_revocation
        CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);

CREATE UNIQUE INDEX uq_catalog_admin_permission_active
    ON catalog_admin_permissions (user_id)
    WHERE revoked_at IS NULL;

CREATE UNIQUE INDEX uq_offer_providers_name_sector_ci
    ON offer_providers (lower(name), sector);

CREATE TABLE catalog_admin_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID NOT NULL,
    details JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_catalog_admin_audit_resource
    ON catalog_admin_audit (resource_type, resource_id, occurred_at DESC);
CREATE INDEX idx_catalog_admin_audit_actor
    ON catalog_admin_audit (actor_user_id, occurred_at DESC);

ALTER TABLE commercial_offers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE offer_providers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE offer_evidence
    ADD COLUMN verified_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN verified_pricing_version BIGINT,
    ADD COLUMN verified_provider_version BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE offer_evidence evidence
SET verified_pricing_version = pricing.version,
    verified_provider_version = provider.version
FROM offer_pricing pricing
JOIN commercial_offers offer ON offer.id = pricing.offer_id
JOIN offer_providers provider ON provider.id = offer.provider_id
WHERE pricing.offer_id = evidence.offer_id
  AND evidence.verification_status = 'VERIFIED'
  AND (evidence.evidence_scope IS NULL OR evidence.evidence_scope IN ('OFFER', 'PROMOTION'));
