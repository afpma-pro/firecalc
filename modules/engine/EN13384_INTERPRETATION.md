<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# INTERPRETATIONS possibles vis à vis de EN13384

Lors de l'implémentation de la norme, plusieurs points ont amené à des questionnements ou interprétations particulières qui demanderaient à être clarifiées dans une prochaine révision.

Celles-ci sont recensées dans ce fichier dans un souci de transparence et de documentation de cette implémentation du moteur de calcul EN 13384.

Des commentaires avec la chaine de caractères ```// __INTERPRETATION__``` ont été ajoutés dans le code afin de mettre en évidence ces potentielles divergences.

## Tableau B.8

Comment faire quand tronçons courts s'enchainent ? L/Dh < 2 ?

### Decisions taken

- ratio ```Ld/Dh``` is assumed to be valid if ```0 <= Ld / Dh < 30``` using ```AngleVifDe0A90_Unsafe```, ```CoudeCourbe90_Unsafe```, ```CoudeCourbe60_Unsafe```
- use `table-B8_shape2_updated.csv` instead of `table-B8_shape2.csv`
- use `table-B8_shape3_updated.csv` instead of `table-B8_shape3.csv`
- `CoefficientOfForm` is supposed to be `1.10` even if `side ratio > 1.5` (see `EN13384-1-A1:2019 Appendix A`)

### Tableau B.6

Comment calculer la température de la surface émettrice ?
Hypothèse : temp surface émettrice = temp moyenne gas

### 5.8.3.3

Calcul RTH + tableau interpolation avec valeurs manquantes.
Va être remplacée par formule.

Voir projet prNF-EN-13384:2025

### Quelle coefficient de forme quand tubage carré / rect et conduit rond ?

=> Coefficient de forme choisi est celui de la forme "externe"

### Lame d'air selon DTU 24.1 ne sont pas considérées comme ventilées au sens de la norme EN13384

cf rapport CSTB


## Définir une lame d'air ventilée, ou non ventilée.

- quelle épaisseur min max ?
- quelles ouvertures en haut et en bas ?

Selon Rapport CSTB : 
- lame d'air selon DTU 24.1 = statique.
- lame d'air tout ouvert = ventilée à 5W (et non 8W)
