# Dashboard API — Eparly

Endpoints de statistiques financières agrégées à partir des transactions importées.

## Avertissement important

Les données du dashboard proviennent d'imports CSV potentiellement incomplets.
`basedOnIncompleteData` est toujours `true`. Ces chiffres ne représentent **pas** un solde bancaire réel ni une épargne garantie.

## Règles financières

| `transactionType` | Traitement dans le dashboard |
|---|---|
| `EXPENSE` | Dépense identifiée (valeur absolue) |
| `FEE` | Intégré aux dépenses (valeur absolue) |
| `INCOME` (montant > 0) | Revenu confirmé |
| `TRANSFER` | Exclu des revenus et dépenses ordinaires |
| `REFUND` (montant > 0) | Montant positif non classé |
| `UNKNOWN` (montant > 0) | Montant positif non classé, **jamais** revenu |
| `UNKNOWN` (montant < 0) | Non compté ni comme dépense ni comme revenu |

Une transaction catégorisée « Revenus » n'est pas automatiquement un revenu confirmé : seul le `transactionType = INCOME` avec montant positif compte.

## Authentification

Tous les endpoints nécessitent un JWT valide dans l'en-tête `Authorization: Bearer <token>`.

---

## GET /v1/dashboard/summary

Résumé statistique sur une période donnée.

### Paramètres

| Paramètre | Type | Requis | Description |
|---|---|---|---|
| `startDate` | `LocalDate` (ISO-8601) | Oui | Date de début incluse |
| `endDate` | `LocalDate` (ISO-8601) | Oui | Date de fin incluse |
| `accountId` | `UUID` | Non | Filtre par compte (doit appartenir à l'utilisateur) |

### Réponse 200

```json
{
  "totalExpensesMinor": 45500,
  "confirmedIncomeMinor": 125000,
  "unclassifiedExpensesMinor": 2500,
  "unclassifiedPositiveMinor": 35020,
  "transactionsCount": 42,
  "uncategorizedTransactionsCount": 5,
  "firstTransactionDate": "2025-01-15",
  "lastTransactionDate": "2025-03-10",
  "currency": "EUR",
  "basedOnIncompleteData": true
}
```

### Signification des indicateurs

- **totalExpensesMinor** : Somme des dépenses identifiées (EXPENSE + FEE) en centimes d'euros, valeur positive.
- **confirmedIncomeMinor** : Revenus confirmés (INCOME avec montant > 0) en centimes d'euros.
- **unclassifiedExpensesMinor** : Dépenses (EXPENSE + FEE) sans catégorie assignée, en centimes.
- **unclassifiedPositiveMinor** : Montants positifs non qualifiés (UNKNOWN > 0, REFUND > 0). Ne pas interpréter comme des revenus.
- **transactionsCount** : Nombre total de transactions dans la période.
- **uncategorizedTransactionsCount** : Transactions sans catégorie.
- **firstTransactionDate** / **lastTransactionDate** : Bornes réelles des transactions dans la période. `null` si aucune transaction.
- **currency** : Devise des agrégats (uniquement EUR pour le MVP).
- **basedOnIncompleteData** : Toujours `true` — les imports CSV peuvent être incomplets.

### Erreurs

| Code | Cause |
|---|---|
| 400 | `startDate` après `endDate` |
| 401 | JWT absent ou invalide |
| 404 | `accountId` inconnu ou appartenant à un autre utilisateur |

---

## GET /v1/dashboard/spending-by-category

Répartition des dépenses par catégorie.

### Paramètres

| Paramètre | Type | Requis | Description |
|---|---|---|---|
| `startDate` | `LocalDate` (ISO-8601) | Oui | Date de début incluse |
| `endDate` | `LocalDate` (ISO-8601) | Oui | Date de fin incluse |
| `accountId` | `UUID` | Non | Filtre par compte |

### Réponse 200

```json
[
  {
    "categoryId": "550e8400-e001-0000-0000-000000000002",
    "categoryName": "Courses",
    "amountMinor": 12500,
    "transactionCount": 8,
    "percentage": 27.47
  },
  {
    "categoryId": null,
    "categoryName": "Non catégorisé",
    "amountMinor": 2500,
    "transactionCount": 3,
    "percentage": 5.49
  }
]
```

### Notes

- Seules les transactions EXPENSE et FEE sont incluses.
- Le groupe "Non catégorisé" (`categoryId: null`) regroupe les dépenses sans catégorie.
- Les pourcentages sont arrondis à 2 décimales et somment à ~100%.
- Tri décroissant par `amountMinor`.

---

## GET /v1/dashboard/monthly-trend

Évolution mensuelle des dépenses et revenus.

### Paramètres

| Paramètre | Type | Requis | Description |
|---|---|---|---|
| `months` | `int` (1-24) | Non | Nombre de mois (défaut: 6) |
| `accountId` | `UUID` | Non | Filtre par compte |

### Réponse 200

```json
[
  {
    "month": "2025-09",
    "expensesMinor": 35000,
    "confirmedIncomeMinor": 125000,
    "unclassifiedPositiveMinor": 35020,
    "transactionCount": 15
  },
  {
    "month": "2025-10",
    "expensesMinor": 0,
    "confirmedIncomeMinor": 0,
    "unclassifiedPositiveMinor": 0,
    "transactionCount": 0
  }
]
```

### Notes

- Retourne les N derniers mois (du plus ancien au plus récent).
- Les mois sans transactions sont inclus avec des indicateurs nuls.
- L'absence de transactions dans un mois ne prouve pas l'absence d'opérations bancaires.

### Erreurs

| Code | Cause |
|---|---|
| 400 | `months` hors de [1, 24] |
| 401 | JWT absent ou invalide |
| 404 | `accountId` inconnu ou appartenant à un autre utilisateur |

---

## Sécurité

- Toutes les requêtes sont cloisonnées par `user_id` via `accounts.user_id`.
- Un utilisateur ne voit que ses propres transactions.
- `accountId` étranger retourne 404 neutre (ne révèle pas l'existence du compte).
- Les agrégats ne mélangent jamais les données de différents utilisateurs.
- Aucune donnée financière n'apparaît dans les logs.

## Performance

- Les agrégations sont exécutées côté PostgreSQL (pas de chargement en mémoire Java).
- Utilisation de `GROUP BY`, `SUM`, `COUNT` natifs.
- Index existant `idx_transactions_account_date` sur `(account_id, booking_date)` utilisé.
- Aucune migration Flyway supplémentaire nécessaire.

## Limites

- Uniquement la devise EUR acceptée pour les agrégats communs.
- `basedOnIncompleteData` est toujours `true` — les imports CSV peuvent être partiels.
- Le dashboard ne calcule pas de solde bancaire ni d'épargne réalisée.
