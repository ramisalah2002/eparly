# Analyse des dépenses et recommandations

Ce module calcule des **statistiques déterministes**, sans modèle d'IA ni API payante. Chaque réponse l'indique par
`dataQuality.analysisMethod = DETERMINISTIC_STATISTICS`. Les montants sont en centimes d'euros et toujours positifs.

## Règles communes

| Transaction | Traitement |
|---|---|
| `EXPENSE` ou `FEE`, montant négatif | Dépense |
| `TRANSFER`, ou catégorie système « Virements » | Exclue. L'import type tout débit en `EXPENSE` : sans catégorie « Virements », un virement interne compte comme une dépense |
| `REFUND` positif | Remboursement, affiché à part, jamais déduit d'une dépense ni compté comme revenu |
| `INCOME` positif | Revenu confirmé |
| `UNKNOWN` | Exclu, exposé dans `dataQuality` |
| Hors EUR | Exclue sans conversion, comptée par devise dans `excludedCurrencies` |

- **Mois** : `COMPLETE`, `PARTIAL` (mois courant, ou données s'arrêtant à plus de 5 jours d'une borne) ou `NO_DATA`.
- **Base de comparaison** : uniquement les mois `COMPLETE` antérieurs au mois analysé, sur 12 mois. Un mois `NO_DATA` n'est pas un mois à zéro.
- **Comparaison** : elle exige au moins 3 mois complets. Sinon `sufficiency = LIMITED_HISTORY` et aucune comparaison n'est produite.
- **Transactions en double** : l'import écarte déjà un fichier réimporté (`fingerprint`). Les doublons restants ne sont que signalés.
- `basedOnIncompleteData` vaut toujours `true` : un import CSV peut être partiel.

## Endpoints

Tous exigent le JWT Eparly et ne lisent que les données de l'utilisateur. Paramètre commun : `month=YYYY-MM`, mois courant par
défaut. Un mois futur renvoie `400`. Un `accountId` d'un autre utilisateur renvoie `404`.

| Route | Paramètres | Réponse |
|---|---|---|
| `GET /v1/insights/overview` | `month`, `accountId` | Résumé du mois |
| `GET /v1/insights/spending-trends` | `month`, `accountId` | Mois, tendances par catégorie, commerçants fréquents, concentration par jour |
| `GET /v1/insights/anomalies` | `month`, `accountId`, `page`, `size` | Signalements paginés |
| `GET /v1/insights/recommendations` | `month`, `page`, `size` | Recommandations classées, économies, alternatives |
| `PATCH /v1/insights/recommendations/{id}` | `month`, corps `{ "status": "NEW|VIEWED|IGNORED|DONE" }` | `204`, ou `404` si elle est inconnue pour cet utilisateur |
| `GET /v1/budgets` | | Budgets de l'utilisateur |
| `PUT /v1/budgets/{categoryId}` | corps `{ "monthlyLimitMinor": 20000 }` | Budget créé ou modifié |
| `DELETE /v1/budgets/{categoryId}` | | `204` |

Pour `PATCH`, `month` doit désigner le mois où la recommandation a été calculée, s'il ne s'agit pas du mois courant.

## Anomalies

Ce sont des signalements à vérifier. Aucun n'affirme une fraude.

| Type | Règle (valeurs par défaut) | Confiance |
|---|---|---|
| `UNUSUAL_AMOUNT` | Au moins 5 achats hors série dans la catégorie ; montant ≥ 50 € et supérieur au plus grand de 3 × médiane et médiane + 3 × 1,4826 × écart absolu médian | `HIGH` dès 10 observations, sinon `MEDIUM` |
| `CATEGORY_SPIKE` | 3 mois complets ; dépassement de la moyenne ≥ 10 € et ≥ 20 % | `HIGH` dès 6 mois, sinon `MEDIUM` |
| `POSSIBLE_DUPLICATE` | Même libellé et même montant ≥ 5 €, à 1 jour au plus, hors série | Toujours `MEDIUM` |
| `MERCHANT_AMOUNT_INCREASE` | 5 observations à montant stable (écart ≤ 10 %) ; hausse ≥ 1 € et ≥ 5 % | `MEDIUM` |
| `RECURRING_AMOUNT_VARIATION` | 3 paiements précédents d'une série non écartée ; écart ≥ 1 € et ≥ 5 % | `HIGH` dès 5 paiements, sinon `MEDIUM` |

`severity` : `HIGH` dès 100 €, `MEDIUM` dès 30 €, sinon `LOW`.

## Recommandations

| Type | Déclencheur | Montant |
|---|---|---|
| `REDUCE_SPENDING` | Catégorie non essentielle au-dessus de sa moyenne | Objectif : dépassement plafonné à 20 % du poste, × 12 |
| `SAVINGS_POTENTIAL` | Autre catégorie non essentielle dont 10 % de la moyenne atteignent 5 € | Objectif × 12 |
| `BUDGET_OVERRUN` | Budget dépassé 2 fois sur 3 mois, ou sur le dernier mois observé | Aucun |
| `BUDGET_LIMIT_HIGH` | Moins de 50 % utilisés pendant 3 mois | Aucun |
| `BUDGET_MARGIN_SHRINKING` | Dépenses en hausse sur 3 mois et ≥ 80 % de la limite | Aucun |
| `RECURRING_INCREASE` | Hausse d'une série du moteur existant | Hausse × 12 |
| `RECURRING_SHARE` | Séries actives ≥ 15 % des dépenses moyennes | Aucun |
| `RECURRING_OVERLAP` | Au moins 2 séries de la catégorie Abonnements, sans conclure à une inutilité | Aucun |

Catégories non essentielles par défaut : Restaurants, Shopping, Loisirs (`eparly.insights.reducible-categories`).

**Classement** : score = confiance (40, 25 ou 10) + impact (1 point par 2,50 €, plafonné à 40) + type (0 à 20). `HIGH` dès 70,
`MEDIUM` dès 40. Les égalités sont départagées par l'identifiant.

**Suivi** : l'identifiant dépend de l'utilisateur, du type et du sujet, pas du mois. `IGNORED` et `DONE` masquent la recommandation
30 jours (`eparly.insights.hide-duration`). Si la situation persiste ensuite, elle revient avec le statut `NEW`.

**Économies** : `savings` sépare `potentialEstimatedAnnualMinor` (projection), `plannedMinor` (plan d'économies existant) et
`observedMinor` (constaté, `null` à ce jour). Ces montants ne s'additionnent pas.

**Alternatives** : `commercialAlternatives` ne compte que les offres `VERIFIED`, à droits `ESTABLISHED` et disponibles. Sinon :
`NONE_AVAILABLE`. Aucun prix ni fournisseur n'est cité : la comparaison reste dans l'API de comparaison.

## Exemple

```json
{
  "recommendations": {
    "content": [{
      "id": "7e1f7b1c-3f0a-3a8e-9a55-0f8a1c2d3e4f",
      "type": "REDUCE_SPENDING",
      "title": "Dépenses Restaurants au-dessus de vos habitudes",
      "description": "Vos dépenses Restaurants dépassent votre moyenne habituelle de 50 € ce mois-ci.",
      "priority": "MEDIUM",
      "confidence": "MEDIUM",
      "analysisPeriod": "2026-06",
      "justification": "150 € ce mois-ci, contre 100 € en moyenne sur 5 mois complets.",
      "potentialAnnualSavingsMinor": 36000,
      "suggestedAction": "Objectif proposé : réduire ce poste de 30 € par mois.",
      "computedAt": "2026-11-03T08:00:00Z",
      "evidenceIds": ["..."],
      "status": "NEW"
    }],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
  },
  "savings": { "potentialEstimatedAnnualMinor": 36000, "plannedMinor": 0, "observedMinor": null },
  "commercialAlternatives": {
    "status": "NONE_AVAILABLE", "publishedOfferCount": 0,
    "message": "Aucune alternative fiable n'est actuellement proposée."
  },
  "dataQuality": { "sufficiency": "SUFFICIENT", "monthCoverage": "COMPLETE", "basedOnIncompleteData": true }
}
```

## Architecture

Le calcul se fait à la demande sur 13 mois au plus, sans cache ni scheduler : un import ou une correction de catégorie compte dès
la requête suivante. Seuls `category_budgets` et `insight_recommendation_states` (V29) sont stockés. Tous les seuils sont dans
`eparly.insights.*`. Une couche générative pourrait reformuler `description` et `justification` plus tard, sans changer les calculs.

## Limites

- Les crédits restent `UNKNOWN` à l'import : le revenu confirmé, et donc le solde (`periodBalanceMinor`), sont souvent absents.
- Un virement interne sans catégorie « Virements » reste compté comme dépense.
- Les remboursements ne sont pas rattachés à l'achat d'origine.
- Hors EUR : aucune conversion.
- L'équivalent mensuel et le rattachement des séries viennent du moteur existant, dont les limites s'appliquent.
- Tout est recalculé à chaque requête : à mesurer sur de gros historiques, avant d'envisager un cache.

## Intégration mobile

1. Afficher `dataQuality` à côté des chiffres, et les états `NO_DATA` et `LIMITED_HISTORY`.
2. Afficher les montants comme des estimations, et ne jamais présenter `potentialAnnualSavingsMinor` comme acquis.
3. Envoyer `VIEWED` à l'ouverture, `IGNORED` ou `DONE` sur action de l'utilisateur.
4. Présenter les doublons et anomalies comme des points à vérifier.
