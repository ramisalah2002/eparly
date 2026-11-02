-- La protection V24 dependait de la forme exacte du JSON. La nature de reference devient une colonne dediee,
-- ecrite par le batch seul : l'edition des caracteristiques ne peut ni la retirer ni la contourner.
ALTER TABLE commercial_offers ADD COLUMN reference_kind VARCHAR(40);

UPDATE commercial_offers
SET reference_kind = substring(characteristics FROM '"offerNature"\s*:\s*"(REFERENCE[A-Z_]*)"')
WHERE characteristics ~ '"offerNature"\s*:\s*"REFERENCE';

ALTER TABLE commercial_offers DROP CONSTRAINT chk_commercial_offers_reference_not_published;
ALTER TABLE commercial_offers ADD CONSTRAINT chk_commercial_offers_reference_not_published CHECK (
    publication_status <> 'VERIFIED' OR reference_kind IS NULL
);
