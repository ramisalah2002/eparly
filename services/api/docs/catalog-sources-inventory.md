# Inventaire des sources de collecte

État au 2 novembre 2026, établi à partir des documents officiels consultés. « Non établi » signifie que
le document n'a pas pu être lu ou ne le précise pas : ce n'est ni un accord ni un refus. Une classification
technique ne remplace pas la revue juridique. Aucune source n'est activée par défaut.

## Connexions réelles au 2 novembre 2026

- **Actives et collectées** : CRE (3 fichiers, électricité), Savoir Placer (banque, Licence Ouverte), MicroBoussole
  (offres gratuites de facturation électronique, Licence Ouverte).
- **Licence ouverte mais bloquées par robots.txt** : Île-de-France Mobilités et Naolib (`/api/` interdit hors robots
  nommés), La Bonne Séance sur `static.data.gouv.fr` (`/resources` interdit). Connecteurs écrits, sources désactivées :
  demander l'accord des éditeurs.
- **Écartées** : TCL (401), Twisto (licence non précisée, tarifs datés de 2024), Modulyx et Facture Futée (prix en texte
  libre), identifiants absents pour Tradedoubler, CJ et Admitad, Selectra payant.

## Synthèse

| Source | Statut | Catégories | Accès | Connecteur |
|---|---|---|---|---|
| CRE, tarifs réglementés d'électricité | **AVAILABLE**, usage commercial à confirmer | Électricité | Public, sans clé | Implémenté, vérifié en réel |
| CRE, tarifs réglementés de gaz | **UNSUITABLE** | Gaz | Public, sans clé | Aucun |
| Tradedoubler Products API | **REQUIRES_CREDENTIALS**, **REQUIRES_APPROVAL** | Selon annonceurs | Compte éditeur, jeton, feed ID | Implémenté, jamais exécuté en réel |
| CJ Product Feed API | **UNKNOWN** | Non établi | Non établi | Aucun |
| Admitad Coupons API | **UNKNOWN** | Coupons, pas des tarifs | Jeton, compte éditeur | Aucun |
| Selectra API | **PAID** | Électricité, gaz | Abonnement payant | Aucun |
| Module du comparateur du médiateur de l'énergie | **UNSUITABLE** | Électricité, gaz | Module à intégrer | Aucun |
| Comparateur public des tarifs bancaires (CCSF) | **UNKNOWN** | Banque | Aucun export identifié | Aucun |
| Awin | Exclu sur décision projet | Télécom | Non réévalué | Parseur non raccordé |

## CRE : tarifs réglementés d'électricité

- **Documentation** : [jeu de données data.gouv.fr](https://www.data.gouv.fr/api/1/datasets/5d31900f8b4c41338237ac8f/),
  [Notice](https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Notice.txt),
  [page open data de la CRE](https://cre.fr/en/documents/open-data.html).
- **Données** : CSV par option (Base, HP/HC, Tempo) : dates de validité, puissance, abonnement annuel et prix
  du kWh, HT et TTC. Vérifié le 1er novembre 2026 : 22 tarifs en vigueur depuis le 1er août 2026.
- **Gratuité, clé, quotas** : gratuit, sans inscription ni clé. Aucun quota publié. `robots.txt` n'interdit pas
  ces fichiers. `Last-Modified` est fourni et le serveur répond bien 304.
- **Actualisation** : déclarée semestrielle sur data.gouv.fr ; dernière mise à jour le 26 août 2026.
- **Droits** : licence `notspecified` sur data.gouv.fr. La CRE parle de « jeux de données réutilisables
  librement » et ses [mentions légales](https://cre.fr/en/legal-information-and-privacy-policy-french.html)
  autorisent la reproduction si l'intégrité des documents est respectée, avec source, date et lien lisible.
  La réutilisation commerciale n'y est pas traitée explicitement.
- **Décision à prendre** : confirmer l'usage commercial et le fait que la normalisation respecte la condition
  d'intégrité. Toute restitution devra citer la source, la date et le lien.
- **Adéquation** : forte comme tarif de référence, mais ce n'est pas un catalogue des offres de marché.
  Le fichier ne nomme pas de fournisseur : le rattachement se fait à la revue.

## CRE : tarifs réglementés de gaz

[Jeu de données](https://www.data.gouv.fr/api/1/datasets/5d31972d8b4c415f8db930cf/) non mis à jour depuis
mars 2024, fichiers XLSX couvrant 2013-2022. Inadapté à une collecte quotidienne de tarifs en vigueur.

## Tradedoubler Products API

- **Documentation** : [dev.tradedoubler.com/products/publisher](https://dev.tradedoubler.com/products/publisher/),
  [contrat éditeur FR](https://publishers.tradedoubler.com/files/legal/fr-publisher-agreement.pdf).
- **Données** : produits en JSON, XML ou CSV : nom, offres, historique de prix, URL. Seuls les champs marqués
  obligatoires sont garantis. Le schéma documenté n'a ni périodicité, ni engagement : ces offres restent en revue.
- **Accès** : compte éditeur, jeton Products, feed ID. Les produits deviennent accessibles après connexion aux
  annonceurs.
- **Limites documentées** : 1 000 produits par recherche ; service Unlimited : 3 téléchargements par version de
  fichier sur 24 h, puis HTTP 429. Limite de débit de la recherche : non établie.
- **Droits** : le service Unlimited est présenté comme destiné au stockage chez l'éditeur, mais l'article 16 du
  contrat éditeur FR n'accorde pas d'autre droit que celui de placer des liens selon ses termes. Un accord écrit
  couvrant le stockage et la comparaison est donc nécessaire avant tout usage.
- **Adéquation** : incertaine. La documentation décrit surtout des produits, et la présence d'annonceurs français
  d'abonnements n'est pas établie.
- **Point à vérifier avec un compte réel** : la pagination. La formule `limit = pageSize*page+pageSize` suggère une
  première page d'indice 0, ce que la documentation ne confirme pas explicitement.

## CJ Product Feed API

Le [portail développeur](https://developers.cj.com/) n'a pas pu être lu (contenu chargé par JavaScript). Seules des
sources tierces, contradictoires, décrivent endpoint et authentification : elles ne sont pas retenues. Aucun
connecteur n'est écrit pour ne pas inventer de contrat d'API.

## Admitad Coupons API

La documentation officielle redirige vers [developers.mitgo.com](https://developers.mitgo.com/), qui a répondu 403 à
la lecture automatique. Un extrait indexé confirme des endpoints de coupons avec jeton Bearer. Portées, quotas et droits :
non établis. Un coupon n'est pas un tarif d'abonnement : au mieux une source complémentaire de promotions.

## Selectra API

D'après [sa page](https://selectra.info/energie/electricite/prix/api) : « 700 € HT par mois » et « 0,01 € HT par
requête », sans offre gratuite pour cette API. La couverture annoncée (offres d'électricité et de gaz, mise à jour
quotidienne) est la plus pertinente pour l'énergie, mais la source est **exclue tant qu'aucune dépense n'est décidée**.
Un autre extrait trouvé mentionne 400 € : le tarif est à confirmer auprès de Selectra.

## Comparateurs publics

- **[Médiateur national de l'énergie](https://comparateur-offres.energie-info.fr/CGV_outil_exportable_energie_info.pdf)** :
  les conditions de novembre 2025 prévoient un module gratuit et temporaire, sans démarche commerciale associée,
  et interdisent notamment de céder ou prêter les données. Il ne constitue pas une source de collecte. Aucune API ni
  donnée ouverte n'a été identifiée : ne pas extraire le comparateur, mais demander au médiateur si un accès existe.
- **[Comparateur CCSF](https://www.tarifs-bancaires.gouv.fr/)** : aucun export ouvert identifié.

## Priorités recommandées

1. Faire confirmer l'usage de la CRE, puis activer les trois fichiers.
2. Écrire au médiateur de l'énergie : c'est la meilleure source potentielle non payante pour les offres de marché.
3. Ouvrir un compte Tradedoubler seulement après avoir confirmé par écrit le droit de stocker et comparer les données,
   et la présence d'annonceurs français d'abonnements.
4. Chercher des flux officiels publiés par les fournisseurs : le lecteur JSON générique les prend déjà en charge.
5. Réévaluer Selectra si la couverture énergie justifie son coût.

La collecte ne sera pas exhaustive : elle couvre seulement les sources autorisées et configurées.
