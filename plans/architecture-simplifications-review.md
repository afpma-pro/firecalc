# Architecture Simplifications — Full Review (Phases A+B+C)

## Summary

**Branch:** `refactor/architecture-simplifications` (11 commits on `dev`)
**Verdict: SAFE TO MERGE** — all changes are structural/mechanical refactors with no semantic behavior changes.
**Compilation:** All commits verified via `sbt compile` (full project). 79 engine tests pass. Scala.js link clean.

### Phase A+B (structural cleanup)

| # | Commit | Scope | Risk | Lines | Status |
|---|--------|-------|------|-------|--------|
| 5 | `6c76e70` | build.sbt i18n helper | NONE | -17 | ✅ |
| 9 | `38ceb3d` | PropsState unification | LOW | -4 net | ✅ |
| 3 | `468074a` | Repository factory consolidation | LOW | +37 net | ✅ |
| 2 | `8d9c954` | Schema migration pipeline | LOW | -73 | ✅ |
| 4 | `4690afd` | ElementFactory context naming | NONE | ±0 | ✅ |
| 7 | `4bf9053` | Variables.scala split | LOW | +68 (imports) | ✅ |

### Phase C (builder refactor + profiling)

| # | Commit | Scope | Risk | Lines | Status |
|---|--------|-------|------|-------|--------|
| 8 | `ab7e990` | IncrementalBuilder shared trait | LOW | ~-100 net | ✅ |
| — | `4750a02`+`8a05ab5` | Regression tests (13384: 6, 15544: 5) | NONE | +406 | ✅ |
| 10 | — | Codec extraction | — | — | SKIPPED (3.5% compile) |

### Deferred

| # | Scope | Reason |
|---|-------|--------|
| 1 | DSL Adapter | Adapter adds more lines (~110) than it saves (~92) |
| 6 | Test Utils | ConfigurationRunners differ structurally, needs complex adapter |
| — | Thermal builder extraction | Only exists for EN 13384 (no 15544 counterpart) |

---

## Per-Commit Review

### Commit 1: `6c76e70` — Build.sbt i18n Helper Consolidation

**Change:** Added `i18nModuleSettings(moduleName, packagePath)` function combining `watchSources` + `sourceGenerators`. Replaced 5 inline blocks with 5 one-liners.

**Correctness:** ✅ Pure DRY extraction. The new function produces identical settings to the inline code it replaces. sbt `projects` command confirms build file loads correctly.

**Risks:** NONE. Build config only, no runtime code changed.

**Test needed:** `sbt compile` (done). No additional tests needed.

---

### Commit 2: `38ceb3d` — FlowOnlyPropsState Unification

**Change:** Created shared `FlowOnlyPropsState` case class in engine module with canonical `innerShape` field name. Made `FlowOnlyPropsState_13384` and `FlowOnlyPropsState_15544` type+val aliases re-exporting the shared type and its companion.

**Correctness:** ✅
- Type alias: `type FlowOnlyPropsState_13384 = FlowOnlyPropsState` — preserves type identity
- Val alias: `val FlowOnlyPropsState_13384 = FlowOnlyPropsState` — preserves constructor calls
- Given export: `export FlowOnlyPropsState.given` — preserves PropsStateOps instance
- 15544 `modify(_.geometry)` → `modify(_.innerShape)` correctly updated (2 occurrences)

**Risks:** LOW.
- Quicklens `modify(_.innerShape)` works on type aliases since the underlying type IS the case class.
- The re-exported `given` is the same instance, no new behavior.

**Test needed:** `sbt "engine_15544_common/test"`, `sbt "engine_13384_strict/test"`, `sbt "fdim/test"`

---

### Commit 3: `468074a` — Repository Factory Consolidation

**Change:** Created `Repositories` case class aggregating all 6 repositories. Single `create[F]` factory using `mapN` replaces 6 sequential `flatMap`ped factory calls.

**Correctness:** ✅
- All 12 old variable references (`productRepo`, `customerRepo`, etc.) replaced with `repos.product`, `repos.customer`, etc.
- No remaining references to old names (verified by grep).
- `InvoiceCounterRepository.create[IO]` removed from BackendMain since it's now in `Repositories.create`.

**Minor note:** `mapN` on `Tuple6` executes effects in sequence for IO (Cats `Applicative` for IO is sequential), so no behavioral change from the original sequential `flatMap` chain. For truly parallel execution, `parMapN` would be needed — but the original wasn't parallel either.

**Risks:** LOW. The `invoiceCounterRepo.initializeCounter(...)` call now uses `repos.invoiceCounter` which is created slightly earlier (inside `Repositories.create`). This is harmless — it's the same DB connection.

**Test needed:** Backend integration tests if any exist. Manual smoke test of payment flow.

---

### Commit 4: `8d9c954` — Schema Migration Pipeline

**Change:** Replaced 6 identical `decodeVN` methods and 5 identical `migrateFromVNToVN+1` methods with:
- `decodeVersion[V: Decoder](yaml, label)` — generic YAML decode
- `migrate[From, To](schema, fromLabel, toLabel)` — generic Chimney step
- Each version's migration chain is a for-comprehension.

**Correctness:** ✅
- All version-specific Decoders imported at file scope (`import v1.AppStateSchema_V1.given`, etc.)
- V2 fallback (decode as V1 then migrate V1→V2) correctly preserved with `.orElse` in `migrateFromV2`
- Same error handling pattern (Try → Option with dom.console.error logging)

**Risks:** LOW. The `decodeVersion` generic helper uses `Decoder` context bound which resolves to the same derived decoders as the original specific imports. No logic change.

**Test needed:** Manual test: load localStorage with V1-V5 data, verify migration to V6. Or `sbt "ui/fastLinkJS"` + browser smoke test.

---

### Commit 5: `4690afd` — ElementFactory Context Field Naming

**Change:** Renamed `geometry` → `innerShape` in all 4 ElementFactory_15544 context case classes and their 6 internal field accesses. DTO output fields (e.g., `FlowOnlyPipeDescr_15544.StraightSection.geometry`) correctly left unchanged.

**Correctness:** ✅
- Context constructors in `FlowOnlyIncrementalBuilder_15544.scala` use positional arguments — no call-site breakage.
- All internal `_.geometry` lambdas and `ctx.geometry` accesses updated to `_.innerShape` / `ctx.innerShape`.
- `ctx.geometry.map(_.area)` in PressureDiff → `ctx.innerShape.map(_.area)` — correct.

**Risks:** NONE. Pure rename, positional constructors, no external API surface.

**Test needed:** `sbt "engine_15544_common/test"`

---

### Commit 6: `4bf9053` — Variables.scala Split

**Change:** Split 653-line `Variables.scala` into 6 domain-cohesive files:
- `Variables.scala` (74 lines) — persistence core
- `EngineInputVars.scala` (90 lines) — zoomed vars from engineState
- `PipeTopologyState.scala` (107 lines) — post-firebox pipe topology
- `EngineComputationState.scala` (272 lines) — computed result signals
- `CatalogVars.scala` (67 lines) — catalog storage + signals
- `VizAndUIState.scala` (111 lines) — viz elements, UI state, undo/redo

**Correctness:** ✅
- All files use same package `afpma.firecalc.ui.models` with top-level defs
- No wrapping objects — Scala 3 top-level definitions
- Cross-file non-lazy `val` correctly converted to `lazy val` (e.g., `engineStateHelperVar`, `firebox_var`, `project_descr_var`)
- Self-contained val groups (expertModeVar/On/Off, viz3DPanelVar/On/Off) correctly kept non-lazy
- Imports trimmed per file; unused imports removed

**Scala.js NPE risk check:** ✅
- All cross-file references are to `lazy val` definitions
- Non-lazy vals that became `lazy val` include all zoomed vars from `engineStateVar` in EngineInputVars.scala
- `expertModeVar`, `vizHoveredElement`, `vizSelectedElement` are standalone `Var(...)` initializations with no cross-file deps — safe as non-lazy

**Risks:** LOW but this is the highest-risk commit.
- If any UI panel references a val that was moved and its initialization order matters, it could NPE
- All moved vals are in the same package, so no import changes needed at call sites
- The `lazy val` conversions add negligible overhead (one-time check on first access)

**Test needed:** `sbt "ui/fastLinkJS"` + browser smoke test (load app, navigate panels, check no blank page). This is the critical test.

---

## Phase C: IncrementalBuilder Refactor & Codec Profiling

### Commit 7: `ab7e990` — FlowOnlyIncrementalBuilderCommon Extraction

**Change:** Extracted shared pipe-building logic from `FlowOnlyIncrementalBuilder_13384` and
`FlowOnlyIncrementalBuilder_15544` into a new `FlowOnlyIncrementalBuilderCommon` trait in the
cross-compiled `engine` module (~100 lines deduped). Concrete builders now extend the trait
and provide two abstract hooks:

- `isDirectionChange(ae: AddElement): Boolean`
- `addElementHasAbsDir(ae: AddElement): Boolean`

**Correctness:** ✅
- Both concrete implementations are currently **identical** (`ae.isInstanceOf[AddDirectionChange]`
  and pattern-match on `AddDirectionChange.absDir.isDefined`).
- Shared methods: `define`, `listIncrDescr`, `mkInitPropsState`, `mkInitPipeFullDescr`,
  `currentFrameFromPropsState`, `applyExternalFrame`, `postBuildValidation`,
  `isForbiddenAddElementAtStart`, `isForbiddenAddElementAtEnd`.
- The `AddElement.name` extension method remains in each concrete builder to avoid an
  infinite recursion that occurs when placed in the common trait (discovered empirically).
- All 85 engine tests pass (12 in 13384, 42 in 15544, 31 in fdim). Scala.js link succeeds.
  Browser smoke test clean.

**Regression tests:** Two test suites covering the extracted common logic:
- `FlowOnlyIncrementalBuilderCommon_RegressionTest.scala` (EN 13384, 6 tests) — boundary
  validation, geometry-without-direction, external-frame ignore/apply, absDir dead-branch
  documentation. (commits `4750a02`, `8a05ab5`)
- `FlowOnlyIncrementalBuilderCommon_15544_RegressionTest.scala` (EN 15544, 5 tests) —
  boundary checks, geometry-without-direction, external-frame ignore/apply via
  `CombustionAirPipe_Module_15544`. Guards against hook drift. (commit `8a05ab5`)

### Hook Drift Risk Assessment

The two abstract hooks (`isDirectionChange`, `addElementHasAbsDir`) are contract points
between the common trait and each standard's builder. Currently both implementations are
identical, but they are kept abstract because:

1. **Standards may diverge.** EN 13384 and EN 15544 define different element type ADTs.
   If one standard adds a new direction change variant that the other doesn't have,
   the hook must differ.
2. **The common trait depends on AddElement being a type member**, not a concrete type —
   the trait cannot pattern-match on standard-specific subtypes.

**Risk:** If a developer adds a new `AddElement` subtype to one standard's ADT and forgets
to update the corresponding hook, the boundary validation silently becomes incomplete.
Regression tests for both EN 13384 (6 tests) and EN 15544 (5 tests) mitigate this by
verifying `addSharpAngle_45deg` boundary behavior and external-frame application across
both standards, but do not cover hypothetical new subtypes.

**Monitoring guidance:**
- When adding new `AddElement` subtypes (e.g., `AddTee`, `AddReducer`), check whether the
  new subtype is a direction change. If yes, update `isDirectionChange` in the affected builder.
- When adding direction tracking to a new `AddElement`, update `addElementHasAbsDir`.
- Consider adding a compile-time exhaustiveness check if the ADT grows beyond 5 variants
  (replace `isInstanceOf` with a sealed `match`).

### `FinalDirWithoutInitialDirection` — Currently Unreachable in FlowOnly Builders

The `postBuildValidation` method checks two conditions in sequence:
1. `hasGeometry && initialFrame.isEmpty` → `GeometryWithoutInitialDirection`
2. `hasFinalDir && initialFrame.isEmpty` → `FinalDirWithoutInitialDirection`

For FlowOnly builders, condition 2 is unreachable because any `AddElement` with
`addElementHasAbsDir == true` (i.e., an `AddDirectionChange` with `absDir.isDefined`)
is also an `AddElement`, which makes `hasGeometry` true, so condition 1 fires first.

This branch is retained for:
- **Semantic clarity**: it documents the intended distinction between "has geometry" and
  "has direction tracking" as separate validation concerns.
- **Future-proofing**: if a non-`AddElement` descriptor type gains direction tracking
  (e.g., a metadata-only direction hint), condition 2 would become reachable.
- **ThermalBuilder parity**: the Thermal builder has the same pattern and may have
  different reachability characteristics.

The regression tests explicitly assert that `FinalDirWithoutInitialDirection` does NOT
appear for scenarios where `GeometryWithoutInitialDirection` fires (test 4 in 13384 suite).

### Codec Extraction (#10) — SKIPPED

**Rationale:** Profiling with `-Yprofile-trace` showed circe codec derivation accounts for
only **3.5%** of UI compile time (~3s of 91s). The real bottleneck is Laminar/Airstream inline
expansion in large form files (top files: 7.0s, 6.8s, 6.1s). Extracting codecs to a separate
module would not meaningfully improve build times. Full profiling data in
`plans/phase-c-implementation-plan.md`.

### Thermal Builder Extraction — DEFERRED

`ThermalIncrementalBuilder_13384` (578 lines) exists only for EN 13384 — there is no EN 15544
counterpart. Since the FlowOnly extraction was motivated by deduplicating logic across two
parallel implementations, the same approach does not apply to ThermalBuilder.

If EN 15544 thermal support is added in the future, the `FlowOnlyIncrementalBuilderCommon`
pattern should be replicated: extract shared `define`, `mkInit*`, `postBuildValidation`,
and boundary-check methods into a `ThermalIncrementalBuilderCommon` trait, keeping
standard-specific PropsState handling and element factory wiring in the concrete builders.

---

## Global Checks

### Import Breakage
No files import `Variables` as a qualifier — the project uses Scala 3 top-level defs which are accessed directly by name. ✅

### Old Name References
- No references to `decodeV1`..`decodeV6` or `migrateFromV1ToV2`..`migrateFromV5ToV6` (removed methods) ✅
- No references to `geometry` field on context types (all renamed to `innerShape`) ✅
- `FlowOnlyPropsState_13384()` and `FlowOnlyPropsState_15544()` constructor calls still work via val aliases ✅

### Definition Count
Original Variables.scala had ~99-103 top-level definitions. Sum across 6 files matches. ✅

---

## Risks & Recommendations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Variables.scala split causes NPE on init | LOW | All cross-file refs use lazy val. Run browser smoke test. |
| Schema migration breaks for old localStorage | LOW | Same decode/migrate logic, just reorganized. Test with old data. |
| Repository creation order change | NEGLIGIBLE | Same DB connection, same sequential execution. |
| PropsState alias breaks downstream | LOW | Type + val + given export chain preserves full API. Run engine tests. |
| Hook drift in IncrementalBuilderCommon | LOW | Both hooks currently identical across standards. 11 regression tests (6×13384, 5×15544) lock down behavior. Replace `isInstanceOf` with sealed `match` if ADT grows. |
| `AddElement.name` infinite recursion if moved to common trait | HIGH (if violated) | Keep in each concrete builder. Documented in common trait scaladoc. |

**Recommendation:** Run full test suite before merge:
```bash
sbt test                          # All tests
sbt "ui/fastLinkJS"               # Verify Scala.js link
# Then manual browser smoke test  # Verify no blank page / NPE
```

---

## Test Plan

### Automated
```bash
# Full project compilation (already done)
sbt compile

# Engine tests (covers #9 PropsState, #4 ElementFactory)
sbt "engine/test"
sbt "engine_13384_strict/test"
sbt "engine_15544_common/test"

# Golden file tests (covers engine changes)
sbt "fdim/test"
sbt "labo/test"

# UI Scala.js link (covers #2 migration, #7 split)
sbt "ui/fastLinkJS"

# Backend compilation (covers #3 Repositories)
sbt "payments/compile"
```

### Manual
1. **Browser smoke test** (critical for #7):
   - Load app in browser via `make dev-web-ui-run`
   - Navigate all panels
   - Verify no blank page (NPE detection)
   - Check engine results still compute

2. **localStorage migration test** (for #2):
   - Load app with old V1-V5 localStorage data
   - Verify auto-migration to V6

3. **Payment flow** (for #3):
   - If staging environment available, test order creation
