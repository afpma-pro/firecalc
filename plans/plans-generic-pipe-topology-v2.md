# Generic Post-Firebox Pipe Chain — Typeclass-Based Refactor (v2)

## Context

The engine currently has a **fixed 6-pipe topology**: airIntake → combustionAir → firebox → flue → connector → chimney. The post-firebox pipes (flue/connector/chimney) are hardcoded with specific types per application variant (Strict vs MCE). Three incompatible `makePipeResult` signatures exist across `FlowOnlyMecaFlu_15544`, `ThermalMecaFlu_13384`, and `FlowOnlyMecaFlu_13384`, and `makePipeResult` is not part of any abstract interface (commented out in `MecaFluAlg`).

**Goal**: Replace the fixed post-firebox pipes with a generic `Vector[PipeSlot]` using a **typeclass-based Strategy Pattern** in Scala 3, where `CanComputePipeResult[D]` encodes algorithm–description compatibility at the type level.

**Scope**: Full stack (engine + DTO + UI). Both EN15544 and EN13384 standalone generalized.

---

## Review of First Draft — Issues Found

### Issue 1: Closure-Captured Env Breaks Param Sharing (SRP violation)

The first draft had `ComputablePipe` capture its `Env` (including `Params_13384`) at construction time. But `Params_13384 = (DraftCondition, LoadQty)` varies across the 4 `AtParams` instances, while the pipe descriptions are **static** (shared by all 4 instances via `inputs.pipes`). Capturing params forces rebuilding the entire `Vector[ComputablePipe]` for each param combo — conflating **what the pipe is** (static) with **how to compute it** (varying).

**Fix**: `Params_13384` is an explicit argument on `compute()`, not captured. The pipe chain is built once.

### ~~Issue 2: T_WN/T_Wmin ≠ Simple Upstream Propagation~~ RESOLVED

Initial analysis was wrong. Verified via code tracing:

- **EN15544 (Strict & MCE)**: `T_WN = flue_PipeResult.gas_temp_end` — **zero transformation**, direct identity mapping through `t_fluepipe_end → HeatingAppliance.Temperatures.flue_gas_temp_nominal → T_WN`. Simple upstream propagation works. No hook needed.
- **EN13384 standalone** (no flue pipe): `T_WN` is a fixed input from the HeatingAppliance specification. The connector is the first pipe in the chain, so `T_WN` = `initialUpstream.temp_start`.
- **MCE subtlety**: current code pins `T_WN` to `atDraftMin_LoadNominal.t_fluepipe_end` (same for all 4 AtParams). With direct propagation each AtParams uses its own flue temp. For Strict this is identical (EN15544 temp formula is draft-independent, verified by `require` assertion). For MCE this is a minor behavior change (arguably more physically correct).

**No `adjustUpstream` hook is needed.** The chain is pure linear propagation.

Ref: `en15544_strict_application.scala:103-117`, `en15544_mce_application.scala:123-128`, `en13384_common_application.scala:490-491`.

### Issue 3: Optional Pipes (PipeCanBe = FullDescr | Without)

The connector pipe is optional (`Without` case). When absent, current code uses `PipeResult.useless(ConnectorPipeT, tw)`. The first draft didn't address this.

**Fix**: Include a `NoOpPipeSlot` in the chain that passes upstream state through with a useless result. This preserves PipeType-based result lookup.

### Issue 4: needsUpstreamTemp is a Leaky Abstraction

A boolean flag the caller must check breaks the uniform interface.

**Fix**: Eliminated. The `adjustUpstream` hook handles temperature overrides at boundaries. Each typeclass instance internally decides whether to use `upstream.temp_start` or compute its own.

### Issue 5: Two-Parameter Typeclass [D, Env] is Over-Engineered

With `Env` captured (not parameterized), the second type parameter adds no value. The context is application-scoped and created via factory functions, not summoned via global `given`s.

**Fix**: Single type parameter `CanComputePipeResult[D]`. Context captured in factory-created instances.

---

## Architecture: Revised Typeclass Design

### Design Principles

1. **Separation of concerns**: Pipe topology (static) ≠ computation params (varying per AtParams)
2. **Single Responsibility**: `PipeSlot` = structural description + typeclass instance. `compute()` takes params.
3. **Open/Closed**: New pipe description algebras require only a new `CanComputePipeResult[NewD]` instance
4. **Interface Segregation**: Typeclass has one method, no flags
5. **DRY**: Factory functions create typeclass instances; existing MecaFlu objects provide computation logic

### Core Typeclass: `CanComputePipeResult[D]` (single type parameter)

```scala
/** Evidence that pipe description algebra D can produce a PipeResult.
  *
  * Application-specific context (en15544App, en13384App, HeatingAppliance,
  * z_geodetical_height, etc.) is captured at instance creation time via
  * factory functions — NOT via a second type parameter.
  *
  * D is always a singleton type (e.g., ThermalPipeDescr_13384.type).
  */
trait CanComputePipeResult[D <: PipeDescrAlg]:
    val descrAlg: D

    def computePipeResult(
        fd      : PipeFullDescrG[descrAlg.PipeElDescr],
        gas     : Gas,
        upstream: UpstreamState,
        params  : Params_13384      // (DraftCondition, LoadQty) — varies per AtParams
    ): Either[MecaFlu_Error, PipeResult]
```

**Why single type parameter**: The `Env` concept from v1 is eliminated. Context that varies per-standard (application instance, HA data, etc.) is captured inside the typeclass instance via factory functions. Context that varies per-AtParams (`Params_13384`) is an explicit argument.

**Why `params` is explicit, not captured**: 4 `AtParams` instances share the same pipe chain. Only `Params_13384 = (DraftCondition, LoadQty)` changes between them. Keeping it as an argument allows building the chain once and computing it 4 times with different params.

### Upstream State

```scala
case class UpstreamState(
    temp_start        : TCelsius,
    last_pipe_density : Option[Density],
    last_pipe_velocity: Option[FlowVelocity]
)

object UpstreamState:
    def fromPipeResult(pr: PipeResult, computeAt: ComputeAt): UpstreamState =
        UpstreamState(
            temp_start         = pr.gas_temp_end,
            last_pipe_density  = computeAt match
                case ComputeAt.Mean   => pr.last_density_mean
                case ComputeAt.Middle => pr.last_density_middle,
            last_pipe_velocity = computeAt match
                case ComputeAt.Mean   => pr.last_velocity_mean
                case ComputeAt.Middle => pr.last_velocity_middle
        )

    /** Initial state when no upstream pipe exists. */
    def initial(temp: TCelsius): UpstreamState =
        UpstreamState(temp, None, None)
```

### Factory Functions (not global givens)

Typeclass instances are **application-scoped**, not globally summoned. Each captures the specific application instance (which may be `EN13384_For_15544_Application` with EN15544 overrides, not a vanilla EN13384 app).

```scala
object CanComputePipeResult:

    /** FlowOnly EN15544 — captures EN15544 app, altitude, ShortSectionAlg */
    def forFlowOnly15544(
        en15544App: EN15544_Strict_Application,
        z_geo     : z_geodetical_height,
        ssa       : ShortSectionAlg
    ): CanComputePipeResult[FlowOnlyPipeDescr_15544.type] = new:
        val descrAlg = FlowOnlyPipeDescr_15544
        def computePipeResult(fd, gas, upstream, params) =
            // upstream.temp_start is IGNORED — EN15544 computes temp from formula
            FlowOnlyMecaFlu_15544.makePipeResult(
                fd, gas, params._2, z_geo, params._1
            )(using en15544App, ssa)

    /** Thermal EN13384 — captures EN13384 app + HeatingAppliance data */
    def forThermal13384(
        en13384App: EN13384_1_A1_2019_Application_Alg,
        hafg: HeatingAppliance.FlueGas,
        hamf: HeatingAppliance.MassFlows,
        hapwr: HeatingAppliance.Powers,
        haeff: HeatingAppliance.Efficiency
    ): CanComputePipeResult[ThermalPipeDescr_13384.type] = new:
        val descrAlg = ThermalPipeDescr_13384
        def computePipeResult(fd, gas, upstream, params) =
            ThermalMecaFlu_13384.makePipeResult(
                fd, hafg, hamf, hapwr, haeff,
                upstream.temp_start,    // uses upstream temp
                upstream.last_pipe_density,
                upstream.last_pipe_velocity,
                gas
            )(using params, en13384App)

    /** FlowOnly EN13384 — same as thermal but no last_pipe_density */
    def forFlowOnly13384(
        en13384App: EN13384_1_A1_2019_Application_Alg,
        hafg: HeatingAppliance.FlueGas,
        hamf: HeatingAppliance.MassFlows,
        hapwr: HeatingAppliance.Powers,
        haeff: HeatingAppliance.Efficiency
    ): CanComputePipeResult[FlowOnlyPipeDescr_13384.type] = new:
        val descrAlg = FlowOnlyPipeDescr_13384
        def computePipeResult(fd, gas, upstream, params) =
            FlowOnlyMecaFlu_13384.makePipeResult(
                fd, hafg, hamf, hapwr, haeff,
                upstream.temp_start,
                upstream.last_pipe_velocity,
                gas
            )(using params, en13384App)
```

### PipeSlot (Existential Wrapper — params NOT captured)

```scala
/** A single slot in the post-firebox pipe chain.
  * Existentially hides the description algebra D.
  * The pipe topology is STATIC — shared across all AtParams instances.
  */
trait PipeSlot:
    val pipeType: PipeType
    val label   : String

    /** Compute the pipe result for this slot.
      * @param upstream  Propagated state from the previous pipe
      * @param params    Computation parameters (varies per AtParams)
      */
    def compute(upstream: UpstreamState, params: Params_13384): Either[MecaFlu_Error, PipeResult]

object PipeSlot:
    /** Smart constructor — packs D into existential. */
    def apply[D0 <: PipeDescrAlg](
        _pipeType : PipeType,
        _label    : String,
        _gas      : Gas,
        _fullDescr: PipeFullDescrG[D0#PipeElDescr]
    )(using _tc: CanComputePipeResult[D0]): PipeSlot = new PipeSlot:
        val pipeType = _pipeType
        val label    = _label
        def compute(upstream, params) =
            _tc.computePipeResult(
                _fullDescr.asInstanceOf[PipeFullDescrG[_tc.descrAlg.PipeElDescr]],
                _gas, upstream, params
            )

    /** No-op slot for optional absent pipes (e.g., connector = Without). */
    def noop(_pipeType: PipeType, _label: String): PipeSlot = new PipeSlot:
        val pipeType = _pipeType
        val label    = _label
        def compute(upstream, _params) =
            Right(PipeResult.useless(_pipeType, upstream.temp_start))
```

### PostFireboxPipeChain (Pure Sequential Fold)

```scala
case class PostFireboxPipeChain(slots: Vector[PipeSlot]):

    /** Compute all pipe results sequentially, propagating upstream state.
      *
      * @param params          (DraftCondition, LoadQty) — varies per AtParams
      * @param initialUpstream State from the last pre-firebox pipe (or T_WN for EN13384 standalone)
      * @param computeAt       Mean or Middle for density/velocity extraction
      */
    def computeAll(
        params         : Params_13384,
        initialUpstream: UpstreamState,
        computeAt      : ComputeAt
    ): Either[MecaFlu_Error, Vector[PipeResult]] =
        slots.foldLeft[Either[MecaFlu_Error, (UpstreamState, Vector[PipeResult])]](
            Right((initialUpstream, Vector.empty))
        ):
            case (Left(err), _) => Left(err)
            case (Right((upstream, results)), slot) =>
                slot.compute(upstream, params).map: result =>
                    val next = UpstreamState.fromPipeResult(result, computeAt)
                    (next, results :+ result)
        .map(_._2)

    /** PipeType-based result lookup (for derived temperatures, validations). */
    def resultByPipeType(results: Vector[PipeResult], pt: PipeType): Option[PipeResult] =
        slots.map(_.pipeType).zip(results).find(_._1 == pt).map(_._2)

    def lastResultByPipeType(results: Vector[PipeResult], pt: PipeType): Option[PipeResult] =
        slots.map(_.pipeType).zip(results).filter(_._1 == pt).lastOption.map(_._2)
```

No `adjustUpstream` hook needed. Upstream propagation is pure linear chaining:
- **EN15544**: `initialUpstream` from firebox result. Flue→connector→chimney chain naturally.
- **EN13384 standalone**: `initialUpstream = UpstreamState.initial(T_WN)`. No flue pipe; connector is first.

### EN15544 Strict: How the Chain is Used

```scala
// In EN15544_Strict_Application (built ONCE, shared by all AtParams):

lazy val postFireboxChain: PostFireboxPipeChain = {
    // Create typeclass instances with captured context
    given CanComputePipeResult[FlowOnlyPipeDescr_15544.type] =
        CanComputePipeResult.forFlowOnly15544(en15544 = this, z_geodetical_height, shortSectionAlg)

    val en13384tc = CanComputePipeResult.forThermal13384(
        en13384_application, hafg, hamf, hapwr, haeff
    )
    given CanComputePipeResult[ThermalPipeDescr_13384.type] = en13384tc

    PostFireboxPipeChain(Vector(
        PipeSlot(FluePipeT,      "Flue",      FlueGas, fluePipeFullDescr),
        connectorSlot,  // PipeSlot or PipeSlot.noop depending on Without
        PipeSlot(ChimneyPipeT,   "Chimney",   FlueGas, chimneyPipeFullDescr)
    ))
}

// In StrictAtParams (called 4 times with different params):
lazy val postFireboxResults: VNelMcalcErr[Vector[PipeResult]] =
    postFireboxChain.computeAll(
        params          = params,
        initialUpstream = UpstreamState.initial(t_firebox_outlet),
        computeAt       = ComputeAt.Middle
    ).toValidatedNel
    // Pure linear chaining: flue temp → connector temp → chimney temp
    // HeatingAppliance.Temperatures still populated from t_fluepipe_end
    // for EN13384 pressure requirements, but the chain itself is self-contained.
```

### EN13384 Standalone: How the Chain is Used

```scala
// No flue pipe. T_WN is a fixed input from HeatingAppliance spec.
lazy val postFireboxChain: PostFireboxPipeChain = {
    given CanComputePipeResult[ThermalPipeDescr_13384.type] =
        CanComputePipeResult.forThermal13384(this, hafg, hamf, hapwr, haeff)

    PostFireboxPipeChain(Vector(
        connectorSlot,  // PipeSlot or PipeSlot.noop
        PipeSlot(ChimneyPipeT, "Chimney", FlueGas, chimneyPipeFullDescr)
    ))
}

// T_WN as initial upstream — the heating appliance outlet temperature
lazy val postFireboxResults =
    postFireboxChain.computeAll(
        params          = params,
        initialUpstream = UpstreamState.initial(T_WN),
        computeAt       = ComputeAt.Middle
    )
```

---

## Implementation Plan

### Phase 1: Core Typeclass Infrastructure (new files, no breaking changes)

New files under `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/`:

- [ ] `UpstreamState.scala` — case class + `fromPipeResult(pr, computeAt)` factory
- [ ] `CanComputePipeResult.scala` — typeclass trait + companion with 3 factory functions
- [ ] `PipeSlot.scala` — existential wrapper + `noop` constructor
- [ ] `PostFireboxPipeChain.scala` — `Vector[PipeSlot]` with `computeAll` fold + `resultByPipeType`

### Phase 2: Generalize PipeChain (Frame Propagation) — orthogonal concern

Modify `modules/engine/.../models/PipeChain.scala`:

- [ ] Add `PipeChain_Generic` that folds over a `Vector` of incremental descriptors with frame propagation
- [ ] Keep existing `PipeChain_15544_Strict` / `PipeChain_15544_MCE` / `PipeChain_13384` as deprecated aliases

### Phase 3: Generalize Pipe Models

Modify `modules/engine/.../models/pipes.scala`:

- [ ] Add `postFireboxChain: PostFireboxPipeChain` to `Pipes_15544_Alg` / `Pipes_13384_Alg`
- [ ] Refactor `Pipes_15544_Strict` and `Pipes_15544_MCE` to build from current flue/connector/chimney
- [ ] Keep deprecated `flue`/`connector`/`chimney` accessors initially

### Phase 4: Generalize Results

Modify `modules/engine/.../models/results.scala`:

- [ ] Refactor `PipesResult_15544` to use `postFireboxResults: Vector[PipeResult]`
- [ ] `orderedPipesUntilFluePipe` = `postFireboxResults.takeWhile(_.typ != ConnectorPipeT)`
- [ ] Derived temperatures via `resultByPipeType`: `t_F = resultByPipeType(FluePipeT).map(_.gas_temp_end)`
- [ ] Keep deprecated named accessors

### Phase 5: Refactor EN13384 Application

Modify `modules/engine/.../impl/en13384/en13384_common_application.scala`:

- [ ] Replace hardcoded `connector_PipeResult` + `chimney_PipeResult` with chain
- [ ] Build once: `PostFireboxPipeChain(Vector(connectorSlot, chimneySlot))`
- [ ] `computeAll(params, UpstreamState.initial(tw), ComputeAt.Middle)`

### Phase 6: Refactor EN15544 Application

Modify `en15544_strict_application.scala`, `en15544_common_application.scala`, `en15544_mce_application.scala`:

- [ ] Build `postFireboxChain` once on the outer application (shared by all AtParams)
- [ ] `CommonAtParams`: replace individual lazy vals with `postFireboxChain.computeAll(params, ...)`
- [ ] Derived temps (`t_connector_pipe_mean`, `t_chimney_entrance`, etc.) via `resultByPipeType`
- [ ] Validations (`validateVelocitiesInFluePipe`, etc.) via `resultByPipeType`

Modify `en15544_application_alg.scala`:

- [ ] `AtParams`: replace named pipe result lazy vals with `postFireboxPipeResults: Vector[PipeResult]`

### Phase 7: Refactor YAML Loader

Modify `modules/engine/.../api/FireCalcYAML_Loader.scala`:

- [ ] Build `PostFireboxPipeChain` from DTO pipe list + `PipeChain_Generic` for frames

### Phase 8: DTO V5

New files under `modules/dto/.../v5/`:

- [ ] `PostFireboxPipeDescr_V5.scala` — tagged union with `pipeType` + description type discriminator
- [ ] `FireCalcYAML_V5.scala` — `post_firebox_pipes: Seq[PostFireboxPipeDescr_V5]`
- [ ] V4 → V5 migration + codecs

### Phase 9: UI Refactor

Modify `modules/ui/.../models/Variables.scala`:

- [ ] Generic `Signal[Vector[(PipeType, PipeResult)]]` replacing individual signals
- [ ] Update `GraphDataConverter`, accordion/tab components

---

## Critical Files

| File | Change |
|------|--------|
| `engine/ops/generic/` (new dir) | Typeclass, PipeSlot, PostFireboxPipeChain |
| `engine/models/pipes.scala` | Add `postFireboxChain` field |
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

- `FlowOnlyMecaFlu_15544.makePipeResult` — delegate from `forFlowOnly15544` factory
- `ThermalMecaFlu_13384.makePipeResult` — delegate from `forThermal13384` factory
- `FlowOnlyMecaFlu_13384.makePipeResult` — delegate from `forFlowOnly13384` factory
- `PipeResult.useless(pt, gas_temp)` — for `PipeSlot.noop` (absent optional pipes)
- `IncrementalPipeDefModule_Common.mkPipeFromIncrDescrWithFinalFrame` — for PipeChain_Generic
- `ComputeAt` enum (already in `EN13384_1_A1_2019_Common_Application`)
- `ConnectorPipe_Module.foldPipeCanBe` pattern — to build `PipeSlot` or `PipeSlot.noop`

## Risks & Mitigations

1. **Path-dependent type cast at PipeSlot boundary**: `PipeFullDescrG[D0#PipeElDescr]` → `PipeFullDescrG[tc.descrAlg.PipeElDescr]`. Safe because `D0` is a singleton type. Consistent with `PipeFullDescrG.findAllByType` in existing code.

2. **Lazy val evaluation order change**: Converting interleaved `lazy val`s to a fold changes evaluation order. Must verify no circular lazy val dependencies break. Mitigated by keeping EN13384 application methods unchanged — only the call site changes.

3. **HeatingAppliance validation**: `en13384_heatingAppliance_powers/efficiency/temperatures` are `VNelMcalcErr[...]`. Factory functions require validated values. The `forThermal13384` factory must be called inside a validation block. See "HA validation" note in Phase 6.

4. **Optional connector pipe**: `foldPipeCanBe` pattern → `PipeSlot` or `PipeSlot.noop`. The noop passes temperature through unchanged, preserving PipeType-based result lookup.

5. **EN13384 connector uses specialized EN13384 formulas** (via `EN13384_For_15544_Application` inner class): The `forThermal13384` factory must capture `en13384_application` (the specialized instance), NOT a generic EN13384 application.

6. **MCE T_WN pinning**: Current MCE code pins `T_WN` to `atDraftMin_LoadNominal.t_fluepipe_end` for all AtParams. With direct upstream propagation, each AtParams uses its own flue pipe end temp. This is a minor behavior change (arguably more correct). Must verify via CasType_MCE tests.

## Verification

1. `sbt "engine/test"` — all existing tests must pass after each phase
2. `sbt "engine/testOnly *PressureLoss*"` — pressure calculation regression
3. `sbt "engine/testOnly *CasType*"` — integration test cases
4. `sbt compile` — full compilation check
5. Manual: run the web UI (`make dev-web-ui-compile` + `make dev-web-ui-run`), verify graph renders correctly with same values as before

---

## Appendix: Derived Quantities from Named Pipe Results

These quantities currently reference `flue_PipeResult`, `connector_PipeResult`, or `chimney_PipeResult` by name. The generic design needs a resolution strategy for each. **Please review and decide how each should be resolved when the pipe list is generic.**

Ref: `en15544_common_application.scala` (CommonAtParams), `en15544_application_alg.scala` (AtParams trait)

### Temperatures (EN15544 §4.8)

| # | Quantity | Current source | Used for | Proposed resolution |
|---|----------|---------------|----------|---------------------|
| 1 | `t_F` | `flue_PipeResult.gas_temp_end` | Efficiency η calculation (§4.10.3) | `lastResultByPipeType(FluePipeT).gas_temp_end` ? |
| 2 | `t_fluepipe_end` | `flue_PipeResult.gas_temp_end` | FlueGasTripleOfVariates (§4.10.4), HeatingAppliance.Temperatures, EstimatedOutputTemperatures.t_stove_out | Same as t_F — alias |
| 3 | `t_connector_pipe_mean` | `connector_PipeResult.gas_temp_mean` | EN15544 §4.8.4 | `lastResultByPipeType(ConnectorPipeT).gas_temp_mean` ? |
| 4 | `t_chimney_entrance` | `chimney_PipeResult.gas_temp_start` | EN15544 §4.8.5 | `lastResultByPipeType(ChimneyPipeT).gas_temp_start` ? |
| 5 | `t_chimney_mean` | `chimney_PipeResult.gas_temp_mean` | EN15544 §4.8.5 | `lastResultByPipeType(ChimneyPipeT).gas_temp_mean` ? |
| 6 | `t_chimney_out` | `chimney_PipeResult.gas_temp_end` | EstimatedOutputTemperatures, validation | `lastResultByPipeType(ChimneyPipeT).gas_temp_end` ? |
| 7 | `t_chimney_wall_top` | `chimney_PipeResult.temperature_iob(0)` | Condensation validation | `lastResultByPipeType(ChimneyPipeT).temperature_iob(0)` ? |

### Pressures (EN15544 §4.10.4)

| # | Quantity | Current source | Proposed resolution |
|---|----------|---------------|---------------------|
| 8 | `required_delivery_pressure` | Sum of `en13384_pr_all-ph` from `combustionAir`, `firebox`, `flue` (pre-connector pipes only) | Sum of all pre-firebox pipes + `postFireboxResults.takeWhile(_.typ != ConnectorPipeT)` ? |
| 9 | `Σ_pR, Σ_pu, Σ_ph` (full) | Sum across `orderedPipesAll` (all 6 pipes) | Sum across pre-firebox + all postFireboxResults |
| 10 | `Σ_pR_until_fluepipe_end` (partial) | Sum across `orderedPipesUntilFluePipe` = `combustionAir :: firebox :: flue :: Nil` | Pre-firebox pipes + `postFireboxResults.takeWhile(_.typ != ConnectorPipeT)` ? |

### Validations

| # | Validation | Current source | Proposed resolution |
|---|-----------|---------------|---------------------|
| 11 | `validateVelocitiesInFluePipe` | `flue_PipeResult` | `resultByPipeType(FluePipeT)` ? Or iterate all FluePipeT results? |
| 12 | `validateVelocitiesInConnectorPipe` | `connector_PipeResult` | `resultByPipeType(ConnectorPipeT)` ? |
| 13 | `validateVelocitiesInChimneyPipe` | `chimney_PipeResult` | `resultByPipeType(ChimneyPipeT)` ? |
| 14 | `validateLzMinConstraint` | `flue_PipeResult.lengthSum >= L_Z_min` | Sum of `lengthSum` for all FluePipeT results? Or last only? |
| 15 | `validateFluePipeShape` | Accesses flue pipe description elements directly | Needs access to PipeSlot's fullDescr — how? |

### Other

| # | Quantity | Current source | Proposed resolution |
|---|----------|---------------|---------------------|
| 16 | `η` (efficiency) | `formulas.η_calc(t_F)` | Depends on t_F resolution (#1) |
| 17 | `en13384_heatingAppliance_temperatures` | `atDraftMin_LoadNominal.t_fluepipe_end` | Still computed from t_fluepipe_end (#2). Chain is self-contained; this feeds the EN13384 pressure requirements independently. |
| 18 | `last_known_density_before_connector_pipe` | `flue_PipeResult.last_density_mean/middle` | Implicit via upstream propagation — fold handles this |
| 19 | `last_known_velocity_before_connector_pipe` | `flue_PipeResult.last_velocity_mean/middle` | Implicit via upstream propagation — fold handles this |

### Questions for You

- **Multiple pipes of same PipeType** (e.g., two FluePipeT segments): For temperatures (#1-7), should we use `lastResultByPipeType` (last wins)? For sums (#8-10, #14), should we sum all matching?

For temperatures (#1-7) : yes use 'lastResultByPipeType'
For #8 : Sum of all pre-firebox pipes + postFireboxResult (take results until and including the last flue pipe type, the remaining connector pipe and/or chimney pipe type at the end should not be included)
For #9 : Correct
For #10 : Pre-firebox pipes + postFireboxResult (take results until and including the last flue pipe type, the remaining connector pipe and/or chimney pipe type at the end should not be included)
For #14 : Sum the length until the end of the LAST element of the LAST flue pipe type.

Conceptually we can define : 
FLUE_PIPE = starts right after the firebox pipe, can contains multiple "FluePipeT" / "ConnectorPipeT", and ends at the last element of the last "FluePipeT"
CONNECTOR_PIPE = First element after the last "FluePipeT"
CHIMNEY_PIPE = Last element

This means the topology after the firebox pipe is : 
1. Zero or one FluePipeT
2. Zero, one or more "FluePipeT"/"ConnectorPipeT" : must end with a "FluePipeT"
3. Zero or one ConnectorPipeT (it Can have type ConnectorPipe.Without or similar)
4. A final "ChimneyPipeT"

(1+2) = FLUE_PIPE
(3) = CONNECTOR_PIPE
(4) = CHIMNEY_PIPE

Make sure this topology is ENFORCED. It should return validation errors.
Design a way to insert custom "PipePanels" in the UI (add, insert, remove button and logic) so we the user can define this custom topology


- **Velocity validation (#11-13)**: Validate each pipe individually, or all pipes of that PipeType?

Validate all pipes of that PipeType
But see previous answer for context.

- **`validateFluePipeShape` (#15)**: This accesses the pipe *description* (not the result). The `PipeSlot` trait currently doesn't expose `fullDescr` to the outside. Should it? Or should this validation be moved elsewhere?

yes the PipeSlot should expose the FullDescr, at least the innerSection for now.