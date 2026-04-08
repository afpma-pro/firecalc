# Phase C — Implementation Plan

## Context

Phase A+B is complete and verified (browser smoke test passed). Phase C contains the two deepest refactors:

- **#8** IncrementalBuilder Shared Logic (MEDIUM-HIGH risk)
- **#10** Compile Time Codec Extraction (MEDIUM risk)

Previous commits already reduced #8's risk:
- `innerShape` vs `geometry` field name difference is resolved (commits #9, #4)
- `FlowOnlyPropsState` is already unified (commit #9)
- ElementFactory context naming is consistent (commit #4)

---

## Item #8: IncrementalBuilder Shared Logic

### Analysis Summary

**Files:**
- `FlowOnlyIncrementalBuilder_13384.scala` — 434 lines
- `FlowOnlyIncrementalBuilder_15544.scala` — 402 lines

### Method Classification

**IDENTICAL (can extract directly — ~16 methods, ~50 lines):**
- `name` extension, `isForbiddenAddElementAtStart`, `isForbiddenAddElementAtEnd`
- `define`, `nbOfFlowsFromPropsState`, `isValid`/`nf` extensions
- `mkInitPipeFullDescr`, `currentFrameFromPropsState`, `applyExternalFrame`
- `postBuildValidation`, `ElementFactory` object, `innerShape`, `roughness`
- `setInitialDirection`, `setInitialPosition`, `setFinalPosition`, `addPressureDiff`

**NEAR-IDENTICAL (differ only by type suffix _13384/_15544 — ~15 methods, ~60 lines):**
- `mkInitPropsState` (constructor name)
- `updateStateAfterConversionStep` (AddSectionChange vs AddSectionShapeChange)
- `updateStateBeforeConversionStep` (variable name only)
- `channelsSplit`, `channelsJoin`, given instances for DSLs
- `addSectionSlopped`, `addSectionSloppedForceManualElevationGain`
- `addFlowResistance` variants, `makeFor` companion factory

**DIFFERENT (must stay per-standard — ~7 areas, ~200 lines):**
1. `nextSectionLengthOpt` — different algorithm (allRemainingOps vs nextOpIfAddElement)
2. `mkFullElementsDescr` — LARGEST divergence:
   - 15544 auto-inserts SectionGeometryChange when geometry changes
   - 15544 uses 3-tuple intermediate (PipeIdx, Option[String], PipeElDescr)
   - 13384 DirectionChangeCtx takes extra `nextSectionLengthOpt` param
3. `material` — different Material type (Material_13384 vs Material_15544)
4. Direction change builders — 18 methods (13384) vs 6 methods (15544)
5. Section change builders — diameter-based (13384) vs shape-based (15544)
6. `addSectionHorizontal`/`addSectionVertical` — different delegation
7. 13384-only: `addRainCapEN13384_*`, `Aux` type alias

### Proposed Approach

**Create `FlowOnlyIncrementalBuilderCommon` trait** in `engine/` with:

1. **Abstract type members** for standard-specific types:
   ```scala
   type PipeElDescr
   type IncrDescr
   type SetProp
   type AddElement
   type PT <: PipeType
   type PropsState
   type MaterialType
   ```

2. **Concrete implementations** for all IDENTICAL methods (directly in the trait)

3. **Abstract methods** for DIFFERENT logic:
   ```scala
   protected def nextSectionLengthOpt(convStep: ConversionStep): Option[QtyD[Meter]]
   protected def mkFullElementsDescr(...): CtxValidatedResult[...]
   def material(m: MaterialType): SetProp
   ```

4. **Each variant** extends the common trait, provides type bindings, and
   implements only the abstract methods + standard-specific builder methods.

### Estimated Impact
- ~110 lines moved to shared trait (IDENTICAL methods)
- ~60 lines simplified in each variant (NEAR-IDENTICAL → delegate to super with type params)
- Each variant shrinks from ~430/400 lines to ~280/250 lines
- New shared trait: ~150 lines

### Risk Mitigation
- The builders are the core calculation pipeline — golden file tests (fdim, labo) catch regressions
- Extract one method at a time, compile after each
- `updateStateBeforeConversionStep` and `updateStateAfterConversionStep` are highest-value extractions
  (near-identical, core pipeline, rarely need per-standard changes)

---

## Item #10: Compile Time Codec Extraction

### Analysis Summary

**Current state:**
- 35 `semiauto.deriveEncoder/Decoder` calls across 12 files in `modules/ui/`
- `-Xmax-inlines:40` set for UI module (raised from default 32 specifically for circe)
- Build comment already suggests: "extract this logic into its own subproject"
- No `-Yprofile-trace` has been run yet

### Codec Locations

| Location | Types | Count | Change Frequency |
|----------|-------|-------|-----------------|
| Schema V1-V6 companions | AppStateSchema_V1..V6 | 12 | Very rare (only on new schema) |
| ClientProjectData_V1 companion | ClientProjectData_V1 | 2 | Very rare |
| CatalogStateCodec object | 5 catalog types | 10 | Rare |
| circe instances object | BillingInfo, ClientProjectData | 4 | Rare |
| ProjectEntry companion | ProjectEntry | 2 | Rare |
| UIState companion | CameraState | 2 | Rare |
| LocalConditionsUI companion | LocalConditions_UIClone | 2 | Moderate |

Total: 34 derivations (17 Encoder + 17 Decoder) + 1 hand-written pair

### Proposed Approach

**Preliminary step:** Profile first to confirm codecs are the bottleneck.
```bash
# Uncomment -Yprofile-trace in build.sbt commonSettings
# Run: sbt "ui/compile"
# Analyze: traces/firecalc-ui.trace in Chrome tracing viewer
```

**If confirmed:**

1. **Create `ui-codecs` JS-only module** in `modules/ui-codecs/`
2. **Move all 12 files' codec derivations** into the new module
3. **UI module depends on `ui-codecs`** — panel code no longer triggers codec recompilation
4. **Lower `-Xmax-inlines`** back to 32 for UI module (codecs module keeps 40)

### Estimated Impact
- UI recompilation on panel changes: potentially 30-50% faster (needs profiling)
- Adding a new codec: requires touching `ui-codecs` module
- Build complexity: +1 module

### Risk Assessment
- MEDIUM: Module boundary design needs care — codec types must be visible from both modules
- The `semiauto.derive*` calls reference types from `dto`, `schema`, `models` — the new module
  needs those dependencies
- Schema version codecs + Chimney transformers may need to stay together (both in `ui-codecs`
  or both in `ui`) to avoid circular deps

### Profiling Results (2026-04-08)

Profiling was performed with `-Yprofile-enabled` / `-Yprofile-trace:traces/ui.trace`
on a clean `ui/compile` (102s total wall time).

**Command:**
```bash
sbt 'set ui / scalacOptions += "-Yprofile-enabled"; set ui / scalacOptions += "-Yprofile-trace:traces/ui.trace"; ui / clean; ui / compile'
```

**Top-level compiler phases:**
```
Total compile:   91.3s
Typer:           33.9s (37%)
Inlining:        18.4s (20%)
genSJSIR:         5.1s
genBCode:         4.3s
```

**Top files by compilation time:**
```
7018ms  VerticalFormCommonInstances.scala
6777ms  FlowOnlyHorizontalForm_13384.scala
6051ms  ThermalHorizontalForm_13384.scala
4476ms  AppendLayersComponent.scala
3821ms  DaisyUIVerticalAccordionAndJoin.scala
```

**Circe derivation files (all 12):**
```
 830ms  CatalogState.scala
 581ms  circe.scala
 506ms  LocalConditionsUI.scala
 201ms  VizAndUIState.scala
 149ms  UIState.scala
 148ms  ProjectEntry.scala
 110ms  AppStateSchema_V1.scala
  96ms  ClientProjectData_V1.scala
  90ms  AppStateSchema_V2.scala
  88ms  AppStateSchema_V3.scala
  86ms  AppStateSchema_V5.scala
  86ms  AppStateSchema_V4.scala
  80ms  AppStateSchema_V6.scala
─────
3051ms  TOTAL (3.5% of 86.4s file-level total)
```

### Decision: SKIP #10

Circe codec derivation files account for only **3.5%** of UI compile time (~3s out of ~91s).
The real bottlenecks are large Laminar UI form files (VerticalFormCommonInstances 7s,
FlowOnlyHorizontalForm 6.8s, ThermalHorizontalForm 6.1s) — these are dominated by
Laminar/Airstream inline expansions, not circe macros.

Extracting codecs to a separate module would save ~3s per clean build, which does not
justify the added module complexity and maintenance overhead.

The `-Xmax-inlines:40` setting remains necessary for the UI module, but it is driven by
Laminar inline depth rather than circe macro expansion.

---

## Execution Order

1. **#8 IncrementalBuilder** — DONE ✅
2. **#10 Codec Extraction** — SKIPPED (profiling disproved the hypothesis)

## Verification Plan

For #8:
```bash
sbt compile                           # Full project
sbt "engine_13384_strict/test"        # Engine tests
sbt "engine_15544_common/test"        # Engine tests
sbt "fdim/test"                       # Golden file tests (critical)
sbt "labo/test"                       # Golden file tests
sbt "ui/fastLinkJS"                   # UI link
```

For #10:
```bash
sbt compile                           # Full project
sbt "ui/fastLinkJS"                   # UI link
# + browser smoke test
# + measure compile time before/after
```
