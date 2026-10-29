# Évaluation des sources commerciales réelles

## Décision au 29 octobre 2026

Aucune source n'est approuvée ni activée pour une collecte Eparly. Les pages
ci-dessous ont été consultées en lecture ponctuelle afin d'évaluer leur
accessibilité et leur contenu apparent ; cette consultation n'était pas un run du
batch et n'a créé aucune observation, aucun candidat ni aucune donnée en base.

L'acceptation par `robots.txt` indique uniquement qu'une URL n'est pas interdite
au user-agent correspondant. Elle ne constitue pas une autorisation juridique,
contractuelle ou commerciale de collecte. Les conditions permettant explicitement
la collecte automatisée et la réutilisation des prix n'ont pas été établies pour
les fournisseurs ci-dessous. Ils restent donc `PENDING`, `collection_enabled=false`
et ne doivent pas être insérés/activés en production avant revue des conditions ou
accord explicite du fournisseur.

## Plateformes d'affiliation examinées

| Plateforme | Capacités documentées | Ce qui reste inaccessible / à vérifier pour Eparly |
|---|---|---|
| [Awin — données telco enrichies](https://help.awin.com/developers/docs/enhanced-telco-data-feeds-overview) | Un schéma telco spécialisé annonce identité produit/forfait, réseau, détails de tarif, allocations, coûts mensuels/upfront, remises, durées et dates de mise à jour. La documentation indique les champs d'identité requis `merchant_product_id`, `merchant_deep_link`, `last_updated`. Le téléchargement Create-a-Feed produit un fichier CSV UTF-8, avec colonnes sélectionnables et séparateur configurable. La liste des feeds peut être scriptée avec une clé produit dédiée. | La liste effective des feeds est limitée aux annonceurs rejoints ou autorisant une promotion avant partenariat. Nécessite un compte publisher, l'accès Create-a-Feed et une clé de flux distincte du Publisher API. Aucun compte, token, feed ID ni feed France n'est disponible ici ; la disponibilité d'opérateurs/forfaits FR, les prix en EUR, droits de stockage/réaffichage et frais n'ont pas été vérifiés. La documentation des champs standards consultée note `currency` GBP et langue English ; il faut valider dans un vrai feed français qu'un feed localisé fournit bien EUR/français avant toute intégration commerciale. Les promotions et remises doivent être interprétées selon leurs champs et conditions, non calculées à partir de texte libre. |
| [Daisycon Publisher API](https://docs-publisher.daisycon.com/) | Spécification OpenAPI 3.0 publiée ; opérations authentifiées par Bearer token, publisher ID et mécanisme de demande d'accès à certains services. Les conditions de service publishers sont référencées dans la spécification. | L'examen documentaire disponible n'a pas établi un flux français de produits telco avec prix, périodicité, engagement et allocations, ni les droits de stockage/réaffichage ou la fréquence de refresh. Aucun compte publisher, approbation de programme ou token Eparly n'est disponible. Coûts et opérateurs français accessibles restent inconnus. |
| [Tradedoubler Products API](https://dev.tradedoubler.com/products/publisher/) | API REST Products documentée en JSON/XML. Demande un compte publisher, un site, un token Products et un feed ID ; le texte dit que les produits deviennent accessibles après connexion aux annonceurs. Des filtres de langue, dates de modification et price history sont documentés. Les réponses peuvent exposer product/program, prix, URL et champs personnalisés, sans garantir que ces champs soient présents pour chaque annonceur. | La documentation décrit surtout des données produit génériques et n'établit pas un feed français de forfaits d'abonnement avec engagement, allocation mobile et tarif promotionnel structuré. Aucun compte, token, feed ID ou annonceur connecté n'est disponible. Conditions concrètes de licence, frais et autorisation d'archivage pour la comparaison Eparly doivent être confirmés dans les conditions du programme concerné. |

**Stratégie recommandée :** demander en premier lieu à Awin si un programme publisher
permettant la comparaison commerciale Eparly et un feed enhanced telco français
est effectivement accessible, avec autorisation écrite de conserver les champs
tarifaires et de les présenter hors du parcours d'affiliation. C'est le schéma le
plus proche du besoin mobile, mais il n'est pas démontré que des forfaits français
ou des prix EUR soient disponibles pour Eparly. Tradedoubler peut être étudié en
second choix si un annonceur FR d'abonnements confirme programme, champs et droits.
Daisycon ne peut pas être recommandé pour les forfaits tant qu'un endpoint/flux
produit correspondant n'a pas été identifié dans la documentation et l'accès
publisher. Aucun choix n'est une approbation de collecte.

| Fournisseur | URL candidate / résultat | Robots et accès | Contenu / faisabilité | Décision |
|---|---|---|---|---|
| Free Mobile | [mobile.free.fr](https://mobile.free.fr/) | `https://mobile.free.fr/robots.txt` autorise la racine `/` et `/shop` pour `User-agent: *`, publie un sitemap, et interdit certaines URLs de commande ou paramètres. | Page française officielle, contenu d'offres rendu dans le HTML accessible. Extracteur HTML ciblé techniquement possible, sous réserve d'une revue contractuelle, d'un fixture et de vérifications des conditions promotionnelles. | **En attente** : permission de collecte et de réutilisation non confirmée. |
| Sosh | [shop.sosh.fr/mobile](https://shop.sosh.fr/mobile) redirige vers `https://www.sosh.fr/`. | Le robots de `shop.sosh.fr` interdit notamment les tunnels, paniers et configurateurs. `https://www.sosh.fr/robots.txt` autorise les chemins (`Disallow:` vide), mais demande un délai d'exploration de 10 secondes. | La page finale observée est une coquille applicative qui charge le contenu par JavaScript ; l'extracteur actuel ne l'exécute pas. Le domaine final diffère du domaine initial et doit être déclaré explicitement avant tout fetch Eparly. | **Bloqué techniquement et en attente** : permission non confirmée ; données d'offre non présentes dans le HTML rendu observé. Aucun navigateur headless ni appel d'API privée ne sera ajouté pour contourner cette limite. |
| RED by SFR | [red-by-sfr.fr/forfaits-mobiles](https://www.red-by-sfr.fr/forfaits-mobiles/) | `https://www.red-by-sfr.fr/robots.txt` autorise ce chemin pour `User-agent: *` ; plusieurs chemins de panier, d'espace client et paramètres sont interdits. | Page française officielle accessible avec prix, formules et conditions visibles. Les tarifs présentés sont liés à une date de validité et certaines offres sont réservées aux nouveaux clients. Un extracteur HTML ciblé pourrait lire une page statique, mais une extraction correcte doit lier prix, formule et conditions correspondantes. | **En attente** : permission de collecte/réutilisation non confirmée ; pas de source enregistrée ou activée. |
| Spotify | [spotify.com/fr/premium](https://www.spotify.com/fr/premium/) | `https://www.spotify.com/robots.txt` autorise les chemins non spécifiquement interdits pour `User-agent: *` et annonce un sitemap. | Page française avec abonnements, essai promotionnel, tarif récurrent après essai, date d'expiration et conditions d'éligibilité. Les conditions d'abonnement payant et d'utilisation indiquent que les offres, prix et critères sont spécifiques au pays et à l'éligibilité. | **En attente** : les conditions examinées n'établissent pas une autorisation de collecte commerciale automatisée ou de réutilisation. Les données promotionnelles ne doivent pas être interprétées comme prix standard. |
| Netflix | [netflix.com/fr](https://www.netflix.com/fr/) | `https://www.netflix.com/robots.txt` applique `Disallow: /` à `User-agent: *`. Eparly ne fait pas partie des user-agents explicitement autorisés ; les exceptions pour d'autres robots ne s'appliquent pas. | Une lecture de recherche a retourné une page française, mais cela ne lève pas l'interdiction robots pour le collecteur Eparly. | **Refusé** : aucune requête de collecte Eparly ne doit viser cette source tant que la règle robots applicable n'autorise pas le chemin et que la permission de collecte n'est pas établie. |

Les observations ci-dessus sont un état ponctuel à la date indiquée, pas une
garantie de disponibilité ou de prix. Aucun montant aperçu lors de cette lecture
n'est copié dans le catalogue, une fixture de production ou une configuration.

## État du registre et résultats de collecte

- Sources réelles configurées : **0**.
- Sources activées : **0**.
- Run du batch contre un site tiers : **aucun**.
- Offres/prix observés et enregistrés par le batch : **0**.
- Candidats et doublons issus de sources réelles : **0**.
- Second passage : **sans objet**.
- Base PostgreSQL persistante locale : **non modifiée**.

La vérification contractuelle étant incomplète et le DNS pinning du fetcher venant
d'être renforcé, aucun flux réel n'a été activé pour ce jalon. Le registre de
production doit être alimenté via les routes réservées à `CATALOG_ADMIN`, après
validation humaine. Ne pas initialiser ces sources par insertion SQL ad hoc.

Un parseur ciblé `AwinTelcoCsvExtractor` a été ajouté pour le CSV enhanced telco
documenté. Il interprète les colonnes d'identité, `deal_cost_json`,
`deal_discounts_json`, `tariff_details_json`, `tariff_allowances_json`,
`network_details_json`, dates et conditions textuelles, sans inventer les champs
absents. Le prix mensuel remonté est une **observation brute** ; en présence de
promotion ou de conditions absentes, la normalisation impose `NEEDS_REVIEW`.
L'adaptateur est testé sur des lignes synthétiques conformes au schéma publié,
mais il n'est pas encore raccordé au downloader/orchestrateur : aucune clé Awin
ni source valide n'a été fournie. Cela évite de placer une API key (utilisée dans
les URLs de téléchargement Awin) dans `source_url` en base et d'activer par erreur
un feed sans autorisation. Il faudra une propriété de secret externe et une
construction de requête sans persistance du secret avant le raccordement.

## Sécurité du collecteur

Le client HTTP utilise Apache HttpClient avec un résolveur DNS dédié. Les adresses
résolues et effectivement remises au gestionnaire de connexions sont contrôlées
avant connexion ; elles ne sont pas résolues une seconde fois par le résolveur
standard du client. Les adresses privées, loopback, link-local, partagées,
multicast, réservées/non globales et les plages IPv6 non globales sont refusées.
Les redirections sont désactivées dans le client et suivies manuellement après
révalidation du domaine, du port et de la résolution. Hors tests, seul HTTPS sur
le port 443 est accepté. Les timeouts, taille de réponse, user-agent, délais,
retries, sitemap et limites de pages restent configurables.

Les tests HTTP locaux gardent une exception loopback uniquement dans le profil
`test` avec `allow-loopback-for-tests=true`. Cette exception n'est pas disponible
dans les profils normaux. En staging/production, appliquer en plus une règle
d'egress qui refuse les réseaux privés et les endpoints de métadonnées cloud.

Cette protection ne remplace ni l'autorisation contractuelle ni les règles de
sortie réseau du déploiement. Tout domaine de redirection doit être approuvé et
enregistré explicitement ; une redirection vers un domaine tiers est refusée par
défaut.

## Conditions pour accepter une source

Pour chaque fournisseur, enregistrer avant activation :

1. L'URL et le domaine canoniques, après vérification des redirections.
2. Les règles robots applicables au user-agent Eparly, y compris au chemin final
   et aux éventuels fichiers de sitemap.
3. La clause contractuelle ou l'autorisation écrite couvrant la collecte
   automatisée et l'usage des prix/caractéristiques dans Eparly. En cas de doute,
   conserver le statut `PENDING` et demander une revue juridique.
4. Les limites de débit, le contact technique et les données de source strictement
   nécessaires.
5. Un fixture synthétique reproduisant la structure du contenu autorisé, avec
   tests d'extraction des prix, périodes, promotions, éligibilité et frais.
6. La validation de la source par `CATALOG_ADMIN` avec une justification. N'activer
   qu'après cette revue.

Le fetcher générique sait lire les flux JSON, JSON-LD, HTML ciblé et sitemaps.
Le parseur Awin CSV est actuellement une brique testée mais non activée dans le
pipeline. Les formats gzip Awin nécessiteront aussi une décompression bornée,
et les champs telco/monnaie devront être vérifiés à partir d'un feed réellement
autorisé avant d'être normalisés en offre comparable.
Pour Sosh, une page dépendant de JavaScript n'est pas prise en charge : ne pas
appeler un endpoint interne découvert dans les scripts sans autorisation et
validation distinctes. Les caractéristiques complexes doivent rester dans leur
description ou `NEEDS_REVIEW` tant qu'un extracteur fiable ne les mappe pas au
modèle intermédiaire.

## Catalogue de démarrage par administration

En attendant des droits de flux, l'équipe peut alimenter le catalogue par les
routes existantes `/v1/admin/catalog/**` : créer le fournisseur, créer une offre
en brouillon, enregistrer le tarif V18, ajouter une preuve officielle `PENDING`,
puis faire vérifier cette preuve par un administrateur après consultation. La
publication reste une action distincte et protégée. Les routes et corps JSON sont
documentés dans `catalog-admin-api.md`. Il n'existe pas de point d'entrée de
chargement massif CSV/JSON ; ne pas transformer un export non vérifié en source
automatique. Pour un premier lot, utiliser ces endpoints de façon unitaire ou
orchestrer un import interne restreint qui appelle exactement ce workflow,
journalise l'acteur, refuse les doublons, et conserve les erreurs par ligne.
Un tel import serait une saisie administrative vérifiée, pas une collecte
nocturne ni une validation humaine implicite.

## Activation nocturne

Après approbation et configuration d'au moins une source par les routes sécurisées :

```text
CATALOG_SYNC_ENABLED=true
CATALOG_SYNC_CRON=0 0 4 * * *
CATALOG_SYNC_ZONE=Europe/Paris
CATALOG_SYNC_CONTACT=<contact technique public>
CATALOG_SYNC_MAX_EXECUTION_DURATION=PT10M
```

Le déploiement doit disposer d'une instance active à l'heure prévue, des migrations
Flyway appliquées et d'un réseau sortant limité. Le verrou PostgreSQL évite que
plusieurs instances collectent simultanément. Conserver `CATALOG_SYNC_ENABLED=false`
dans les environnements sans sources approuvées. Consulter les bilans et erreurs
par les endpoints admin après un premier run contrôlé. Les observations restent
séparées du catalogue publié ; tout changement commercial requiert la revue
humaine habituelle.
