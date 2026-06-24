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
sbt "engine-15544-strict/test"
sbt "engine-kernel/testOnly *InterpolationSuite"
sbt "engine-15544-strict/testOnly *strict_p1_decouverte_Suite"
sbt "fdim/testOnly *strict_p5_appl_Suite"
sbt "engine-15544-mce/testOnly *mce_p1_decouverte_Suite"
sbt "fdim/testOnly *ex01_colonne_ascendante_Suite"
sbt "engine-15544-strict/testOnly *velocity_limits_Suite"
sbt "engine-15544-strict/testOnly *ThermalResistance_Suite"
sbt "labo/testOnly *labo_Suite"
sbt "engine-15544-strict/testOnly *cas_types_15544_v20241001_Suite"
sbt "engine-13384-strict/testOnly *cas_types_13384_C2_Suite"
sbt "engine-13384-strict/testOnly *cas_types_13384_C16_Suite"
```

## Scalafix

```sbt "engine / scalafix"```

```sbt "engine / scalafix RemoveUnused"```