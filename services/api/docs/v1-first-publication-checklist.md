# Checklist de première publication contrôlée — Eparly V1

## 1. Offres éligibles à la revue humaine (6)

### Free Max
- ID : consulter via `GET /v1/admin/catalog/offers?status=DRAFT`
- Fournisseur : Free Mobile
- Formule : Forfait Free Max
- Prix standard : 29,99 EUR/mois
- Promotion : aucune
- Périodicité : MONTHLY
- Frais : inconnus
- Conditions : sans engagement, tarif hors avantage Freebox
- Caractéristiques vérifiées : network5G=true, roaming=true, données illimitées (non quantifiées)
- Informations manquantes : frais d'activation, volume exact (illimité)
- Source : https://mobile.free.fr/
- Date de consultation : 2026-11-01
- Preuve OFFER : PENDING
- Risques comparaison : données illimitées non représentables comme volume
- Décision proposée : VALIDABLE

### Free 5G+
- Prix standard : 19,99 EUR/mois
- Promotion : aucune
- Périodicité : MONTHLY
- Caractéristiques : dataGb=350, network5G=true, roaming=true
- Source : https://mobile.free.fr/
- Date : 2026-11-01
- Preuve : PENDING
- Décision proposée : VALIDABLE

### Série Free
- Prix standard : 19,99 EUR/mois
- Promotion : 12,99 EUR/mois pendant 12 mois (FIXED_PRICE)
- Périodicité : MONTHLY
- Caractéristiques : dataGb=110, network5G=true, roaming=true
- Source : https://mobile.free.fr/
- Date : 2026-11-01
- Preuve : PENDING
- Risques : prix de la deuxième année non garanti
- Décision proposée : VALIDABLE

### Disney+ Standard avec publicité
- Prix standard : 6,99 EUR/mois
- Promotion : 5,99 EUR/mois pendant 6 mois (FIXED_PRICE)
- Périodicité : MONTHLY
- Caractéristiques : ads=true, screens=2, videoQuality=HD, offlineDownload=true
- Source : https://www.disneyplus.com/fr-fr
- Date : 2026-11-01
- Preuve : PENDING
- Décision proposée : VALIDABLE

### Disney+ Standard
- Prix standard : 10,99 EUR/mois
- Promotion : 8,99 EUR/mois pendant 6 mois (FIXED_PRICE)
- Périodicité : MONTHLY
- Caractéristiques : ads=false, screens=2, videoQuality=HD, offlineDownload=true
- Source : https://www.disneyplus.com/fr-fr
- Date : 2026-11-01
- Preuve : PENDING
- Décision proposée : VALIDABLE

### Disney+ Premium
- Prix standard : 15,99 EUR/mois
- Promotion : 11,99 EUR/mois pendant 6 mois (FIXED_PRICE)
- Périodicité : MONTHLY
- Caractéristiques : ads=false, screens=4, videoQuality=4K, offlineDownload=true
- Source : https://www.disneyplus.com/fr-fr
- Date : 2026-11-01
- Preuve : PENDING
- Décision proposée : VALIDABLE

## 2. Offres NEEDS_INFORMATION (2)

### Forfait 2 €
- Prix : 2,00 EUR/mois
- Bloqueur : volume de données non vérifié
- Décision proposée : À COMPLÉTER

### Netflix avec publicité
- Prix : 7,99 EUR/mois
- Bloqueur : caractéristiques non confirmées
- Décision proposée : À COMPLÉTER

## 3. Requêtes administratives exactes

### Authentification
`POST /v1/auth/login`
Body : `{ "email": "<admin>", "password": "<password>" }`
→ récupérer le JWT.

### Consulter les brouillons
`GET /v1/admin/catalog/offers?status=DRAFT`
Header : `Authorization: Bearer <token>`

### Examiner les preuves
`GET /v1/admin/catalog/offers/{offerId}/evidences`

### Valider une preuve (après confirmation humaine)
`PATCH /v1/admin/catalog/offers/{offerId}/evidences/{evidenceId}/verification`
Body :
```json
{
  "expectedVersion": <version>,
  "status": "VERIFIED",
  "summary": "Source officielle verifiee manuellement le 2026-11-01"
}
```

### Publier une offre
`POST /v1/admin/catalog/offers/{offerId}/publish`

### Consulter le résultat public
`GET /v1/offers`

### Comparaison
`GET /v1/comparison?sector=MOBILE_PLAN&...` (paramètres selon ComparisonController)

### Estimation d'économies
`POST /v1/savings/estimate` (paramètres selon SavingsEstimationController)

## 4. Procédure Postman / Swagger
1. Se connecter avec un compte CATALOG_ADMIN existant.
2. Lister les offres DRAFT.
3. Pour chaque offre prête : examiner les preuves, valider la preuve OFFER, puis publier.
4. Vérifier `GET /v1/offers`.
5. Tester le comparateur avec deux offres publiées.

## 5. Actions à effectuer personnellement
- Valider manuellement les preuves des 6 offres READY_FOR_HUMAN_REVIEW.
- Publier les offres choisies.
- Vérifier les APIs publiques.
- Compléter Forfait 2 € et Netflix avant publication.

## 6. Tests HTTP de bout en bout
- Couverts par les tests d'intégration existants (CatalogAdminIntegrationTest, OfferCatalogIntegrationTest, ComparisonIntegrationTest).
- Vérification de la visibilité des offres publiées, exclusion des DRAFT, estimations 12/24 mois, promotions.

## 7. Résultat Maven clean verify
- BUILD SUCCESS (dernière exécution complète).

## 8. Limites restantes
- Catalogue partiel (8 offres, 3 fournisseurs).
- Pas de comparaison musique/cloud.
- Validation humaine requise pour publication.