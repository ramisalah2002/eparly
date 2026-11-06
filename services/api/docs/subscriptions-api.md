# Subscriptions API

Base path: `/v1/subscriptions`. All endpoints require a valid JWT.

## GET /v1/subscriptions
Returns subscriptions (confirmed or manually declared) for the connected user.

Query: `accountId` (optional UUID), `status` (ACTIVE|PAUSED|CANCELED|UNKNOWN).

## GET /v1/subscriptions/{id}
Returns one subscription with declared and observed information.

## POST /v1/subscriptions
Creates a subscription manually or transforms a detected recurring series.

Body:
```json
{
  "accountId": "uuid",
  "recurringSeriesId": "uuid",
  "name": "Netflix",
  "provider": "Netflix",
  "declaredAmountMinor": 1599,
  "currency": "EUR",
  "declaredFrequency": "MONTHLY",
  "nextDueDate": "2025-05-05",
  "declaredStatus": "ACTIVE",
  "notes": "optional"
}
```

## PATCH /v1/subscriptions/{id}
Updates declared information only. Observed banking data is never overwritten.

## PATCH /v1/subscriptions/{id}/status
Declares a lifecycle change.

Body:
```json
{ "status": "CANCELED", "note": "optional" }
```

Allowed states: ACTIVE, PAUSED, CANCELED, UNKNOWN.

## GET /v1/subscriptions/summary
Returns cost estimates grouped by currency.

## Observed vs declared
- Observed fields come from the linked recurring series (banking detection).
- Declared fields come from the user.
- Declared values are never overwritten by CSV re-import or recurring analysis.
- A declared CANCELED status is not proof of real cancellation.
- If a payment is observed after a declared cancellation, `contradictoryObservation` is true and the declared status is preserved.

## Financial calculations
- Amounts are in minor units (cents).
- Monthly equivalent = declared or observed amount * frequency factor.
- Annual equivalent = monthly equivalent * 12.
- Currencies are never mixed; summary is grouped by currency.
- Estimated costs are not actual payments.

## Errors
- 400 invalid request
- 401 missing or invalid JWT
- 404 unknown or foreign subscription/account/series
- 409 recurring series already linked to a subscription

---

# Suivi des abonnements et des résiliations

Montants en centimes, jamais additionnés entre devises. Eparly **n'envoie aucune demande au fournisseur** : le module suit ce que
l'utilisateur déclare et ce que ses transactions montrent. Il ne connaît pas l'usage réel d'un service.

## Parcours mobile

1. `POST /v1/recurring/analyze`, puis `GET /v1/subscriptions/detected` : propositions à valider.
2. Confirmer : `POST /v1/subscriptions` avec `recurringSeriesId`. Sans confirmation, rien n'apparaît dans les abonnements.
3. Si `possibleExistingSubscriptionId` est renseigné (par exemple un autre compte) : `POST /v1/subscriptions/{id}/linked-series`.
4. Corriger : `PATCH /v1/subscriptions/{id}`, avec en plus `commitmentEndDate`, `noLongerNeeded` et
   `unknownFields` (`AMOUNT`, `FREQUENCY`, `NEXT_DUE_DATE`, `COMMITMENT_END_DATE`, `PROVIDER`).
5. Résilier : suivre le parcours ci-dessous.

`origin` vaut `AUTO_DETECTED` (DETECTED) ou `USER_DECLARED` (MANUAL). Une nouvelle détection ne modifie que la série, jamais les
valeurs déclarées de l'abonnement.

## Endpoints ajoutés sous `/v1/subscriptions`

| Route | Rôle |
|---|---|
| `GET /detected` | Détections à confirmer, avec confiance |
| `GET /overview` | Totaux mensuel et annuel des actifs, catégories, évolution, par devise ; `unknownCostCount` exclus |
| `GET /analysis` | `priceIncreases`, `possibleRedundancies`, `toReview` |
| `GET /renewals?days=60` | Échéances `RENEWAL` et `COMMITMENT_END` ; `dateStatus` : `CONFIRMED` ou `ESTIMATED` |
| `GET /post-cancellation-charges` | Prélèvements suspects |
| `GET /{id}/transactions` | Transactions ; `matchedBy` : `SERIES`, ou `NAME` à vérifier |
| `GET /{id}/history` | Corrections, archivage, résiliation, événements |
| `GET /{id}/savings` | Économies et hypothèses |
| `POST /{id}/archive` | Archive ; `409` si une résiliation est en cours |
| `POST /{id}/linked-series` | Rattache une autre série ; `409` si déjà rattachée |
| `POST`, `GET`, `PATCH /{id}/cancellation` | Démarche ; `POST /{id}/cancellation/confirmation` pour la confirmation |

`GET /v1/subscriptions` masque les abonnements archivés. `GET /summary` garde son contrat historique : il inclut aussi les résiliés.
Préférez `/overview` pour les coûts actuels.

## Résiliation

`PLANNED` → `REQUESTED` ou `ABANDONED` ; `REQUESTED` → `CONFIRMED`, `FAILED` ou `ABANDONED` ; `CONFIRMED` → `ENDED` ou `FAILED`.

```json
// PATCH /{id}/cancellation
{ "expectedVersion": 0, "status": "REQUESTED", "requestedOn": "2026-11-03", "channel": "EMAIL", "requestReference": "ABC-12", "providerEndDate": "2026-12-01" }
// POST /{id}/cancellation/confirmation
{ "expectedVersion": 1, "source": "USER_DECLARED", "confirmedOn": "2026-11-05", "reference": "ABC-12", "providerEndDate": "2026-12-01" }
```

- `source` vaut `USER_DECLARED` ou `USER_HOLDS_PROVIDER_DOCUMENT`. `providerVerified` reste `false` : à afficher comme une déclaration.
- `requestedOn` est obligatoire pour `REQUESTED`, et ces dates ne peuvent pas être futures.
- Le contrat (`declaredStatus`) reste `ACTIVE` jusqu'à `ENDED`, puis passe à `CANCELED`.
- Une seule démarche ouverte par abonnement. `expectedVersion` périmée ou transition interdite : `409`.

## Vérification (`verification.state`)

| État | Sens |
|---|---|
| `NOT_APPLICABLE` | Démarche seulement planifiée, ou close sans résiliation |
| `AWAITING_END_DATE` | Date de fin inconnue |
| `WAITING_END_DATE`, `WAITING_NEXT_CYCLE` | Trop tôt pour conclure |
| `INSUFFICIENT_DATA` | Les imports ne couvrent pas le cycle attendu : aucune conclusion |
| `NO_CHARGE_OBSERVED` | Aucun prélèvement sur des cycles importés : un indice, pas une preuve |
| `CHARGE_OBSERVED` | Prélèvement observé après la date de fin + 5 jours |

Référence : date annoncée par le fournisseur, sinon date de confirmation. Chaque prélèvement suspect crée un événement unique
`POST_CANCELLATION_CHARGE` dans `subscription_events`, avec `notified_at` nul, à la fin d'un import et à la lecture de la route.
Rien n'est envoyé : ces lignes attendent le futur moteur de notifications.

## Économies (`GET /{id}/savings`)

| Champ | Condition |
|---|---|
| `theoreticalAnnualMinor` | Coût connu |
| `plannedAnnualMinor` | Démarche `PLANNED` ou `REQUESTED` |
| `afterConfirmationAnnualMinor` | `CONFIRMED` ou `ENDED` ; projection |
| `observedMinor` | `CONFIRMED` ou `ENDED`, état `NO_CHARGE_OBSERVED`, et au moins 2 cycles couverts (1 si trimestriel ou annuel) |

Le coût est figé à la décision (`baseline`). `assumptions` liste les hypothèses. `commercialAlternatives` vaut `NONE_AVAILABLE` tant
qu'aucune offre n'est publiée.

## Recommandations

`/v1/insights/recommendations` ajoute `POST_CANCELLATION_CHARGE`, `CANCELLATION_UNFINISHED`, `SUBSCRIPTION_RENEWAL` et
`SUBSCRIPTION_SAVINGS`, avec le même classement et le même suivi. Hausses et chevauchements restent portés par `RECURRING_INCREASE`
et `RECURRING_OVERLAP`, sans doublon. Seuils : `eparly.subscriptions.*`.

## Limites

- Deux produits d'un même marchand au même libellé (par exemple un paiement `APPLE`) restent groupés.
- Un abonnement manuel sans série est rapproché par son nom : `matchedBy = NAME`, à vérifier.
- Les familles de services proches sont une courte liste fixe (vidéo, musique, cloud).
- Un virement interne sans catégorie « Virements » ni type `TRANSFER` peut encore être détecté comme récurrent, si son libellé ne le trahit pas.
- Les frais de résiliation ou de fin d'engagement ne sont pas connus.
