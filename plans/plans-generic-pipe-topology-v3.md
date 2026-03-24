# Generic Post-Firebox Pipe Chain — Typeclass-Based Refactor (v3)

## Context

The engine currently has a **fixed 6-pipe topology**: airIntake → combustionAir → firebox → flue → connector → chimney. The post-firebox pipes (flue/connector/chimney) are hardcoded with specific types per application variant (Strict vs MCE). Three incompatible `makePipeResult` signatures exist across `FlowOnlyMecaFlu_15544`, `ThermalMecaFlu_13384`, and `FlowOnlyMecaFlu_13384`, and `makePipeResult` is not part of any abstract interface (commented out in `MecaFluAlg`).

**Goal**: Replace the fixed post-firebox pipes with a generic `Vector[PipeSlot]` using a **typeclass-based Strategy Pattern** in Scala 3, where `CanComputePipeResult[D]` encodes algorithm–description compatibility at the type level.

**Scope**: Full stack (engine + DTO + UI). Both EN15544 and EN13384 standalone generalized.

---

## v3 Changes from v2

- **Topology grammar**: Post-firebox chain has structured regions (FLUE_PIPE / CONNECTOR_PIPE / CHIMNEY_PIPE) with enforced validation
- **Resolved all 19 derived quantities** per user decisions
- **`PipeSlot` exposes `fullDescr`** (at least inner sections) for validation access
- **UI panel design** for add/insert/remove pipe topology editing
- **Removed `adjustUpstream` hook** (confirmed: pure linear chaining works for all contexts)

---

## Post-Firebox Topology Grammar

The post-firebox chain is NOT a flat list. It has a **structured grammar**:

```
PostFireboxChain := FLUE_PIPE_REGION  CONNECTOR_PIPE  CHIMNEY_PIPE

FLUE_PIPE_REGION := (FluePipeT | ConnectorPipeT)*  FluePipeT
                   | ε                                           (empty — EN13384 standalone)

CONNECTOR_PIPE   := ConnectorPipeT                               (present)
                   | ConnectorPipe.Without                        (absent / noop)

CHIMNEY_PIPE     := ChimneyPipeT                                 (always exactly one, always last)
```

**Conceptual groupings** (for boundary logic):
- **FLUE_PIPE** = everything from the start of the chain up to and including the **last FluePipeT**. Can contain interleaved FluePipeT and ConnectorPipeT segments. Must end with a FluePipeT.
- **CONNECTOR_PIPE** = first element after the last FluePipeT. Type ConnectorPipeT or absent (Without/noop).
- **CHIMNEY_PIPE** = always the last element. Type ChimneyPipeT.

**Validation rules** (return errors, not silent failures):
1. Last slot MUST be ChimneyPipeT
2. If FLUE_PIPE_REGION is non-empty, its last element MUST be FluePipeT
3. No FluePipeT may appear after the CONNECTOR_PIPE position
4. At most one ConnectorPipeT after the last FluePipeT
5. No ChimneyPipeT except the last slot

---

## Architecture: Typeclass Design (unchanged from v2)

### Core Typeclass: `CanComputePipeResult[D]` (with cast-free `mkSlot`)

```scala
/** Evidence that pipe description algebra D can produce a PipeResult.
  *
  * D is always a singleton type (e.g., ThermalPipeDescr_13384.type).
  * Application-scoped context is captured at instance creation time via factory functions.
  *
  * KEY DESIGN: `mkSlot` lives on this trait (not on PipeSlot companion). This ensures
  * the `PipeFullDescrG[descrAlg.PipeElDescr]` parameter and `computePipeResult` share
  * the SAME stable path (`this.descrAlg`), so the Scala 3 compiler can prove type
  * equality without any `asInstanceOf`.
  */
trait CanComputePipeResult[D <: PipeDescrAlg]:
    val descrAlg: D

    def computePipeResult(
        fd      : PipeFullDescrG[descrAlg.PipeElDescr],
        gas     : Gas,
        upstream: UpstreamState,
        params  : Params_13384
    ): Either[MecaFlu_Error, PipeResult]

    /** Create a PipeSlot with types statically aligned. Zero casts.
      *
      * Both `fd` and `computePipeResult` are typed via the same path
      * `this.descrAlg.PipeElDescr`, so the compiler proves equality trivially.
      */
    final def mkSlot(
        pipeType: PipeType,
        label   : String,
        gas     : Gas,
        fd      : PipeFullDescrG[descrAlg.PipeElDescr]
    ): PipeSlot =
        val self = this
        val capturedFd = fd
        new PipeSlot:
            val pipeType = pipeType
            val label    = label
            val gas      = gas
            def elements = capturedFd.elements  // Vector[NamedPipeElDescrG[self.descrAlg.PipeElDescr]]
                                                 // widens to Vector[NamedPipeElDescrG[?]] ✓
            def compute(upstream, params) =
                self.computePipeResult(capturedFd, gas, upstream, params)
                //                     ^^^^^^^^^^  typed as PipeFullDescrG[self.descrAlg.PipeElDescr]
                //                     matches computePipeResult's parameter type ✓
```

**Why zero casts**: Inside `mkSlot`, `capturedFd` has type `PipeFullDescrG[this.descrAlg.PipeElDescr]`. `self.computePipeResult` expects `PipeFullDescrG[self.descrAlg.PipeElDescr]`. Same stable path `self.descrAlg` → same `PipeElDescr`. The compiler needs no help.

**Singleton type requirement**: Factory functions MUST return concrete singleton types (e.g., `CanComputePipeResult[FlowOnlyPipeDescr_15544.type]`), and the inner `val descrAlg` MUST be typed with the singleton type. This ensures call sites can match `tc.descrAlg.PipeElDescr` with `FlowOnlyPipeDescr_15544.PipeElDescr`.

### UpstreamState

```scala
case class UpstreamState(
    temp_start        : TCelsius,
    last_pipe_density : Option[Density],
    last_pipe_velocity: Option[FlowVelocity]
)

object UpstreamState:
    def fromPipeResult(pr: PipeResult, computeAt: ComputeAt): UpstreamState
    def initial(temp: TCelsius): UpstreamState
```

### Factory Functions

```scala
object CanComputePipeResult:

    def forFlowOnly15544(
        en15544App: EN15544_Strict_Application,
        z_geo: z_geodetical_height,
        ssa: ShortSectionAlg
    ): CanComputePipeResult[FlowOnlyPipeDescr_15544.type] =
        new CanComputePipeResult[FlowOnlyPipeDescr_15544.type]:
            val descrAlg: FlowOnlyPipeDescr_15544.type = FlowOnlyPipeDescr_15544  // singleton type!
            def computePipeResult(fd, gas, upstream, params) =
                FlowOnlyMecaFlu_15544.makePipeResult(
                    fd, gas, params._2, z_geo, params._1
                )(using en15544App, ssa)

    def forThermal13384(
        en13384App: EN13384_1_A1_2019_Application_Alg,
        hafg: HeatingAppliance.FlueGas,
        hamf: HeatingAppliance.MassFlows,
        hapwr: HeatingAppliance.Powers,
        haeff: HeatingAppliance.Efficiency
    ): CanComputePipeResult[ThermalPipeDescr_13384.type] =
        new CanComputePipeResult[ThermalPipeDescr_13384.type]:
            val descrAlg: ThermalPipeDescr_13384.type = ThermalPipeDescr_13384
            def computePipeResult(fd, gas, upstream, params) =
                ThermalMecaFlu_13384.makePipeResult(
                    fd, hafg, hamf, hapwr, haeff,
                    upstream.temp_start, upstream.last_pipe_density, upstream.last_pipe_velocity,
                    gas
                )(using params, en13384App)

    def forFlowOnly13384(
        en13384App: EN13384_1_A1_2019_Application_Alg,
        hafg: HeatingAppliance.FlueGas,
        hamf: HeatingAppliance.MassFlows,
        hapwr: HeatingAppliance.Powers,
        haeff: HeatingAppliance.Efficiency
    ): CanComputePipeResult[FlowOnlyPipeDescr_13384.type] =
        new CanComputePipeResult[FlowOnlyPipeDescr_13384.type]:
            val descrAlg: FlowOnlyPipeDescr_13384.type = FlowOnlyPipeDescr_13384
            def computePipeResult(fd, gas, upstream, params) =
                FlowOnlyMecaFlu_13384.makePipeResult(
                    fd, hafg, hamf, hapwr, haeff,
                    upstream.temp_start, upstream.last_pipe_velocity,
                    gas
                )(using params, en13384App)
```

### PipeSlot (no smart constructor — created by `tc.mkSlot`)

```scala
/** A single slot in the post-firebox pipe chain.
  * Existentially hides the description algebra.
  * Created ONLY via `CanComputePipeResult.mkSlot` (cast-free) or `PipeSlot.noop`.
  */
trait PipeSlot:
    val pipeType: PipeType
    val label   : String
    val gas     : Gas
    def compute(upstream: UpstreamState, params: Params_13384): Either[MecaFlu_Error, PipeResult]
    def elements: Vector[NamedPipeElDescrG[?]]

object PipeSlot:
    /** No-op slot for absent optional pipes (e.g., connector = Without). */
    def noop(_pipeType: PipeType, _label: String): PipeSlot = new PipeSlot:
        val pipeType = _pipeType
        val label    = _label
        val gas      = FlueGas
        def elements = Vector.empty
        def compute(upstream, _params) =
            Right(PipeResult.useless(_pipeType, upstream.temp_start))
```

### Call Site Example (cast-free)

```scala
// Types flow naturally — no asInstanceOf anywhere:

val tc15544 = CanComputePipeResult.forFlowOnly15544(this, z_geodetical_height, shortSectionAlg)
val tc13384 = CanComputePipeResult.forThermal13384(en13384_application, hafg, hamf, hapwr, haeff)

// FluePipe_Module_15544.unwrap returns PipeFullDescrG[FlowOnlyPipeDescr_15544.PipeElDescr]
// tc15544.descrAlg : FlowOnlyPipeDescr_15544.type
// tc15544.descrAlg.PipeElDescr == FlowOnlyPipeDescr_15544.PipeElDescr  ✓ (singleton narrowing)
val flueSlot = tc15544.mkSlot(FluePipeT, "Flue", FlueGas,
    FluePipe_Module_15544.unwrap(inputs.pipes.flue))

// ConnectorPipe_Module.unwrap returns PipeFullDescrG[ThermalPipeDescr_13384.PipeElDescr]
// tc13384.descrAlg.PipeElDescr == ThermalPipeDescr_13384.PipeElDescr  ✓
val connectorSlot = ConnectorPipe_Module.foldPipeCanBe(inputs.pipes.connector)(
    onWithout   = PipeSlot.noop(ConnectorPipeT, "Connector"),
    onFullDescr = fd => tc13384.mkSlot(ConnectorPipeT, "Connector", FlueGas,
        ConnectorPipe_Module.unwrap(fd))
)

val chimneySlot = tc13384.mkSlot(ChimneyPipeT, "Chimney", FlueGas,
    ChimneyPipe_Module.unwrap(inputs.pipes.chimney))
```

### PostFireboxPipeChain (validated topology + region accessors)

```scala
/** A validated post-firebox pipe chain. Private constructor enforces grammar via `validated` factory. */
case class PostFireboxPipeChain private (slots: Vector[PipeSlot]):

    // ── Topology boundaries (computed once, guaranteed valid) ──

    /** Index of the last FluePipeT slot. None if FLUE_PIPE_REGION is empty. */
    private val lastFluePipeIdx: Option[Int] =
        slots.zipWithIndex.filter(_._1.pipeType == FluePipeT).lastOption.map(_._2)

    // ── Region accessors ──

    /** FLUE_PIPE region: all slots up to and including the last FluePipeT. */
    def fluePipeRegion: Vector[PipeSlot] =
        lastFluePipeIdx.map(i => slots.take(i + 1)).getOrElse(Vector.empty)

    /** CONNECTOR_PIPE: the slot immediately after the last FluePipeT (if ConnectorPipeT). */
    def connectorSlot: Option[PipeSlot] =
        lastFluePipeIdx
            .flatMap(i => slots.lift(i + 1))
            .filter(_.pipeType == ConnectorPipeT)
            .orElse(slots.find(s => s.pipeType == ConnectorPipeT && lastFluePipeIdx.isEmpty))

    /** CHIMNEY_PIPE: always the last slot. */
    def chimneySlot: PipeSlot = slots.last

    // ── Computation ──

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
                    (UpstreamState.fromPipeResult(result, computeAt), results :+ result)
        .map(_._2)

    // ── Result region accessors ──

    /** Results in the FLUE_PIPE region. */
    def fluePipeResults(results: Vector[PipeResult]): Vector[PipeResult] =
        lastFluePipeIdx.map(i => results.take(i + 1)).getOrElse(Vector.empty)

    /** CONNECTOR_PIPE result. */
    def connectorResult(results: Vector[PipeResult]): Option[PipeResult] =
        connectorSlot.flatMap(_ => lastFluePipeIdx.flatMap(i => results.lift(i + 1)))

    /** CHIMNEY_PIPE result (always last). */
    def chimneyResult(results: Vector[PipeResult]): PipeResult = results.last

    /** Last FluePipeT result (for t_F, t_fluepipe_end). */
    def lastFluePipeResult(results: Vector[PipeResult]): Option[PipeResult] =
        lastFluePipeIdx.flatMap(results.lift)

object PostFireboxPipeChain:
    /** Validated factory. Returns errors if topology grammar is violated. */
    def validated(slots: Vector[PipeSlot]): ValidatedNel[TopologyError, PostFireboxPipeChain] =
        val errors = scala.collection.mutable.ListBuffer.empty[TopologyError]

        // Rule 1: last slot must be ChimneyPipeT
        if slots.isEmpty || slots.last.pipeType != ChimneyPipeT then
            errors += TopologyError.MissingChimney

        // Rule 2: find last FluePipeT
        val lastFluePipeIdx = slots.zipWithIndex.filter(_._1.pipeType == FluePipeT).lastOption.map(_._2)

        // Rule 3: no FluePipeT after the connector position
        lastFluePipeIdx.foreach: lfpi =>
            val afterFlue = slots.drop(lfpi + 1)
            if afterFlue.exists(_.pipeType == FluePipeT) then
                errors += TopologyError.FluePipeAfterConnector

        // Rule 4: at most one ConnectorPipeT after last FluePipeT
        lastFluePipeIdx.foreach: lfpi =>
            val afterFlue = slots.drop(lfpi + 1).dropRight(1) // exclude chimney
            if afterFlue.count(_.pipeType == ConnectorPipeT) > 1 then
                errors += TopologyError.MultipleConnectorsAfterFlue

        // Rule 5: no ChimneyPipeT except last
        if slots.dropRight(1).exists(_.pipeType == ChimneyPipeT) then
            errors += TopologyError.ChimneyNotLast

        NonEmptyList.fromList(errors.toList) match
            case Some(nel) => Validated.Invalid(nel)
            case None      => Validated.Valid(PostFireboxPipeChain(slots))
```

---

## Resolved Derived Quantities

All quantities use `PostFireboxPipeChain` region accessors on the computed results.

### Temperatures (§4.8) — `lastResultByPipeType` (last wins)

| # | Quantity | Resolution |
|---|----------|-----------|
| 1 | `t_F` | `chain.lastFluePipeResult(results).map(_.gas_temp_end)` |
| 2 | `t_fluepipe_end` | Alias for t_F |
| 3 | `t_connector_pipe_mean` | `chain.connectorResult(results).map(_.gas_temp_mean)` |
| 4 | `t_chimney_entrance` | `chain.chimneyResult(results).gas_temp_start` |
| 5 | `t_chimney_mean` | `chain.chimneyResult(results).gas_temp_mean` |
| 6 | `t_chimney_out` | `chain.chimneyResult(results).gas_temp_end` |
| 7 | `t_chimney_wall_top` | `chain.chimneyResult(results).temperature_iob(0)` |

### Pressures (§4.10.4) — sum over regions

| # | Quantity | Resolution |
|---|----------|-----------|
| 8 | `required_delivery_pressure` | pre-firebox pipes + `chain.fluePipeResults(results)` — sum `en13384_pr_all-ph` |
| 9 | `Σ_pR, Σ_pu, Σ_ph` (full) | pre-firebox + **all** postFireboxResults |
| 10 | `Σ_pR_until_fluepipe_end` | pre-firebox + `chain.fluePipeResults(results)` |

### Validations — all matching pipes of each PipeType

| # | Validation | Resolution |
|---|-----------|-----------|
| 11 | `validateVelocitiesInFluePipe` | Validate **all** results in `chain.fluePipeRegion` |
| 12 | `validateVelocitiesInConnectorPipe` | Validate `chain.connectorResult(results)` |
| 13 | `validateVelocitiesInChimneyPipe` | Validate `chain.chimneyResult(results)` |
| 14 | `validateLzMinConstraint` | Sum `lengthSum` across all `chain.fluePipeResults(results)` >= L_Z_min |
| 15 | `validateFluePipeShape` | Access `elements` on all slots in `chain.fluePipeRegion` |

### Other

| # | Quantity | Resolution |
|---|----------|-----------|
| 16 | `η` | `formulas.η_calc(t_F)` — depends on #1 |
| 17 | `en13384_heatingAppliance_temperatures` | Still from `atDraftMin_LoadNominal.t_fluepipe_end` (#2) |
| 18-19 | `last_known_density/velocity` | Implicit via fold's `UpstreamState` propagation |

---

## Implementation Plan

### Phase 1: Core Typeclass Infrastructure (new files, no breaking changes)

New files under `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/`:

- [ ] `UpstreamState.scala` — case class + `fromPipeResult(pr, computeAt)` + `initial(temp)`
- [ ] `CanComputePipeResult.scala` — typeclass trait with `final def mkSlot` + companion with 3 factory functions
- [ ] `PipeSlot.scala` — existential wrapper trait (no `apply` constructor) + `noop` factory only. Created via `tc.mkSlot(...)`
- [ ] `PostFireboxPipeChain.scala` — validated topology, region accessors, `computeAll` fold
- [ ] `TopologyError.scala` — validation error ADT

### Phase 2: Generalize PipeChain (Frame Propagation) — orthogonal concern

Modify `modules/engine/.../models/PipeChain.scala`:

- [ ] Add `PipeChain_Generic` that folds over a `Vector` of incremental descriptors with frame propagation
- [ ] Keep existing `PipeChain_15544_Strict` / `PipeChain_15544_MCE` / `PipeChain_13384` as deprecated aliases

### Phase 3: Generalize Pipe Models

Modify `modules/engine/.../models/pipes.scala`:

- [ ] Add `postFireboxChain: PostFireboxPipeChain` to `Pipes_15544_Alg` / `Pipes_13384_Alg`
- [ ] Refactor `Pipes_15544_Strict` and `Pipes_15544_MCE` to build validated chain from flue/connector/chimney
- [ ] Keep deprecated `flue`/`connector`/`chimney` accessors initially

### Phase 4: Generalize Results

Modify `modules/engine/.../models/results.scala`:

- [ ] Refactor `PipesResult_15544` to use `postFireboxResults: Vector[PipeResult]` + `PostFireboxPipeChain` for region accessors
- [ ] Pressure sums use region-aware methods (fluePipeResults, all results, etc.)
- [ ] Keep deprecated named accessors

### Phase 5: Refactor EN13384 Application

Modify `modules/engine/.../impl/en13384/en13384_common_application.scala`:

- [ ] Replace hardcoded `connector_PipeResult` + `chimney_PipeResult` with chain
- [ ] Build once: `PostFireboxPipeChain.validated(Vector(connectorSlot, chimneySlot))`
- [ ] `computeAll(params, UpstreamState.initial(T_WN), ComputeAt.Middle)`

### Phase 6: Refactor EN15544 Application

Modify `en15544_strict_application.scala`, `en15544_common_application.scala`, `en15544_mce_application.scala`:

- [ ] Build `postFireboxChain` once on the outer application (shared by all AtParams)
- [ ] `CommonAtParams`: replace individual lazy vals with `postFireboxChain.computeAll(params, ...)`
- [ ] Derived temps use chain region accessors: `chain.lastFluePipeResult(results)`, etc.
- [ ] Validations iterate over regions: `chain.fluePipeRegion.flatMap(validateVelocitiesIn)`
- [ ] **HA validation**: `forThermal13384` factory called inside `.mapN { ... }.andThen { ... }` block

Modify `en15544_application_alg.scala`:

- [ ] `AtParams`: replace named pipe result lazy vals with `postFireboxPipeResults: Vector[PipeResult]`

### Phase 7: Refactor YAML Loader

Modify `modules/engine/.../api/FireCalcYAML_Loader.scala`:

- [ ] Build `PostFireboxPipeChain` from DTO pipe list + `PipeChain_Generic` for frames
- [ ] Validate topology at load time (return errors if grammar violated)

### Phase 8: DTO V5

New files under `modules/dto/.../v5/`:

- [ ] `PostFireboxPipeDescr_V5.scala` — tagged union: each entry has `pipeType` + description type discriminator
- [ ] `FireCalcYAML_V5.scala` — `post_firebox_pipes: Seq[PostFireboxPipeDescr_V5]` replaces flue/connector/chimney
- [ ] V4 → V5 migration + codecs

### Phase 9: UI Refactor — Pipe Panel Management

Modify `modules/ui/.../`:

- [ ] Generic `Signal[Vector[(PipeType, PipeResult)]]` replacing individual pipe result signals
- [ ] **Pipe panel UI**: Each post-firebox pipe gets its own collapsible panel in the accordion
  - "Add pipe" button (inserts a new FluePipeT or ConnectorPipeT before the chimney)
  - "Remove pipe" button on each panel (with validation — can't remove the chimney)
  - "Change type" dropdown (FluePipeT ↔ ConnectorPipeT for panels in the FLUE_PIPE region)
  - Topology validation shown as error banner if grammar is violated
- [ ] Update `GraphDataConverter` to iterate over generic pipe list
- [ ] Update accordion/tab components for dynamic pipe count

---

## Critical Files

| File | Change |
|------|--------|
| `engine/ops/generic/` (new dir) | Typeclass, PipeSlot, PostFireboxPipeChain, TopologyError |
| `engine/models/pipes.scala` | Add `postFireboxChain` field |
| `engine/models/PipeChain.scala` | Add `PipeChain_Generic` |
| `engine/models/results.scala` | Generalize PipesResult_* with region-aware sums |
| `engine/impl/en13384/en13384_common_application.scala` | Replace hardcoded connector/chimney |
| `engine/impl/en15544/common/en15544_common_application.scala` | Replace CommonAtParams pipe chain |
| `engine/impl/en15544/strict/en15544_strict_application.scala` | Replace StrictAtParams pipe chain |
| `engine/impl/en15544/mce/en15544_mce_application.scala` | Replace MCEAtParams pipe chain |
| `engine/alg/en15544/en15544_application_alg.scala` | Generalize AtParams trait |
| `engine/api/FireCalcYAML_Loader.scala` | Build generic chain from DTO |
| `dto/v5/` (new dir) | PostFireboxPipeDescr_V5, FireCalcYAML_V5 |
| `ui/models/Variables.scala` | Generic pipe result signals |
| `ui/components/` | Pipe panel add/insert/remove UI |

## Existing Functions to Reuse

- `FlowOnlyMecaFlu_15544.makePipeResult` — delegate from `forFlowOnly15544` factory
- `ThermalMecaFlu_13384.makePipeResult` — delegate from `forThermal13384` factory
- `FlowOnlyMecaFlu_13384.makePipeResult` — delegate from `forFlowOnly13384` factory
- `PipeResult.useless(pt, gas_temp)` — for `PipeSlot.noop` (absent optional pipes)
- `IncrementalPipeDefModule_Common.mkPipeFromIncrDescrWithFinalFrame` — for PipeChain_Generic
- `ComputeAt` enum (already in `EN13384_1_A1_2019_Common_Application`)
- `ConnectorPipe_Module.foldPipeCanBe` pattern — to build `PipeSlot` or `PipeSlot.noop`

## Risks & Mitigations

1. ~~**Path-dependent type cast at PipeSlot boundary**~~: **ELIMINATED** by moving `mkSlot` onto the typeclass. Zero `asInstanceOf` in the design. The compiler proves type equality via singleton narrowing on stable paths.

2. **Singleton type preservation**: Factory functions MUST return `CanComputePipeResult[ConcreteObject.type]` (not widened to `CanComputePipeResult[? <: PipeDescrAlg]`). The inner `val descrAlg` MUST be typed as `ConcreteObject.type`. If either is widened, the compiler loses the path link and types don't unify. Never store typeclass instances with wildcard type params.

3. **Lazy val evaluation order change**: Converting interleaved `lazy val`s to a fold changes evaluation order. Must verify no circular lazy val dependencies break.

4. **HeatingAppliance validation**: Factory functions require validated HA values. Must be called inside `.mapN { ... }.andThen { ... }` block.

5. **Optional connector pipe**: `PipeSlot.noop` passes temperature through unchanged, preserving region accessors.

6. **EN13384 specialized formulas**: `forThermal13384` must capture `en13384_application` (the `EN13384_For_15544_Application` inner class), not a generic EN13384 app.

7. **MCE T_WN pinning**: Current MCE pins `T_WN` to `atDraftMin_LoadNominal` value. Direct propagation uses per-AtParams values. Minor behavior change (more correct). Verify via CasType_MCE tests.

8. **Topology validation at UI level**: User can create invalid topologies while editing (e.g., no chimney). Show validation errors as warnings, allow saving invalid state (to avoid data loss), but block computation.

## Verification

1. `sbt "engine/test"` — all existing tests must pass after each phase
2. `sbt "engine/testOnly *PressureLoss*"` — pressure calculation regression
3. `sbt "engine/testOnly *CasType*"` — integration test cases (Strict, MCE, 13384)
4. `sbt compile` — full compilation check
5. Manual: run the web UI, verify graph renders correctly with same values as before
6. Manual: test add/remove pipe panels in UI, verify topology validation errors appear correctly
