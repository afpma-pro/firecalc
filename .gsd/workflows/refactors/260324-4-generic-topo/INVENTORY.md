# Inventory: Generic Post-Firebox Pipe Topology Refactor

## Summary

Replace the fixed 3-pipe post-firebox topology (flue → connector → chimney) with a generic `Vector[PipeSlot]` using a typeclass-based strategy pattern (`CanComputePipeResult[D]`). This eliminates the hardcoded pipe types per application variant and enables N-pipe topologies.

**Estimated scope:** ~35 files modified, ~5 new files, ~2,600 lines of application code touched across engine + UI.

---

## 1. New Files (Engine Core — Phase 1)

Target directory: `modules/engine/src/main/scala/afpma/firecalc/engine/ops/generic/`

| File | Purpose |
|------|---------|
| `UpstreamState.scala` | Case class for inter-pipe state propagation (temp, density, velocity) |
| `CanComputePipeResult.scala` | Typeclass trait with `mkSlot` + companion with 3 factory functions |
| `PipeSlot.scala` | Existential wrapper trait + `noop` factory |
| `PostFireboxPipeChain.scala` | Validated topology container, region accessors, `computeAll` fold |
| `TopologyError.scala` | Validation error ADT for topology grammar |

---

## 2. Engine Model Files (Modify)

### 2.1 Core Models

| File | Lines | Change |
|------|-------|--------|
| `models/PipeChain.scala` | 116 | Add `PipeChain_Generic` fold; keep `PipeChain_15544_Strict`, `PipeChain_15544_MCE`, `PipeChain_13384` as deprecated |
| `models/pipes.scala` | 52 | Add `postFireboxChain: PostFireboxPipeChain` field to `Pipes_15544_Alg` / `Pipes_13384_Alg`; keep deprecated `flue`/`connector`/`chimney` |
| `models/results.scala` | 295 | Refactor `PipesResult_15544` to use `postFireboxResults: Vector[PipeResult]` + region-aware sums; keep deprecated named accessors |
| `models/PipeType.scala` | 49 | No change needed (types already exist) |
| `models/PipeDescrAlg.scala` | 65 | No change needed (trait already abstract enough) |
| `models/PipeIncrAndFullDescrG.scala` | 200 | No change needed |

### 2.2 Existing MecaFlu Ops (delegate targets — no signature changes)

| File | Lines | Change |
|------|-------|--------|
| `ops/en15544/FlowOnlyMecaFlu_15544.scala` | ~498 | No change — factory function delegates to existing `makePipeResult` |
| `ops/en13384/ThermalMecaFlu_13384.scala` | ~883 | No change — factory function delegates to existing `makePipeResult` |
| `ops/en13384/FlowOnlyMecaFlu_13384.scala` | ~493 | No change — factory function delegates to existing `makePipeResult` |
| `ops/MecaFluAlg.scala` | ~80 | No change — commented-out `makePipeResult` stays as-is |

---

## 3. Engine Application Files (Major Refactor)

### 3.1 EN 15544 Application

| File | Lines | Change |
|------|-------|--------|
| `alg/en15544/en15544_application_alg.scala` | 233 | Replace named pipe result lazy vals in `AtParams` with `postFireboxPipeResults: Vector[PipeResult]` |
| `impl/en15544/common/en15544_common_application.scala` | 1069 | Replace `CommonAtParams` individual pipe lazy vals with `postFireboxChain.computeAll()`; derived temps use region accessors; validations iterate over regions |
| `impl/en15544/strict/en15544_strict_application.scala` | 315 | Build `PostFireboxPipeChain` from flue/connector/chimney; create `CanComputePipeResult.forFlowOnly15544` + `.forThermal13384`; replace hardcoded `makePipeResult` calls |
| `impl/en15544/mce/en15544_mce_application.scala` | 410 | Same pattern as strict but using `forThermal13384` for all post-firebox pipes |

### 3.2 EN 13384 Application

| File | Lines | Change |
|------|-------|--------|
| `alg/en13384/en13384_application_alg.scala` | 268 | Replace `connector_PipeResult` / `chimney_PipeResult` with chain-based equivalents |
| `impl/en13384/en13384_common_application.scala` | 825 | Build `PostFireboxPipeChain.validated(Vector(connectorSlot, chimneySlot))`; `computeAll()` fold; replace individual pipe result computation |

### 3.3 EN 13384 Variants (indirect consumers)

| File | Lines | Change |
|------|-------|--------|
| `impl/en13384/en13384_withFlowOnlyAirIntake_application.scala` | ~100 | Minor — inherits from common, may need adapter |
| `impl/en13384/en13384_withThermalAirIntake_application.scala` | ~100 | Minor — inherits from common, may need adapter |
| `impl/en13384/en13384_for15544_application.scala` | ~100 | Adapter between EN15544 and EN13384 — needs update for chain |

---

## 4. YAML Loader

| File | Lines | Change |
|------|-------|--------|
| `api/FireCalcYAML_Loader.scala` | ~120 | Build `PostFireboxPipeChain` from DTO pipe list + validate topology at load time |

---

## 5. DTO (V5 — New)

Target: `modules/dto/src/main/scala/afpma/firecalc/dto/v5/` (new directory)

| File | Purpose |
|------|---------|
| `PostFireboxPipeDescr_V5.scala` | Tagged union: `pipeType` + description type discriminator per slot |
| `FireCalcYAML_V5.scala` | New schema: `post_firebox_pipes: Seq[PostFireboxPipeDescr_V5]` replaces flue/connector/chimney |
| V4→V5 migration | Codec + migration from V4 format |

---

## 6. UI Files

| File | Lines | Change |
|------|-------|--------|
| `ui/models/Variables.scala` | ~230 | Generic `Signal[Vector[(PipeType, PipeResult)]]` replacing individual pipe result signals |
| `ui/models/EngineState.scala` | 154 | Adapt to generic pipe chain state |
| `ui/panels/FluePipePanel.scala` | 495 | Adapt for dynamic pipe count in flue region |
| `ui/panels/ConnectingPipePanel.scala` | 71 | Adapt for optional connector via PipeSlot |
| `ui/panels/ChimneyPipePanel.scala` | 72 | Adapt — chimney always last |
| `ui/viz/GraphDataConverter.scala` | 347 | Iterate over generic pipe list instead of hardcoded flue/connector/chimney |
| `ui/viz/GraphPanel.scala` | 69 | Minor — consumes converted data |
| `ui/instances/defaultable.scala` | ~100 | Default values for new pipe chain state |

---

## 7. Test Files

| File | Purpose | Change |
|------|---------|--------|
| `cas_types/en15544/cas_types_15544_C1.scala` | Integration: EN15544 C1 | Regression — must pass unchanged |
| `cas_types/en15544/cas_types_15544_C2.scala` | Integration: EN15544 C2 | Regression — must pass unchanged |
| `cas_types/en15544/cas_types_15544_C3.scala` | Integration: EN15544 C3 | Regression — must pass unchanged |
| `cas_types/en13384/cas_types_13384_C2.scala` | Integration: EN13384 C2 | Regression — must pass unchanged |
| `cas_types/en13384/cas_types_13384_C16.scala` | Integration: EN13384 C16 | Regression — must pass unchanged |
| `CasTypesRunner_15544_Strict.scala` | Runner base class | May need update for chain result access |
| `CasTypesRunner_13384_Common.scala` | Runner base class | May need update for chain result access |
| `ops/MecaFlu_13384_Suite.scala` | Unit: MecaFlu 13384 | Regression |
| `ops/en15544/FlowOnlyDynamicFrictionCoeff_15544.scala` | Unit: friction coeff | Regression |
| `SingleTested_Integration_Suite.scala` | Integration: single tested firebox | Regression |

**New tests to add:**
- `PostFireboxPipeChainSuite.scala` — topology validation (grammar rules 1–5)
- `CanComputePipeResultSuite.scala` — typeclass factory + `mkSlot` cast-free verification

---

## 8. Config/Build Files

| File | Change |
|------|--------|
| `build.sbt` | No change expected — new files go in existing module |

---

## 9. Dependency Order

```
Phase 1: New core types (UpstreamState, CanComputePipeResult, PipeSlot, PostFireboxPipeChain, TopologyError)
    ↓ no existing code depends on these yet
Phase 2: PipeChain_Generic (orthogonal to Phase 1 — for frame propagation)
    ↓ additive
Phase 3: Pipe models (add postFireboxChain field to Pipes_*_Alg)
    ↓ additive — deprecated accessors preserved
Phase 4: Result models (add postFireboxResults to PipesResult_*)
    ↓ additive — deprecated accessors preserved  
Phase 5: EN13384 application (simpler — only connector+chimney)
    ↓ changes existing computation
Phase 6: EN15544 application (full refactor — flue+connector+chimney)
    ↓ changes existing computation (depends on Phase 5 for inner EN13384)
Phase 7: YAML Loader (topology validation at load time)
    ↓ depends on Phase 1 types
Phase 8: DTO V5 (new schema)
    ↓ depends on Phase 7 for deserialization
Phase 9: UI (dynamic pipe panels)
    ↓ depends on Phases 3–6 for new signals
```

**Critical path:** Phases 1 → 5 → 6 (core typeclass → EN13384 refactor → EN15544 refactor)

---

## 10. Risks Identified

1. **Singleton type preservation** — Factory functions MUST return concrete `CanComputePipeResult[X.type]`. Widening kills compiler proofs.
2. **Lazy val evaluation order** — Converting interleaved lazy vals to fold changes evaluation order. Must verify no circular dependencies.
3. **MCE T_WN pinning** — Current MCE pins T_WN to `atDraftMin_LoadNominal`. Fold propagation uses per-AtParams values (more correct but different).
4. **EN13384 inner application capture** — `forThermal13384` must capture the `EN13384_For_15544_Application` inner class, not generic EN13384.
5. **Optional connector** — `PipeSlot.noop` passes temperature through unchanged. Must verify region accessors still work.
6. **UI topology editing** — Users can create invalid topologies while editing. Show warnings, allow save, block computation.
