# Progress Report: `roll` → `absDir` Migration

**Date**: 2026-03-14
**Branch**: `feat-replace-roll-with-finaldir`

---

## Completed Commits

| Commit | Step | Description |
|--------|------|-------------|
| `d79adb0` | 11 | i18n: added `terms.absolute_direction` (EN/FR) |
| `af78d04` | 1 | dto: `AzimuthDirection`, `InclinationDirection`, `AbsoluteDirection` enums + Circe codecs + exports |
| `696bc38` | 2 | engine: PipeFrame generalized — `rollAngleForOutputDirection(target, deflDeg)`, `computeRequiredDeflection`, `applyBendForFinalDir`, `reachableCardinals(deflDeg)` with 8 diagonal+cardinal directions. 65 PipeFrame tests. |
| `dfaff0f` | 3-9 | Atomic batch: DTOs, DSL typeclasses, DSL instances, builders, element factories, cas types, engine tests. 144 engine tests pass, 29 DTO tests pass. |
| `de9ecc6` | 10 | UI: deleted `RollAngleInput.scala`, refactored `DirectionBadgeComponent`, updated 3 panel subclasses, defaultables, horizontal form instances. `ui/fastLinkJS` compiles. |
| `62c1830` | fix | labo + fdim modules: migrated 8 exercise/lab files (not in original plan). |
| `98224c2` | fix | Removed stale `horizontal_form_Option_Angle` given, bumped `CURRENT_SCHEMA_VERSION` 3→4. |
| `b3e1232` | fix | UI warnings: unused members, non-exhaustive match. |
| `5e9d375` | fix | Reorder direction codecs before sealed trait derivations to fix ClassCastException. Added `DirectionCodecSuite` (17 tests) and `DirectionProductElementSuite` (6 tests). |

## Verification Status

| Check | Result |
|-------|--------|
| `sbt compile` (full project) | **PASS** |
| `engine/test` (144 tests) | **PASS** |
| `dto/test` (52 tests, JVM+JS) | **PASS** |
| `ui/fastLinkJS` | **PASS** |
| `labo/compile` | **PASS** |
| `fdim/compile` | **PASS** |

---

## Resolved: ClassCastException at Runtime

### Error
```
ObserverError: ObserverError: UndefinedBehaviorError: java.lang.ClassCastException:
  afpma.firecalc.dto.v4.AzimuthDirection$$anon$1 cannot be cast to java.lang.Double
```

### Root Cause

Circe's `semiauto.deriveEncoder` / `semiauto.deriveDecoder` macros resolve field codecs **at macro expansion time**. In `V4Instances.scala`, the sealed trait codecs (`FlowOnlyPipeDescr_13384_V3`, `ThermalPipeDescr_13384_V3`, etc.) were defined **before** the `AzimuthDirection` / `InclinationDirection` / `AbsoluteDirection` codecs. When the macro expanded, it could not find `Encoder[AzimuthDirection]` and fell back to the polymorphic `encoder_QtyD[U]` from `CommonInstances`, which treats any value as `Double` (since `Angle = QtyD[Degree]` erases to `Double`). At runtime, encoding an `AzimuthDirection.Rear` enum singleton through that wrong encoder triggered the cast failure.

### Fix (`5e9d375`)

Moved direction codecs (`AzimuthDirection`, `InclinationDirection`, `AbsoluteDirection`) **before** the sealed trait codecs that depend on them in `V4Instances.scala`. Added `DirectionCodecSuite` (17 tests) and `DirectionProductElementSuite` (6 tests) confirming correct encoding/decoding on both JVM and Scala.js.

### Lesson

Circe `semiauto` macros are sensitive to definition order — even though Scala 3 `given`s in an `object` are normally order-independent, the macro expansion doesn't see forward-declared givens. Always define leaf-type codecs before the sealed trait codecs that reference them.

---

## Deviations from Original Plan

1. **`toVec3`/`fromVec3` placement**: `Vec3` lives in `engine`, not `dto`. Used `AbsoluteDirection.toAzimuthElevationDeg` (pure degrees) in dto, `AbsoluteDirectionOps` extension in engine for Vec3 conversions.

2. **`labo`/`fdim` modules**: Not in plan, but used same DSL methods — required follow-up commit (71 compilation errors across 8 files).

3. **`PositionTracker.scala`**: Engine utility not in plan, referenced `dc.roll` — updated in Steps 3-9 batch.

4. **`EngineState.scala`**: UI file with hardcoded DSL calls — updated in Step 10.

5. **HorizontalForm instances**: Added `DaisyUIHorizontalForm[AzimuthDirection]` and `DaisyUIHorizontalForm[InclinationDirection]` select dropdowns in `HorizontalFormCommonInstances.scala`.

---

## Follow-Up: Relative Direction Input (`a308d5c`)

Added relative Left/Right + theta rotation input to handle non-cardinal deflection angles (e.g. 65°). See `plans/relative-direction-input-progress.md` for full details.

| Commit | Description |
|--------|-------------|
| `a308d5c` | `localRight`, `relativeTarget`, `recoverRelative` on PipeFrame + `RelativeDirectionInput` UI component wired into all 3 panels (22 DC call sites). 87 PipeFrame tests pass. |

## Follow-Up: 4 Quadrants + Inline Layout

Extended relative direction from 2 (Left/Right) to 4 quadrants (Right/Up/Left/Down) with theta ∈ [0°, 90°]. Added dropdown selector UI, "Final dir." label, inline layout. See `plans/relative-direction-4-quadrants.md` (plan) and `plans/relative-direction-input-progress.md` (progress).

## Follow-Up: Remove elevation_gain from AddSectionSlopped

Removed redundant `elevation_gain` field from `AddSectionSlopped` — PositionTracker now computes elevation from `frame.direction.z`. Kept `AddSectionSloppedForceManualElevationGain` as escape hatch. See `plans/remove-elevation-gain-from-slopped.md`.

| Check | Result |
|-------|--------|
| `engine/test` (188 tests) | **PASS** |
| `dto/test` (52 tests) | **PASS** |
| `compile-full` | **PASS** (no warnings) |
