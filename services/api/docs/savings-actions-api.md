# API du plan d'actions et suivi des économies (Étape 11A)

Base path : `/v1/savings/actions`. Tous les endpoints exigent une authentification Bearer JWT valide (`Authorization: Bearer <token>`).

---

## 1. Vue d'ensemble

Le module `com.eparly.api.savings.action` permet aux utilisateurs d'Eparly de transformer des opportunités d'économies estimées (étape 10C) en actions concrètes au sein d'un plan d'action personnel, et de suivre leur concrétisation à travers différents stades décisionnels.

### Principes clés de conception
1. **Recalcul systématique côté backend** : Le backend réexécute systématiquement l'estimation d'économies lors de la création d'une action. Il ne fait jamais confiance aux montants ou aux statuts transmis par le frontend.
2. **Snapshot JSONB figé** : Au moment de la création d'une action, un instantané (`estimate_snapshot`) de l'estimation financière est persisté en base de données PostgreSQL (colonne `JSONB`). Cet instantané reste immuable même si le prix catalogue de l'offre alternative augmente ou expire ultérieurement.
3. **Traçabilité et auditabilité** : Tout changement de statut donne lieu à l'insertion immédiate et transactionnelle d'un enregistrement d'audit dans la table `savings_action_history`.
4. **Intégrité financière stricte** :
   - Aucune modification des comptes bancaires, transactions ou agrégats financiers préexistants.
   - Différenciation rigoureuse entre économie potentielle, engagée, déclarée et observée.
   - À l'étape 11A, `observedSavingsMinor` reste toujours `null` (aucune économie observée fictive).

---

## 2. Cycle de vie et statuts d'une action

Une action d'économie traverse un cycle d'états strict :

```
                  ┌──────────────┐
                  │   PLANNED    │
                  └──────┬───────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
  ┌──────────────┐                ┌──────────────┐
  │  COMMITTED   │───────────────►│   CANCELED   │ (terminal)
  └──────┬───────┘                └──────────────┘
         │                               ▲
         │                               │
         ▼                               │
  ┌──────────────┐                       │
  │   DECLARED   │───────────────────────┘
  └──────────────┘
```

| Statut | Définition |
|---|---|
| `PLANNED` | **Planifiée** : L'utilisateur a retenu l'opportunité dans son plan. L'estimation nette est calculée et figée. Aucune démarche externe n'a été entamée. |
| `COMMITTED` | **Engagée** : L'utilisateur a initié les démarches concrètes (ex: ouverture de dossier, demande de résiliation, commande de carte SIM). Date d'engagement : `committedAt`. |
| `DECLARED` | **Déclarée** : L'utilisateur atteste avoir finalisé le changement de fournisseur. Date de déclaration : `declaredAt`. |
| `CANCELED` | **Annulée** : L'action a été abandonnée ou annulée par l'utilisateur à n'importe quel stade. Statut terminal irréversible. Date d'annulation : `canceledAt`. |

### Transitions autorisées
- `PLANNED` → `COMMITTED`
- `PLANNED` → `CANCELED`
- `COMMITTED` → `DECLARED`
- `COMMITTED` → `CANCELED`
- `DECLARED` → `CANCELED`

### Transitions interdites (HTTP 400 Bad Request)
- `PLANNED` → `DECLARED` (doit obligatoirement passer par `COMMITTED`).
- Tout retour en arrière (`COMMITTED` → `PLANNED`, `DECLARED` → `COMMITTED`, `DECLARED` → `PLANNED`).
- Toute modification depuis `CANCELED` (`CANCELED` est un état final définitif).

### Idempotence des mises à jour de statut
L'envoi d'un statut identique à l'état actuel (ex: `COMMITTED` → `COMMITTED`) est idempotent : il met à jour la note si fournie, renvoie HTTP 200 avec l'état courant, et ne génère aucun événement superflu dans l'historique ni n'écrase les horodatages initiaux.

---

## 3. Distinction entre Potentiel, Engagé, Déclaré et Observé

| Concept | Étape | Définition | Prise en compte dans le résumé |
|---|---|---|---|
| **Économie Potentielle** (`potentialSavingsMinor`) | 10C / 11A | Économie théorique calculée par rapport à l'offre alternative compatible. | Somme des économies nettes de toutes les actions non annulées (`PLANNED`, `COMMITTED`, `DECLARED`). |
| **Économie Engagée** (`committedPotentialSavingsMinor`) | 11A | Économie associée aux actions dont les démarches ont été officiellement démarrées. | Somme des économies nettes des actions au statut `COMMITTED`. |
| **Économie Déclarée** (`declaredPotentialSavingsMinor`) | 11A | Économie attestée par l'utilisateur suite à la finalisation du changement. | Somme des économies nettes des actions au statut `DECLARED`. |
| **Économie Observée** (`observedSavingsMinor`) | 11B | Économie matériellement vérifiée sur les relevés bancaires (nouvelles transactions effectives constatées). | Toujours `null` à l'étape 11A (aucune extrapolation fictive). |

---

## 4. Fonctionnement du Snapshot JSONB

Lorsqu'une action est créée, le backend stocke un objet `SavingsEstimateSnapshot` dans la colonne `estimate_snapshot` (`JSONB`) de la table `savings_actions` :

```json
{
  "calculationVersion": "1.0.0",
  "horizonMonths": 12,
  "currency": "EUR",
  "currentTotalCostMinor": 19188,
  "currentCostSource": "USER_DECLARED",
  "alternativeTotalCostMinor": 11988,
  "switchingCostMinor": 0,
  "potentialNetSavingsMinor": 7200,
  "estimationStatus": "SAVINGS_POSSIBLE",
  "offerVerifiedAt": "2026-10-29T01:30:06.228863Z",
  "estimatedAt": "2026-10-29T01:30:07.123459Z",
  "assumptions": [
    "Horizon de comparaison : 12 mois.",
    "Périodicité de facturation conservée entre l'offre actuelle et l'alternative.",
    "Aucune promotion appliquée (prix normal sur toute la période)."
  ],
  "missingInformation": [],
  "officialUrl": "https://stream-alt.com/tarifs"
}
```

### Propriétés essentielles :
- **Montants exacts en centimes** : Évite les imprécisions d'arrondi sur les devises.
- **Indépendance vis-à-vis du catalogue** : Si l'opérateur augmente son prix ultérieurement, la promesse d'économie faite à l'utilisateur au moment de sa décision reste intacte.
- **Détection d'obsolescence** : Le champ calculé `estimateMayBeStale` informe le client si la vérification de l'offre source date de plus de 30 jours (`offerVerifiedAt < now - 30j`).
- **Devise obligatoire** : Aucune valeur par défaut silencieuse ou mélange de devises.

---

## 5. Description détaillée des cinq endpoints

### 5.1 POST `/v1/savings/actions` — Créer une action d'économie

Ajoute une action au plan d'actions personnel de l'utilisateur.

#### Requête
- **Headers** : `Authorization: Bearer <token>`, `Content-Type: application/json`
- **Body JSON** :
```json
{
  "subscriptionId": "113adbe9-226f-406f-8565-2c5691910aa8",
  "offerId": "01423994-075f-4f39-9546-a9ed424c0312",
  "sector": "STREAMING",
  "horizonMonths": 12,
  "requirements": {
    "minScreens": 2
  },
  "userKnownCancellationFeeMinor": 0,
  "userKnownOtherFeesMinor": 0,
  "notes": "Passage vers StreamAlt Eco pour économiser 72€/an"
}
```

#### Réponse : `201 Created`
```json
{
  "id": "64187c0d-921c-4c28-bef9-a15ca6f85a33",
  "subscriptionId": "113adbe9-226f-406f-8565-2c5691910aa8",
  "subscriptionName": "Abonnement Streaming Standard",
  "currentProvider": "StreamCurrent",
  "alternativeOfferId": "01423994-075f-4f39-9546-a9ed424c0312",
  "alternativeOfferName": "StreamAlt Eco",
  "alternativeProvider": "StreamAltProvider",
  "sector": "STREAMING",
  "status": "PLANNED",
  "verificationStatus": "NOT_VERIFIED",
  "horizonMonths": 12,
  "createdAt": "2026-10-29T02:00:00Z",
  "updatedAt": "2026-10-29T02:00:00Z",
  "committedAt": null,
  "declaredAt": null,
  "canceledAt": null,
  "estimateSnapshot": {
    "calculationVersion": "1.0.0",
    "horizonMonths": 12,
    "currency": "EUR",
    "currentTotalCostMinor": 19188,
    "currentCostSource": "USER_DECLARED",
    "alternativeTotalCostMinor": 11988,
    "switchingCostMinor": 0,
    "potentialNetSavingsMinor": 7200,
    "estimationStatus": "SAVINGS_POSSIBLE",
    "offerVerifiedAt": "2026-10-29T01:30:00Z",
    "estimatedAt": "2026-10-29T02:00:00Z",
    "assumptions": [
      "Horizon de comparaison : 12 mois."
    ],
    "missingInformation": [],
    "officialUrl": "https://stream-alt.com/tarifs"
  },
  "estimateMayBeStale": false,
  "notes": "Passage vers StreamAlt Eco pour économiser 72€/an"
}
```

#### Erreurs possibles
- `400 Bad Request` :
  - `Aucun gain net positif estimé pour cette offre` (économie nulle ou négative).
  - `L'offre alternative n'est pas compatible avec les critères de l'utilisateur`.
  - `Données insuffisantes pour créer une action d'économie` (frais obligatoires manquants).
  - `L'offre alternative est expirée`.
- `401 Unauthorized` : JWT absent ou invalide.
- `404 Not Found` : Abonnement ou offre introuvable, ou abonnement appartenant à un tiers.
- `409 Conflict` : Une action active (`PLANNED`, `COMMITTED` ou `DECLARED`) existe déjà pour le couple `(subscriptionId, offerId)`.

---

### 5.2 GET `/v1/savings/actions` — Lister les actions

Retourne la liste paginée des actions de l'utilisateur connecté avec filtres optionnels.

#### Paramètres de requête
- `status` (optionnel) : Filtrer par statut (`PLANNED`, `COMMITTED`, `DECLARED`, `CANCELED`).
- `subscriptionId` (optionnel) : Filtrer par abonnement spécifique.
- `page` (défaut : 0) : Numéro de page (0-indexed).
- `size` (défaut : 20, max : 100) : Nombre d'éléments par page.

#### Réponse : `200 OK`
```json
{
  "content": [
    {
      "id": "64187c0d-921c-4c28-bef9-a15ca6f85a33",
      "subscriptionId": "113adbe9-226f-406f-8565-2c5691910aa8",
      "subscriptionName": "Abonnement Streaming Standard",
      "currentProvider": "StreamCurrent",
      "alternativeOfferId": "01423994-075f-4f39-9546-a9ed424c0312",
      "alternativeOfferName": "StreamAlt Eco",
      "alternativeProvider": "StreamAltProvider",
      "sector": "STREAMING",
      "status": "PLANNED",
      "verificationStatus": "NOT_VERIFIED",
      "horizonMonths": 12,
      "createdAt": "2026-10-29T02:00:00Z",
      "updatedAt": "2026-10-29T02:00:00Z",
      "committedAt": null,
      "declaredAt": null,
      "canceledAt": null,
      "estimateSnapshot": {
        "calculationVersion": "1.0.0",
        "horizonMonths": 12,
        "currency": "EUR",
        "currentTotalCostMinor": 19188,
        "currentCostSource": "USER_DECLARED",
        "alternativeTotalCostMinor": 11988,
        "switchingCostMinor": 0,
        "potentialNetSavingsMinor": 7200,
        "estimationStatus": "SAVINGS_POSSIBLE",
        "offerVerifiedAt": "2026-10-29T01:30:00Z",
        "estimatedAt": "2026-10-29T02:00:00Z",
        "assumptions": ["Horizon de comparaison : 12 mois."],
        "missingInformation": [],
        "officialUrl": "https://stream-alt.com/tarifs"
      },
      "estimateMayBeStale": false,
      "notes": "Passage vers StreamAlt Eco"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

### 5.3 GET `/v1/savings/actions/{id}` — Consulter une action

Consulte le détail complet d'une action d'économie.

#### Réponse : `200 OK`
Structure identique à l'élément unitaire détaillé ci-dessus.

#### Erreurs possibles
- `401 Unauthorized` : JWT absent ou invalide.
- `404 Not Found` : Action inexistante ou appartenant à un autre utilisateur (isolation neutre).

---

### 5.4 PATCH `/v1/savings/actions/{id}/status` — Modifier le statut d'une action

Met à jour le statut d'une action conformément à la machine à états finis.

#### Requête
- **Headers** : `Authorization: Bearer <token>`, `Content-Type: application/json`
- **Body JSON** :
```json
{
  "status": "COMMITTED",
  "notes": "Souscription validée en ligne, en attente de la facture"
}
```

#### Réponse : `200 OK`
Retourne l'objet action mis à jour avec le nouveau statut et l'horodatage correspondant renseigné (`committedAt`, `declaredAt` ou `canceledAt`).

#### Erreurs possibles
- `400 Bad Request` :
  - Transition de statut interdite (ex: `PLANNED` vers `DECLARED`, ou modification d'une action `CANCELED`).
- `401 Unauthorized` : JWT absent ou invalide.
- `404 Not Found` : Action inexistante ou appartenant à un autre utilisateur.

---

### 5.5 GET `/v1/savings/actions/summary` — Résumé du plan d'économies

Retourne une synthèse financière globale des actions de l'utilisateur.

#### Réponse : `200 OK`
```json
{
  "actionsCount": 4,
  "plannedCount": 1,
  "committedCount": 1,
  "declaredCount": 1,
  "canceledCount": 1,
  "potentialSavingsMinor": 21600,
  "committedPotentialSavingsMinor": 7200,
  "declaredPotentialSavingsMinor": 7200,
  "observedSavingsMinor": null,
  "verificationStatus": "NOT_VERIFIED",
  "currency": "EUR",
  "horizonMonths": 12,
  "note": "Les économies potentielles sont des estimations, pas des gains acquis. Aucune économie n'est considérée comme réalisée sans vérification bancaire."
}
```

#### Règles de calcul du résumé :
1. **Comptage unitaire strict** : Chaque action est comptabilisée exactement une fois selon son statut.
2. **Exclusion des actions annulées** : Les actions avec statut `CANCELED` sont exclues de `potentialSavingsMinor`.
3. **Isolation des devises et horizons** : Seules les actions dont la devise et l'horizon correspondent à la référence sont agrégées.
4. **Pas d'économie observée inventée** : `observedSavingsMinor` reste `null` tant qu'une observation bancaire n'est pas confirmée (étape 11B).

---

## 6. Hypothèses, limites et sécurité

1. **Isolation multilocataire** : Toutes les requêtes filtrent par `userId` extrait du JWT cryptographiquement vérifié. L'accès à une ressource appartenant à un tiers retourne systématiquement un code HTTP `404 Not Found` neutre sans fuite d'information.
2. **Contrainte d'unicité en base** : La contrainte PostgreSQL `idx_savings_actions_unique_active` garantit au niveau de la base qu'aucune concurrence ne peut créer deux actions actives sur le même couple `(subscription_id, alternative_offer_id)`.
3. **Périmètre temporel** : Les calculs se basent sur l'horloge système injectée (`java.time.Clock`), assurant un comportement déterministe en environnement de test comme en production.
