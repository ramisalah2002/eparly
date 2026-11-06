-- Correction de tracabilite de l'import SFR du 2 novembre 2026. Une seule execution utile, idempotente, apres V28.
-- Les 32 evenements d'origine restent inchanges : ils portent le compte du demandeur comme acteur, alors que
-- l'operation etait executee par un script. Une trace corrective SYSTEM_IMPORT est ajoutee pour chacun.
-- consulted_at des 10 preuves SFR : 20:25:00Z etait une approximation ; la lecture source a eu lieu a 20:36:23Z
-- selon le journal de session.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE sfr_import_audit ON COMMIT DROP AS
SELECT a.* FROM catalog_admin_audit a
WHERE a.actor_user_id = 'd9b2f43a-cc46-41fb-9f35-6fcf7f62206b'
  AND a.execution_mode IS NULL
  AND a.occurred_at BETWEEN '2026-11-02 20:44:19+00' AND '2026-11-02 20:48:10+00';

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM sfr_import_audit) <> 32 THEN
        RAISE EXCEPTION 'Expected 32 SFR import audit rows, found %', (SELECT COUNT(*) FROM sfr_import_audit);
    END IF;
END $$;

INSERT INTO catalog_admin_audit
    (actor_user_id, action, resource_type, resource_id, details, execution_mode, requested_by_user_id)
SELECT NULL, 'AUDIT_ACTOR_CORRECTED', s.resource_type, s.resource_id,
       jsonb_build_object(
           'correctedAuditId', s.id,
           'correctedAction', s.action,
           'recordedActorUserId', s.actor_user_id,
           'realExecutionMode', 'SYSTEM_IMPORT',
           'method', CASE WHEN s.action = 'EVIDENCE_ADDED' THEN 'DIRECT_SQL' ELSE 'CATALOG_IMPORT_RUNNER' END,
           'note', 'Execute par un script a la demande du titulaire du compte ; ni action ni verification humaine.'),
       'SYSTEM_IMPORT', s.actor_user_id
FROM sfr_import_audit s
WHERE NOT EXISTS (
    SELECT 1 FROM catalog_admin_audit c
    WHERE c.action = 'AUDIT_ACTOR_CORRECTED' AND c.details ->> 'correctedAuditId' = s.id::text);

CREATE TEMP TABLE sfr_evidence ON COMMIT DROP AS
SELECT e.id, e.offer_id, e.consulted_at FROM offer_evidence e
JOIN sfr_import_audit s ON s.action = 'EVIDENCE_ADDED' AND s.resource_id = e.id
WHERE e.consulted_at = '2026-11-02 20:25:00+00' AND e.verification_status = 'PENDING';

UPDATE offer_evidence e
SET consulted_at = '2026-11-02 20:36:23+00', updated_at = CURRENT_TIMESTAMP, version = e.version + 1
FROM sfr_evidence s WHERE e.id = s.id;

INSERT INTO catalog_admin_audit
    (actor_user_id, action, resource_type, resource_id, details, execution_mode, requested_by_user_id)
SELECT NULL, 'EVIDENCE_CONSULTED_AT_CORRECTED', 'EVIDENCE', s.id,
       jsonb_build_object('offerId', s.offer_id, 'before', s.consulted_at, 'after', '2026-11-02T20:36:23Z',
           'basis', 'Horodatage de la lecture dans le journal de session ; la valeur precedente etait approximative.'),
       'SYSTEM_IMPORT', 'd9b2f43a-cc46-41fb-9f35-6fcf7f62206b'
FROM sfr_evidence s;

COMMIT;
