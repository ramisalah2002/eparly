-- Registre des sources ouvertes autorisees pour l'environnement LOCAL. Idempotent, aucun secret.
-- Usage : docker exec -i eparly-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < ops/catalog-sources-local.sql
INSERT INTO catalog_sync_sources
    (source_name, official_domain, source_url, source_type, sector, country, collection_enabled,
     permission_status, permission_reason, permission_updated_at, connector_key)
SELECT v.name, v.domain, v.url, v.type, v.sector, 'FR', TRUE, 'AUTHORIZED', v.reason, CURRENT_TIMESTAMP, v.connector
FROM (VALUES
  ('CRE TRV electricite - Base', 'cre.fr', 'https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_Base.csv', 'CSV_FEED', 'ELECTRICITY', 'cre-trv-electricity',
   'Open data CRE, reutilisable librement selon la CRE ; citer source, date et lien. Usage commercial a confirmer avant production.'),
  ('CRE TRV electricite - HP/HC', 'cre.fr', 'https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_HPHC.csv', 'CSV_FEED', 'ELECTRICITY', 'cre-trv-electricity',
   'Open data CRE, reutilisable librement selon la CRE ; citer source, date et lien. Usage commercial a confirmer avant production.'),
  ('CRE TRV electricite - Tempo', 'cre.fr', 'https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_Tempo.csv', 'CSV_FEED', 'ELECTRICITY', 'cre-trv-electricity',
   'Open data CRE, reutilisable librement selon la CRE ; citer source, date et lien. Usage commercial a confirmer avant production.'),
  ('Savoir Placer - tarifs bancaires', 'savoirplacer.fr', 'https://savoirplacer.fr/donnees/csv/tarifs-bancaires.csv', 'CSV_FEED', 'BANKING', 'savoirplacer-bank-tariffs',
   'Licence Ouverte, reutilisation commerciale permise, mention source : savoirplacer.fr.'),
  ('La Bonne Seance - abonnements salles de sport', 'data.gouv.fr', 'https://static.data.gouv.fr/resources/observatoire-des-abonnements-de-salles-de-sport-en-france-la-bonne-seance/20260928-092454/observatoire-abonnements-salles-de-sport.csv', 'CSV_FEED', 'SPORT_FITNESS', 'labonneseance-gym-subscriptions',
   'Licence Ouverte 2.0, mais robots.txt interdit /resources a ce robot : desactivee sans accord.'),
  ('MicroBoussole - offres gratuites de facturation', 'microboussole.fr', 'https://microboussole.fr/facture-electronique/offres-gratuites.csv', 'CSV_FEED', 'SOFTWARE_SAAS', 'microboussole-free-invoicing',
   'Licence Ouverte 2.0 sur data.gouv.fr. Attribution requise.'),
  ('Ile-de-France Mobilites - titres et tarifs', 'iledefrance-mobilites.fr', 'https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/titres-et-tarifs/records', 'OFFICIAL_API', 'TRANSPORT', 'idfm-transport-titles',
   'Licence Ouverte 2.0, mais robots.txt interdit /api/ a ce robot : desactivee sans accord de l editeur.'),
  ('Naolib Nantes Metropole - tarifs', 'nantesmetropole.fr', 'https://data.nantesmetropole.fr/api/explore/v2.1/catalog/datasets/244400404_transports-en-commun-naolib-tarifs-01072026/records', 'OFFICIAL_API', 'TRANSPORT', 'naolib-transport-tariffs',
   'Licence Ouverte 2.0, mais robots.txt interdit /api/ a ce robot : desactivee sans accord de l editeur.')
) AS v(name, domain, url, type, sector, connector, reason)
ON CONFLICT (source_url) DO NOTHING;

-- Bloquees par robots.txt au 2 novembre 2026.
UPDATE catalog_sync_sources SET collection_enabled = FALSE, permission_status = 'PENDING'
WHERE connector_key IN ('idfm-transport-titles', 'naolib-transport-tariffs', 'labonneseance-gym-subscriptions');
