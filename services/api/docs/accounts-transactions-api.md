# Comptes et transactions — intégration React Native

Toutes les routes ci-dessous nécessitent `Authorization: Bearer <access-token>` (JWT RS256). L'identité et le propriétaire sont dérivés du JWT ; aucun `userId` ne doit être envoyé. Les réponses financières ne contiennent ni empreinte de déduplication, ni identifiant de fichier ou données de traitement interne.

## Comptes

| Méthode | Route | Succès | Principales erreurs |
|---|---|---|---|
| `POST` | `/v1/accounts` | `201` | `400` nom/devise invalide, `401`, `409` nom déjà utilisé |
| `GET` | `/v1/accounts` | `200` | `401` |
| `GET` | `/v1/accounts/{id}` | `200` | `401`, `404` inconnu ou étranger |
| `PATCH` | `/v1/accounts/{id}` | `200` | `400`, `401`, `404` inconnu ou étranger, `409` doublon/devise verrouillée |

Création : `name` obligatoire (1–100 caractères après trim), `currency` facultative (3 lettres ISO, `EUR` par défaut). Le compte créé a toujours `sourceType: "CSV"`.

```json
{"name":"Compte courant","currency":"EUR"}
```

Modification partielle : `name` (1–100 caractères) et/ou `currency` (3 lettres ISO). La devise n'est modifiable que tant qu'aucune transaction n'existe.

```json
{"name":"Compte principal"}
```

La réponse de compte contient `id`, `name`, `sourceType`, `currency`, `createdAt` et `updatedAt`. Un identifiant de compte étranger produit le même `404` qu'un identifiant absent.

## Transactions

| Méthode | Route | Succès | Principales erreurs |
|---|---|---|---|
| `GET` | `/v1/transactions` | `200` | `400` filtre/pagination invalide, `401`, `404` compte absent/étranger si `accountId` est fourni |
| `GET` | `/v1/transactions/{id}` | `200` | `401`, `404` inconnu ou étranger |

Filtres combinables : `accountId` (UUID), `startDate` et `endDate` (dates ISO `YYYY-MM-DD`, bornes inclusives sur `bookingDate`) et `transactionType` (`INCOME`, `EXPENSE`, `TRANSFER`, `REFUND`, `FEE`, `UNKNOWN`). `startDate` ne peut pas être postérieure à `endDate`.

Pagination : `page` commence à 0 (défaut 0), `size` par défaut 20 et plafonné à 100. Tri déterministe `bookingDate` décroissante puis `id` décroissant. L'enveloppe est `{content, page, size, totalElements, totalPages}`.

```http
GET /v1/transactions?accountId=7a9b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d&startDate=2026-01-01&endDate=2026-03-31&transactionType=EXPENSE&page=0&size=20
```

Chaque transaction expose `id`, `accountId`, `bookingDate`, `valueDate`, `amountMinor` (centimes entiers), `currency`, `rawLabel`, `normalizedLabel`, `transactionType`, `categoryId` et `customCategoryFlag`. Les imports positifs restent `UNKNOWN` ; l'API ne calcule aucun solde bancaire.

Les erreurs standard ont la forme `{timestamp,status,error,message,path}`. Un JWT absent ou invalide renvoie `401`; une ressource privée absente ou étrangère renvoie `404`. La spécification complète, avec Bearer JWT et exemples, est disponible sur `/v3/api-docs` et Swagger UI.

## Parcours d'import

1. Créer le compte avec `POST /v1/accounts` et conserver son `id`.
2. Envoyer le CSV à `POST /v1/imports/preview` avec ce `accountId` pour obtenir le hash et le mapping proposé.
3. Confirmer sur `POST /v1/imports` avec le même CSV, son `fileHash` et le mapping validé.
4. Afficher les opérations avec `GET /v1/transactions?accountId=<id>` et les imports avec `GET /v1/accounts/{id}/imports`.

Le contrat multipart complet (fichier, mapping, encodage, hash et déduplication) est décrit dans [`imports-api.md`](imports-api.md). Ne pas fixer soi-même le `Content-Type` multipart dans `fetch` : le runtime doit ajouter la boundary.
