-- ============================================================================
-- Migration V16 : Ajout du champ full_name pour l'identité de l'utilisateur
-- ============================================================================

ALTER TABLE users ADD COLUMN full_name VARCHAR(100);
