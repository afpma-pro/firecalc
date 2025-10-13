<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

## Compile

```sbt compile```
```sbt ~engine/compile```
```sbt ~engineJS/compile```


## Run

```sbt engine/run```

## Run and Watch

```sbt ~engine/run```

## PublishLocal

```sbt engine/publishLocal```

## Testing

```
sbt "~engine/test"
sbt "~engine/testOnly *InterpolationSuite"
sbt "~engine/testOnly *strict_p1_decouverte_Suite"
sbt "~engine/testOnly *strict_p5_appl_Suite"
sbt "~engine/testOnly *mce_p1_decouverte_Suite"
sbt "~engine/testOnly *ex01_colonne_ascendante_Suite"
sbt "~engine/testOnly *velocity_limits_Suite"
sbt "~engine/testOnly 
sbt "~engine/testOnly *ThermalResistance_Suite"
sbt "~engine/testOnly *labo_Suite"
sbt "~engine/testOnly *cas_types_15544_v20241001_Suite"

sbt "~engine/testOnly *cas_types_13384_C2_Suite"
sbt "~engine/testOnly *cas_types_13384_C16_Suite"

```

## Scalafix

```sbt "engine / scalafix"```

```sbt "engine / scalafix RemoveUnused"```