# Administration sécurisée du catalogue commercial

## Architecture et contrôle d'accès

Les routes `/v1/admin/catalog/**` exigent une permission persistante `CATALOG_ADMIN`.
Le JWT prouve uniquement l'identité du compte ; les rôles ou claims fournis par le
client ne donnent aucun privilège. À chaque authentification JWT, l'API vérifie en
base que l'UUID du sujet possède une permission active et que le compte est actif
et son adresse vérifiée. Une révocation est donc effective à la requête suivante.
L'absence de JWT renvoie `401`, un compte standard `403`.

V19 crée `catalog_admin_permissions`, `catalog_admin_audit` et les colonnes de
traçabilité des preuves. Il n'existe ni endpoint public d'attribution, ni privilège
attribué à l'inscription. Le premier administrateur est provisionné hors API par
un opérateur ayant accès sécurisé à la base :

```sql
BEGIN;
INSERT INTO catalog_admin_permissions (user_id, granted_by_user_id)
SELECT id, NULL
FROM users
WHERE id = 'UUID_EXISTANT_DU_COMPTE'
  AND status = 'ACTIVE'
  AND email_verified_at IS NOT NULL;
-- Vérifier qu'une seule ligne a été créée avant le COMMIT.
COMMIT;
```

Le compte est identifié par son UUID existant, jamais par un email fourni dans une
requête d'inscription. En local comme en staging, le UUID doit être transmis par
le canal d'exploitation sécurisé et ne doit pas être inscrit dans le dépôt. Pour
accorder un accès ultérieur, insérer un nouveau grant avec l'UUID du compte
administrateur opérateur dans `granted_by_user_id`. Révoquer sans supprimer
l'historique :

```sql
UPDATE catalog_admin_permissions
SET revoked_at = CURRENT_TIMESTAMP
WHERE user_id = 'UUID_ADMIN'
  AND revoked_at IS NULL;
```

Une réattribution crée une nouvelle ligne, conservant les périodes précédentes.
Les opérations effectuées hors API doivent également suivre les journaux
d'exploitation de la base.

## Contrats REST

Toutes les routes ci-dessous nécessitent un bearer JWT d'administrateur.
Les montants sont des unités mineures de la devise indiquée. Les champs absents
ou `null` restent inconnus ; ils ne signifient jamais zéro. Les listes sont
paginées (`page=0`, `size=20`, taille plafonnée à 100).

| Méthode et route | Fonction |
|---|---|
| `POST /v1/admin/catalog/providers` | Créer un fournisseur actif |
| `GET /v1/admin/catalog/providers?page=0&size=20` | Lister tous les fournisseurs |
| `GET /v1/admin/catalog/providers/{id}` | Consulter un fournisseur, actif ou non |
| `PATCH /v1/admin/catalog/providers/{id}` | Modifier fournisseur/statut, avec `expectedVersion` |
| `GET /v1/admin/catalog/providers/{id}/audit` | Lire les actions visant un fournisseur |
| `POST /v1/admin/catalog/offers` | Créer un brouillon |
| `GET /v1/admin/catalog/offers` | Lister/filtrer par `status`, `providerId`, `sector` |
| `GET /v1/admin/catalog/offers/{id}` | Consulter offre, tarif, preuves |
| `PATCH /v1/admin/catalog/offers/{id}` | Modifier un brouillon/une offre, avec `expectedVersion` |
| `PUT /v1/admin/catalog/offers/{id}/pricing` | Créer ou réviser le tarif canonique |
| `POST /v1/admin/catalog/offers/{id}/evidences` | Ajouter une preuve en `PENDING` |
| `GET /v1/admin/catalog/offers/{id}/evidences` | Lister les preuves et validateurs |
| `PATCH /v1/admin/catalog/offers/{id}/evidences/{evidenceId}/verification` | Valider ou invalider une preuve |
| `POST /v1/admin/catalog/offers/{id}/publish` | Publier après contrôles de disponibilité |
| `POST /v1/admin/catalog/offers/{id}/archive` | Archiver sans suppression physique |
| `GET /v1/admin/catalog/offers/{id}/price-history` | Lire les snapshots tarifaires |
| `GET /v1/admin/catalog/offers/{id}/audit` | Lire les actions visant l'offre |

### Contrats fournisseurs

`GET /v1/admin/catalog/providers?page=0&size=20` retourne un
`AdminPageResponse<OfferProviderResponse>` (`items`, `page`, `size`,
`totalElements`, `totalPages`). Le tri est alphabétique par nom. Cette route ne
prend pas de filtre de nom ou de statut. `GET /providers/{id}` retourne le même
`OfferProviderResponse` pour un fournisseur actif ou inactif. Les deux routes
exigent `CATALOG_ADMIN` (`401` sans authentification, `403` sans permission,
`404` pour un identifiant inconnu).

La création accepte `name` (1–200 caractères), `sector` (`STREAMING`,
`MOBILE_PLAN` ou `INTERNET_BOX`), `officialWebsite` (URL HTTPS, 1–500 caractères)
et `country` (code de deux lettres, facultatif; `FR` par défaut). Le statut créé
est `ACTIVE`. Le succès renvoie `201` avec un `OfferProviderResponse`;
validation invalide renvoie `400` et doublon nom/secteur `409`.

`PATCH /providers/{id}` accepte `expectedVersion` (obligatoire), ainsi que les
champs modifiables `name`, `sector`, `country`, `officialWebsite` et `status`
(`ACTIVE` ou `INACTIVE`). Les champs omis restent inchangés. Le succès renvoie
`200` avec le fournisseur mis à jour; version périmée ou changement de secteur
impossible lorsque des offres existent renvoie `409`, données invalides `400`,
identifiant inconnu `404`. Toute modification effective est auditée. Les offres
liées ne sont pas supprimées; les offres publiées peuvent être rendues `STALE`.
Un fournisseur inactif n'est pas exposé dans le catalogue public.

`GET /providers/{id}/audit` renvoie une liste de `CatalogAuditResponse`
ordonnée par date décroissante; un fournisseur inconnu renvoie `404`.

### Création et modification

Exemple fictif de fournisseur et d'offre :

```json
{
  "name": "Fournisseur de test",
  "sector": "STREAMING",
  "officialWebsite": "https://provider.example.test",
  "country": "FR"
}
```

```json
{
  "providerId": "UUID",
  "commercialName": "Formule de test",
  "sector": "STREAMING",
  "characteristics": "{\"screens\":2,\"videoQuality\":\"HD\"}",
  "eligibilityConditions": null,
  "commitment": "Sans engagement",
  "territory": "FR",
  "validFrom": "2026-01-01",
  "validUntil": "2026-12-31"
}
```

`characteristics` est un objet JSON sérialisé. Un JSON malformé, une racine non
objet, des clés connues d'un autre secteur ou des types invalides sont refusés.
Les attributs inconnus restent extensibles pour les informations non encore
comparées. Les offres futures peuvent être créées, mais restent en brouillon :
la publication différée n'est pas prise en charge.

Les mises à jour PATCH acceptent uniquement les champs présents et exigent la
version reçue dans la réponse (`expectedVersion`). Un changement de contenu remet
l'offre en brouillon et invalide les preuves vérifiées antérieures, sans les
supprimer. Une modification de fournisseur rend ses offres publiées `STALE`.

### Prix et promotions

Exemple de tarif standard :

```json
{
  "amountMinor": 1999,
  "currency": "EUR",
  "billingPeriod": "MONTHLY",
  "activationFeeMinor": null,
  "otherFees": null,
  "promotionPriceType": null,
  "promotionPercent": null,
  "promotionDurationMonths": null,
  "priceAfterPromotionMinor": null,
  "promotionValidFrom": null,
  "promotionValidUntil": null,
  "discountConditions": null,
  "taxesIncluded": true,
  "dataUnknown": false
}
```

La sémantique 12C.2B est conservée : `amountMinor` est le tarif standard/après
promotion ; la promotion est typée `PERCENTAGE` ou `FIXED_PRICE`, avec une durée
positive et une fenêtre de souscription éventuellement bornée. Le champ historique
`priceAfterPromotionMinor` porte le montant temporaire fixe uniquement pour
`FIXED_PRICE`. Les deux représentations promotionnelles sont mutuellement
exclusives. À la création du tarif, omettre `expectedVersion`; à toute révision,
fournir la version actuelle. La révision conserve des snapshots JSONB avant/après,
incrémente la version et remet une offre publiée en brouillon. Une preuve existante
ne valide pas le nouveau tarif.

### Sources et validation humaine

L'ajout d'une preuve ne déclenche aucun accès externe et ne la vérifie pas. L'état
initial est toujours `PENDING` et `verifiedAt` reste nul. La source doit être une
URL HTTPS sur le domaine officiel renseigné pour le fournisseur ou l'un de ses
sous-domaines. Cette vérification de domaine réduit les erreurs de provenance ;
elle ne prouve pas à elle seule l'authenticité ou l'exactitude du contenu.

```json
{
  "officialUrl": "https://provider.example.test/offers",
  "evidenceScope": "OFFER",
  "nature": "Tarif et conditions",
  "provenance": "Consultation humaine",
  "consultedAt": "2026-01-10T12:00:00Z",
  "expiresAt": null
}
```

Une validation est un acte explicite de l'administrateur : l'opération
`/verification` accepte `VERIFIED` ou `INVALID`, un `expectedVersion` de preuve
et un résumé non vide. `VERIFIED` actualise les dates de consultation et de
validation à l'instant serveur et enregistre l'UUID du validateur. La preuve
conserve les versions tarifaire et fournisseur au moment de la vérification.
L'offre publiée requiert une preuve `OFFER` vérifiée, fraîche, non expirée,
validée par un administrateur et associée aux versions courantes. `PROMOTION`
requiert sa preuve dédiée et fraîche ; les preuves `PRICING` ou `PROMOTION` ne
remplacent pas la preuve générale de l'offre.

Le client doit avoir effectivement consulté la source avant de demander
`VERIFIED`. L'API n'effectue ni scraping ni contrôle externe automatisé. La
politique centrale `OfferAvailabilityService` demeure l'autorité de disponibilité
des routes publiques.

### Publication, retrait et historiques

`publish` refuse un fournisseur inactif, un secteur incohérent, un tarif non
fiable/incomplet, une offre future ou expirée, et toute preuve manquante, périmée
ou ne correspondant pas aux versions courantes. La transition réutilise
`OfferCatalogService.validateAndPublish` et vérifie ensuite la politique
`OfferAvailabilityService`; elle ne contourne pas la disponibilité publique.
Les statuts existants `DRAFT`, `VERIFIED`, `STALE`, `EXPIRED` et `ARCHIVED` sont
conservés. `archive` est logique et idempotent ; aucune offre historique n'est
supprimée.

Les modifications tarifaires alimentent `offer_price_history` et ses snapshots
JSONB. `catalog_admin_audit` conserve l'acteur, l'action, la ressource et les
détails avant/après des opérations d'administration. Les versions optimistes des
fournisseurs, offres, prix et preuves empêchent les écrasements silencieux ; une
version périmée renvoie `409 Conflict`. Les snapshots d'estimations existants
ne sont pas recalculés.

## Workflow administratif

1. Un opérateur autorisé provisionne hors API un compte actif et vérifié dans
   `catalog_admin_permissions`.
2. L'administrateur crée un fournisseur, puis une offre en brouillon.
3. Il ajoute le tarif standard et, le cas échéant, une promotion typée.
4. Il ajoute une preuve `OFFER` et, séparément, une preuve `PROMOTION` si requise.
5. Après consultation humaine de la source, il valide chaque preuve avec son
   `expectedVersion` et un résumé.
6. Il publie l'offre ; une réponse `400` signifie qu'une condition métier ou de
   disponibilité manque, `409` qu'une version a changé.
7. Après révision tarifaire ou de contenu, il collecte/revalide les preuves,
   republie, puis consulte `price-history` et `audit`.
8. Il archive l'offre lorsqu'elle ne doit plus être recommandée.

Les réponses d'erreur suivent le format global de l'API. Les codes principaux
sont `400` pour une requête ou une publication invalide, `401` sans authentification,
`403` sans permission, `404` pour une ressource absente et `409` pour un doublon
ou une version concurrente.

## Limites

- L'attribution/révocation des administrateurs est une opération d'exploitation
  SQL, sans interface d'élévation de privilège.
- La vérification humaine est déclarative ; aucune page externe n'est récupérée.
- La correspondance officielle est limitée au domaine et ses sous-domaines ; une
  liste explicite de domaines tiers approuvés n'est pas encore modélisée.
- Les caractéristiques extensibles ne constituent pas encore un schéma exhaustif
  par secteur, et le secteur `INTERNET_BOX` n'a pas encore de stratégie de comparaison.
