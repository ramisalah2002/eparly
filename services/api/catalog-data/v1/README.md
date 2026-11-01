# Catalogue manuel V1 — Eparly

Les fichiers de ce dossier sont des **modèles vides** pour la V1.

- Aucun prix réel n'est inclus.
- Aucune offre n'est publiée automatiquement.
- Les offres doivent être renseignées manuellement à partir de pages officielles.
- Chaque offre importée reste en brouillon tant qu'elle n'a pas été vérifiée et publiée via l'administration sécurisée.

## Fichiers
- `providers.json` : fournisseurs.
- `mobile-offers.json` : forfaits mobiles.
- `video-streaming-offers.json` : streaming vidéo.
- `music-offers.json` : musique.
- `cloud-software-offers.json` : cloud et logiciels.

## Règles
- Ne jamais mettre de secrets, tokens, JWT ou données personnelles.
- Ne jamais inventer de prix.
- Toujours conserver la date réelle de consultation dans `evidence.consultedAt`.
- Utiliser le dry-run avant tout import réel.