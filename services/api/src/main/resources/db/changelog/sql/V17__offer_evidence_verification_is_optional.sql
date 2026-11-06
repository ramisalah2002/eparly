-- A source consultation does not imply a human verification.
ALTER TABLE offer_evidence
    ALTER COLUMN consulted_at DROP NOT NULL,
    ALTER COLUMN verified_at DROP NOT NULL;
