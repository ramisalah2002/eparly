-- ============================================================================
-- Migration V23 : rattachement des offres collectees au catalogue metier.
-- Additive : aucune ligne existante n'est modifiee.
-- ============================================================================

-- created_by_batch = FALSE : offre preexistante, seulement liee, jamais modifiee par le batch.
-- owner_source_id : seule cette source peut faire evoluer le tarif de travail du brouillon.
ALTER TABLE catalog_offer_identities
    ADD COLUMN commercial_offer_id UUID REFERENCES commercial_offers(id) ON DELETE RESTRICT,
    ADD COLUMN owner_source_id UUID REFERENCES catalog_sync_sources(id) ON DELETE RESTRICT,
    ADD COLUMN created_by_batch BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_catalog_identities_offer ON catalog_offer_identities (commercial_offer_id);

-- Motif pour lequel un candidat ouvert n'a pas ete integre automatiquement.
ALTER TABLE catalog_offer_candidates
    ADD COLUMN business_block_reason VARCHAR(100);

ALTER TABLE catalog_sync_runs
    ADD COLUMN providers_created INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN catalog_offers_created INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN catalog_prices_changed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN catalog_evidence_added INTEGER NOT NULL DEFAULT 0;
