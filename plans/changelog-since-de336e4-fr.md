# Journal des modifications depuis `de336e4`

## Moteur de calcul

### Nouvelles fonctionnalités
- Refonte des foyers — typeclasses de contraintes, foyer testé unitairement (type 15a), correctif du crash d'égalité
- Table TSV de pertes de charge avec interpolation bilinéaire (`readSingle`, `readAll`)
- Suivi de direction 3D avec angle de roulis et repère de conduit
- Suivi de position avec coordonnées XYZ par segment de conduit
- Suivi de direction pour les descriptions de conduits FlowOnly
- Erreur de validation lorsque `finalDir` est défini sans `SetInitialDirection`
- Remplacement de `expectedAirIntakePipeShape` par une liste de formes attendues + forme réelle
- Généralisation de `PipeFrame` pour des angles de déviation arbitraires
- Remplacement de `roll` par `finalDir` dans les DTOs, DSL, builders, factories, cas types et tests
- Extension de la direction relative à 4 quadrants ; suppression de `elevation_gain` des sections inclinées
- Début de dépréciation du type de section horiz/vert au profit du suivi de direction
- Autorisation du foyer AFPMA_PRSE sans valeur d'émissions (affiche « non respecté » dans la note PDF)

### Corrections de bugs
- Correction du bug de calcul `angleN2` (problème d'état du repère courant)
- Arrondi des composantes vec3 après trigonométrie pour éliminer le bruit en virgule flottante
- Re-normalisation de `upref` du repère de conduit selon la convention gravitaire après chaque coude
- Correction du cas_type 15544 (mauvais angle de roulis)
- Correction de l'ordre des conduits dans la validation moteur 13384 C16
- Autorisation du gain d'élévation manuel pour les cas_types 13384 C16 (conduit de raccordement)
- Correction de l'incohérence d'étiquettes entre AF et X
- Déduplication de la normalisation TSV, `InterpolationError.ValueOutOfRange` yi rendu optionnel
- Utilitaire Log multiplateforme, journalisation des erreurs d'interpolation, suppression de branche morte, ajout de tests sur les chemins d'erreur
- Héritage correct du type d'appareil depuis le DTO, application des contraintes granulés sur `t_BU`

### Refactoring
- Introduction du trait interne `AtParams` pour l'application EN 15544
- Renommage de `EcoLabeled` en `Ecolabeld`, correction du dispatch de type de foyer
- Organisation des types de foyers dans un package dédié avec des noms propres
- Intégration des valeurs SB cm dans les en-têtes TSV, suppression du paramètre `measured_sb_values`
- Forme intérieure forcée à disponible pour PressureDiff, correction du signe
- Ajout d'une fonction utilitaire pour `TSVTableString`
- Centralisation de l'héritage du repère de conduit via les builders `PipeChain`
- Ajout du paramètre roll au DSL `DirectionChange`, correction de la confusion `angleN2`/roll

## DTO

### Nouvelles fonctionnalités
- Ajout des enums `FinalDirection` (`AzimuthDirection` + `InclinationDirection`)
- Ajout de `SetPropertiesInBatch` au schéma `SetThermalPipeProp_13384_V3`

### Corrections de bugs
- Réordonnancement des codecs de direction avant les dérivations de traits scellés pour corriger le `ClassCastException`
- Azimut rendu optionnel pour les directions verticales, correction du write-back de signal de la viz

### Refactoring
- Unification des émissions SingleTested avec `EmissionsAndEfficiencyValues_DTO`
- Renommage de `pn_reduced` en `heat_output_reduced` dans `Door15aFirebox_Catalog`
- Suppression des valeurs par défaut pour le foyer door15a
- Utilisation d'objets compagnons pour stocker les versions de schéma

### Tests
- Ajout d'un test de régression YAML aller-retour pour `FinalDirection` via le pipeline V4
- Ajout de la couverture du format V4 aux suites de tests ; correction du codec null `AirSpaceDetailed` YAML

## Interface utilisateur

### Nouvelles fonctionnalités
- **Annuler/Rétablir** : Annuler/rétablir en mémoire avec raccourcis clavier et boutons dans la barre de navigation
- **Catalogue** : Composant « Sélectionner depuis le catalogue » pour charger les propriétés de conduit depuis la base de données
- **Visualisation 3D** : Boîte de distribution d'air sous le foyer, conduits colorisés, positionnement avec décalage, état de caméra persistant
- **Saisie de direction** : Dialogue de direction personnalisé, refonte avec labels empilés et points cardinaux composés, `DirectionBadgeComponent` avec notation fléchée
- **Panneaux de conduit** : Séparation des propriétés en position/direction et matériau/rugosité
- **Support de position** : Interface complète pour `SetInitialPosition` / `SetFinalPosition` dans les panneaux 13384 et conduit de fumée
- **Graphiques** : Pression, gain d'élévation, température et vitesse
- **Mode expert** : En-têtes de tableau déplacés dans les titres d'accordéon collants
- **Foyer testé unitairement** : Nouvelle catégorie de catalogue avec import/export xlsx
- Saisie de direction relative gauche/droite avec rotation theta
- Remplacement de `roll` par `finalDir` dans tous les composants et panneaux UI
- Correction de la synchronisation avant avec synchronisation en cascade de la direction de coude lors d'un changement de repère amont incompatible, badge d'avertissement

### Corrections de bugs
- Correction du crash `None.get` lors du changement de méthode de dimensionnement ; report des valeurs calculées
- Calcul correct de `m_B` et `P_n` dans le panneau d'en-tête des paramètres du poêle
- Découplage de `mb_min`/`mb_max` de la synchronisation `stove_params` dans le formulaire catalogue door15a
- Traduction du bouton de sélection et correction de la zone de clic morte dans l'accordéon du formulaire par lot
- Correction de la réinitialisation de vue viz, canevas responsive et labels de boutons i18n
- Propagation du repère de direction aux panneaux raccordement et cheminée
- Suppression de `horizontal_form_Option_Angle` obsolète, mise à jour de `CURRENT_SCHEMA_VERSION`
- Renommage de `FinalDirection` en `AbsoluteDirection`

### Refactoring
- Extraction d'un `CatalogSelectDialog` générique, suppression de `hasMatchVar` redondant

### Style
- Réorganisation des groupes d'icônes de la barre de navigation avec des espaceurs flex
- Indentation de 20px des éléments non-propriété dans les panneaux de conduit

## Visualisation

### Nouvelles fonctionnalités
- Ajout du sous-module de visualisation 3D (Three.js)
- Disposition 2/3 panneaux + 1/3 viz pour la vue 3D
- Traduction des labels de gizmo et des annotations (i18n)

## Catalogue

### Nouvelles fonctionnalités
- Module catalogue indépendant : gestion d'un ou plusieurs catalogues avec import d'entrées
- Entrées de catalogue de résistances d'écoulement prédéfinies avec intégration au menu des panneaux
- Module `xlsx_catalog` pour l'import/export de catalogue au format Excel

### Corrections de bugs
- Renforcement de la gestion d'erreurs du parseur, sections typées, vérification d'unicité du registre

## I18n
- Ajout des clés de traduction `final_direction`
- Correction de problèmes de traduction et clés manquantes

## Paiements
- Mise à jour des données de test et régénération de la ressource base64

## Build / Outillage
- Ajout des prérequis de build graph et viz aux cibles de production de l'UI web
- Suppression des en-têtes de licence pour les fichiers `.falc`
- Blocage de l'injection d'en-têtes de licence dans les fichiers JSON des dossiers cachés
- Ajout du mode `--client` aux scripts sbt-compile pour la réutilisation du serveur Metals
- Augmentation du délai d'attente maximum à 6 min pour le script de vérification de compilation sbt
- Migration de roll vers finalDir dans les modules labo et fdim

## Divers
- Mises à jour multiples de la version de Scala Metals
- Arrêt du serveur Bloop à la fermeture de VSCode
- Suppression des imports inutilisés
