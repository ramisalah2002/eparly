# API de comparaison d'offres

Base path: `/v1/comparisons`. Tous les endpoints exigent un JWT valide.

## Architecture

Le module `com.eparly.api.comparison` constitue le moteur de comparaison d'offres commerciales (étape 10B).
Il détermine si une offre alternative répond aux besoins d'un utilisateur sans calculer d'économies (étape 10C).

Composants :
- `ComparisonService` : orchestration, récupération de l'abonnement, appel des stratégies
- `OfferEligibilityService` : filtrage des offres publiables (VERIFIED, non expirées, non STALE, territoire)
- `OfferEquivalenceService` : logique d'équivalence par critère
- `ComparisonStrategy` (interface) + `StreamingComparisonStrategy` + `MobilePlanComparisonStrategy` : stratégies par secteur
- `ComparisonCriteriaValidator` : validation des critères d'entrée
- `ComparisonExplanationBuilder` : construction des explications lisibles

## Modèle de données

Aucune nouvelle table ni migration Flyway (V13 non créée). La comparaison est calculée à la demande, sans persistance des résultats.

Les données s'appuient sur le catalogue existant (V12) :
- `commercial_offers`, `offer_pricing`, `offer_evidence`, `offer_providers`
- `subscriptions` (abonnement utilisateur à comparer)

## Secteurs et critères de comparaison

### STREAMING

Critères obligatoires :
- `minScreens` (number) : nombre minimal d'écrans simultanés
- `minVideoQuality` (string: SD, HD, 4K) : qualité vidéo minimale

Critères facultatifs :
- `adsAccepted` (boolean) : publicités acceptées
- `offlineDownloadRequired` (boolean) : téléchargement hors ligne requis
- `maxCommitmentMonths` (number) : engagement maximal acceptable
- `requiredContent` (string) : contenu recherché (NON vérifiable automatiquement → UNKNOWN)

### MOBILE_PLAN

Critères obligatoires :
- `minDataGb` (number) : données mobiles minimales en Go

Critères facultatifs :
- `requires5G` (boolean) : 5G obligatoire
- `roamingRequired` (boolean) : itinérance requise
- `minRoamingDataGb` (number) : données en itinérance minimales
- `maxCommitmentMonths` (number) : engagement maximal acceptable
- `eligibilityRestrictions` (string) : restrictions d'éligibilité

## Statuts d'équivalence

- `COMPATIBLE` : tous les critères obligatoires sont satisfaits
- `INCOMPATIBLE` : au moins un critère obligatoire est explicitement non satisfait
- `NEEDS_INFORMATION` : un critère obligatoire est inconnu (donnée manquante)
- `NOT_ELIGIBLE` : une condition d'éligibilité connue n'est pas respectée

Un score heuristique (0-100) est fourni pour le tri, ce n'est PAS une probabilité de satisfaction.

## Endpoints REST

### GET /v1/comparisons/criteria

Paramètre : `sector` (STREAMING, MOBILE_PLAN)

Retourne la liste des critères disponibles avec leurs définitions, types, valeurs autorisées, et indication obligatoire/facultatif.

### POST /v1/comparisons/evaluate

Corps de la requête :
```json
{
  "subscriptionId": "uuid",
  "sector": "MOBILE_PLAN",
  "requirements": {
    "minDataGb": 100,
    "requires5g": true,
    "maxCommitmentMonths": 0
  }
}
```

Réponse :
- Abonnement analysé (id, nom, fournisseur actuel)
- Secteur comparé
- Critères appliqués
- Offres compatibles / incompatibles / nécessitant des informations / non éligibles
- Nombre total d'offres éligibles dans le catalogue
- Fraîcheur des données
- Note de non-exhaustivité du catalogue
- Note de transparence commerciale

Aucun calcul d'économies n'est retourné (étape 10C).

### GET /v1/comparisons/metadata

Retourne : secteurs supportés, version du moteur, méthode de comparaison, conditions de référencement, critères de tri, politique de fraîcheur, note de couverture, transparence commerciale.

## Règles de filtrage

Seules les offres VERIFIED, non expirées, non obsolètes (règle de fraîcheur configurable, défaut 30 jours ; 7 jours pour les promotions), disponibles dans le territoire, et avec une tarification fiable sont évaluées.

Les offres DRAFT, STALE, EXPIRED et ARCHIVED sont exclues.

## Sécurité

- JWT obligatoire sur tous les endpoints
- L'abonnement doit appartenir à l'utilisateur du JWT (404 neutre sinon)
- Aucune modification de l'abonnement
- Aucun userId fourni par le client
- Aucune donnée bancaire personnelle dans les logs
- DTO distincts des entités JPA

## Transparence

- Le catalogue n'est pas exhaustif
- Une rémunération d'affiliation ne doit jamais influencer le score d'équivalence
- Aucun placement sponsorisé mélangé au classement organique
- Contrôle juridique requis avant publication

## Exemples

### Requête évaluation mobile
```http
POST /v1/comparisons/evaluate
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "subscriptionId": "550e8400-e29b-41d4-a716-446655440000",
  "sector": "MOBILE_PLAN",
  "requirements": {
    "minDataGb": 100,
    "requires5G": true,
    "maxCommitmentMonths": 12
  }
}
```

### Réponse évaluation mobile
```json
{
  "subscriptionId": "550e8400-...",
  "subscriptionName": "Mon Forfait",
  "currentProvider": "Opérateur Actuel",
  "sector": "MOBILE_PLAN",
  "appliedCriteria": [
    {"code": "minDataGb", "label": "Données mobiles minimales (Go)", "mandatory": true},
    {"code": "requires5G", "label": "5G obligatoire", "mandatory": false}
  ],
  "compatibleOffers": [
    {
      "offerId": "...",
      "commercialName": "Forfait 200 Go 5G",
      "providerName": "AltMobile",
      "status": "COMPATIBLE",
      "amountMinor": 1999,
      "currency": "EUR",
      "billingPeriod": "MONTHLY",
      "heuristicScore": 100,
      "criteria": [
        {"code": "minDataGb", "result": "SATISFIED", "explanation": "...", "mandatory": true},
        {"code": "requires5G", "result": "SATISFIED", "explanation": "...", "mandatory": false}
      ]
    }
  ],
  "incompatibleOffers": [],
  "needsInformationOffers": [],
  "notEligibleOffers": [],
  "totalEligibleOffers": 5,
  "catalogExhaustive": false,
  "commercialTransparencyNote": "Le catalogue n'est pas exhaustif... Aucune rémunération d'affiliation ne influence le classement.",
  "evaluatedAt": "2026-10-28T10:00:00Z"
}
```

## Limites connues

- Aucune persistance des résultats de comparaison
- Aucun calcul d'économies (réservé à l'étape 10C)
- Aucun rapprochement automatique entre abonnement et offre
- Le contenu des catalogues de streaming n'est pas vérifiable automatiquement (marqué UNKNOWN)
- La couverture réseau à une adresse précise n'est pas affirmée