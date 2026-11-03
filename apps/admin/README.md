# Eparly Admin

Console d'administration du catalogue Eparly (Next.js 16, App Router, Tailwind).
Lot livré : **13E — P0** du cahier des charges (connexion, tableau de bord, offres, fiche offre, revue des preuves, publication contrôlée).

## Démarrage

Prérequis : PostgreSQL et l'API Spring Boot démarrés (voir le README racine), Node 22.

```bash
pnpm dev:admin          # depuis la racine → http://localhost:3000
```

Variables d'environnement (serveur uniquement, jamais exposées au navigateur) :

| Variable | Défaut | Rôle |
|---|---|---|
| `EPARLY_API_BASE_URL` | `http://127.0.0.1:8080` | URL de l'API Spring Boot |
| `EPARLY_ADMIN_ALLOW_PUBLISH` | absent (désactivé) | `true` autorise le BFF à transmettre une publication. Laisser désactivé en développement et sur la base locale. |

### Compte administrateur

La connexion exige un compte **actif, email vérifié** et une permission `CATALOG_ADMIN`.
Aucune interface n'attribue ce droit : il se provisionne en SQL par un opérateur, selon
`services/api/docs/catalog-admin-api.md`. Un compte sans cette permission est refusé à la
connexion et sa session backend est aussitôt révoquée.

## Vérifications

```bash
pnpm --filter admin lint
pnpm --filter admin typecheck   # next typegen + tsc
pnpm --filter admin test        # Vitest
pnpm --filter admin build
```

## Architecture et sécurité

- **BFF** : le navigateur ne parle jamais à Spring Boot. Les lectures se font dans les Server
  Components (`src/lib/server/`), les mutations via `/api/admin/[...path]`.
- **Session** : jetons d'accès et de refresh en cookies `HttpOnly`, `SameSite=Strict`
  (`Secure` en production). Aucun jeton n'est lisible par le JavaScript client.
- **Rotation** : `src/proxy.ts` rafraîchit le jeton d'accès avant expiration. Les refresh
  concurrents d'un même jeton sont mutualisés (un rejeu révoquerait toute la session).
  Limite : mutualisation locale au processus, à revoir pour un déploiement multi-instance.
- **CSRF** : jeton double-submit (`x-csrf-token` = cookie `eparly_admin_csrf`) et contrôle
  de l'en-tête `Origin` sur toute mutation.
- **Liste blanche** : le BFF ne transmet que les mutations utilisées par les écrans
  (`src/lib/bff-policy.ts`). Spring Boot reste seul juge des autorisations et de la publication.
- **Checklist de publication** : indicative (`src/lib/checklist.ts`), elle reprend les
  contrôles de `CatalogAdminService.publish`. Le statut ne change qu'après réponse du serveur.

## Écrans livrés

| Route | Contenu |
|---|---|
| `/login` | Connexion email / mot de passe, refus des comptes non administrateurs |
| `/dashboard` | Totaux par statut et secteur, preuves à vérifier, offres bloquées, dernières modifications, dernière collecte |
| `/catalog/offers` | Table paginée côté serveur, filtres statut / secteur / fournisseur / « avec blocage », URL partageables |
| `/catalog/offers/[offerId]` | Synthèse, tarification, conditions, caractéristiques, preuves et décision, historique des prix, audit, checklist, publication / archivage |
| `/catalog/reviews` | File des preuves `PENDING`, priorisée, avec points d'attention |

## Endpoints consommés

| Méthode | Route Spring Boot | Usage |
|---|---|---|
| POST | `/v1/auth/login`, `/v1/auth/refresh`, `/v1/auth/logout` | Session |
| GET | `/v1/me` | Identité affichée |
| GET | `/v1/admin/catalog/offers` | Liste, dashboard, file de revue |
| GET | `/v1/admin/catalog/offers/{id}` (+ `/evidences`, `/price-history`, `/audit`) | Fiche offre |
| GET | `/v1/admin/catalog/providers` | Filtre, total, sonde de permission |
| GET | `/v1/admin/catalog-sync/runs` | Encart du batch |
| PATCH | `/v1/admin/catalog/offers/{id}/evidences/{evidenceId}/verification` | Décision sur une preuve |
| POST | `/v1/admin/catalog/offers/{id}/publish`, `/archive` | Publication / archivage |

## Contrats backend manquants

Constatés sur le contrat OpenAPI actuel (migration V21). À demander au responsable API :

1. **Droits de réutilisation** : `GET/PATCH /v1/admin/catalog/offers/{id}/reuse-rights`
   (cités par le cahier des charges, absents du dépôt). L'écran affiche « non exposés ».
2. **Blocages de publication structurés** : `publish` ne renvoie que le premier motif ; une
   liste complète remplacerait la checklist indicative.
3. **Agrégats catalogue** et **file de revue globale** : aujourd'hui calculés en parcourant
   toutes les offres (plafond 2 000).
4. **Recherche textuelle** sur les offres et fournisseurs.
5. **État du scheduler** (prochaine échéance, actif ou non) : non exposé.
6. **Utilisateurs** (lot P2) : aucun endpoint admin.

Écart à signaler : le cahier des charges décrit un état V28 (secteurs banque, électricité,
SaaS ; 83 offres) qui n'existe sur aucune branche du dépôt.
