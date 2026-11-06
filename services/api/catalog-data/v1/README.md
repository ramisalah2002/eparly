# Catalogue manuel V1 — Eparly

Ces fichiers contiennent des offres saisies hors collecte automatique, à vérifier par une personne.

- Aucune offre n'est publiée automatiquement.
- Les offres doivent être renseignées à partir de pages officielles.
- Chaque offre importée reste en brouillon tant qu'elle n'a pas été vérifiée et publiée via l'administration sécurisée.

## Fichiers
- `providers.json` : fournisseurs.
- `mobile-offers.json` : forfaits mobiles.
- `internet-offers.json` : box internet.
- `video-streaming-offers.json` : streaming vidéo.
- `music-offers.json` : musique.
- `cloud-software-offers.json` : cloud et logiciels.

## Règles
- Ne jamais mettre de secrets, tokens, JWT ou données personnelles.
- Ne jamais inventer de prix.
- Toujours conserver la date réelle de consultation dans `evidence.consultedAt`, en UTC et jamais dans le futur.
- Mettre `dataUnknown: true` tant que le coût complet (frais d'activation, promotion, résiliation) n'est pas vérifié.
- Utiliser le dry-run avant tout import réel, et renseigner `CATALOG_IMPORT_ACTOR_USER_ID` avec le compte qui en répond.
- Pour corriger une offre déjà créée, utiliser un fichier de révision : voir `docs/v1-manual-catalog.md`.

## Réutilisation
Les mentions légales de SFR limitent la reproduction et l'utilisation secondaire de leurs contenus : voir
`docs/catalog-sources-inventory.md`. Faire confirmer la réutilisation avant toute publication.
