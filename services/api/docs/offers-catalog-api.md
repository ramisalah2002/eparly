# Catalogue d'offres commerciales - API

Base path: `/v1/offers`. Tous les endpoints exigent un JWT valide.

## Architecture

Le module `com.eparly.api.offer` constitue un catalogue structuré d'offres commerciales vérifiables.
Il sert de base à l'étape 10B (moteur de comparaison). Aucun prix n'est inventé ; toute offre publiée
doit être reliée à une source officielle vérifiée.

## Entités et schéma PostgreSQL (Migration V12)

### offer_providers
Fournisseur de service commercial.
- id UUID PK
- name VARCHAR(200)
- sector VARCHAR(30) CHECK (STREAMING, MOBILE_PLAN, INTERNET_BOX)
- official_website VARCHAR(500) CHECK (^https://.+)
- country VARCHAR(2) DEFAULT 'FR'
- status VARCHAR(20) DEFAULT 'ACTIVE' (ACTIVE, INACTIVE)
- created_at, updated_at

### commercial_offers
Offre commerciale associée à un fournisseur.
- id UUID PK
- provider_id UUID FK -> offer_providers
- commercial_name VARCHAR(200)
- sector VARCHAR(30)
- characteristics TEXT (JSON sérialisé)
- eligibility_conditions TEXT (JSON sérialisé)
- commitment VARCHAR(100)
- territory VARCHAR(100) DEFAULT 'FR'
- publication_status VARCHAR(20) DEFAULT 'DRAFT' (DRAFT, VERIFIED, STALE, EXPIRED, ARCHIVED)
- valid_from DATE
- valid_until DATE
- created_at, updated_at

### offer_pricing
Tarification d'une offre (montants en centimes).
- id UUID PK
- offer_id UUID FK UNIQUE -> commercial_offers
- amount_minor BIGINT (prix récurrent)
- currency VARCHAR(3) DEFAULT 'EUR'
- billing_period VARCHAR(20) DEFAULT 'MONTHLY' (WEEKLY, MONTHLY, QUARTERLY, YEARLY, UNKNOWN)
- activation_fee_minor BIGINT
- other_fees TEXT (JSON sérialisé)
- promotion_percent INTEGER (0-100)
- promotion_duration_months INTEGER
- price_after_promotion_minor BIGINT
- discount_conditions VARCHAR(500)
- taxes_included BOOLEAN DEFAULT TRUE
- data_unknown BOOLEAN DEFAULT FALSE
- created_at, updated_at

### offer_evidence
Preuve / source officielle vérifiée.
- id UUID PK
- offer_id UUID FK -> commercial_offers
- official_url VARCHAR(1000) CHECK (^https://.+)
- consulted_at TIMESTAMPTZ
- verified_at TIMESTAMPTZ
- nature VARCHAR(200)
- verified_summary VARCHAR(2000)
- expires_at TIMESTAMPTZ
- verification_status VARCHAR(20) DEFAULT 'PENDING' (PENDING, VERIFIED, EXPIRED, INVALID)
- provenance VARCHAR(200)
- created_at, updated_at

### offer_price_history
Historique des révisions tarifaires (traçabilité).
- id UUID PK
- offer_id UUID FK -> commercial_offers
- old_amount_minor BIGINT
- new_amount_minor BIGINT
- changed_at TIMESTAMPTZ
- reason VARCHAR(500)

## Secteurs supportés (MVP)

1. **STREAMING** : forfaits de vidéo/audio en streaming (publicités, écrans, qualité, engagement).
2. **MOBILE_PLAN** : forfaits mobile (data, 4G/5G, roaming, engagement, frais d'activation).
3. **INTERNET_BOX** : prévu pour extension, non implémenté à cette étape.

## Représentation des prix et promotions

- Les montants sont en centimes (Long). Ex: 15,99 EUR = 1599.
- `amount_minor` = prix normal récurrent.
- `price_after_promotion_minor` = prix après promotion, distinct du prix normal.
- `promotion_percent` + `promotion_duration_months` = description de la promotion.
- Une absence de frais documentés ne signifie pas automatiquement des frais nuls.
- Les prix annuels et mensuels restent distinguables via `billing_period`.
- `data_unknown = true` indique une donnée manquante (jamais substituée par zéro).

## Sources et règles de vérification

Chaque offre publiée (VERIFIED) doit disposer :
- D'au moins une `OfferEvidence` avec `verification_status = VERIFIED`.
- D'une URL officielle HTTPS validée.
- D'une tarification fiable (`amount_minor >= 0`, `billing_period` connu).
- D'une période de validité future ou indéterminée.

Une simple URL ne prouve pas automatiquement l'exactitude du prix.

## Cycle de vie des offres

- **DRAFT** : fiche en préparation (état par défaut à la création).
- **VERIFIED** : informations vérifiées et publiables. Requiert tarification + source vérifiée.
- **STALE** : vérification trop ancienne (seuil configurable, défaut 30 jours ; 7 jours pour les promotions).
- **EXPIRED** : offre dont `valid_until` est dépassée.
- **ARCHIVED** : offre retirée du catalogue actif (conservée pour traçabilité).

Règles :
- Une offre archivée n'est jamais supprimée physiquement.
- Une modification de prix remet l'offre en DRAFT (nécessite re-vérification).
- Les offres DRAFT, STALE, EXPIRED, ARCHIVED n'apparaissent pas dans la liste publique.

## Politique de fraîcheur

Configurable via `eparly.offer.catalog.freshness-threshold` (défaut : 30 jours) et
`eparly.offer.catalog.promotion-freshness-threshold` (défaut : 7 jours).
Ce seuil est une règle interne, pas une garantie d'exactitude du prix.

## Administration du catalogue

Aucun endpoint d'écriture public n'est créé.
- Service interne `OfferCatalogService` pour création, validation, publication, archivage.
- Import contrôlé de fiches JSON par un outil de maintenance non exposé.
- Mode dry-run et validation avant insertion (à prévoir).
- Toute modification de prix est tracée dans `offer_price_history`.

## API REST (lecture seule, JWT obligatoire)

### GET /v1/offers
Liste paginée des offres VERIFIED publiquement consultables.

Query params :
- `sector` (STREAMING|MOBILE_PLAN|INTERNET_BOX)
- `providerId` (UUID)
- `territory` (ex: FR)
- `status` (limité aux états publiquement consultables)
- `page` (défaut 0)
- `size` (défaut 20, max 100)

### GET /v1/offers/{id}
Détail complet : fournisseur, offre, tarification, promotions, conditions, caractéristiques, frais, source officielle, dernière vérification, fraîcheur.

### GET /v1/offers/metadata
Secteurs supportés, periodicités, statuts publics, territoires, devise par défaut, note de non-exhaustivité.

### GET /v1/offers/providers
Fournisseurs actifs, optionnellement filtrés par secteur.

## Contrats OpenAPI

Tous les endpoints sont documentés via SpringDoc (Swagger/OpenAPI) avec `@Operation`, `@ApiResponse`, `@SecurityRequirement`.
Disponible sur `/v3/api-docs` et `/swagger-ui.html` (profils local/test uniquement).

## Exemples JSON fictifs

### Offre streaming (VERIFIED)
```json
{
  "id": "uuid",
  "provider": {
    "id": "uuid",
    "name": "ExampleStream",
    "sector": "STREAMING",
    "officialWebsite": "https://example-stream.com",
    "country": "FR",
    "status": "ACTIVE"
  },
  "commercialName": "Forfait Start",
  "sector": "STREAMING",
  "characteristics": "{\"qualite\":\"HD\",\"ecrans\":2,\"publicites\":false}",
  "eligibilityConditions": "Nouveaux abonnés uniquement",
  "commitment": "Aucun engagement",
  "territory": "FR",
  "publicationStatus": "VERIFIED",
  "pricing": {
    "amountMinor": 999,
    "currency": "EUR",
    "billingPeriod": "MONTHLY",
    "activationFeeMinor": 0,
    "promotionPercent": 20,
    "promotionDurationMonths": 3,
    "priceAfterPromotionMinor": 799,
    "taxesIncluded": true
  },
  "evidence": [
    {
      "officialUrl": "https://example-stream.com/offers/start",
      "verifiedAt": "2026-10-28T10:00:00Z",
      "nature": "Prix officiel",
      "verificationStatus": "VERIFIED"
    }
  ]
}
```

### Offre mobile (VERIFIED)
```json
{
  "id": "uuid",
  "provider": {
    "name": "ExampleMobile",
    "sector": "MOBILE_PLAN",
    "officialWebsite": "https://example-mobile.com"
  },
  "commercialName": "Forfait 200 Go",
  "sector": "MOBILE_PLAN",
  "pricing": {
    "amountMinor": 1999,
    "currency": "EUR",
    "billingPeriod": "MONTHLY",
    "activationFeeMinor": 1000,
    "otherFees": "{\"carte_sim\":1000}"
  },
  "characteristics": "{\"dataGo\":200,\"reseau\":\"5G\",\"roamingUE\":\"inclus\"}"
}
```

## Erreurs HTTP

- 400 : Requête invalide (champ manquant, URL non HTTPS, prix invalide...)
- 401 : JWT absent ou invalide
- 404 : Offre inconnue ou non disponible publiquement (réponse neutre)
- 409 : Conflit (fournisseur ou offre déjà existant)
- 500 : Erreur interne (ex: problème de base de données)

## Limites de fraîcheur et non-exhaustivité

- Le catalogue n'est pas exhaustif : il référence uniquement les offres vérifiées manuellement.
- Eparly ne couvre pas l'intégralité du marché français.
- Une vérification ancienne rend l'offre STALE ; elle est retirée des résultats fiables.
- Une rémunération d'affiliation ne doit jamais être présentée comme un critère d'économie.

## Préparation pour l'étape 10B (comparateur)

Le catalogue est conçu pour le rapprochement futur :
- Abonnement déclaré -> fournisseur -> secteur -> offre alternative
- Aucune association automatique obligatoire entre Subscription et CommercialOffer à cette étape
- Aucun calcul d'économies ni classement personnalisé à cette étape

Un contrôle juridique est requis avant publication du comparateur.

## Procédure de mise à jour contrôlée

1. Créer le fournisseur (createProvider) ou réutiliser un fournisseur existant.
2. Créer l'offre en DRAFT (createDraftOffer).
3. Ajouter la tarification (OfferPricing).
4. Ajouter la preuve officielle vérifiée (OfferEvidence avec VERIFIED).
5. Appeler validateAndPublish pour passer en VERIFIED.
6. En cas de modification de prix, utiliser revisePrice (trace dans offer_price_history, remise en DRAFT).
7. Pour retirer une offre : archive (ARCHIVED) ou laisser expirer (EXPIRED).

## Contrôles de sécurité

- Tous les endpoints exigent un JWT valide (SecurityConfiguration : anyRequest().authenticated()).
- Aucun endpoint d'écriture public (POST/PUT/PATCH sur /v1/offers).
- Les URL doivent utiliser HTTPS et être validées (pas de SSRF).
- Aucun secret dans les URL.
- Pas de récupération HTTP arbitraire depuis le backend.
- DTO distincts des entités JPA.
- Réponses 404 neutres pour les offres inconnues.