# API d'import CSV bancaire (étape 7B)

Contrat, formats, stratégie de déduplication et limites restantes du moteur d'import de relevés bancaires CSV.
Le contrat machine (OpenAPI avec exemples JSON) est exposé par l'API : `/v3/api-docs` et `/swagger-ui.html`.

Toutes les routes exigent un JWT Bearer valide d'un utilisateur **actif**. L'identité vient exclusivement du JWT :
aucun `userId` n'est accepté du client. Aucun fichier brut n'est conservé après traitement.

## 1. Parcours

```
1. POST /v1/imports/preview   -> hash SHA-256, mapping proposé, échantillon, erreurs/avertissements par ligne (aucune écriture)
2. (l'utilisateur corrige mapping / encodage si besoin, puis relance la preview)
3. POST /v1/imports           -> import atomique (le serveur reparse et revalide tout lui-même)
4. GET  /v1/imports, /v1/imports/{id}, /v1/accounts/{accountId}/imports   -> historique
```

## 2. Endpoints

| Méthode | Route | Rôle | Succès |
|---|---|---|---|
| POST | `/v1/imports/preview` | Prévisualisation, **aucune écriture en base** | 200 |
| POST | `/v1/imports` | Confirmation, import atomique dédupliqué | 201 |
| GET | `/v1/imports?page=0&size=20` | Historique de l'utilisateur | 200 |
| GET | `/v1/imports/{id}` | Détail d'un import | 200 |
| GET | `/v1/accounts/{accountId}/imports?page=0&size=20` | Historique d'un compte | 200 |

Pagination : `page` ≥ 0 (défaut 0), `size` ≥ 1 (défaut 20, **plafonné à 100**), tri `createdAt` décroissant.
Réponse : `{content[], page, size, totalElements, totalPages}`.

### Formulaire `multipart/form-data`

| Champ | Preview | Import | Description |
|---|---|---|---|
| `accountId` | obligatoire | obligatoire | UUID d'un compte de l'utilisateur |
| `file` | obligatoire | obligatoire | Le CSV (5 Mo max) |
| `mapping` | optionnel | **obligatoire** | JSON **sous forme de texte** (voir §3) |
| `fileHash` | — | **obligatoire** | `fileHash` renvoyé par la preview |
| `charset` | optionnel | obligatoire si la preview a signalé `charsetAmbiguous` | `UTF_8`, `WINDOWS_1252`, `ISO_8859_1` |
| `delimiter` | optionnel | optionnel | `;`, `,` ou tabulation (sinon détecté) |

Exemple React Native / Expo :

```ts
const form = new FormData();
form.append('accountId', accountId);
form.append('file', { uri: fileUri, name: 'releve.csv', type: 'text/csv' } as any);
form.append('mapping', JSON.stringify(mapping));      // texte JSON
form.append('fileHash', preview.fileHash);            // import uniquement
if (preview.charsetAmbiguous) form.append('charset', chosenCharset);

await fetch(`${API}/v1/imports`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${accessToken}` }, // ne pas fixer Content-Type : fetch ajoute le boundary
  body: form,
});
```

## 3. Mapping

Les colonnes sont désignées par **nom d'en-tête** (insensible à la casse, aux accents et à la ponctuation).
Colonnes inconnues, lignes vides et espaces superflus sont ignorés.

```json
{
  "dateColumn": "Date operation",
  "valueDateColumn": "Date valeur",
  "labelColumn": "Libelle",
  "amountColumn": "Montant",
  "debitColumn": null,
  "creditColumn": null,
  "currencyColumn": null,
  "sourceIdColumn": null,
  "dateFormat": "dd/MM/yyyy",
  "amountFormat": "FR"
}
```

- Obligatoires : `dateColumn`, `labelColumn`, et **soit** `amountColumn` (montant signé) **soit** `debitColumn` + `creditColumn`.
- `valueDateColumn` : date de valeur, optionnelle et distincte de la date comptable (`bookingDate`, jamais inventée).
- `currencyColumn` : si présente, doit correspondre à la devise du compte (sinon `CURRENCY_MISMATCH`).
- `sourceIdColumn` : identifiant de transaction **fiable et unique** (voir §6).
- `dateFormat` : `AUTO` (preview uniquement) ou un pattern de la liste blanche :
  `dd/MM/yyyy`, `dd-MM-yyyy`, `dd.MM.yyyy`, `yyyy-MM-dd`, `yyyy/MM/dd`, `MM/dd/yyyy`, `MM-dd-yyyy`, `yyyyMMdd`.
  **À l'import le format doit être explicite** (pas d'inversion jour/mois silencieuse). En `AUTO`, seuls les formats
  « jour d'abord » ou ISO sont essayés ; le format détecté est renvoyé dans `suggestedMapping.dateFormat`.
- `amountFormat` : `AUTO`, `FR` (virgule décimale) ou `EN` (point décimal).

La preview propose `suggestedMapping` (dictionnaire de synonymes d'en-têtes français/anglais, **sans nom de banque**).
Ajouter plus tard un profil de banque = implémenter `BankProfile` (bean Spring ordonné), sans toucher au reste du moteur.

## 4. Formats supportés

**Encodage** : UTF-8, UTF-8 avec BOM, Windows-1252, ISO-8859-1. BOM → UTF-8 ; sinon UTF-8 strict ; sinon repli 8 bits avec
l'avertissement `CHARSET_AMBIGUOUS` et `charsetCandidates` (`WINDOWS_1252`, `ISO_8859_1`, indiscernables) :
l'import exige alors un `charset` explicite (`400 CHARSET_REQUIRED`).

**Séparateurs** : `;`, `,`, tabulation. Champs entre guillemets (séparateurs et retours à la ligne inclus).

**Montants** (BigDecimal → centimes `long`, jamais de float) :

| Saisie | Centimes | Remarque |
|---|---|---|
| `-45,50` | -4550 | |
| `1250,00` | 125000 | |
| `1 250,00` (espace, NBSP, espace fine) | 125000 | |
| `1 250.00` | 125000 | `AUTO` ou `EN` |
| `+350,20` | 35020 | un montant positif n'est **jamais** déduit comme revenu |
| `-45,50 €`, `45,50 EUR` | | symbole accepté seulement s'il correspond à la devise du compte |
| `1,250` | refusé | ambigu en `AUTO` : choisir `FR` ou `EN` |

Refusés : valeur ambiguë, décimales supérieures à celles de la devise, dépassement d'un `long`, texte non numérique.
Montant nul : avertissement `ZERO_AMOUNT`. Débit/crédit : les deux remplis, ou aucun, est une erreur ; un crédit négatif est refusé.

**Type de transaction** : négatif → `EXPENSE`, sinon `UNKNOWN`. Un montant positif peut être un revenu, un remboursement ou un
virement : cette qualification est repoussée à l'étape 7C. Aucune catégorie n'est affectée à l'import.

## 5. Erreurs HTTP

Format standard `{timestamp,status,error,message,path}` ; les erreurs d'import ajoutent un `code` stable. Jamais de stack trace,
de chemin local, de libellé ni de montant.

| Statut | Cas |
|---|---|
| 400 | Paramètre/partie manquant ou invalide, mapping invalide (`INVALID_MAPPING`), `FILE_HASH_REQUIRED`, `MAPPING_REQUIRED`, `CHARSET_REQUIRED`, `INVALID_CHARSET`, `INVALID_DELIMITER`, `EMPTY_FILE`, `NO_DATA_ROWS`, `INVALID_ENCODING`, pagination invalide |
| 401 | JWT absent, invalide ou expiré |
| 403 | Compte utilisateur inactif |
| 404 | Compte ou import inconnu **ou appartenant à un autre utilisateur** (réponse identique, neutre) |
| 409 | `FILE_HASH_MISMATCH` (fichier différent de celui prévisualisé), `IMPORT_IN_PROGRESS` (attente de verrou dépassée) |
| 413 | Fichier trop volumineux |
| 415 | Fichier non CSV ou binaire (`BINARY_FILE`, `NOT_CSV`), quelle que soit son extension |
| 422 | Import : erreurs bloquantes (corps `ImportRejectedResponse`) ; fichier inexploitable (trop de lignes/colonnes, champ trop long, CSV mal formé, délai dépassé) |
| 500 | Erreur inattendue, message générique |

### Import refusé (422)

Une erreur bloquante n'enregistre **aucune** transaction. Un `ImportJob` au statut `FAILED` est tracé (compteurs et résumé
non sensible : codes et nombres uniquement).

```json
{
  "timestamp": "2026-10-28T09:15:30Z", "status": 422, "error": "Unprocessable Content",
  "message": "Le fichier contient des erreurs bloquantes : aucune transaction n'a été importée",
  "path": "/v1/imports",
  "importJobId": "3f1c2a54-8d7e-4b0a-9a53-0c1d2e3f4a5b",
  "errors": [{"line": 14, "column": "Montant", "code": "INVALID_AMOUNT", "message": "Montant non reconnu"}],
  "issueCount": 1, "truncated": false
}
```

Les numéros de ligne sont ceux du fichier (1 = première ligne) ; les listes sont plafonnées à 100 (`truncated`).

## 6. Déduplication (version d'empreinte `v1`)

Contraintes en base : `UNIQUE(account_id, fingerprint)` (V7) et index unique partiel `(account_id, source_uid)` (V8).

1. **Identifiant source fiable** (`sourceIdColumn` renseignée) : `fingerprint = SHA-256("v1|uid|" + sourceUid)`.
   Un identifiant **répété dans un même fichier** est jugé non fiable : erreur bloquante `DUPLICATE_SOURCE_ID`.
2. **Sinon** : `base = dateComptable|montantEnCentimes|devise|libelléNormalisé`,
   `fingerprint = SHA-256("v1|" + base + "|" + n)` où `n` est le **rang d'occurrence** de `base` dans le fichier (1, 2, …).
   Le libellé normalisé est sans accents, en majuscules, espaces réduits. La date de valeur est exclue (elle varie entre exports).

Conséquences :

- Réimporter le même fichier : job `COMPLETED`, `insertedRows = 0`, `skippedRows = N`.
- Deux achats identiques légitimes le même jour (`n = 1, 2`) coexistent ; l'avertissement `AMBIGUOUS_IDENTICAL_ROWS` les signale.
- Relevés qui se chevauchent : les opérations communes ont les mêmes `(base, n)` et sont ignorées.
- Indépendant de l'ordre des lignes et de leur numéro.
- Imports concurrents : verrou `pg_advisory_xact_lock` par compte (les imports d'un même compte sont sérialisés) et
  `INSERT … ON CONFLICT DO NOTHING` comme filet ; le nombre de lignes affectées donne les compteurs exacts.

### Limites connues (à ne pas présenter comme une certitude)

- Si un relevé B contient **plus** d'occurrences identiques que le relevé A déjà importé, les surnuméraires sont insérés
  (ce peuvent être de vrais doublons, ou de vrais achats supplémentaires : indécidable sans identifiant fiable).
- Mélanger des exports avec et sans identifiant source sur un même compte peut créer des doublons.
- Un libellé réécrit par la banque entre deux exports (sans identifiant source) n'est pas reconnu comme la même opération.
- Les colonnes de type « référence » ne sont pas proposées automatiquement comme identifiant source (non fiables).
- Le verrou advisory sérialise les imports d'un même compte (acceptable pour le MVP).

## 7. Limites et configuration

Variables d'environnement (valeurs par défaut) : `IMPORT_MAX_FILE_BYTES` (5 242 880), `IMPORT_MAX_ROWS` (10 000),
`IMPORT_MAX_COLUMNS` (50), `IMPORT_MAX_FIELD_LENGTH` (2 000), `IMPORT_PROCESSING_TIMEOUT` (`PT30S`),
`IMPORT_PREVIEW_SAMPLE_SIZE` (20), `IMPORT_MAX_REPORTED_ISSUES` (100) ; `spring.servlet.multipart.max-file-size=5MB`,
`max-request-size=6MB`. Le fichier est lu en mémoire (≤ 5 Mo) : aucune ressource non bornée.

## 8. Sécurité et intégrité

- Compte d'un autre utilisateur ou inconnu : **404 neutre** ; historique et détail strictement filtrés par utilisateur.
- Le serveur recalcule le SHA-256, reparse et revalide tout ; aucun montant ni transaction du client n'est cru.
- Extension et `Content-Type` ne sont pas des preuves : taille réelle, octets NUL, signatures binaires (ZIP, PDF, …), structure minimale.
- Aucun libellé ni montant dans les logs (testé) ; `Transaction.toString()` ne contient plus le libellé.
- Migration **V8** (additive, V1–V7 inchangées) : `import_jobs(account_id, user_id)` référence `accounts(id, user_id)` ;
  `transactions(import_job_id, account_id)` référence `import_jobs(id, account_id)` ; trigger de visibilité des catégories
  (système, ou privée du propriétaire du compte) ; unicité `(account_id, source_uid)` ; `inserted + skipped + failed ≤ total`.

## 9. Préparation de l'étape 7C (catégorisation)

- `transaction_type` vaut `EXPENSE` (montant négatif) ou `UNKNOWN` : 7C devra qualifier revenu / remboursement / virement / frais.
- `category_id` est `NULL` après import ; `custom_category_flag` reste faux. Le trigger V8 garantit déjà qu'une catégorie ne peut
  pas appartenir à un autre utilisateur.
- `normalized_label` (sans accents, majuscules, espaces réduits) est prêt pour des règles de catégorisation.
- Les montants sont en centimes `BIGINT` ; la devise est celle du compte (multi-devises non géré).
- Restent à traiter : profils spécifiques par banque (`BankProfile`), détection plus fine des doublons quasi identiques,
  gestion des virements internes entre comptes, import multi-devises, correction a posteriori d'un import.
