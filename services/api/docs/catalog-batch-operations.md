# Exploitation du batch de collecte

Référence opérationnelle unique. Conception : [`catalog-collection-engine.md`](catalog-collection-engine.md).
Sources : [`catalog-sources-inventory.md`](catalog-sources-inventory.md).

## Fonctionnement

Le scheduler vit dans le backend Spring Boot : **il ne s'exécute que si le backend tourne et que PostgreSQL est
démarré**. PC éteint ou backend arrêté à 03:00 : pas de collecte à cette heure. Avec
`CATALOG_SYNC_CATCH_UP_ON_STARTUP=true`, une collecte de rattrapage part 2 minutes après le démarrage si aucune
collecte réussie n'a eu lieu depuis 24 h. Une collecte `FAILED` à 03:00 est relancée une fois après 30 minutes.

## Configuration (`infra/.env`, non versionné)

| Variable | Rôle | Local |
|---|---|---|
| `CATALOG_SYNC_ENABLED` | Scheduler ; `false` = arrêt d'urgence | `true` |
| `CATALOG_SYNC_BUSINESS_INGESTION` | Écriture dans le catalogue ; `false` = observations seules | `true` |
| `CATALOG_SYNC_CATCH_UP_ON_STARTUP` | Rattrapage au démarrage | `true` |
| `CATALOG_SYNC_CRON` | Heure, par exemple `0 0 3 * * *` | 03:00 |
| `CATALOG_SYNC_ZONE` | Fuseau ; les changements d'heure sont gérés par Spring | `Europe/Paris` |

Ces valeurs sont `false` par défaut dans `application.properties` : un autre environnement n'est jamais activé
implicitement. Toute modification demande un redémarrage du backend.

## Prouver l'activation

Seule une ligne écrite par le processus vivant fait foi, pas `infra/.env` :

```sql
SELECT pid, cron, zone, next_run_at, last_heartbeat_at FROM catalog_sync_scheduler_instances
WHERE stopped_at IS NULL AND last_heartbeat_at > now() - interval '10 minutes';
```

Exactement une ligne attendue. Le heartbeat est renouvelé toutes les 5 minutes ; `catalog_sync_runs.triggered_by`
indique l'instance à l'origine de chaque collecte.

## Démarrer

```powershell
cd services/api
.\ops\start-local-batch.ps1
```

Démarrage automatique à la session Windows, sans droits administrateur : `.\ops\install-local-batch-task.ps1`.
Arrêt propre : `.\ops\stop-local-batch.ps1`. Après un changement de code : `.\ops\start-local-batch.ps1 -Rebuild`.
Journaux : `%LOCALAPPDATA%\Eparly\logs`. Pour développer avec `spring-boot:run`, arrêter d'abord le batch.

## Relancer manuellement

`POST /v1/admin/catalog-sync/runs` avec un jeton `CATALOG_ADMIN`. Un lancement concurrent est enregistré `SKIPPED`
grâce au verrou PostgreSQL.

## Contrôler

Se connecter avec `docker exec -it eparly-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`.

```sql
-- Dernières exécutions
SELECT started_at, status, offers_collected, offers_discovered, offers_unchanged, offers_duplicates, offers_rejected,
       errors_count, providers_created, catalog_offers_created, catalog_prices_changed, catalog_evidence_added
FROM catalog_sync_runs ORDER BY started_at DESC LIMIT 10;

-- Source en erreur
SELECT source_name, last_success_at, consecutive_failures, circuit_open_until, last_error
FROM catalog_sync_sources ORDER BY source_name;

-- Détail par source de la dernière exécution
SELECT s.source_name, r.status, r.offers_collected, r.error_code, r.error_message
FROM catalog_sync_run_sources r JOIN catalog_sync_sources s ON s.id = r.source_id
WHERE r.run_id = (SELECT id FROM catalog_sync_runs ORDER BY started_at DESC LIMIT 1);

-- Offres ajoutées par le batch
SELECT o.sector, o.commercial_name, p.amount_minor, p.billing_period, p.data_unknown, o.created_at
FROM catalog_offer_identities i JOIN commercial_offers o ON o.id = i.commercial_offer_id
JOIN offer_pricing p ON p.offer_id = o.id WHERE i.created_by_batch ORDER BY o.created_at DESC;

-- Offres en attente et motif
SELECT business_block_reason, change_type, count(*) FROM catalog_offer_candidates
WHERE candidate_status = 'NEEDS_REVIEW' GROUP BY 1, 2;
```

Le statut `SKIPPED_ROBOTS` signifie que le `robots.txt` de la source interdit l'accès au robot : ne pas le
contourner, demander l'autorisation à l'éditeur ou trouver une autre source.

## Ajouter une source

1. Vérifier licence, gratuité, `robots.txt` et présence de prix ; noter le résultat dans l'inventaire.
2. Sans schéma compatible, ajouter un connecteur dans `OpenTariffFeedConnectors`, avec test.
3. Ajouter la ligne dans `ops/catalog-sources-local.sql` et l'appliquer, ou utiliser `POST /sources` puis
   `PATCH /sources/{id}/permission` ailleurs qu'en local.

Pour désactiver une seule source : `UPDATE catalog_sync_sources SET collection_enabled = FALSE WHERE source_name = '...';`

## Règles d'écriture

Le batch crée fournisseur, offre `DRAFT`, tarif, preuve `PENDING` et historique. Il ne modifie que les brouillons
qu'il a créés et seulement depuis leur source d'origine. Une offre existante ou validée n'est jamais écrasée : le
changement reste un candidat `NEEDS_REVIEW` avec motif. Rien n'est publié ni vérifié automatiquement, rien n'est
supprimé si une source disparaît. La publication reste une action humaine via `/v1/admin/catalog/**`.

## Déployer sur un serveur toujours allumé

Exécuter le JAR comme service (systemd, conteneur avec redémarrage automatique) avec les mêmes variables, une
base PostgreSQL managée, et une règle de sortie réseau refusant les réseaux privés. Plusieurs instances restent
sûres : le verrou PostgreSQL n'autorise qu'une collecte. Avant production, faire confirmer l'usage commercial de
chaque source et prévoir l'affichage des mentions de source imposées par les licences.
