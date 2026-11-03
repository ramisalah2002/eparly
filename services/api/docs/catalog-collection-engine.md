# Moteur de collecte multi-catégories

Ce document complète [`catalog-nightly-sync.md`](catalog-nightly-sync.md) pour l'étape 14A. Le moteur
reste dans `com.eparly.api.catalogsync` : il découvre des offres candidates, sans jamais publier.

```text
Scheduler / lancement admin
  -> verrou PostgreSQL, clôture des exécutions interrompues
  -> sources AUTHORIZED + collection_enabled (parallélisme borné)
       circuit breaker -> configuration du connecteur -> robots.txt
       -> connecteur (HTTP borné, retry, ETag / Last-Modified)
       -> normalisation -> validation -> comparabilité -> identité
       -> observation immuable -> état par source -> historique des prix -> candidat NEEDS_REVIEW
  -> bilan global et bilan par source
Revue humaine -> DRAFT + preuve PENDING (CatalogAdminService) -> vérification -> publication explicite
```

## Correspondance avec les noms de l'étape 14A

| Rôle | Classe |
|---|---|
| Scheduler | `CatalogSyncScheduler` |
| Orchestrateur | `CatalogSyncJobService`, `CatalogSyncCoordinator` |
| Connecteurs | `CatalogSourceConnector`, `CatalogConnectorRegistry` |
| Normalisation et validation | `OfferNormalizationService`, `PriceNormalizationService` |
| Comparabilité par catégorie | `CatalogCategorySpecs` |
| Déduplication | `CatalogDeduplicationService` |
| Persistance candidate | `CatalogSyncRepository` |
| Changements de prix | `CatalogPriceChangeDetector` |
| Rapport | `catalog_sync_runs`, `catalog_sync_run_sources`, `GET /runs/{id}/sources` |

## Catégories

`OfferSector` gagne 12 valeurs ; les 3 valeurs historiques gardent leur nom :

| Catégorie | Secteur | Attributs requis pour comparer |
|---|---|---|
| Mobile | `MOBILE_PLAN` | `dataGb`, `network5G`, `commitmentMonths` |
| Internet fixe et fibre | `INTERNET_BOX` | `technology`, `downloadMbps`, `commitmentMonths` |
| Streaming vidéo | `STREAMING` | `screens`, `videoQuality`, `ads` |
| Streaming musical | `MUSIC_STREAMING` | `accounts`, `ads` |
| Cloud et stockage | `CLOUD_STORAGE` | `storageGb`, `users` |
| SaaS et outils IA | `SOFTWARE_SAAS` | `productFamily`, `users`, `storageGb` |
| Gaming | `GAMING` | `platform`, `productFamily` |
| Électricité | `ELECTRICITY` | `tariffOption`, `powerKva`, `zone`, `kwhPricingKnown` |
| Gaz | `GAS` | `tariffOption`, `consumptionClass`, `zone`, `kwhPricingKnown` |
| Assurances | `INSURANCE` | `insuranceType`, `guarantees`, `exclusions`, `deductibles` |
| Services bancaires | `BANKING` | `accountType`, `cardType` |
| Sport et fitness | `SPORT_FITNESS` | `activityType`, `zone`, `commitmentMonths` |
| Presse | `PRESS` | `publication`, `format` |
| Transport | `TRANSPORT` | `network`, `zone`, `passengerProfile` |
| Autres abonnements | `OTHER_RECURRING` | jamais comparable automatiquement |

Les caractéristiques restent dans le JSON existant : une nouvelle catégorie se déclare dans
`CatalogCategorySpecs`, sans changement de schéma. `comparison_group` regroupe les offres dont les
attributs discriminants sont égaux (même option et même puissance, par exemple). Ce statut est un
prérequis documenté : le batch ne calcule aucune économie. Les comparateurs mobile et streaming sont
inchangés ; un secteur sans stratégie répond 404, comme `INTERNET_BOX` avant cette étape.

## Prix

`price_kind` vaut `MISSING`, `FREE`, `FIXED`, `PROMOTIONAL`, `VARIABLE` ou `PROFILE_DEPENDENT`. La
périodicité reste celle de la source : un tarif annuel n'est jamais mensualisé. Un prix inconnu reste
`null`. Un montant textuel à plus de deux décimales ou signé est traité comme ambigu, jamais tronqué.

## Déduplication et historique

- **Même source** : source + clé externe + empreinte du contenu. Une relance identique ne crée rien.
- **Même flux** : une clé répétée compte comme doublon.
- **Entre sources** : secteur + fournisseur normalisé + plan normalisé + attributs discriminants.
  Tarif identique : doublon, sans nouvelle revue. Tarif différent : candidat `SOURCE_CONFLICT`.
- **Identité prudente** : si un attribut discriminant est inconnu d'un côté, les offres restent
  distinctes. Un doublon manqué se voit en revue ; une fusion abusive masquerait une offre.
- **Historique** : `catalog_observed_price_history`, en insertion seule, distingue `FIRST_OBSERVED`,
  `PRICE_CHANGED` et `PROMOTION_CHANGED`, y compris un retour à un ancien prix (A → B → A).
  `offer_price_history`, qui trace les tarifs validés, n'est jamais écrite par le batch.
- **Offre non retournée** : `missing_since` est renseigné seulement après une lecture complète et non
  vide. Rien n'est archivé, supprimé ou modifié. Une panne, un 304 ou une lecture partielle ne prouve rien.

## Résilience

Par source : délais de connexion et de réponse, retry avec backoff sur 429, 5xx et erreurs réseau,
`Retry-After` plafonné à 30 s, délai minimal par hôte, taille de réponse bornée, pagination bornée,
`ETag` / `Last-Modified`, circuit breaker persistant. Par exécution : parallélisme borné, durée maximale.
La protection SSRF existante s'applique à chaque requête et redirection. Les messages d'erreur ne
contiennent ni URL ni secret.

## Validation humaine

Le batch n'écrit que dans les tables de collecte. `POST /v1/admin/catalog-sync/candidates/{id}/draft`,
réservé à `CATALOG_ADMIN`, crée l'offre via `CatalogAdminService` : `DRAFT`, tarif et preuve `PENDING`.
Le tarif est marqué `data_unknown` si une donnée manque ou si le prix dépend de l'usage ou du profil, ce
qui bloque la publication jusqu'à sa révision. La preuve doit appartenir au domaine officiel du
fournisseur ; à défaut, tout est annulé. Les promotions observées restent du texte à vérifier.

## Activer le tarif réglementé CRE

Après la décision décrite dans [`catalog-sources-inventory.md`](catalog-sources-inventory.md), créer une
source par fichier (`Option_Base.csv`, `Option_HPHC.csv`, `Option_Tempo.csv`) :

```http
POST /v1/admin/catalog-sync/sources
{
  "sourceName": "CRE TRV électricité - Base",
  "officialDomain": "cre.fr",
  "sourceUrl": "https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_Base.csv",
  "sourceType": "OFFICIAL_API",
  "sector": "ELECTRICITY",
  "connectorKey": "cre-trv-electricity"
}
```

Puis consigner la décision avec `PATCH /sources/{id}/permission`, contrôler un lancement avec
`POST /runs`, et seulement ensuite définir `CATALOG_SYNC_ENABLED=true`.

## Ajouter une source

1. Vérifier le droit d'usage et classer la source dans l'inventaire.
2. Implémenter `CatalogSourceConnector` : aucune persistance, aucun secret dans `source_url`.
3. Tester sur une fixture dont la provenance est documentée.
4. Créer la source par l'API, `PENDING` et désactivée, puis l'autoriser explicitement.

## Vérification réelle

`CreLiveCollectionIT` lit les trois fichiers CRE sur une base PostgreSQL jetable :
`CATALOG_LIVE_CRE=true ./mvnw test -Dtest=CreLiveCollectionIT`. Il est ignoré par défaut.
