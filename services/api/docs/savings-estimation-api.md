# API d'estimation d'économies nettes potentielles

Base path: `/v1/savings`. Tous les endpoints exigent un JWT valide.

## Architecture

Le module `com.eparly.api.savings` constitue le moteur d'estimation d'économies nettes potentielles (étape 10C).
Il calcule l'économie nette estimée si un utilisateur remplace son abonnement actuel par une offre alternative réellement compatible.

Composants :
- `SavingsEstimationService` : orchestration, calcul des coûts, détermination du statut
- `SubscriptionBaselineService` : récupération du coût actuel de l'abonnement (déclaré ou observé)
- `PricingCalculationService` : calcul monétaire exact sur un horizon (BigDecimal, centimes)
- `PromotionCalculationService` : gestion des promotions temporaires
- `SwitchingCostService` : calcul des frais de changement (résiliation, activation, autres)
- `SavingsEligibilityValidator` : validation de l'offre et de l'éligibilité
- `SavingsExplanationBuilder` : construction des explications accessibles
- `SavingsController` : endpoints REST

Aucune nouvelle table ni migration Flyway (V13 non créée). La comparaison est calculée à la demande, sans persistance des résultats.

## Entrée : abonnement existant

L'utilisateur sélectionne un abonnement qu'il a confirmé ou ajouté manuellement (module `subscription`).

Le moteur récupère :
- L'identifiant de l'abonnement et son propriétaire (depuis le JWT).
- Son fournisseur et son nom.
- Les informations déclarées (montant, fréquence).
- Les informations éventuellement observées (via la série récurrente liée).

Priorité du coût actuel :
1. Montant déclaré par l'utilisateur (`USER_DECLARED`).
2. Montant observé dans les transactions bancaires (`TRANSACTION_OBSERVED`).
3. Inconnu (`UNKNOWN`) → impossible de fournir une estimation chiffrée fiable.

Les abonnements `CANCELED` ou `PAUSED` ne sont pas présentés comme une occasion d'économie sur un coût actif.

## Besoins utilisateur

Les critères de comparaison sont ceux définis à l'étape 10B (module `comparison`).
Ils sont typés et validés selon le secteur (STREAMING ou MOBILE_PLAN).

Quand l'utilisateur exprime explicitement un critère (ex: `requires5G=true`, `adsAccepted=false`), ce critère devient obligatoire pour l'équivalence. Une offre qui ne le satisfait pas est déclarée `INCOMPATIBLE_OFFER`.

## Résultat d'estimation

### Statuts financiers

| Statut | Signification |
|--------|--------------|
| `SAVINGS_POSSIBLE` | Gain net estimé positif, données suffisantes |
| `NO_SAVINGS` | Coût équivalent ou supérieur, données suffisantes |
| `INSUFFICIENT_DATA` | Coût, frais ou conditions nécessaires manquants |
| `INCOMPATIBLE_OFFER` | Offre non compatible avec les critères utilisateur |
| `NOT_ELIGIBLE` | Conditions commerciales non remplies |
| `UNVERIFIED_OFFER` | Prix ou offre non vérifiable |

### Formule de calcul

```
estimatedNetSavings = currentTotalCost − alternativeTotalCost − switchingCost
```

Où :
- `currentTotalCost` : coût de l'abonnement actuel sur l'horizon (montant déclaré ou observé)
- `alternativeTotalCost` : coût de l'offre alternative sur le même horizon, promotions incluses
- `switchingCost` : frais de changement (résiliation + activation + autres frais connus)

Un résultat négatif représente un surcoût, pas une économie.

### Promotions

Une promotion est appliquée uniquement si :
- Elle a un pourcentage et une durée définis
- Le prix après promotion est défini

Le prix pendant la promotion = `amountMinor × (1 − promotionPercent/100)`.
Après la durée de la promotion, le prix normal s'applique.
Une promotion n'est jamais appliquée indéfiniment.

### Frais de changement

- Frais de résiliation du contrat actuel (fourni par l'utilisateur)
- Frais d'activation du nouveau contrat (depuis le catalogue)
- Autres frais obligatoires

Si un frais est inconnu, il n'est pas substitué par zéro. Le statut devient `INSUFFICIENT_DATA`.

## Endpoints REST

### POST /v1/savings/estimate

Estime les économies nettes potentielles.

Corps de la requête :
```json
{
  "subscriptionId": "UUID",
  "offerId": "UUID",
  "sector": "MOBILE_PLAN",
  "horizonMonths": 12,
  "requirements": {
    "minDataGb": 100,
    "requires5G": true,
    "maxCommitmentMonths": 0
  },
  "userKnownCancellationFeeMinor": 1500,
  "userKnownOtherFeesMinor": 0
}
```

Réponse :
- Statut financier (SAVINGS_POSSIBLE, NO_SAVINGS, INSUFFICIENT_DATA, INCOMPATIBLE_OFFER, NOT_ELIGIBLE, UNVERIFIED_OFFER)
- Compatibilité de l'offre
- Coûts détaillés (actuel, alternatif, frais de changement)
- Économie nette potentielle ou surcoût
- Hypothèses, informations manquantes, étapes de calcul
- Provenance des prix, fraîcheur de la vérification

### GET /v1/savings/methodologie

Documentation des formules, horizons, hypothèses, sources, frais, arrondis et limites.

## Exemple

Abonnement actuel : 19,99 €/mois (déclaré).
Alternative : 9,99 €/mois.
Frais de changement : 15,00 €.
Horizon : 12 mois.

Calcul :
- Coût actuel : 19,99 × 12 = 239,88 €
- Coût alternatif : 9,99 × 12 = 119,88 €
- Frais de changement : 15,00 €
- Économie nette estimée : 239,88 − 119,88 − 15,00 = **105,00 €**

Cet exemple est fictif et réservé aux tests.

## Sécurité

- JWT obligatoire sur tous les endpoints.
- L'abonnement doit appartenir à l'utilisateur authentifié (404 neutre sinon).
- L'offre doit être consultable et vérifiée.
- Aucune modification de l'abonnement.
- Aucune donnée bancaire personnelle dans les logs.
- DTO distincts des entités JPA.

## Transparence

- Le catalogue n'est pas exhaustif.
- Une rémunération d'affiliation ne doit jamais augmenter artificiellement les économies affichées.
- Une estimation n'est pas une garantie d'économie réelle.
- Distinction entre économie potentielle (estimée) et économie réalisée (observée).
- Contrôle juridique requis avant publication.

## Limites

- Calcul déterministe, non probabiliste.
- Aucune économie chiffrée n'est créée ou enregistrée (pas de statut OBSERVED).
- Pas de scraping commercial, pas d'IA.
- Comparaison limitée à l'EUR (pas de taux de change).
- Le catalogue peut être vide si aucune offre n'a été vérifiée.