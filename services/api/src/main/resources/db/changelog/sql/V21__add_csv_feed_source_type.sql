-- Add CSV_FEED to the list of allowed source types
-- This migration extends V20 without modifying it, preserving Flyway checksum integrity

-- Update the constraint on catalog_sync_sources.source_type
-- We need to drop the old constraint and add a new one that includes CSV_FEED

-- First, drop the existing constraint
ALTER TABLE catalog_sync_sources DROP CONSTRAINT IF EXISTS chk_catalog_sync_source_type;

-- Then, recreate the constraint with CSV_FEED added
ALTER TABLE catalog_sync_sources 
    ADD CONSTRAINT chk_catalog_sync_source_type CHECK (
        source_type IN ('OFFICIAL_API', 'JSON_FEED', 'CSV_FEED', 'SITEMAP', 'JSON_LD', 'HTML_PAGE')
    );
