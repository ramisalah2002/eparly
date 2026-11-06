-- ============================================================================
-- Migration V1 : Initialisation technique de la base de données Eparly
-- ============================================================================

-- Activation de l'extension pgcrypto pour la génération sécurisée d'identifiants UUID v4
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Commentaire d'initialisation sur le schéma public
COMMENT ON SCHEMA public IS 'Schema principal pour l''assistant financier Eparly';
