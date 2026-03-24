# Comment fonctionne la direction des tuyaux en 3D

## L'idee principale

Imagine que tu es une fourmi qui marche a l'interieur d'un tuyau de poele.
Tu avances tout droit, et parfois le tuyau tourne. Pour decrire ton chemin
a quelqu'un, tu as besoin de deux informations a chaque virage :

1. **De combien tu tournes** : l'angle du coude (45°, 60°, 90°...)
2. **Vers ou tu tournes** : a gauche, a droite, vers le haut, vers le bas...

Le numero 1, on l'avait deja. Le numero 2, c'est le **roulis** : la nouveaute.

## Le roulis (roll)

Le roulis, c'est comme une horloge placee a la sortie du tuyau,
face a toi. Si tu regardes dans le tuyau :

- **0°** = le coude va vers l'arriere (derriere le poele)
- **90°** = le coude va vers la droite
- **180°** = le coude va vers l'avant (devant le poele)
- **270°** = le coude va vers la gauche

```text
        0° (arriere)
         |
270° ---+--- 90°
(gauche) |   (droite)
        180° (avant)
```

### Boutons presets dans l'interface

Pour chaque element de changement de direction (coude, courbe, etc.),
le champ **roulis** affiche quatre boutons :

```
[ 0° ]  [ 90° ]  [ 180° ]  [ 270° ]  [ … ]
```

- Clique sur un bouton pour selectionner cet angle de roulis.
- Reclique sur le meme bouton pour deselectionner (aucun roulis = comportement identique a avant).
- Le bouton **…** (Custom) ouvre un champ numerique pour entrer
  un angle libre.

Si aucun bouton n'est actif, le champ `roll` reste a `None` :
le logiciel ne suit pas la direction en 3D pour cet element
(comportement identique aux projets existants).

## La direction de depart

Avant de decrire les virages, il faut dire dans quelle direction le tuyau
commence. C'est comme donner un point de depart sur une boussole :

- **Azimut** : la direction horizontale (0° = arriere, 90° = droite)
- **Inclinaison** : l'angle par rapport au sol (0° = horizontal, 90° = vertical)

Exemple : un tuyau de fumee qui demarre droit vers le haut a une
inclinaison de 90°.

## Exemples simples

### Tuyau vertical avec un coude vers l'arriere

```text
Direction initiale : inclinaison = 90° (vertical)

1. Section verticale (2 metres)     → le tuyau monte tout droit
2. Coude 90°, roulis = 0°          → le tuyau tourne vers l'arriere
3. Section horizontale (1 metre)    → le tuyau va vers l'arriere
```

### Tuyau horizontal avec un coude vers la gauche

```text
Direction initiale : azimut = 0°, inclinaison = 0° (horizontal vers l'arriere)

1. Section horizontale (1 metre)    → le tuyau va vers l'arriere
2. Coude 90°, roulis = 270°        → le tuyau tourne a gauche
3. Section horizontale (0.5 metre)  → le tuyau va vers la gauche
```

### Deux coudes qui se suivent

```text
1. Section verticale (1 metre)      → le tuyau monte
2. Coude 45°, roulis = 0°          → le tuyau penche vers l'arriere
3. Section en pente (0.5 metre)     → le tuyau va en diagonale
4. Coude 30°, roulis = 90°         → le tuyau tourne a droite
5. Section en pente (1 metre)       → le tuyau continue en diagonale
```

## Affichage de la direction dans la liste

Apres chaque element du tuyau, un petit badge indique la direction
courante du tuyau apres cet element. L'affichage suit une hierarchie
a trois niveaux :

1. **Nom cardinal** quand la direction est exactement selon un axe :
   ex. `Haut`, `Arriere`, `Gauche`
2. **Cardinal horizontal + elevation** quand la direction horizontale
   est exacte mais avec un angle vertical :
   ex. `Arriere ↑45°`, `Droite ↓15°`
3. **Azimut + elevation** pour toutes les autres directions :
   ex. `az:135° el:30°`

Note : le badge n'apparait que si le logiciel suit la direction en 3D
(c'est-a-dire si au moins un roulis a ete specifie). Il reste vide
pour les elements sans suivi de direction.

## Avantage

Avant, l'utilisateur devait calculer a la main l'angle par rapport a
la direction d'origine. Maintenant, l'ordinateur le fait tout seul
grace au roulis. Il suffit de dire "je tourne a gauche de 90 degres"
et le logiciel sait exactement ou va le tuyau.
