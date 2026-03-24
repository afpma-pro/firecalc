# Review: 3D Direction Tracking Feature

**Reviewed commits**: `c6205c1..c5bff85` (10 commits, ~1530 insertions across 37 files)
**Date**: 2026-03-10
**Decisions applied**: 2026-03-10
**Roll convention redesign**: 2026-03-10 (commit `feat-pipe-frame` branch)

---

## 1. Architecture Review

### Overall Design: Sound

The layered approach is well-structured:
1. **Geometry primitives** (`Vec3`, `PipeFrame`) in the engine module -- clean, self-contained, well-tested.
2. **DTO changes** (`roll: Option[Angle]` on `AddDirectionChange`, `SetInitialDirection` as a new `SetProp`) -- backward compatible via `= None` defaults.
3. **Engine builder integration** (direction state in `PropsState`, frame tracking in `updateStateAfterConversionStep`, `angleN2` auto-computation in element factories) -- correctly follows the existing incremental builder pattern.
4. **Direction inheritance** (flue -> connector -> chimney frame propagation in `FireCalcYAML_Loader`) -- pragmatic placement.
5. **UI** (`RollAngleInput` component, direction badge, `frameBeforeByIdx` scan) -- properly decoupled from engine success.

The coordinate system and roll convention are documented in `plans/direction-3d.md` and implemented consistently. The Rodrigues rotation math is correct and tested.

### Architecture Concern: Dual Frame Computation Paths

The most significant architectural issue is that **PipeFrame is computed twice through independent paths**:

- **Engine-side**: `ThermalIncrementalBuilder_13384.updateStateAfterConversionStep` tracks `currentFrame` in `PropsState`, populates `frameBefore`/`directionBefore`/`directionAfter` on `NamedPipeElDescrG`, threads through `PipeSectionResult`, arrives in UI via `XtraOutputs`.
- **UI-side**: `PipePanel_13384_Thermal.frameBeforeByIdx` scans the raw element list and independently computes the frame per element index.

The UI-side was added because the engine path is unavailable when validation errors exist (e.g., DC as last element). This is correct, but it means the same computation exists in two places with no shared code.

---

## 2. Simplification Opportunities (Key Finding)

### 2a. `frameBefore` on `NamedPipeElDescrG` and `PipeSectionResult`: LIKELY REMOVABLE

**Current flow**:
- `ThermalIncrementalBuilder_13384.mkFullElementsDescr` (line 223) passes `st.currentFrame` as `frameBefore` to `namedWithDirection`.
- This propagates through `NamedPipeElDescrG.frameBefore` -> `ThermalMecaFlu_13384` (line 782) -> `PipeSectionResult.frameBefore` -> UI.
- In the UI, `PipePanel_13384_Thermal.frameBeforeSig` (line 66-67) prefers the engine's `psr.frameBefore` but falls back to `frameBeforeByIdx`.

**Question**: Is the engine-side `frameBefore` ever needed if the UI always has `frameBeforeByIdx` available?

**Answer**: No. The UI-side `frameBeforeByIdx` is a strict superset -- it works with OR without engine results. The engine-side `frameBefore` only adds value when engine results are available, but in that case `frameBeforeByIdx` also works identically (both compute the same thing from the same inputs). The `orElse` fallback in `frameBeforeSig` (line 67) means the engine path is redundant.

**Recommendation**: Remove `frameBefore` from:
- `NamedPipeElDescrG` (field, `PipeIncrAndFullDescrG.scala:38`)
- `PipeSectionResult` (def, `results.scala:66`)
- `PipeDescrAlg.namedWithDirection` (parameter, `PipeDescrAlg.scala:33`)
- `ThermalMecaFlu_13384` (override, `ThermalMecaFlu_13384.scala:782`)
- `ThermalIncrementalBuilder_13384.mkFullElementsDescr` (the `st.currentFrame` pass-through, line 223)

Then simplify `frameBeforeSig` to just use `frameBeforeByIdx`:
```scala
private def frameBeforeSig(idx: Int): Signal[Option[PipeFrame]] =
    frameBeforeByIdx.map(_.get(idx))
```

This removes ~15 lines of engine plumbing and one field from two core data types.

> **✅ Done.** `frameBefore` removed from `NamedPipeElDescrG`, `PipeSectionResult`, `PipeDescrAlg.namedWithDirection` (method eliminated entirely), `ThermalMecaFlu_13384`, and `ThermalIncrementalBuilder_13384.mkFullElementsDescr` (~18-line direction computation block removed). `frameBeforeSig` simplified to `frameBeforeByIdx.map(_.get(idx))`.

### 2b. `directionBefore` and `directionAfter` on `NamedPipeElDescrG` and `PipeSectionResult`: PARTIALLY REMOVABLE

**Current consumers** (exhaustive search):
1. `ThermalMecaFlu_13384.scala:780-781`: Copies from `curr.directionBefore`/`curr.directionAfter` to `PipeSectionResult`.
2. `Preview.scala:180`: `psr.directionAfter` -> `Preview.direction` field -> direction badge in `PipePanel.renderElemTyped` (line 143).

**These are only used for the direction badge.** The direction badge could instead use the UI-side `frameBeforeByIdx` computation (which already exists) to derive the direction. The badge currently works through: engine -> `PipeSectionResult.directionAfter` -> `Preview.direction`. If the engine fails, no badge is shown.

**However**, the direction badge using the engine path means it shows direction ONLY when the engine succeeds. If you wanted to show direction even with validation errors (like roll labels do), you would need the UI-side path anyway.

**Recommendation**: Consider whether direction badges should show even with validation errors. If yes, migrate to UI-side computation (from `frameBeforeByIdx`) and remove `directionBefore`/`directionAfter` from `NamedPipeElDescrG` and `PipeSectionResult`. If no (current behavior is acceptable), keep as-is but document the asymmetry.

If removed, this eliminates:
- 2 fields from `NamedPipeElDescrG` (`PipeIncrAndFullDescrG.scala:36-37`)
- 2 defs from `PipeSectionResult` trait (`results.scala:64-65`)
- 2 overrides from `ThermalMecaFlu_13384` (lines 780-781)
- ~18 lines of direction computation in `mkFullElementsDescr` (lines 201-218)
- The `directionBefore`/`directionAfter` parameters in `namedWithDirection` (`PipeDescrAlg.scala:31-32`)

The `Preview.direction` field would be populated from the UI-side frame map instead.

> **✅ Done.** Decision: badges should show even with validation errors. `directionBefore`/`directionAfter` removed from `NamedPipeElDescrG`, `PipeSectionResult`, `ThermalMecaFlu_13384`, and `ThermalIncrementalBuilder_13384`. `Preview.direction` field removed. `PipePanel` badge migrated to a `directionBadgeSig` virtual method (default returns `None`). `PipePanel_13384_Thermal` overrides it with `directionAfterByIdx` (UI-side: DC+roll → output direction, other elements → frame direction, DC without roll → no badge).

### 2c. Direction State Fields in FlowOnly PropsState: DEAD CODE

`FlowOnlyPropsState_13384` (`PropsStateOps_FlowOnly_13384_Instance.scala:28-35`) and `FlowOnlyPropsState_15544` (`PropsStateOps_FlowOnly_15544_Instance.scala:29-36`) both declare `initialFrame`, `currentFrame`, and `dirBeforePreviousDC` fields. These fields are **never written** -- the FlowOnly builders do not override `currentFrameFromPropsState` or `applyExternalFrame`, and the `updateStateBeforeConversionStep`/`updateStateAfterConversionStep` in FlowOnly builders do not handle `SetInitialDirection` or update frame state.

The plan acknowledges this ("FlowOnly direction tracking is a no-op"), but the fields themselves are dead weight that could confuse future readers.

**Recommendation**: Remove the three direction fields from both FlowOnly PropsState case classes until the DTO version bump actually wires them. Add a comment: `// Direction tracking: deferred until DTO V3 -- see plans/direction-3d.md`.

> **❌ Not done.** Decision: keep as-is for now; remove when the DTO version bump actually wires FlowOnly direction tracking.

---

## 3. Code Quality

### 3a. Repetitive `extra = ev => RollAngleInput(...)` Lambdas (HIGH)

**File**: `PipePanel_13384_Thermal.scala`, lines 206, 220, 234, 246, 260, 272, 286, 298, 310, 322

Ten identical lambdas:
```scala
extra = ev => RollAngleInput(ev.zoomLazy(_.roll)((a, r) => a.copy(roll = r)), frameBeforeSig(iaax._1, sig.map(_._3)))
```

Each one:
1. Zooms into the `roll` field of a different `AddDirectionChange` subtype.
2. Passes the same `frameBeforeSig` computation.

**Recommendation**: Extract a helper method in `PipePanel_13384_Thermal`:
```scala
private def rollAngleExtra[A <: AddDirectionChange](
    ev: Var[A], idx: Int, xtraSig: Signal[XtraOutputs]
)(using HasRoll[A]): HtmlElement =
    RollAngleInput(
        ev.zoomLazy(_.roll)((a, r) => a.copy(roll = r)),
        frameBeforeSig(idx, xtraSig)
    )
```

The challenge is that each `A` is a different case class with its own `copy` method. You could define a typeclass `HasRoll[A]` with `def withRoll(a: A, r: Option[QtyD[Degree]]): A`, or use a simpler approach: since all DC subtypes extend `AddDirectionChange` which has a `roll` field, a single helper taking `Var[_ <: AddDirectionChange]` with a generic lens might work. Even if a typeclass is too heavy, a `def rollExtra(idx: Int, sig: Signal[...])` that returns the `extra` function would reduce the 10 sites to one-liners.

> **✅ Done.** Simple approach chosen: `private def rollExtra[A <: AddDirectionChange](idx, getter, setter): Var[A] => HtmlElement`. All 10 sites reduced to `extra = rollExtra(iaax._1, _.roll, (a, r) => a.copy(roll = r))`.

### 3b. `autoDeriveAndOverwriteFieldNames_DC_Subtype` Suppression Pattern

**File**: `ThermalHorizontalForm_13384.scala`, lines 377-384

This helper creates a dummy `DaisyUIHorizontalForm[Option[QtyD[Degree]]]` that renders `span()` (empty), effectively suppressing the magnolia-derived roll field in DC forms. The actual roll input is rendered via the `extra` callback in `PipePanel_13384_Thermal`. This is a clever workaround but somewhat fragile -- if a new DC subtype is added, the developer must remember to:
1. Use `autoDeriveAndOverwriteFieldNames_DC_Subtype` (not the regular `_AddElement_Subtype` version).
2. Add the `extra = ev => RollAngleInput(...)` line in `PipePanel_13384_Thermal`.

**Recommendation**: Add a comment in `autoDeriveAndOverwriteFieldNames_DC_Subtype` explaining that roll is rendered separately via `RollAngleInput` in `PipePanel_13384_Thermal`, and that both must be updated together.

> **✅ Done.** Comment added to `autoDeriveAndOverwriteFieldNames_DC_Subtype` in `ThermalHorizontalForm_13384.scala`.

### 3c. `horizontal_form_Option_Angle` Dead Code

**File**: `ThermalHorizontalForm_13384.scala`, lines 298-369

The `horizontal_form_Option_Angle` given instance (lines 301-369) provides a standalone static roll input with `[0, 90, 180, 270]` preset buttons. This was the Phase 6a original implementation before `RollAngleInput` replaced it. It is now only used as the fallback form generated by magnolia derivation -- but that form is **suppressed** by `autoDeriveAndOverwriteFieldNames_DC_Subtype` (which renders `span()` instead).

So `horizontal_form_Option_Angle` is effectively dead code: it is defined but the forms that would use it are replaced by empty spans, and the actual roll input comes from `RollAngleInput` via `extra`.

**Recommendation**: Check if this given is still resolved anywhere. If not, remove it to reduce confusion. The static fallback behavior (no `SetInitialDirection`) is already handled inside `RollAngleInput` (lines 36-37 of `RollAngleInput.scala`, where `frameSig = None` -> `staticPresets`).

**Update**: On closer inspection, `autoDeriveAndOverwriteFieldNames_DC_Subtype` provides its *own* local `DaisyUIHorizontalForm[Option[QtyD[Degree]]]` (line 383: `DaisyUIHorizontalForm.makeFor[...](Defaultable(None))((_, _) => span())`). So the outer `horizontal_form_Option_Angle` given at line 301 is indeed unused by DC subtypes. It may still be resolved by non-DC forms, but no other form in the codebase uses `Option[QtyD[Degree]]`. This is likely dead code.

> **✅ Done.** Removed by user in commit `67b054b` ("refactor(ui): remove dead code 'horizontal_form_Option_Angle'").

### 3d. Roll Convention Comment Discrepancy

**File**: `PipeFrame.scala`, lines 13-16

The doc comment says:
```
Roll convention (looking into the pipe):
  0° = toward upRef direction
  Vertical pipe (up): upRef = Rear → roll 0°=rear, 90°=right, 180°=front, 270°=left
```

But the plan document (`plans/direction-3d.md`, lines 104-106) says:
```
Vertical pipe: Right=0°, Rear=90°, Left=180°, Front=270°
```

And the actual math in `applyBend` (line 30):
```scala
val bendAxis = (upRef * math.cos(rollRad) + rightRef * math.sin(rollRad)).normalized
```

When roll=0, `bendAxis = upRef = Rear`. Rotating `+Z` (Up) around `+Y` (Rear) by 90 degrees gives `+X` (Right). So roll=0 produces **Right**, not Rear. The inline comment's "roll 0°=rear" is wrong; the plan's "Right=0°" is correct.

**Recommendation**: Fix the comment in `PipeFrame.scala` lines 15-16 to match reality:
```
Vertical pipe (up): upRef = Rear → roll 0°=Right, 90°=Rear, 180°=Left, 270°=Front
Horizontal pipe (rear): upRef = Up → roll 0°=Left, 90°=Up, 180°=Right, 270°=Down
```

The plan already notes this as a "known deviation" (line 37), but the code comment should be fixed.

> **✅ Done.** Full convention redesign implemented (see `plans/redefine-roll-conventions.md`):
> - `applyBend` formula: `bendAxis = rightRef*cos(roll) - upRef*sin(roll)` (right-hand rule)
> - `rollAngleForOutputDirection` inverse updated to match
> - Doc comment rewritten with right-hand rule table (0°=Rear for vertical up, 0°=Up for horizontal rear)
> - `PipeFrameSuite` 38 tests updated and passing
> - `plans/direction-3d.md` coordinate system section updated; known deviation removed
> - No DTO migration (work unreleased, existing data has no stored non-None roll values in production)

### 3e. Fully Qualified Type Annotations in `FireCalcYAML_Loader`

**File**: `FireCalcYAML_Loader.scala`, lines 48-65

The type annotations use fully qualified names like:
```scala
(afpma.firecalc.engine.models.FluePipe_Module_15544.FullDescrResult,
 ValidatedNel[IncrementalValidation_Error, Option[afpma.firecalc.engine.models.geometry.PipeFrame]])
```

This could be simplified with imports already in scope. Minor readability issue.

> **✅ Done.** Fixed by user in commit `c5347a8` ("fix: remove FQN").

### 3f. `toDisplayString` Robustness

**File**: `Vec3.scala`, lines 48-82

The `toDisplayString` method uses exact floating-point equality checks (`v == 0.0`, `v == 1.0`). For a normalized vector, components of exact cardinals should be exactly 0.0 or 1.0 since the normalization of `(0,0,1)` preserves exact values. However, after rotations (e.g., `applyBend`), accumulated floating-point error could produce values like `0.9999999999999998` instead of `1.0`, causing the cardinal check to fail and falling through to the az/el format.

This is partially mitigated by `reachableCardinals` (which uses `rollAngleForOutputDirection` with a 1e-6 tolerance), but `toDisplayString` itself is fragile.

**Recommendation**: Use a tolerance in `toDisplayString`'s cardinal check:
```scala
def isCard(v: Double) = math.abs(v) < 1e-9 || math.abs(math.abs(v) - 1.0) < 1e-9
```

> **✅ Done.** Both `isCard` and `isCard2` in `Vec3.toDisplayString` updated to use tolerance `1e-9`.

---

## 4. Test Coverage Gaps

### 4a. Direction Tracking Integration Tests: MISSING

There are no integration tests that verify `angleN2` is correctly auto-computed through the full `ThermalIncrementalBuilder_13384` pipeline. The `PipeFrameSuite` tests the geometry primitives (Vec3, PipeFrame), but no test exercises:
- A pipe with `SetInitialDirection` + `AddSectionVertical` + `AddSharpeAngle_0_to_90(roll = Some(...))` + `AddSectionHorizontal`
- Verifying the resulting `DirectionChange` element has the expected `angleN2` value
- Verifying `directionBefore`/`directionAfter` on the resulting `NamedPipeElDescrG`

**Recommendation**: Add 2-3 builder integration tests in a new test file (e.g., `DirectionTrackingSuite`) that construct a pipe via the `ThermalIncrementalBuilder_13384` DSL, build the full description, and assert `angleN2` values on DC elements.

> **❌ Deferred.** Not covered in this iteration.

### 4b. Direction Inheritance: NOT TESTED

The `FireCalcYAML_Loader` flue->connector->chimney frame propagation has no test coverage. A test should verify that when a flue pipe ends with a tracked direction, the connector pipe's first element inherits that direction (absent its own `SetInitialDirection`).

> **❌ Deferred.** Not covered in this iteration.

### 4c. `reachableCardinals` and `rollAngleForOutputDirection`: COVERED (1 test)

One test in `PipeFrameSuite` (lines 273-293) verifies that tracked frame produces different roll angles than `PipeFrame.initial` for the same direction. This is adequate for the core logic but could be expanded to verify specific cardinal label mappings.

### 4d. Edge Cases Not Tested

- **Roll = None with SetInitialDirection present**: What happens to direction tracking when some DCs have `roll` and others don't? The engine correctly sets direction to `None` for those elements (lines 209-218 of `ThermalIncrementalBuilder_13384`), but this is untested.
- **Multiple `SetInitialDirection` elements**: What happens if the user adds two? The second should override the first (the `updateStateBeforeConversionStep` simply overwrites), but this is untested.
- **`SetInitialDirection` after a DC**: If `SetInitialDirection` appears mid-pipe after a DC, does it correctly reset the frame? Not tested.

---

## 5. Summary of Recommendations (Priority Order)

### Must-fix
1. ✅ **Fix roll convention** in `PipeFrame.scala` -- redesigned to right-hand rule (0°=Rear for vertical / 0°=Up for horizontal). Formula, inverse, doc comment, tests, and plan doc all updated.

### Should-fix (simplification)
2. ✅ **Remove `frameBefore`** from `NamedPipeElDescrG`, `PipeSectionResult`, and the engine threading chain. The UI-side `frameBeforeByIdx` is sufficient and always available.
3. ❌ **Remove dead direction fields** from `FlowOnlyPropsState_13384` and `FlowOnlyPropsState_15544`. → Kept as-is until DTO version bump wires FlowOnly direction tracking.
4. ✅ **Remove or verify `horizontal_form_Option_Angle`** -- confirmed dead code, removed in commit `67b054b`.

### Should-fix (code quality)
5. ✅ **Extract helper for the 10 identical `RollAngleInput` lambdas** in `PipePanel_13384_Thermal`. → `rollExtra[A](idx, getter, setter)` helper, all 10 sites are one-liners.
6. ✅ **Add tolerance to `Vec3.toDisplayString`** cardinal checks for post-rotation robustness.

### Should-fix (testing)
7. ❌ **Add builder integration tests** for `angleN2` auto-computation through the full incremental builder pipeline. → Deferred.
8. ❌ **Add direction inheritance tests** for flue->connector->chimney frame propagation. → Deferred.

### Nice-to-have
9. ✅ **Migrate direction badge** to UI-side computation so it shows even with validation errors. → Done via `directionBadgeSig` virtual method + `directionAfterByIdx` in `PipePanel_13384_Thermal`.
10. ✅ **Add doc comment** in `autoDeriveAndOverwriteFieldNames_DC_Subtype` explaining the roll field suppression pattern.
11. ✅ **Simplify FQCNs** in `FireCalcYAML_Loader.scala` type annotations. → Done by user in commit `c5347a8`.

---

## 6. Files Referenced

| File | Lines | Topic |
|------|-------|-------|
| `modules/engine/src/main/scala/.../geometry/Vec3.scala` | full | Core geometry type |
| `modules/engine/src/main/scala/.../geometry/PipeFrame.scala` | 13-16 | Roll convention comment (incorrect) |
| `modules/engine/src/main/scala/.../PipeIncrAndFullDescrG.scala` | 36-38 | `directionBefore`, `directionAfter`, `frameBefore` fields |
| `modules/engine/src/main/scala/.../results.scala` | 64-66 | `PipeSectionResult` direction/frame defs |
| `modules/engine/src/main/scala/.../PipeDescrAlg.scala` | 27-35 | `namedWithDirection` helper |
| `modules/engine/src/main/scala/.../ThermalMecaFlu_13384.scala` | 780-782 | Override forwarding direction/frame |
| `modules/engine/src/main/scala/.../ThermalIncrementalBuilder_13384.scala` | 132-248 | Frame tracking, direction computation |
| `modules/engine/src/main/scala/.../ElementFactory_13384_Instances.scala` | 191-196, 221-226, 339-344 | `DirectionChangeCtx_13384`, `angleN2` computation |
| `modules/engine/src/main/scala/.../PropsStateOps_Thermal_13384_Instance.scala` | 29-41 | Direction state fields |
| `modules/engine/src/main/scala/.../PropsStateOps_FlowOnly_13384_Instance.scala` | 28-35 | Dead direction fields |
| `modules/engine/src/main/scala/.../PropsStateOps_FlowOnly_15544_Instance.scala` | 29-36 | Dead direction fields |
| `modules/engine/src/main/scala/.../Preview.scala` | 58, 180 | `direction` field, populated from `psr.directionAfter` |
| `modules/engine/src/main/scala/.../FireCalcYAML_Loader.scala` | 47-68 | Direction inheritance wiring |
| `modules/ui/src/main/scala/.../PipePanel_13384_Thermal.scala` | 43-67, 206-322 | `frameBeforeByIdx`, 10 repeated `RollAngleInput` lambdas |
| `modules/ui/src/main/scala/.../RollAngleInput.scala` | full | Context-aware roll presets component |
| `modules/ui/src/main/scala/.../ThermalHorizontalForm_13384.scala` | 298-384 | Dead `horizontal_form_Option_Angle`, DC suppression pattern |
| `modules/ui/src/main/scala/.../PipePanel.scala` | 141-149 | Direction badge rendering |
| `modules/engine/src/test/scala/.../PipeFrameSuite.scala` | full | 38+ tests for Vec3 and PipeFrame |
| `plans/direction-3d.md` | full | Feature plan and status |
