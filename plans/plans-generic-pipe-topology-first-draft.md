# Generic Post-Firebox Pipe Chain — Typeclass-Based Refactor

## Context

The engine currently has a **fixed 6-pipe topology**: airIntake → combustionAir → firebox → flue → connector → chimney. The post-firebox pipes (flue/connector/chimney) are hardcoded with specific types per application variant (Strict vs MCE). Three incompatible `makePipeResult` signatures exist across `FlowOnlyMecaFlu_15544`, `ThermalMecaFlu_13384`, and `FlowOnlyMecaFlu_13384`, and `makePipeResult` is not part of any abstract interface (commented out in `MecaFluAlg`).

**Goal**: Replace the fixed post-firebox pipes with a generic `Vector[ComputablePipe]` using a **typeclass-based Strategy Pattern** in Scala 3, where `CanComputePipeResult[D, Env]` encodes algorithm–description compatibility at the type level.

**Scope**: Full stack (engine + DTO + UI). Both EN15544 and EN13384 standalone generalized.

---

## Architecture: Typeclass Design

### Core Typeclass: `CanComputePipeResult[D, Env]`

Two type parameters:
- `D <: PipeDescrAlg` — the pipe description algebra singleton type (e.g., `ThermalPipeDescr_13384.type`)
- `Env <: PipeComputeEnv` — the application environment (carries standard-specific params)

```scala
trait CanComputePipeResult[D <: PipeDescrAlg, Env <: PipeComputeEnv]:
    val descrAlg: D
    def needsUpstreamTemp: Boolean
    def computePipeResult(
        fd      : PipeFullDescrG[descrAlg.PipeElDescr],
        gas     : Gas,
        upstream: Option[UpstreamState],
        env     : Env
    ): Either[MecaFlu_Error, PipeResult]
```

**Why this shape**: `D` as singleton type allows path-dependent `descrAlg.PipeElDescr`. `Env` selects the standard. Upstream state is separate (varies per-pipe, not per-application).

### Upstream State (propagated between pipes)

```scala
case class UpstreamState(
    temp_start        : TCelsius,
    last_pipe_density : Option[Density],
    last_pipe_velocity: Option[FlowVelocity]
)
```

Extracted from `PipeResult` using `ComputeAt` (Mean vs Middle) — reifies the existing ad-hoc propagation in `en13384_common_application.scala:107-155`.

### Environment Types

```scala
sealed trait PipeComputeEnv

case class EN15544StrictEnv(
    z_geodetical_height, loadQty, draftCondition,
    en15544App: EN15544_Strict_Application,
    shortSectionAlg: ShortSectionAlg
) extends PipeComputeEnv

case class EN13384Env(
    hafg, hamf, hapwr, haeff,  // HeatingAppliance.*
    params: Params_13384,
    en13384App: EN13384_1_A1_2019_Application_Alg
) extends PipeComputeEnv
```

### Given Instances (3 total, adapters to existing MecaFlu objects)

| Given | D | Env | Delegates to |
|-------|---|-----|-------------|
| `flowOnly15544` | `FlowOnlyPipeDescr_15544.type` | `EN15544StrictEnv` | `FlowOnlyMecaFlu_15544.makePipeResult` |
| `thermal13384` | `ThermalPipeDescr_13384.type` | `EN13384Env` | `ThermalMecaFlu_13384.makePipeResult` |
| `flowOnly13384` | `FlowOnlyPipeDescr_13384.type` | `EN13384Env` | `FlowOnlyMecaFlu_13384.makePipeResult` |

### ComputablePipe (Existential Wrapper with Closure-Captured Env)

**Critical design decision**: EN15544 Strict has mixed environments (flue uses `EN15544StrictEnv`, connector/chimney use `EN13384Env`). The environment is captured at construction time via closure, yielding a uniform `Vector[ComputablePipe]`.

```scala
trait ComputablePipe:
    val pipeType: PipeType      // existing PipeType enum for gas discrimination + boundary ID
    val label   : String        // user-visible label
    val gas     : Gas
    def needsUpstreamTemp: Boolean
    def compute(upstream: Option[UpstreamState]): Either[MecaFlu_Error, PipeResult]

object ComputablePipe:
    def apply[D0 <: PipeDescrAlg, Env0 <: PipeComputeEnv](
        pipeType, label, gas,
        fullDescr: PipeFullDescrG[D0#PipeElDescr],
        env: Env0
    )(using tc: CanComputePipeResult[D0, Env0]): ComputablePipe = ...
```

### GenericPipeChain (Sequential Fold)

```scala
case class GenericPipeChain(pipes: Vector[ComputablePipe]):
    def computeAll(
        initialUpstream: Option[UpstreamState],
        computeAt: ComputeAt
    ): Either[MecaFlu_Error, Vector[PipeResult]]
    // foldLeft propagating UpstreamState.fromPipeResult between pipes
```

---

## Implementation Plan

### Phase 1: Core Typeclass Infrastructure (new files, no breaking changes)

New files under `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/`:

- [ ] `UpstreamState.scala` — case class + `fromPipeResult(pr, computeAt)` factory
- [ ] `PipeComputeEnv.scala` — sealed trait + `EN15544StrictEnv` + `EN13384Env`
- [ ] `CanComputePipeResult.scala` — the typeclass trait
- [ ] `ComputablePipe.scala` — existential wrapper with smart constructor
- [ ] `GenericPipeChain.scala` — `Vector[ComputablePipe]` with `computeAll` fold

### Phase 2: Given Instances (new files, adapters to existing MecaFlu objects)

New files under `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/instances/`:

- [ ] `CanComputePipeResult_FlowOnly15544.scala` — delegates to `FlowOnlyMecaFlu_15544.makePipeResult`
- [ ] `CanComputePipeResult_Thermal13384.scala` — delegates to `ThermalMecaFlu_13384.makePipeResult`
- [ ] `CanComputePipeResult_FlowOnly13384.scala` — delegates to `FlowOnlyMecaFlu_13384.makePipeResult`

### Phase 3: Generalize Pipe Models

Modify `modules/engine/.../models/pipes.scala`:

- [ ] Add `postFireboxPipes: Vector[ComputablePipe]` to `Pipes_15544_Alg` / `Pipes_13384_Alg`
- [ ] Refactor `Pipes_15544_Strict` and `Pipes_15544_MCE` to build `postFireboxPipes` from current flue/connector/chimney
- [ ] Keep deprecated `flue`/`connector`/`chimney` accessors initially for backward compat

### Phase 4: Generalize PipeChain (Frame Propagation)

Modify `modules/engine/.../models/PipeChain.scala`:

- [ ] Add `PipeChain_Generic` that folds over a `Vector` of incremental descriptors with frame propagation
- [ ] Each entry carries its pipe module + role (to dispatch `mkPipeFromIncrDescrWithFinalFrame`)
- [ ] Keep existing `PipeChain_15544_Strict` / `PipeChain_15544_MCE` / `PipeChain_13384` as deprecated aliases

### Phase 5: Generalize Results

Modify `modules/engine/.../models/results.scala`:

- [ ] Refactor `PipesResult_15544` to use `postFireboxResults: Vector[PipeResult]` instead of named `flue`/`connector`/`chimney` fields
- [ ] `orderedPipesUntilFluePipe` uses `postFireboxResults.takeWhile(_.typ != ConnectorPipeT)`
- [ ] `orderedPipesAll` = pre-firebox results ++ postFireboxResults
- [ ] Add deprecated `def flue`/`def connector`/`def chimney` accessors
- [ ] Same for `PipesResult_13384` and `PipesResult_15544_VNelString`

### Phase 6: Refactor EN13384 Application

Modify `modules/engine/.../impl/en13384/en13384_common_application.scala`:

- [ ] Replace hardcoded `connector_PipeResult` + `chimney_PipeResult` (lines 107-155) with `GenericPipeChain.computeAll()`
- [ ] Build the chain: `Vector(connectorComputablePipe, chimneyComputablePipe)` using `ComputablePipe.apply` with `EN13384Env`
- [ ] `initialUpstream` = `UpstreamState(tw, last_known_density, last_known_velocity)`

### Phase 7: Refactor EN15544 Application

Modify `modules/engine/.../impl/en15544/strict/en15544_strict_application.scala` and `common/en15544_common_application.scala`:

- [ ] In `StrictAtParams`: replace `flue_PipeResult` + delegated `connector_PipeResult` + `chimney_PipeResult` with chain
- [ ] Build: `Vector(flueComputablePipe(EN15544StrictEnv), connectorComputablePipe(EN13384Env), chimneyComputablePipe(EN13384Env))`
- [ ] `initialUpstream` for flue = from firebox result; for connector/chimney = propagated

Modify `modules/engine/.../impl/en15544/mce/en15544_mce_application.scala` similarly:

- [ ] MCE uses `ThermalMecaFlu_13384` for ALL pipes — chain uses `EN13384Env` throughout

Modify `modules/engine/.../alg/en15544/en15544_application_alg.scala`:

- [ ] Refactor `AtParams` trait: replace individual `lazy val flue_PipeResult`, `connector_PipeResult`, `chimney_PipeResult` with `lazy val postFireboxPipeResults: Vector[PipeResult]`
- [ ] Add convenience accessors: `lastFluePipeResult`, `lastChimneyPipeResult` etc. via `PipeType` filtering

### Phase 8: Refactor YAML Loader

Modify `modules/engine/.../api/FireCalcYAML_Loader.scala`:

- [ ] Generalize to build a `Vector[ComputablePipe]` from the DTO pipe list
- [ ] Use `PipeChain_Generic` for frame propagation

### Phase 9: DTO V5

New files under `modules/dto/src/main/scala/afpma/firecalc/dto/v5/`:

- [ ] `PostFireboxPipeDescr_V5.scala` — tagged union: `FlowOnly15544(descr)` | `Thermal13384(descr)` | `FlowOnly13384(descr)`
- [ ] `FireCalcYAML_V5.scala` — replaces `flue_pipe_descr` + `connector_pipe_descr` + `chimney_pipe_descr` with `post_firebox_pipes: Seq[PostFireboxPipeDescr_V5]`
- [ ] V4 → V5 migration in `FireCalcYAMLMigrations`
- [ ] Codec updates (circe encoder/decoder with discriminated union)

### Phase 10: UI Refactor

Modify `modules/ui/.../models/Variables.scala`:

- [ ] Replace individual pipe result signals (`results_en15544_channel_pipe`, etc.) with a generic `Signal[Vector[(PipeType, PipeResult)]]`
- [ ] Update `GraphDataConverter` to iterate over generic pipe list
- [ ] Update accordion/tab UI components for dynamic pipe count

---

## Critical Files

| File | Change |
|------|--------|
| `engine/ops/generic/` (new dir) | Core typeclass, ComputablePipe, GenericPipeChain |
| `engine/models/pipes.scala` | Add `postFireboxPipes` field |
| `engine/models/PipeChain.scala` | Add `PipeChain_Generic` |
| `engine/models/results.scala` | Generalize PipesResult_* |
| `engine/impl/en13384/en13384_common_application.scala` | Replace hardcoded connector/chimney |
| `engine/impl/en15544/common/en15544_common_application.scala` | Replace CommonAtParams pipe chain |
| `engine/impl/en15544/strict/en15544_strict_application.scala` | Replace StrictAtParams pipe chain |
| `engine/impl/en15544/mce/en15544_mce_application.scala` | Replace MCEAtParams pipe chain |
| `engine/alg/en15544/en15544_application_alg.scala` | Generalize AtParams trait |
| `engine/api/FireCalcYAML_Loader.scala` | Build generic chain from DTO |
| `dto/v5/` (new dir) | PostFireboxPipeDescr_V5, FireCalcYAML_V5 |
| `ui/models/Variables.scala` | Generic pipe result signals |

## Existing Functions to Reuse

- `FlowOnlyMecaFlu_15544.makePipeResult` — delegate from `flowOnly15544` given instance
- `ThermalMecaFlu_13384.makePipeResult` — delegate from `thermal13384` given instance
- `FlowOnlyMecaFlu_13384.makePipeResult` — delegate from `flowOnly13384` given instance
- `PipeResult.useless(pt, gas_temp)` — for empty/absent pipes
- `IncrementalPipeDefModule_Common.mkPipeFromIncrDescrWithFinalFrame` — for generic frame propagation
- `ComputeAt` enum (already exists in `EN13384_1_A1_2019_Common_Application`)

## Risks & Mitigations

1. **Path-dependent type alignment**: `PipeFullDescrG[D#PipeElDescr]` vs `PipeFullDescrG[tc.descrAlg.PipeElDescr]` — may require `asInstanceOf` at the ComputablePipe construction boundary. Safe because D is a singleton type. Consistent with existing codebase patterns.

2. **Lazy val initialization topology**: Current `en13384_common_application` uses interleaved lazy vals. Converting to fold changes evaluation order. Must verify no circular dependencies break.

3. **EN15544 Strict mixed environments**: Flue uses `EN15544StrictEnv`, connector/chimney use `EN13384Env`. Solved by closure-capture in `ComputablePipe`.

4. **EN13384 connector PipeResult uses customized EN13384 formulas** (overridden in `EN13384_For_15544_Application`): The `EN13384Env.en13384App` must capture the correct specialized instance, not the generic one.

## Verification

1. `sbt "engine/test"` — all existing tests must pass after each phase
2. `sbt "engine/testOnly *PressureLoss*"` — pressure calculation regression
3. `sbt "engine/testOnly *CasType*"` — integration test cases
4. `sbt compile` — full compilation check
5. Manual: run the web UI (`make dev-web-ui-compile` + `make dev-web-ui-run`), verify graph renders correctly with same values as before
