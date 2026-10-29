# Rapport financier mensuel — API Eparly

## Endpoint

`GET /v1/reports/monthly?month=2026-10&accountId=<uuid>`

Un JWT est obligatoire. `month` est obligatoire au format `YYYY-MM`. `accountId` est facultatif et doit appartenir à l'utilisateur authentifié; un compte inconnu ou étranger retourne un `404` neutre. Les bornes sont les dates civiles inclusives du mois, évaluées sur `bookingDate`.

Le rapport est calculé à la demande. Il synthétise uniquement les données enregistrées dans Eparly et ne prouve pas qu'un relevé complet a été importé.

## Réponse

Les montants des transactions et des paiements sont des entiers en centimes. Les équivalents mensuels/annuels d'abonnements et les pourcentages sont des `BigDecimal`. Les champs `observedSavingsMinor` et `changePercentage` peuvent être `null`.

```json
{
  "period": {
    "month": "2026-10",
    "startDate": "2026-10-01",
    "endDate": "2026-10-31"
  },
  "financialSummary": {
    "currency": "EUR",
    "totalExpensesMinor": 142350,
    "confirmedIncomeMinor": 250000,
    "uncategorizedExpensesMinor": 1200,
    "unclassifiedPositiveMinor": 2500,
    "unclassifiedNegativeMinor": 900,
    "transactionCount": 28
  },
  "categoryBreakdown": [
    {
      "categoryId": "550e8400-e001-0000-0000-000000000002",
      "categoryName": "Courses",
      "amountMinor": 90000,
      "transactionCount": 12,
      "percentage": 63.22
    },
    {
      "categoryId": null,
      "categoryName": "Non catégorisé",
      "amountMinor": 1200,
      "transactionCount": 2,
      "percentage": 0.84
    },
    {
      "categoryId": "550e8400-e001-0000-0000-000000000005",
      "categoryName": "Logement",
      "amountMinor": 51150,
      "transactionCount": 10,
      "percentage": 35.93
    }
  ],
  "previousMonthComparison": {
    "currentExpensesMinor": 142350,
    "previousExpensesMinor": 130000,
    "changeMinor": 12350,
    "changePercentage": 9.50,
    "coverageWarning": "La comparaison porte uniquement sur les transactions EUR enregistrées; la couverture des imports est inconnue."
  },
  "subscriptionsSummary": {
    "activeDeclaredCount": 2,
    "pausedDeclaredCount": 1,
    "canceledDeclaredCount": 1,
    "unknownStatusCount": 0,
    "declaredMonthlyEquivalentMinor": 2999.0000,
    "declaredAnnualEquivalentMinor": 35988.0000,
    "observedMonthlyEquivalentMinor": 1499.0000,
    "observedAnnualEquivalentMinor": 17988.0000,
    "costsUnavailableCount": 0,
    "nonEurSubscriptionCount": 0,
    "indicativeDueDates": [
      {
        "subscriptionId": "550e8400-e001-0000-0000-000000000003",
        "name": "Service fictif",
        "date": "2026-11-05",
        "source": "DECLARED"
      }
    ]
  },
  "savingsActionsSummary": {
    "plannedCount": 1,
    "committedCount": 1,
    "declaredCount": 0,
    "canceledCount": 0,
    "opportunities": [
      {
        "actionId": "550e8400-e001-0000-0000-000000000004",
        "subscriptionId": "550e8400-e001-0000-0000-000000000003",
        "subscriptionName": "Service fictif",
        "status": "PLANNED",
        "potentialNetSavingsMinor": 7200,
        "currency": "EUR",
        "horizonMonths": 12,
        "estimatedAt": "2026-10-15T12:00:00Z",
        "obsolescence": "CURRENT",
        "verificationStatus": "NOT_VERIFIED"
      }
    ]
  },
  "verificationSummary": {
    "notVerifiedCount": 1,
    "partiallyVerifiedCount": 1,
    "verifiedObservedCount": 0,
    "observedSavingsMinor": null,
    "observedSavingsExplanation": "Aucune économie nette réellement observée n'est certifiée par les preuves actuellement disponibles.",
    "actions": [
      {
        "actionId": "550e8400-e001-0000-0000-000000000004",
        "status": "PARTIALLY_VERIFIED",
        "currency": "EUR",
        "observedPayments": [
          {
            "bookingDate": "2026-10-04",
            "amountMinor": -999,
            "currency": "EUR",
            "role": "OLD_SUBSCRIPTION_PAYMENT",
            "provenance": "USER_CONFIRMED"
          }
        ],
        "observedOldMonthlyAmountMinor": 999,
        "observedNewMonthlyAmountMinor": null,
        "observedMonthlyDifferenceMinorGross": null,
        "coveredFrom": "2026-09-01",
        "coveredTo": "2026-10-31",
        "contradictions": [],
        "missingInformation": ["Paiement postérieur au changement non observé"]
      }
    ]
  },
  "dataQuality": {
    "basedOnCsvImports": true,
    "sourceTypes": ["CSV"],
    "firstObservedBookingDate": "2026-10-01",
    "lastObservedBookingDate": "2026-10-30",
    "transactionCountIncluded": 28,
    "accountCountIncluded": 2,
    "excludedCurrencyTransactionCount": 0,
    "incompatibleAccountCount": 0,
    "currenciesObserved": ["EUR"],
    "noTransactionsRecorded": false,
    "coverageStatus": "UNKNOWN",
    "warnings": [
      "La couverture bancaire est inconnue; les CSV ne prouvent pas l'import de l'historique complet."
    ]
  },
  "generatedAt": "2026-10-31T12:00:00Z"
}
```

Le JSON ci-dessus est fictif. Il illustre notamment une preuve partielle et ne signifie pas que les sommes affichées constituent une économie réellement acquise.

## Règles d'agrégation

- `EXPENSE` et `FEE` alimentent les dépenses identifiées et leur répartition par catégorie. Les montants sont présentés en valeur positive.
- Seul `INCOME` avec un montant positif alimente `confirmedIncomeMinor`. Un libellé ou une catégorie « Revenus » ne qualifie pas une transaction.
- `TRANSFER` est exclu des revenus et dépenses ordinaires. `REFUND` et `UNKNOWN` positif sont inclus dans `unclassifiedPositiveMinor`, jamais dans les revenus.
- Un montant `UNKNOWN` négatif est explicitement signalé en valeur positive dans `unclassifiedNegativeMinor`; il n'est pas silencieusement assimilé à une dépense connue. Une opération non qualifiée déclenche un avertissement de qualité des données.
- Les dépenses non catégorisées restent dans `totalExpensesMinor` et apparaissent dans le groupe `Non catégorisé`. Les catégories et pourcentages totalisent le périmètre des dépenses `EXPENSE` et `FEE`.
- Les agrégats communs n'incluent que EUR. Les opérations dans une autre devise sont signalées dans `dataQuality`, pas converties ni additionnées.
- Les abonnements sont des déclarations suivies, pas des paiements du mois. Les coûts déclarés et observations récurrentes sont séparés; les prochaines échéances restent indicatives.
- Les économies potentielles proviennent de snapshots, et sont présentées action par action. Les alternatives ne sont pas cumulées et des horizons différents ne sont pas additionnés.
- Les paiements de vérification et leurs écarts sont des observations brutes. Un écart brut n'est pas une économie nette. `observedSavingsMinor` reste `null` tant qu'aucune économie nette n'est certifiée.

## Mois vide et couverture

Un mois sans mouvement enregistré retourne des agrégats financiers nuls, une répartition vide, `noTransactionsRecorded: true` et une comparaison sans pourcentage si le mois précédent n'a pas de transactions. Cela signifie uniquement qu'aucune transaction n'est enregistrée pour la période; cela ne prouve pas l'absence de dépense.

Les imports CSV n'ont pas de métadonnées fiables de continuité des relevés. `coverageStatus` reste donc `UNKNOWN`; les dates minimale et maximale observées et la présence d'opérations en début/fin de mois ne sont pas une mesure de complétude.
