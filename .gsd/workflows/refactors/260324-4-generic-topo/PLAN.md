# Plan: Generic Post-Firebox Pipe Topology Refactor

## Wave Structure

The refactor is broken into 6 waves. Each wave leaves the codebase compiling and tests passing. Waves 1–2 are purely additive (zero risk). Waves 3–5 are the core migration. Wave 6 is cleanup and UI.

---

## Wave 1: Core Typeclass Infrastructure (additive, no breaking changes)

**Goal:** Introduce all new types in `engine/ops/generic/`. Nothing depends on them yet.

**Files (all new):**
- `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/TopologyError.scala`
- `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/UpstreamState.scala`
- `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/PipeSlot.scala`
- `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/PostFireboxPipeChain.scala`
- `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/CanComputePipeResult.scala`

**Tests (new):**
- `modules/engine/src/test/scala/afpma/firecalc/engine/ops/generic/PostFireboxPipeChainSuite.scala`

**Verification:** `sbt "engine/test"` — all existing tests pass, new topology suite passes.

**Commit:** `refactor(engine): wave 1 — core typeclass infrastructure for generic pipe topology`

---

## Wave 2: Additive Model Extensions (additive, deprecated aliases)

**Goal:** Add `PostFireboxPipeChain` fields to model types alongside existing fields. Old fields are preserved and still functional.

**Files (modify):**
- `modules/engine/src/main/scala/afpma/firecalc/engine/models/PipeChain.scala`
  — Add `PipeChain_Generic` object with a fold over `Vector` of incremental descriptors + frame propagation
- `modules/engine/src/main/scala/afpma/firecalc/engine/models/results.scala`
  — Add region-aware pressure sum helpers to `PipesResult_15544` (alongside existing named sums)

**Verification:** `sbt "engine/test"` — all existing tests pass unchanged.

**Commit:** `refactor(engine): wave 2 — additive model extensions for generic pipe chain`

---

## Wave 3: EN13384 Application Migration

**Goal:** Migrate the EN13384 standalone application to use the generic pipe chain internally. This is the simplest case (only connector + chimney, no flue region).

**Files (modify):**
- `modules/engine/src/main/scala/afpma/firecalc/engine/impl/en13384/en13384_common_application.scala`
  — Build `PostFireboxPipeChain.validated(Vector(connectorSlot, chimneySlot))` using `CanComputePipeResult.forThermal13384`
  — Replace `connector_PipeResult` / `chimney_PipeResult` to delegate to chain's `computeAll`
  — Keep API contract (return types) identical — callers don't see the change
- `modules/engine/src/main/scala/afpma/firecalc/engine/alg/en13384/en13384_application_alg.scala`
  — No change yet (API contract preserved in wave 3)

**Verification:**
- `sbt "engine/testOnly *cas_types_13384*"` — EN13384 integration tests pass
- `sbt "engine/testOnly *MecaFlu_13384*"` — MecaFlu unit tests pass
- `sbt "engine/test"` — full suite green

**Commit:** `refactor(engine): wave 3 — EN13384 application uses generic pipe chain internally`

---

## Wave 4: EN15544 Strict Application Migration

**Goal:** Migrate the EN15544 Strict variant. This is the most complex case: flue (FlowOnly15544) + connector (Thermal13384) + chimney (Thermal13384) with a cross-algorithm boundary.

**Files (modify):**
- `modules/engine/src/main/scala/afpma/firecalc/engine/impl/en15544/strict/en15544_strict_application.scala`
  — Create `CanComputePipeResult.forFlowOnly15544(...)` for flue pipe
  — Create `CanComputePipeResult.forThermal13384(...)` for connector + chimney (inside `.mapN { ... }.andThen { ... }` block for HA validation)
  — Build `PostFireboxPipeChain.validated(Vector(flueSlot, connectorSlot, chimneySlot))`
  — `StrictAtParams.flue_PipeResult` / `connector_PipeResult` / `chimney_PipeResult` delegate to chain region accessors
- `modules/engine/src/main/scala/afpma/firecalc/engine/impl/en15544/common/en15544_common_application.scala`
  — `CommonAtParams`: derive `connector_PipeResult` / `chimney_PipeResult` / temps from chain when available
  — Validation methods iterate over chain regions
  — `required_delivery_pressure` uses `chain.fluePipeResults(results)`
  — `validateLzMinConstraint` sums lengths across `chain.fluePipeRegion`

**Verification:**
- `sbt "engine/testOnly *cas_types_15544*"` — EN15544 C1/C2/C3 integration tests pass
- `sbt "engine/testOnly *PressureLoss*"` — pressure calculation regression
- `sbt "engine/test"` — full suite green

**Commit:** `refactor(engine): wave 4 — EN15544 Strict application uses generic pipe chain`

---

## Wave 5: EN15544 MCE Application Migration

**Goal:** Migrate the MCE variant. All post-firebox pipes use Thermal13384 — simpler than Strict but shares `CommonAtParams`.

**Files (modify):**
- `modules/engine/src/main/scala/afpma/firecalc/engine/impl/en15544/mce/en15544_mce_application.scala`
  — Create `CanComputePipeResult.forThermal13384(...)` (single typeclass instance for all post-firebox pipes)
  — Build `PostFireboxPipeChain.validated(Vector(flueSlot, connectorSlot, chimneySlot))`
  — `MCEAtParams.flue_PipeResult` / `connector_PipeResult` / `chimney_PipeResult` delegate to chain
  — Handle `last_known_density/velocity` via `UpstreamState` propagation instead of `ComputeAt` pattern-matching
- `modules/engine/src/main/scala/afpma/firecalc/engine/alg/en15544/en15544_application_alg.scala`
  — Update `EN13384_For_15544_Application` overrides of `last_known_density/velocity_before_connector_pipe` — these now come from the chain fold naturally

**Verification:**
- `sbt "engine/testOnly *CasType*"` — all CasType integration tests (Strict + MCE + 13384)
- `sbt "engine/test"` — full suite green

**Commit:** `refactor(engine): wave 5 — EN15544 MCE application uses generic pipe chain`

---

## Wave 6: YAML Loader + UI + Cleanup (scope boundary — may be deferred)

**Goal:** Wire the generic topology through to the DTO and UI layers.

**Sub-waves:**

### 6a: YAML Loader
- `modules/engine/src/main/scala/afpma/firecalc/engine/api/FireCalcYAML_Loader.scala`
  — Build `PostFireboxPipeChain` from existing DTO pipe descriptors at load time
  — Validate topology grammar; return errors on violations

### 6b: DTO V5 (if needed — can be deferred)
- New files in `modules/dto/src/main/scala/afpma/firecalc/dto/v5/`
- V4→V5 migration + codecs
- Only needed when UI supports dynamic pipe count

### 6c: UI Adaptation
- `modules/ui/src/main/scala/afpma/firecalc/ui/models/Variables.scala` — generic pipe result signals
- `modules/ui/src/main/scala/afpma/firecalc/ui/models/EngineState.scala` — adapt state
- `modules/ui/src/main/scala/afpma/firecalc/ui/viz/GraphDataConverter.scala` — iterate over generic pipe list
- `modules/ui/src/main/scala/afpma/firecalc/ui/panels/FluePipePanel.scala` — dynamic pipe count
- `modules/ui/src/main/scala/afpma/firecalc/ui/panels/ConnectingPipePanel.scala` — optional via PipeSlot
- `modules/ui/src/main/scala/afpma/firecalc/ui/panels/ChimneyPipePanel.scala` — always last

**Verification:**
- `sbt compile` — full compilation (JVM + JS)
- `sbt "engine/test"` — engine regression
- Manual: run web UI, verify graph renders correctly

**Commit:** `refactor(engine): wave 6a — YAML loader builds generic pipe chain`
*UI commits as separate sub-waves.*

---

## Risk Mitigations Per Wave

| Risk | Wave | Mitigation |
|------|------|-----------|
| Singleton type widening | 1 | Factory function return types explicitly annotated; compile-time test |
| Lazy val evaluation order | 3–5 | Run full CasType integration suite after each wave — numerical regression detectable |
| MCE T_WN pinning behavior | 5 | Compare MCE CasType test outputs before/after; minor diff is expected and correct |
| EN13384 inner application capture | 4 | Factory called inside `.andThen` block after HA validation — matches existing pattern |
| Cross-wave breakage | All | Each wave compiles + tests independently; waves are committed separately |

---

## Decision: Scope Boundary

Waves 1–5 form the **engine core** refactor. Wave 6 (YAML loader, DTO V5, UI) is a separate concern that can be executed in a follow-up milestone. The engine can run with the generic topology internally while the UI and DTO continue using the existing fixed-field API through the preserved deprecated accessors.

**Recommendation:** Execute Waves 1–5 now. Defer Wave 6 unless the user wants the full stack done in one pass.
