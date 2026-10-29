# Politique de disponibilité du catalogue

Cette politique s'applique aux lectures utilisateur du catalogue, aux recommandations du comparateur et aux estimations savings. Les changements ne modifient pas les snapshots d'actions historiques.

## Conditions de visibilité

Une offre est disponible uniquement si toutes les conditions suivantes sont remplies :

- Son statut est `VERIFIED`. `DRAFT`, `STALE`, `EXPIRED` et `ARCHIVED` sont exclus.
- Son fournisseur est `ACTIVE` et son secteur correspond à celui de l'offre.
- Le territoire correspond à la requête.
- `validFrom` est absent ou inférieur ou égal à la date courante. `validUntil` est absent ou supérieur ou égal à la date courante ; les deux bornes sont inclusives.
- Une tarification existe avec un montant non négatif, une devise renseignée, une périodicité connue et `dataUnknown` faux. Une valeur inconnue n'est jamais assimilée à zéro.
- La preuve d'offre la plus récente (`OFFER`, ou ancienne preuve à scope nul) est valide, vérifiée et suffisamment fraîche. Une preuve `PRICING` ou `PROMOTION` ne peut pas la remplacer.

Les endpoints du catalogue et du comparateur filtrent ces conditions en base avant pagination ou comparaison. Une page sans résultat reste une réponse vide normale ; le détail d'une offre inconnue ou indisponible renvoie `404`. Demander un statut différent de `VERIFIED` sur la liste produit une page vide et ne modifie pas les règles de visibilité.

## Preuves et fraîcheur

Les preuves sont ordonnées par `consultedAt` décroissant, puis par `createdAt` décroissant et identifiant décroissant pour départager les égalités, au sein du scope d'offre. Une preuve d'offre plus récemment consultée dont le statut est `PENDING`, `EXPIRED` ou `INVALID` invalide l'ancienne preuve vérifiée : elle n'est pas ignorée au profit d'une vérification plus ancienne. Les scopes distincts (`PRICING`, `PROMOTION`) ont leur propre cycle de fraîcheur.

Une preuve sans `consultedAt` est traitée comme l'information la plus récente et invalide toute ancienne preuve ; elle ne peut pas être retenue comme preuve actuelle.

La preuve sélectionnée doit être `VERIFIED`, contenir une URL HTTPS valide, avoir des dates `consultedAt` et `verifiedAt` connues et non futures, et avoir été vérifiée au plus tard après consultation. `expiresAt`, lorsqu'il existe, est une borne exclusive : la preuve n'est plus valable à cet instant. `verifiedAt` doit se situer dans le seuil `eparly.offer.catalog.freshness-threshold` (30 jours par défaut).

La consultation de la source n'est pas une validation humaine. Les dates de consultation et de vérification ne sont plus remplies automatiquement à l'insertion ; les deux colonnes sont nullable depuis V17 afin qu'une vérification inconnue ne soit pas présentée comme récente. Une date manquante exclut la preuve.

## Critères et modules consommateurs

Les stratégies valident les clés et le type de chaque critère avant comparaison. Les critères inconnus, de type invalide ou hors plage renvoient une erreur client `400`. Une donnée d'offre manquante pour un critère valide reste une situation métier `NEEDS_INFORMATION`.

`roamingCountries` n'est pas pris en charge : le comparateur le rejette explicitement et ne prétend pas disposer d'une liste de pays. `INTERNET_BOX` n'a pas de stratégie ; une demande de comparaison de ce secteur renvoie l'erreur `404` existante signalant l'absence de stratégie.

`GET /v1/offers` et `GET /v1/offers/{id}` exposent uniquement les offres actuellement disponibles. Sans territoire explicite, la liste utilise `FR`, comme le détail et le comparateur. Les fournisseurs listés restent limités aux fournisseurs actifs. Le comparateur sélectionne en base les offres disponibles et utilise la preuve actuelle retenue. Savings réutilise la même vérification ; une offre indisponible renvoie `UNVERIFIED_OFFER` sans coûts ni économie calculée, plutôt qu'une estimation présentée comme fiable. Les actions antérieures conservent leurs données snapshots.

## Politique promotionnelle

Les promotions nécessitent une preuve dédiée `PROMOTION` fraîche de moins de sept jours par défaut. La règle complète des prix, périodes, éligibilité et frais est décrite dans [offer-pricing-policy.md](offer-pricing-policy.md). Une preuve promotionnelle ne modifie pas la disponibilité du tarif standard. Aucun mécanisme d'administration ou d'alimentation d'offres n'est ajouté ici.
