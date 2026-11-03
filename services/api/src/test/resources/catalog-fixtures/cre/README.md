# Extraits CRE

Extraits non modifiés des fichiers open data de la Commission de régulation de l'énergie,
récupérés le 1er novembre 2026 (`Last-Modified: Wed, 26 Aug 2026`) :

- https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_Base.csv
- https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_HPHC.csv
- https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_Tempo.csv

Chaque extrait contient l'en-tête, les deux premières lignes historiques et les lignes alors
en vigueur (`DATE_FIN` vide). Le fichier Tempo conserve la ligne réelle à prix vides.
Schéma : https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Notice.txt

Ces montants servent à tester le connecteur. Ils ne sont ni un catalogue ni un tarif à jour.
Les scénarios de changement de prix des tests dérivent de ces lignes et sont synthétiques.
