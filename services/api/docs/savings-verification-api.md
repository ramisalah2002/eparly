# API de Vérification des Économies — Étape 11B.1

> **Portée** : Rapprochement et vérification financière des économies déclarées à partir des transactions bancaires importées (CSV).
> **Base URI** : `/v1/savings/actions/{id}/verification`
> **Authentification** : JWT Bearer obligatoire sur tous les endpoints (`Authorization: Bearer <token>`)

---

## Vue d'ensemble

L'API de vérification permet à l'utilisateur de **confirmer empiriquement** que le changement d'abonnement qu'il a déclaré a bien eu lieu, en associant des transactions bancaires réelles (importées via CSV) aux paiements attendus chez l'ancien et le nouveau fournisseur.

### Principes fondamentaux

| Principe | Description |
|---|---|
| **Intégrité financière** | Une transaction bancaire ne peut être associée **qu'une seule fois** à une preuve, toutes actions confondues. La contrainte UNIQUE sur `transaction_id` est garantie par PostgreSQL. |
| **Isolation multi-tenant** | Toutes les opérations sont strictement isolées par `user_id`. Un trigger PostgreSQL (`trg_savings_action_evidences_user_check`) vérifie la cohérence au niveau base de données. |
| **Compte explicite** | Si l'abonnement est associé à un compte, seuls les paiements de ce compte peuvent être proposés ou rattachés comme preuve. |
| **Faits bancaires immuables** | Une transaction utilisée comme preuve ne peut plus voir ses données financières sources modifiées ; retirer la preuve ne supprime pas la transaction. |
| **Pas de date inventée** | La date effective du changement (`effectiveChangeDate`) n'est jamais déduite automatiquement. Elle doit être fournie explicitement par l'utilisateur. |
| **VERIFIED_OBSERVED réservé** | Le statut `VERIFIED_OBSERVED` n'est jamais accordé dans cette version MVP. La simple absence de débit de l'ancien fournisseur ne constitue pas une preuve de résiliation. |
| **Idempotence** | Le endpoint `/evaluate` sérialise les évaluations simultanées d'une action et ne crée pas de ligne d'audit si le résultat n'a pas changé. |

---

## Migration V14 (PostgreSQL 17)

La migration Flyway `V14__savings_verification.sql` crée :

### Tables

#### `savings_action_evidences`
Stocke chaque transaction bancaire associée manuellement à une action d'économie.

| Colonne | Type | Description |
|---|---|---|
| `id` | UUID PK | Identifiant unique de la preuve |
| `user_id` | UUID FK → users | Propriétaire (isolation multi-tenant) |
| `action_id` | UUID FK → savings_actions | Action concernée |
| `transaction_id` | UUID FK UNIQUE → transactions | **Unicité stricte** : une transaction ne peut justifier qu'une seule économie |
| `role` | VARCHAR(30) | `OLD_SUBSCRIPTION_PAYMENT` ou `NEW_SUBSCRIPTION_PAYMENT` |
| `provenance` | VARCHAR(30) | `USER_CONFIRMED` (confirmé manuellement) ou `SYSTEM_SUGGESTED` |
| `confirmed_at` | TIMESTAMPTZ | Moment de l'association |
| `created_at` | TIMESTAMPTZ | Horodatage technique d'insertion |

> [!IMPORTANT]
> La contrainte `UNIQUE (transaction_id)` empêche toute double facturation, même en cas de requêtes concurrentes. Le trigger `trg_savings_action_evidences_user_check` valide que `user_id` est cohérent avec le propriétaire de l'action ET le propriétaire de la transaction via son compte.

#### `savings_action_verifications`
Historique des décisions : une nouvelle ligne est créée uniquement lorsque le résultat de l'évaluation change.

| Colonne | Type | Description |
|---|---|---|
| `id` | UUID PK | Identifiant de l'audit |
| `action_id` | UUID FK | Action évaluée |
| `user_id` | UUID FK | Propriétaire |
| `status` | VARCHAR(20) | Statut calculé : `NOT_VERIFIED`, `PARTIALLY_VERIFIED`, `VERIFIED_OBSERVED` |
| `evaluated_at` | TIMESTAMPTZ | Moment de l'évaluation |
| `old_monthly_amount_minor` | BIGINT | Moyenne mensuelle observée de l'ancien abonnement (centimes) |
| `new_monthly_amount_minor` | BIGINT | Moyenne mensuelle observée du nouvel abonnement (centimes) |
| `observed_difference_minor` | BIGINT | Écart mensuel brut observé (centimes) |
| `covered_from` | DATE | Début de la période couverte par les preuves |
| `covered_to` | DATE | Fin de la période couverte |
| `missing_information` | JSONB | Liste des informations manquantes |
| `contradictions` | JSONB | Liste des contradictions détectées |
| `decision_reasons` | JSONB | Raisons explicites de la décision de statut |

### Colonne ajoutée à `savings_actions`

| Colonne | Type | Description |
|---|---|---|
| `effective_change_date` | DATE | Date effective du changement déclarée explicitement par l'utilisateur |

### Migration V15 (intégrité additive)

`V15__savings_verification_integrity.sql` renforce les garanties sans modifier V14 :

- Une contrainte composite garantit que chaque ligne d'audit appartient au même utilisateur que son action.
- Le trigger des preuves rejette les transactions hors du compte explicitement associé à l'abonnement.
- Les données financières et d'identification d'une transaction déjà utilisée comme preuve ne peuvent plus être modifiées.
- Les champs de catégorisation non financière (`category_id`, `custom_category_flag`) restent modifiables.

---

## Statuts de vérification (`VerificationStatus`)

| Statut | Signification | Conditions requises |
|---|---|---|
| `NOT_VERIFIED` | Aucune preuve bancaire suffisante | Action non `DECLARED` OU aucune preuve `NEW_SUBSCRIPTION_PAYMENT` |
| `PARTIALLY_VERIFIED` | Paiements vers le nouveau fournisseur observés | Action `DECLARED` + ≥ 1 transaction `NEW_SUBSCRIPTION_PAYMENT` confirmée |
| `VERIFIED_OBSERVED` | **Réservé — jamais accordé dans MVP** | — Voir note ci-dessous — |

> [!CAUTION]
> `VERIFIED_OBSERVED` n'est **jamais accordé automatiquement** dans cette version. Les imports CSV ne prouvent pas la résiliation juridique de l'ancien abonnement. L'absence de transaction de l'ancien fournisseur ne constitue pas une preuve de fin de facturation. Deux nouveaux paiements + l'absence d'ancien prélèvement ≠ `VERIFIED_OBSERVED`.

---

## Rôles de preuve (`EvidenceRole`)

| Valeur | Signification |
|---|---|
| `OLD_SUBSCRIPTION_PAYMENT` | Transaction de l'ancien abonnement (paiement historique à l'ancien fournisseur) |
| `NEW_SUBSCRIPTION_PAYMENT` | Transaction du nouvel abonnement (paiement au nouveau fournisseur après changement) |

---

## Endpoints

### 1. `GET /v1/savings/actions/{id}/verification/candidates`

Retourne les transactions bancaires de l'utilisateur pouvant correspondre à l'ancien ou au nouveau fournisseur, avec un score de confiance heuristique.

**Paramètres de requête** :
| Paramètre | Type | Défaut | Description |
|---|---|---|---|
| `role` | `OLD_SUBSCRIPTION_PAYMENT` \| `NEW_SUBSCRIPTION_PAYMENT` | — | Filtrer par rôle suggéré |
| `page` | int | `0` | Page demandée (base 0) |
| `size` | int | `20` | Taille de page |

La taille est limitée à 100 éléments ; les index de page invalides sont normalisés pour éviter les débordements.

**Réponse 200** : [`VerificationCandidatesResponse`](#verificationcandidatesresponse)

**Codes HTTP** :
- `200` Succès
- `401` JWT absent ou invalide
- `404` Action d'économie introuvable ou appartenant à un autre utilisateur

---

### 2. `POST /v1/savings/actions/{id}/verification/evidence`

Associe une transaction bancaire réelle comme preuve de paiement.

**Corps JSON** :
```json
{
  "transactionId": "uuid-de-la-transaction",
  "role": "NEW_SUBSCRIPTION_PAYMENT",
  "effectiveChangeDate": "2026-09-01"
}
```

| Champ | Requis | Description |
|---|---|---|
| `transactionId` | ✅ | UUID d'une transaction appartenant à l'utilisateur |
| `role` | ✅ | `OLD_SUBSCRIPTION_PAYMENT` ou `NEW_SUBSCRIPTION_PAYMENT` |
| `effectiveChangeDate` | ❌ | Date effective du changement (non inventée si absente) |

**Réponse 201** : [`EvidenceResponse`](#evidenceresponse)

**Codes HTTP** :
- `201` Association créée
- `400` Transaction non éligible (virement, remboursement, devise incompatible ou compte différent du compte lié à l'abonnement)
- `401` JWT absent ou invalide
- `404` Action ou transaction introuvable (isolation multi-tenant)
- `409` Transaction déjà associée à une preuve financière

> [!WARNING]
> Seules les transactions de type `EXPENSE` ou `FEE` sont acceptées. Les virements (`TRANSFER`), revenus (`INCOME`) et remboursements (`REFUND`) sont rejetés avec HTTP 400.

---

### 3. `DELETE /v1/savings/actions/{id}/verification/evidence/{evidenceId}`

Supprime le lien entre une transaction et l'action d'économie. La transaction bancaire sous-jacente n'est pas supprimée.

**Réponse 204** : No Content

**Codes HTTP** :
- `204` Suppression effectuée
- `401` JWT absent ou invalide
- `404` Action ou preuve introuvable

---

### 4. `GET /v1/savings/actions/{id}/verification`

Retourne le détail complet de la vérification financière : statut, paiements observés, montants, période couverte, informations manquantes et contradictions.

**Réponse 200** : [`VerificationDetailsResponse`](#verificationdetailsresponse)

**Codes HTTP** :
- `200` Succès
- `401` JWT absent ou invalide
- `404` Action introuvable

---

### 5. `POST /v1/savings/actions/{id}/verification/evaluate`

Recalcule de manière idempotente le niveau de vérification à partir des preuves actuelles. Peut être appelé avec un corps optionnel pour fixer ou modifier la date effective.

**Corps JSON (optionnel)** :
```json
{
  "effectiveChangeDate": "2026-09-01"
}
```

**Réponse 200** : [`VerificationDetailsResponse`](#verificationdetailsresponse)

**Codes HTTP** :
- `200` Évaluation effectuée (idempotente)
- `401` JWT absent ou invalide
- `404` Action introuvable

---

## Schémas de réponse

### `VerificationCandidatesResponse`
```json
{
  "items": [
    {
      "transactionId": "uuid",
      "bookingDate": "2026-09-01",
      "amountMinor": -999,
      "currency": "EUR",
      "rawLabel": "PRIMEVIDEO ABONNEMENT MENSUEL",
      "normalizedLabel": "primevideo abonnement mensuel",
      "accountId": "uuid",
      "suggestedRole": "NEW_SUBSCRIPTION_PAYMENT",
      "matchingReasons": ["Libellé compatible avec le nouveau fournisseur", "Montant proche de la nouvelle offre"],
      "confidenceScore": 75
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

### `EvidenceResponse`
```json
{
  "id": "uuid",
  "actionId": "uuid",
  "transactionId": "uuid",
  "role": "NEW_SUBSCRIPTION_PAYMENT",
  "provenance": "USER_CONFIRMED",
  "confirmedAt": "2026-10-29T02:00:00Z",
  "amountMinor": -999,
  "currency": "EUR",
  "bookingDate": "2026-09-28",
  "label": "primevideo abonnement mensuel"
}
```

### `VerificationDetailsResponse`
```json
{
  "actionId": "uuid",
  "verificationStatus": "PARTIALLY_VERIFIED",
  "effectiveChangeDate": "2026-09-01",
  "declaredAt": "2026-10-01T10:00:00Z",
  "observedOldPayments": [],
  "observedNewPayments": [
    {
      "evidenceId": "uuid",
      "transactionId": "uuid",
      "bookingDate": "2026-09-28",
      "amountMinor": -999,
      "currency": "EUR",
      "label": "primevideo abonnement mensuel",
      "role": "NEW_SUBSCRIPTION_PAYMENT",
      "provenance": "USER_CONFIRMED"
    }
  ],
  "observedOldMonthlyAmountMinor": null,
  "observedNewMonthlyAmountMinor": 999,
  "observedMonthlyDifferenceMinor": null,
  "coveredFrom": "2026-09-28",
  "coveredTo": "2026-09-28",
  "missingInformation": [
    "Aucun paiement historique de l'ancien abonnement n'a été rattaché pour comparaison empirique directe.",
    "Une seule échéance du nouvel abonnement a été observée : confirmation de régularité dans la durée impossible."
  ],
  "contradictions": [],
  "decisionReasons": [
    "Paiements vers le nouveau fournisseur observés (1 transaction(s) rattachée(s)).",
    "Le statut VERIFIED_OBSERVED reste réservé et non attribué dans cette version MVP..."
  ],
  "initialEstimateSnapshot": {
    "potentialNetSavingsMinor": 7200,
    "currency": "EUR",
    "horizonMonths": 12
  },
  "differenceVersusEstimate": "Économie mensuelle nette estimée : 6,00 €/mois | Écart brut entre montants des paiements associés : 0,00 €/mois. Ce montant brut n'est pas une économie nette constatée et n'est pas annualisé."
}
```

### Exemple d'intégration React Native (TypeScript)

Le jeton d'accès doit être obtenu auprès du flux d'authentification existant et conservé dans le stockage sécurisé de l'application. Ne jamais embarquer de secret dans l'application.

```typescript
type EvidenceRole = "OLD_SUBSCRIPTION_PAYMENT" | "NEW_SUBSCRIPTION_PAYMENT";

type VerificationCandidatesResponse = {
  items: Array<{
    transactionId: string;
    bookingDate: string;
    amountMinor: number;
    currency: string;
    rawLabel: string;
    normalizedLabel: string;
    accountId: string;
    suggestedRole: EvidenceRole;
    matchingReasons: string[];
    confidenceScore: number;
  }>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

async function getVerificationCandidates(
  apiBaseUrl: string,
  accessToken: string,
  actionId: string,
  role: EvidenceRole,
  page = 0,
): Promise<VerificationCandidatesResponse> {
  const query = new URLSearchParams({ role, page: String(page), size: "20" });
  const response = await fetch(
    `${apiBaseUrl}/v1/savings/actions/${actionId}/verification/candidates?${query}`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  if (!response.ok) throw new Error(`Candidates request failed: ${response.status}`);
  return response.json() as Promise<VerificationCandidatesResponse>;
}

async function addVerificationEvidence(
  apiBaseUrl: string,
  accessToken: string,
  actionId: string,
  transactionId: string,
  role: EvidenceRole,
  effectiveChangeDate?: string,
): Promise<void> {
  const response = await fetch(
    `${apiBaseUrl}/v1/savings/actions/${actionId}/verification/evidence`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ transactionId, role, effectiveChangeDate }),
    },
  );
  if (!response.ok) throw new Error(`Evidence request failed: ${response.status}`);
}
```

La réponse des candidats est paginée sous `items` (et non `content`). `PARTIALLY_VERIFIED` indique des paiements associés observés, pas une économie nette acquise. `VERIFIED_OBSERVED` reste réservé.

---

## Algorithme d'évaluation (`SavingsVerificationEvaluator`)

### Étape 1 : Calcul des montants observés
- Moyenne arithmétique des montants absolus des preuves `OLD_SUBSCRIPTION_PAYMENT`
- Moyenne arithmétique des montants absolus des preuves `NEW_SUBSCRIPTION_PAYMENT`
- Les paiements ne sont comparés en équivalent mensuel que si les périodicités déclarée et tarifaire sont connues (`WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`) ; sinon, les montants comparables restent indisponibles.
- Différence mensuelle brute = ancien équivalent mensuel − nouvel équivalent mensuel

### Étape 2 : Détection des informations manquantes
- Action non au statut `DECLARED`
- `effectiveChangeDate` non fournie explicitement
- Aucune preuve `NEW_SUBSCRIPTION_PAYMENT`
- Seulement 1 preuve nouvelle (régularité non confirmée)
- Aucune preuve `OLD_SUBSCRIPTION_PAYMENT` (comparaison impossible)
- Périodicité non connue (normalisation mensuelle impossible)
- Les transactions disponibles ne s'étendent pas sur 30 jours après la date effective ; la complétude CSV ne peut pas être confirmée. Sans date explicite, cette couverture n'est pas évaluée.

### Étape 3 : Détection des contradictions
- Débit de l'ancien fournisseur détecté **après** la `effectiveChangeDate` (matching heuristique par libellé)
- Paiements ancien et nouveau observés à ≤ 15 jours d'intervalle

### Étape 4 : Décision de statut
```
IF action.status != DECLARED OR newEvidences.isEmpty():
    status = NOT_VERIFIED
ELSE:
    status = PARTIALLY_VERIFIED
    # VERIFIED_OBSERVED est réservé et jamais accordé automatiquement
```

### Étape 5 : Comparaison avec l'estimation initiale
- Si un `estimateSnapshot` existe, la réponse rappelle séparément l'estimation nette et l'écart brut entre montants associés.
- Cet écart brut n'est pas comparé numériquement à l'estimation nette, n'est pas annualisé et ne déduit pas de frais de changement, doubles prélèvements ou périodes de couverture.

---

## Score de confiance des candidats (`SavingsTransactionMatchingService`)

| Critère | Points |
|---|---|
| Libellé contenant le nom du fournisseur/service | +40 |
| Montant strictement identique à l'attendu | +35 |
| Montant proche (±15%) de l'attendu | +20 |
| Date cohérente avec la période (avant/après bascule) | +15 |
| Match avec la série récurrente connue | +10 |

**Seuil minimum** : 30 points pour apparaître dans les candidats.

---

## Garanties de sécurité

| Menace | Protection |
|---|---|
| Accès croisé entre utilisateurs | `user_id` vérifié dans chaque requête JPQL + trigger PostgreSQL |
| Audit affecté au mauvais propriétaire | Clé étrangère composite `(action_id, user_id)` |
| Preuve provenant d'un compte non lié | Vérification applicative + trigger PostgreSQL quand l'abonnement a un compte explicite |
| Modification des données source d'une preuve | Trigger PostgreSQL bloquant les changements de compte, import, fingerprint, date, montant, devise, libellé et type |
| Double facturation d'une transaction | Contrainte UNIQUE PostgreSQL + vérification applicative avec HTTP 409 |
| Double facturation concurrente | Contrainte UNIQUE PostgreSQL + gestion `DataIntegrityViolationException` |
| Virement ou revenu comme preuve | `transaction.isExpense()` vérifié avant toute association |
| Date effective inventée | Aucune déduction automatique de la date — `null` si non fournie explicitement |
| VERIFIED_OBSERVED non mérité | Statut `VERIFIED_OBSERVED` non accordé dans l'évaluateur MVP |

---

## Tests d'intégration (étape 11B.1)

La classe [`SavingsVerificationIntegrationTest`](../src/test/java/com/eparly/api/savings/verification/SavingsVerificationIntegrationTest.java) couvre :

| Groupe | Tests |
|---|---|
| **1. Scénario complet HTTP** | DECLARED → preuve → persistance PostgreSQL → évaluation → relecture → suppression |
| **2. Sécurité JWT** | 5 endpoints renvoient 401 sans token |
| **3. Isolation multi-tenant** | UserB ne peut pas accéder aux ressources de UserA ; trigger PostgreSQL vérifié |
| **4. Double facturation** | Transaction déjà utilisée → 409 ; concurrence → un seul succès |
| **5. Règles métier** | Action PLANNED reste NOT_VERIFIED ; VERIFIED_OBSERVED jamais accordé ; date non inventée ; virement rejeté ; contradictions détectées |
| **6. CSV incomplets** | Couverture insuffisante signalée dans `missingInformation` |
| **7. Idempotence** | Appels répétés et simultanés à `/evaluate` ne créent pas de doublons d'audit |
| **8. Migration V14** | Tables, colonnes, contraintes, trigger et entrée Flyway vérifiés dans PostgreSQL 17 |
| **9. Candidats** | Filtrage par rôle, pagination bornée et overflow |
| **10. Action introuvable** | Tous les endpoints renvoient 404 pour un `actionId` inexistant |
| **11. Compte explicite** | Filtrage des candidats et preuves, y compris au niveau PostgreSQL |
| **12. Intégrité V15** | Audit même propriétaire que l'action et faits sources des transactions immuables |

**Nombre de tests ciblés** : 26 méthodes après l'audit.
