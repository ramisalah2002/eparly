# Catalogue manuel V1 — Eparly

## 1. Pourquoi le batch automatique est reporté

Pour la V1, la collecte automatique nocturne est désactivée par défaut
(`eparly.catalog-sync.enabled=false`). Aucune source externe n'est activée par
défaut et aucun job ne s'exécute au démarrage. La collecte automatique sera
réétudiée après validation de la V1.

## 2. Ce qui a été retiré concernant Awin

- Clients, services, contrôleur REST et DTO exclusifs à Awin.
- Extracteur spécialisé Awin Telco (`AwinTelcoCsvExtractor`).
- Documentation d'activation Awin dans les guides actifs.

## 3. Ce qui a été conservé

- Infrastructure générique `catalogsync` (fetch sécurisé, SSRF, DNS pinning,
  historique d'observations, déduplication, verrou PostgreSQL).
- Administration sécurisée du catalogue (`CATALOG_ADMIN`).
- Tarification, promotions, historisation, publication, archivage.
- Comparateur et estimation d'économies.

## 4. Structure des fichiers d'offres

Dossier : `services/api/catalog-data/v1/`

- `providers.json`
- `mobile-offers.json`
- `video-streaming-offers.json`
- `music-offers.json`
- `cloud-software-offers.json`
- `README.md`

Les fichiers sont des modèles vides en V1 (aucun prix réel inclus).

## 5. Procédure d'ajout manuel

1. Renseigner les fournisseurs dans `providers.json`.
2. Renseigner les offres dans le fichier correspondant.
3. Renseigner la tarification, les caractéristiques et la preuve officielle.
4. Conserver la date réelle de consultation dans `evidence.consultedAt`.

## 6. Procédure de correction de prix

Utiliser la procédure métier prévue : `PUT /v1/admin/catalog/offers/{offerId}/pricing`
avec `expectedVersion`. La révision est historisée automatiquement.

## 7. Validation des sources officielles

- L'URL de preuve doit appartenir au domaine officiel du fournisseur.
- La preuve doit être validée explicitement via
  `PATCH /v1/admin/catalog/offers/{offerId}/evidences/{evidenceId}/verification`.
- Aucune offre n'est publiée sans vérification effective.

## 8. Procédure d'import local

Configurer :
- `CATALOG_IMPORT_ENABLED=true`
- `CATALOG_IMPORT_FILE=services/api/catalog-data/v1`
- `CATALOG_IMPORT_APPLY=false` (dry-run) puis `true` pour appliquer.

Ou lancer avec arguments :
`--catalog-import-file=... --apply`

## 9. Dry-run

Par défaut, l'import affiche les créations et modifications prévues sans modifier
la base. Les données importées restent en brouillon.

## 10. Validation et publication

Après import :
1. Vérifier les offres en brouillon via l'API admin.
2. Ajouter/valider les preuves.
3. Publier via `POST /v1/admin/catalog/offers/{offerId}/publish`.

## 11. Contrôle du catalogue public

Les offres publiées sont visibles via l'API catalogue utilisateur et utilisables
par le comparateur du même secteur.

## 12. Mise à jour manuelle avant le lancement

Répéter la procédure pour chaque catégorie. Ne pas inventer de prix. Laisser en
brouillon toute offre non vérifiée.

## 13. Réactivation future du batch après V1

Le moteur générique `catalogsync` est conservé et désactivé. Pour le réactiver :
mettre `eparly.catalog-sync.enabled=true` et configurer des sources autorisées.