# Worker Context: Generic Pipe Topology Refactor

## Project
Scala 3 full-stack masonry heater dimensioning software (EN 15544, EN 13384).
sbt build, 21 modules. Engine module is cross-compiled (JVM + JS).

## License Header (REQUIRED on all new files)
```scala
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
```

## Compilation
Primary: Use firecalc-metals MCP server (`compile-file` or `compile-module` tools).
Fallback: `LANG=en_US.UTF-8 sbt 'engine/compile'`

## Key existing types and their locations
- `PipeDescrAlg` (trait with `type PipeElDescr`): `models/PipeDescrAlg.scala`
- `PipeFullDescrG[PipeElDescr]`: `models/PipeIncrAndFullDescrG.scala`
- `NamedPipeElDescrG[PipeElDescr]`: `models/PipeIncrAndFullDescrG.scala`
- `PipeResult` (sealed trait): `models/results.scala`
- `PipeResult.useless(pt, gas_temp)`: factory for noop results
- `PipeType` (sealed trait): `models/PipeType.scala` — `FluePipeT`, `ConnectorPipeT`, `ChimneyPipeT`
- `Gas` (sealed trait): `models/Gas.scala` — `FlueGas`, `CombustionAir`
- `MecaFlu_Error`: `standard.scala`
- `Params_13384 = (DraftCondition, LoadQty)`: `alg/en13384/en13384_application_alg.scala`
- `ComputeAt` (enum Mean|Middle): `impl/en13384/en13384_common_application.scala`
- `FlowOnlyPipeDescr_15544` (object extends PipeDescrAlg): `models/en15544/FlowOnlyPipeDescr_15544.scala`
- `ThermalPipeDescr_13384` (object extends PipeDescrAlg): `models/en13384/ThermalPipeDescr_13384.scala`
- `FlowOnlyPipeDescr_13384` (object extends PipeDescrAlg): `models/en13384/FlowOnlyPipeDescr_13384.scala`
- `ShortSectionAlg` (trait): `models/en15544/shortsection.scala`
- `HeatingAppliance.FlueGas/MassFlows/Powers/Efficiency`: `models/en13384/std.scala`
- `EN15544_Strict_Application`: `impl/en15544/strict/en15544_strict_application.scala`
- `EN13384_1_A1_2019_Application_Alg`: `alg/en13384/en13384_application_alg.scala`
- `z_geodetical_height`: `models/gtypedefs.scala`
- `TCelsius`, `Density`, `FlowVelocity`: `units/coulombutils`
- `PipesResult_15544`: `models/results.scala`
- `PipesResult_13384`: `models/results.scala`

All paths relative to `modules/engine/src/main/scala/afpma/firecalc/engine/`

## makePipeResult signatures (the delegate targets)

### FlowOnlyMecaFlu_15544.makePipeResult
```scala
def makePipeResult(
    fd: PipeFullDescrG[PipeElDescr], gas: Gas, loadQty: LoadQty,
    z_geodetical_height: z_geodetical_height, params: DraftCondition
)(using alg: ApplicationAlg, ssa: ShortSectionAlg): Either[MecaFlu_Error, PipeResult]
```

### ThermalMecaFlu_13384.makePipeResult
```scala
def makePipeResult(
    fd: PipeFullDescrG[PipeElDescr], hafg: HeatingAppliance.FlueGas,
    hamf: HeatingAppliance.MassFlows, hapwr: HeatingAppliance.Powers,
    haeff: HeatingAppliance.Efficiency, temp_start: TCelsius,
    last_pipe_density: Option[Density], last_pipe_velocity: Option[FlowVelocity], gas: Gas
)(using params: Params_13384, alg: EN13384_1_A1_2019_Application_Alg): Either[MecaFlu_Error, PipeResult]
```

### FlowOnlyMecaFlu_13384.makePipeResult
```scala
def makePipeResult(
    fd: PipeFullDescrG[PipeElDescr], hafg: HeatingAppliance.FlueGas,
    hamf: HeatingAppliance.MassFlows, hapwr: HeatingAppliance.Powers,
    haeff: HeatingAppliance.Efficiency, temp_start: TCelsius,
    last_pipe_velocity: Option[FlowVelocity], gas: Gas
)(using params: Params_13384, alg: EN13384_1_A1_2019_Application_Alg): Either[MecaFlu_Error, PipeResult]
```
