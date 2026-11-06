ALTER TABLE offer_pricing
    ADD COLUMN promotion_price_type VARCHAR(20),
    ADD COLUMN promotion_valid_from DATE,
    ADD COLUMN promotion_valid_until DATE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE offer_pricing
    ADD CONSTRAINT chk_offer_promotion_price_type
        CHECK (promotion_price_type IS NULL OR promotion_price_type IN ('PERCENTAGE', 'FIXED_PRICE')),
    ADD CONSTRAINT chk_offer_promotion_dates
        CHECK (promotion_valid_from IS NULL OR promotion_valid_until IS NULL
            OR promotion_valid_until >= promotion_valid_from),
    ADD CONSTRAINT chk_offer_promotion_exclusive
        CHECK (
            promotion_price_type IS NULL
            OR (promotion_price_type = 'PERCENTAGE'
                AND promotion_percent IS NOT NULL
                AND price_after_promotion_minor IS NULL)
            OR (promotion_price_type = 'FIXED_PRICE'
                AND promotion_percent IS NULL
                AND price_after_promotion_minor IS NOT NULL)
        );

ALTER TABLE offer_evidence
    ADD COLUMN evidence_scope VARCHAR(20);

ALTER TABLE offer_evidence
    ADD CONSTRAINT chk_offer_evidence_scope
        CHECK (evidence_scope IS NULL OR evidence_scope IN ('OFFER', 'PRICING', 'PROMOTION'));

ALTER TABLE offer_price_history
    ADD COLUMN old_pricing_snapshot JSONB,
    ADD COLUMN new_pricing_snapshot JSONB;
