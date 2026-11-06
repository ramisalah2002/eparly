# Collecte nocturne du catalogue commercial

> Étape 14A : connecteurs, catégories, déduplication et historique sont décrits dans
> [`catalog-collection-engine.md`](catalog-collection-engine.md), les sources dans
> [`catalog-sources-inventory.md`](catalog-sources-inventory.md). Ce document reste la référence pour
> le registre et la sécurité réseau. Les statuts d'exécution sont désormais `SUCCESS` et `PARTIAL_SUCCESS`.

## Architecture

Le collecteur reste dans le monolithe Spring Boot, sous `com.eparly.api.catalogsync`.
Le flux est :

```text
Registre des sources autorisées
  -> robots.txt / contrôle de domaine / téléchargement borné
  -> flux JSON, sitemap, JSON-LD ou sélecteurs HTML configurés
  -> normalisation et contrôles structurels
  -> observations immuables par empreinte
  -> candidats NEEDS_REVIEW et historique d'exécution
  -> administration catalogue existante
  -> vérification humaine et publication habituelles
```

Les sources, observations, candidats et exécutions sont stockés dans les tables V20.
Le collecteur ne crée ni fournisseur ni `CommercialOffer`, ne modifie aucun tarif
publié et ne renseigne jamais `OfferEvidence.verifiedAt`. Une observation automatique
reste distincte d'une preuve commerciale validée par une personne.

Spring Batch n'est pas ajouté : les sources sont traitées indépendamment, les erreurs
sont isolées par source, les exécutions sont persistées et un verrou consultatif
PostgreSQL empêche deux collectes concurrentes, y compris entre plusieurs instances.
Le traitement ne possède pas encore de reprise au milieu d'une source : une exécution
suivante reprend au début, sans dupliquer les observations identiques.

## Activation et planification

Valeurs par défaut dans `application.properties` :

```properties
eparly.catalog-sync.enabled=false
eparly.catalog-sync.cron=0 0 3 * * *
eparly.catalog-sync.zone=Europe/Paris
eparly.catalog-sync.user-agent=EparlyCatalogBot/1.0
eparly.catalog-sync.contact=
eparly.catalog-sync.connect-timeout=PT3S
eparly.catalog-sync.request-timeout=PT8S
eparly.catalog-sync.request-delay=PT1S
eparly.catalog-sync.max-execution-duration=PT10M
eparly.catalog-sync.max-response-bytes=2097152
eparly.catalog-sync.max-pages-per-source=30
eparly.catalog-sync.max-redirects=3
eparly.catalog-sync.max-retries=2
```

Le scheduler est conditionnel et ne s'exécute pas au démarrage. Il est actif à
03:00, heure de Paris, uniquement si `CATALOG_SYNC_ENABLED=true` (ou la propriété
équivalente) est explicitement configuré dans un environnement actif. `cron` et
`zone` sont surchargés par `CATALOG_SYNC_CRON` et `CATALOG_SYNC_ZONE`.
Le bean interne `CatalogSyncJobService.runNow()` permet un lancement contrôlé pour
les tests et l'exploitation.

Les routes de contrôle sont réservées à `CATALOG_ADMIN` :

- `POST /v1/admin/catalog-sync/runs` lance une exécution synchrone.
- `GET /v1/admin/catalog-sync/runs?limit=20` retourne les derniers bilans.
- `GET /v1/admin/catalog-sync/sources` liste les sources configurées.
- `POST /v1/admin/catalog-sync/sources` crée une source désactivée et `PENDING`.
- `PATCH /v1/admin/catalog-sync/sources/{id}/permission` consigne l'autorisation
  ou le refus, la raison et l'administrateur responsable.
- `GET /v1/admin/catalog-sync/candidates?status=NEEDS_REVIEW&limit=50` liste les
  changements à examiner.
- `PATCH /v1/admin/catalog-sync/candidates/{id}/review` rejette le candidat ou
  l'accepte en le liant à une offre publiée dont le prix observé correspond.

L'acceptation d'un candidat ne publie rien : l'offre doit déjà être publiée et
disponible selon `OfferAvailabilityService`. L'administrateur suit d'abord le
workflow catalogue existant, puis lie le résultat à l'observation. Toutes les
routes sont soumises aux permissions JWT existantes et auditent les décisions.

## Registre et autorisation des sources

Une source comprend fournisseur facultatif, nom, domaine officiel, URL HTTPS, type,
secteur, pays, configuration d'extraction, état d'autorisation, état robots, cache
HTTP et horodatages de contrôle. Aucun site tiers n'est préconfiguré.

1. Vérifier les conditions contractuelles et techniques du site et obtenir, si
   nécessaire, l'autorisation de collecte.
2. Créer une source par l'API d'administration. Si un fournisseur est lié, le
   domaine doit appartenir à son site officiel et le secteur doit correspondre.
3. Laisser la source `PENDING` pendant la revue.
4. Après validation, appeler l'endpoint de permission avec `AUTHORIZED`,
   `collectionEnabled: true` et une justification explicite.
5. Le collecteur vérifie `robots.txt` avant les pages et recontrôle les règles lors
   de la découverte par sitemap. Une interdiction robots empêche le téléchargement.

Exemple de configuration de source HTML ciblée :

```json
{
  "sourceName": "Exemple interne",
  "officialDomain": "offers.example.fr",
  "sourceUrl": "https://offers.example.fr/plans",
  "sourceType": "HTML_PAGE",
  "sector": "MOBILE_PLAN",
  "country": "FR",
  "extractorConfig": "{\"offerSelector\":\".plan-card\",\"nameSelector\":\".name\",\"priceSelector\":\".price\",\"urlSelector\":\"a\",\"billingPeriod\":\"MONTHLY\"}"
}
```

Une configuration de flux JSON accepte un tableau ou un objet avec `offers`, dont
chaque entrée peut inclure `id`, `name`, `url`, `price`, `currency`,
`billingPeriod`, `description`, `promotionText`, `eligibility`, `conditions` et
`characteristics`. Les extracteurs JSON-LD lisent les blocs `Product`/`Service`
avec `offers`. Les sitemaps découverts sont limités au nombre de pages configuré ;
les URLs hors domaine officiel sont rejetées. Les pages HTML ne sont pas parcourues
de façon générale : seuls les sélecteurs configurés ou les blocs JSON-LD sont lus.

Les types `OFFICIAL_API`, `JSON_FEED`, `JSON_LD`, `HTML_PAGE` et `SITEMAP` sont
supportés. `OFFICIAL_API` emploie le lecteur de flux JSON. Il n'y a pas de crawler
généraliste, de navigateur headless ni d'accès aux zones authentifiées.

## Sécurité réseau et accès responsable

Le client n'accepte que le domaine officiel configuré (ou ses sous-domaines), ne
transmet aucun cookie ou credential, désactive les proxies système et limite les
redirections, délais, réponses et types MIME. Chaque destination et redirection
repasse par le contrôle de domaine et la résolution DNS ; les adresses privées,
loopback, link-local, multicast, ULA et non spécifiées sont refusées. En production,
seul HTTPS est admis. Le mode HTTP loopback n'est activable que dans le profil de
test et sert aux serveurs simulés locaux.

Le `User-Agent` identifie Eparly ; un contact peut être configuré. Les requêtes d'un
même hôte sont limitées en fréquence. Les réponses 429 et 5xx bénéficient de deux
tentatives avec backoff borné et prise en compte plafonnée de `Retry-After`.
Le nombre maximal de nouvelles tentatives (2 par défaut, au plus 5) et la durée
globale coopérative sont configurables.
L'arrêt sur durée maximale intervient entre les requêtes/pages ; une requête déjà
en cours reste bornée par les timeouts et retries configurés.
`ETag` et `Last-Modified` sont envoyés pour la source racine lorsque disponibles ;
un 304 ne génère pas d'observation.

La résolution utilisée par Apache HttpClient est épinglée par son `DnsResolver` :
chaque réponse DNS est validée avant que ces adresses précises soient utilisées
pour la connexion. Les adresses non globalement routables sont rejetées, y compris
les plages privées, partagées, link-local, de documentation et les plages IPv6
non globales. Seul le port HTTPS standard 443 est accepté hors profil de test.
Chaque redirection repasse par les contrôles de domaine, port et DNS. Le déploiement
doit également appliquer une politique de sortie réseau (pare-feu/egress)
interdisant les réseaux privés et les endpoints de métadonnées cloud. La politique robots est volontairement
conservatrice et ne tente pas de contourner CAPTCHA, protections anti-bot ou refus.
Une autorisation technique ne remplace pas l'examen juridique des conditions
d'utilisation et des licences.

L'évaluation détaillée des sites candidats et leurs statuts de permission figure
dans [`catalog-real-sources.md`](catalog-real-sources.md). Aucun site ne doit être
activé sur la seule base de son accessibilité HTTP ou d'une règle robots permissive.

## Normalisation et confiance

Le modèle intermédiaire conserve la clé stable, URL canonique, nom, secteur,
description, caractéristiques, conditions, texte tarifaire observé, montant en
unités mineures, devise, périodicité, promotion textuelle, éligibilité, niveau de
confiance, état structurel et raisons de revue.

Les montants avec séparateur décimal français sont convertis en centimes. Une
périodicité est conservée selon la source (hebdomadaire, mensuelle, trimestrielle
ou annuelle) et n'est jamais ramenée à une mensualité. Une devise ou une
périodicité absente reste `null`. Les prix « à partir de » et les promotions non
interprétées sont conservés comme observations mais restent `NEEDS_REVIEW`.
L'absence de prix, de nom ou d'URL canonique fiable entraîne un rejet ou une revue,
jamais un prix de remplacement.

`VALIDATED_STRUCTURALLY` signifie seulement que les champs essentiels de
l'observation sont cohérents. Ce statut ne vaut ni validation commerciale, ni
preuve humaine, ni publication.

## Idempotence, changements et historique

Une observation est identifiée par source, clé externe stable et hash SHA-256 des
données normalisées. Une observation identique actualise `last_observed_at` sans
créer une ligne d'observation ou de candidat supplémentaire.

- Première identité : candidat `NEW_OFFER`.
- Prix différent d'une observation précédente ou de l'offre catalogue rapprochée :
  candidat `PRICE_CHANGED`.
- Autres changements : candidat `DETAILS_CHANGED`.
- Offre non extraite : aucune suppression ou conclusion de retrait.
- Page HTML/JSON-LD téléchargée sans données commerciales extractibles :
  observation `UNSUPPORTED` sans candidat et compteur `pagesUnsupported`.
- Source indisponible : erreur rattachée à la source ; les observations et prix
  publiés précédents restent intacts.

Chaque version de contenu distincte reste persistée ; les candidats sont liés à
l'observation correspondante. Le rapprochement catalogue utilise fournisseur et nom
commercial, pas seulement le prix. Les modifications de tarifs publiés continuent
de passer par l'administration sécurisée, son historique et une nouvelle preuve
humaine. Aucun snapshot de savings n'est recalculé par le batch.

## Bilans et erreurs

`catalog_sync_runs` conserve les heures, le résultat, les compteurs configurés,
traités, pages, observations nouvelles, modifications observées, doublons,
rejets et erreurs. `catalog_sync_sources` maintient l'état par source, dernier
contrôle, succès, erreur, ETag et Last-Modified. Une erreur de source ne stoppe pas
les autres. Une exécution vide se termine avec succès et des compteurs nuls.
Un lancement concurrent est enregistré `SKIPPED` grâce au verrou consultatif.

Les compteurs décrivent les opérations réellement persistées : `offers_discovered`
correspond aux premières observations candidates, `offers_updated` aux nouvelles
observations d'une identité connue, et `offers_unchanged` aux contenus dont le hash
est déjà présent. Un candidat en attente est marqué `SUPERSEDED` lorsqu'une
observation plus récente du même identifiant externe le remplace.

## Tests

Les tests utilisent PostgreSQL Testcontainers et `HttpServer` local ; ils ne
contactent aucun site tiers. Le parcours couvert comprend une source JSON, un sitemap
vers une page JSON-LD et une page HTML ciblée, puis déduplication, détection de
changement, observation PostgreSQL et candidat. Les tests de politique tarifaire
vérifient également les formats français, les tarifs annuels et les prix « à partir
de ».

## Limites et extension

Aucune source réelle n'est autorisée ou livrée avec l'application. La couverture
mesurée ne concerne donc que les sources explicitement configurées. Le collecteur
ne garantit pas l'exhaustivité, la fraîcheur d'une page non disponible, ni
l'extraction de pages nécessitant JavaScript. Les règles d'extraction personnalisées
et leurs tests doivent être ajoutés au registre avant activation. Des adaptateurs
fournisseurs spécifiques et les index de sitemaps imbriqués pourront être ajoutés
sans modifier le stockage des offres publiées.

Pour un staging/production actif : configurer les paramètres d'environnement,
déployer la migration Flyway V20, vérifier les règles d'egress, créer les sources
avec un compte `CATALOG_ADMIN`, consigner l'autorisation obtenue, puis activer
`CATALOG_SYNC_ENABLED=true`. Le scheduler Spring s'exécute tant que l'instance API
est active ; le verrou PostgreSQL garantit qu'une seule instance collecte pour un
cycle. Il n'y a pas d'exécution automatique au démarrage.
