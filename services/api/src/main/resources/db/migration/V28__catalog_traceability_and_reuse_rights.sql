-- ============================================================================
-- Migration V28 : mode d'execution des evenements d'audit et droits de reutilisation.
-- Aucune ligne existante n'est modifiee : execution_mode NULL signifie "non qualifie".
-- ============================================================================

ALTER TABLE catalog_admin_audit ALTER COLUMN actor_user_id DROP NOT NULL;
ALTER TABLE catalog_admin_audit ADD COLUMN execution_mode VARCHAR(30);
-- Personne ayant demande une operation systeme : elle n'en est pas l'auteur.
ALTER TABLE catalog_admin_audit ADD COLUMN requested_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE catalog_admin_audit ADD CONSTRAINT chk_catalog_audit_execution_mode
    CHECK (execution_mode IS NULL OR execution_mode IN ('SYSTEM_IMPORT', 'ADMIN_ACTION', 'MANUAL_VERIFICATION'));
ALTER TABLE catalog_admin_audit ADD CONSTRAINT chk_catalog_audit_actor
    CHECK (actor_user_id IS NOT NULL OR execution_mode = 'SYSTEM_IMPORT');

ALTER TABLE commercial_offers ADD COLUMN reuse_rights_status VARCHAR(20) NOT NULL DEFAULT 'NOT_ESTABLISHED';
ALTER TABLE commercial_offers ADD COLUMN reuse_rights_basis VARCHAR(500);
ALTER TABLE commercial_offers ADD CONSTRAINT chk_commercial_offers_reuse_rights
    CHECK (reuse_rights_status IN ('NOT_ESTABLISHED', 'ESTABLISHED', 'RESTRICTED'));
