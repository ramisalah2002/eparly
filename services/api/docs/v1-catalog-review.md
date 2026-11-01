# Revue administrative du catalogue V1 — Eparly

## 1. Offres initiales (8 brouillons)

| Offre | Fournisseur | Prix standard | Promotion | Statut revue | Raisons bloquantes |
|---|---|---|---|---|---|
| Forfait Free Max | Free Mobile | 29,99 EUR/mois | Aucune | READY_FOR_HUMAN_REVIEW | Aucune. Données illimitées non quantifiées (omission volontaire). |
| Forfait Free 5G+ | Free Mobile | 19,99 EUR/mois | Aucune | READY_FOR_HUMAN_REVIEW | Tarifs Freebox conditionnels distincts du tarif général. |
| Série Free | Free Mobile | 19,99 EUR/mois | 12,99 EUR/mois pendant 12 mois | READY_FOR_HUMAN_REVIEW | Prix de la deuxième année non garanti (bascule vers Free 5G+). |
| Forfait 2 euros | Free Mobile | 2,00 EUR/mois | Aucune | NEEDS_INFORMATION | Volume de données non vérifié (omis). |
| Netflix avec publicité | Netflix | 7,99 EUR/mois | Aucune | NEEDS_INFORMATION | Caractéristiques (écrans, qualité) non confirmées. |
| Disney+ Standard avec publicité | Disney+ | 6,99 EUR/mois | 5,99 EUR/mois pendant 6 mois | READY_FOR_HUMAN_REVIEW | Durée minimale de 6 mois à respecter. |
| Disney+ Standard | Disney+ | 10,99 EUR/mois | 8,99 EUR/mois pendant 6 mois | READY_FOR_HUMAN_REVIEW | Durée minimale de 6 mois à respecter. |
| Disney+ Premium | Disney+ | 15,99 EUR/mois | 11,99 EUR/mois pendant 6 mois | READY_FOR_HUMAN_REVIEW | Durée minimale de 6 mois à respecter. |

## 2. Promotions
- Les promotions sont enregistrées avec `promotionPriceType=FIXED_PRICE`, durée et prix promotionnel.
- La promotion n'est applicable que si la preuve est vérifiée et fraîche.
- Si la promotion n'est pas vérifiable, le tarif standard est conservé.

## 3. Procédure exacte de validation et publication (CATALOG_ADMIN)

1. Consulter l'offre :
   `GET /v1/admin/catalog/offers/{offerId}`
2. Examiner les preuves :
   `GET /v1/admin/catalog/offers/{offerId}/evidences`
3. Valider explicitement une preuve :
   `PATCH /v1/admin/catalog/offers/{offerId}/evidences/{evidenceId}/verification`
   Body : `{ "expectedVersion": <n>, "status": "VERIFIED", "summary": "..." }`
4. Publier :
   `POST /v1/admin/catalog/offers/{offerId}/publish`

La publication échoue si la preuve n'est pas vérifiée, expirée ou si le tarif a changé.

## 4. Nouveaux fournisseurs préparés
- Aucun nouveau fournisseur vérifié dans cette étape (Sosh non accessible, tarifs RED modulaires non vérifiables).

## 5. Nouvelles offres importées en DRAFT
- Aucune nouvelle offre (catalogue inchangé à 8 brouillons).

## 6. Nombre réel d'offres publiques
- 0 (aucune publication automatique).

## 7. Tests HTTP de bout en bout
- Couverts par les tests d'intégration existants (CatalogAdminIntegrationTest, OfferCatalogIntegrationTest, ComparisonIntegrationTest).
- Vérification de la visibilité des offres publiées, exclusion des DRAFT, estimations 12/24 mois, promotions.

## 8. Actions nécessaires pour le premier comparatif réel
1. Valider manuellement au moins 2 offres mobiles (ex. Free 5G+ et Forfait 2 €) et 2 offres streaming (ex. Disney+ Standard et Netflix).
2. Publier ces offres via CATALOG_ADMIN.
3. Vérifier `GET /v1/offers` et le comparateur.

## 9. Limites restantes
- Catalogue partiel (8 offres, 3 fournisseurs).
- Pas de comparaison musique/cloud.
- Validation humaine requise pour publication.