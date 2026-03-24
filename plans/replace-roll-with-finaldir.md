# Plan: Replace `roll` with `absDir` (AzimuthDirection + InclinationDirection)

## Context

After the successful `angleN2 → roll` migration, the `roll` angle is still user-facing in the UI and DTOs. This is confusing — users think in terms of "which direction does the pipe go after this bend," not "what roll angle relative to the gravity-normalized frame." This refactoring replaces `roll` with `absDir` (a pair of `AzimuthDirection` + `InclinationDirection` enums) throughout DTOs, engine, and UI. Roll becomes an internal computation, never exposed.

**Outcome**: Users set the final direction of each bend via the `DirectionBadgeComponent` dropdown. The engine internally computes the roll angle from `frameBefore + deflectionAngle + absDir`.

---

## Step 1: Create `AzimuthDirection` + `InclinationDirection` + `AbsoluteDirection` enums

**New file**: `modules/dto/src/main/scala/afpma/firecalc/dto/v4/AbsoluteDirection.scala`

```scala
enum AzimuthDirection:
  case Rear                              //   0°
  case RearRight                         //  +45°
  case Right                             //  +90°
  case FrontRight                        // +135°
  case Front                             // ±180°
  case FrontLeft                         // -135°
  case Left                              //  -90°
  case RearLeft                          //  -45°
  case Custom(azimuth: QtyD[Degree])     // -360° to +360°, 0°=Rear

enum InclinationDirection:
  case Up                                // +90°
  case Down                              // -90°
  case Horizontal                        //   0°
  case Custom(inclination: QtyD[Degree]) // -90° to +90°

case class AbsoluteDirection(
  azimuth    : AzimuthDirection,
  inclination: InclinationDirection
)
```

Companion objects with:
- `AzimuthDirection.toDegrees(d): Double` — pattern match on enum → degree value
- `InclinationDirection.toDegrees(d): Double` — same
- `AbsoluteDirection.toVec3(fd): Vec3` — calls `Vec3.fromAzimuthElevation`
- `AbsoluteDirection.fromVec3(v): AbsoluteDirection` — reverse lookup (snap to named enum if close, else Custom)

**Circe codecs** in `V4Instances.scala`: Custom Encoder/Decoder for `AzimuthDirection`, `InclinationDirection`, `AbsoluteDirection`. Named variants encode as strings (`"Rear"`, `"Up"`), `Custom` as `{"Custom": value}`. Follow existing patterns (`TypeOfAppliance`, `AirSpaceDetailed_V2`).

**Exports** in `modules/dto/src/main/scala/afpma/firecalc/dto/all.scala`.

**Files**:
- `modules/dto/src/main/scala/afpma/firecalc/dto/v4/AbsoluteDirection.scala` (NEW)
- `modules/dto/src/main/scala/afpma/firecalc/dto/instances/V4Instances.scala` (ADD codecs)
- `modules/dto/src/main/scala/afpma/firecalc/dto/all.scala` (ADD exports)

---

## Step 2: Generalize `PipeFrame` inverse function + add shared helper

**File**: `modules/engine/src/main/scala/afpma/firecalc/engine/models/geometry/PipeFrame.scala`

### 2a. Generalize `rollAngleForOutputDirection` to arbitrary deflection angles

Current signature (90° only): `def rollAngleForOutputDirection(targetDir: Vec3): Option[Double]`

New overload: `def rollAngleForOutputDirection(targetDir: Vec3, deflectionDeg: Double): Option[Double]`

Algorithm: Check that `direction.angleTo(targetDir) ≈ deflectionDeg` (within ~1° tolerance). If yes, project targetDir onto perpendicular plane, decompose into `(rightRef, upRef)` components, recover roll via `atan2`. If no, return `None`.

Keep the 1-arg overload as `rollAngleForOutputDirection(t) = rollAngleForOutputDirection(t, 90.0)`.

### 2b. Add `computeRequiredDeflection(targetDir: Vec3): Double`

Returns `direction.angleTo(targetDir.normalized)` — the angle between current direction and target.

### 2c. Update `reachableCardinals` to accept deflection angle

`def reachableCardinals(deflectionDeg: Double = 90.0): List[(Vec3, Double)]`

Add the 4 intermediate directions (RearRight, FrontRight, FrontLeft, RearLeft) to the search list.

### 2d. Add shared helper: `applyBendForFinalDir`

```scala
def applyBendForFinalDir(deflectionDeg: Double, targetDir: Vec3): PipeFrame =
  val rollDeg = rollAngleForOutputDirection(targetDir, deflectionDeg).getOrElse(0.0)
  applyBend(deflectionDeg, rollDeg)
```

This is used by BOTH the engine builders and the UI panel frame tracking, avoiding duplication.

### 2e. Add tests for new methods

In `PipeFrameSuite.scala`: tests for `rollAngleForOutputDirection(target, 45.0)`, `rollAngleForOutputDirection(target, 60.0)`, round-trip property (`frame.applyBendForFinalDir(defl, target).direction ≈ target`).

---

## Step 3: Update V3 DTOs — replace `roll` with `absDir`

All three DTO files — replace `roll: Option[Angle]` with `absDir: Option[AbsoluteDirection]` on `AddDirectionChange` abstract class and all concrete subclasses. Remove `@Transl(I(_.terms.roll))` annotations, add `@Transl(I(_.terms.absolute_direction))`.

Also update `SetInitialDirection` from `(azimuth: Angle, inclination: Angle)` to `(azimuth: AzimuthDirection, inclination: InclinationDirection)`.

**Files**:
- `modules/dto/src/main/scala/afpma/firecalc/dto/v4/FlowOnlyPipeDescr_15544_V3.scala` — `AddDirectionChange` + 2 subclasses + `SetInitialDirection`
- `modules/dto/src/main/scala/afpma/firecalc/dto/v4/FlowOnlyPipeDescr_13384_V3.scala` — `AddDirectionChange` + 11 subclasses + `SetInitialDirection`
- `modules/dto/src/main/scala/afpma/firecalc/dto/v4/ThermalPipeDescr_13384_V3.scala` — `AddDirectionChange` + 11 subclasses + `SetInitialDirection`

---

## Step 4: Update DSL typeclasses — `roll: Angle` → `absDir: AbsoluteDirection`

Replace `roll: Angle` with `absDir: AbsoluteDirection` on every method signature.

**Files**:
- `modules/engine/src/main/scala/.../typeclasses/DirectionChangeDSL_15544.scala`
- `modules/engine/src/main/scala/.../typeclasses/DirectionChangeDSL_13384.scala`

---

## Step 5: Update DSL instances — wrap `absDir` in `Some(...)`

Replace `Some(roll)` with `Some(absDir)` in DTO construction.

**Files**:
- `modules/engine/src/main/scala/.../instances/DirectionChangeDSL_15544_Instances.scala`
- `modules/engine/src/main/scala/.../instances/DirectionChangeDSL_13384_Instances.scala`

---

## Step 6: Update Builders — compute roll from absDir internally

In `updateStateAfterConversionStep` of all 3 builders, change from:
```scala
addDC.roll match
  case Some(rollAngle) => frame.applyBend(deflDeg, rollAngle)
```
To:
```scala
addDC.absDir match
  case Some(fd) =>
    val targetVec = AbsoluteDirection.toVec3(fd)
    val newFrame = frame.applyBendForFinalDir(deflDeg, targetVec)
```

Same for `SetInitialDirection` handling — convert enum values via `AzimuthDirection.toDegrees` / `InclinationDirection.toDegrees`.

Update builder convenience methods (`addSharpAngle_*deg`, `setInitialDirection`) to take `absDir: AbsoluteDirection` instead of `roll: Angle`.

**Files**:
- `modules/engine/src/main/scala/.../en15544/common/FlowOnlyIncrementalBuilder_15544.scala`
- `modules/engine/src/main/scala/.../en13384/FlowOnlyIncrementalBuilder_13384.scala`
- `modules/engine/src/main/scala/.../en13384/ThermalIncrementalBuilder_13384.scala`

---

## Step 7: Update Element Factories — compute roll from absDir

Replace `op.roll` pattern matching with `op.absDir` + internal roll computation via `frame.rollAngleForOutputDirection(target, defl)`.

**Files**:
- `modules/engine/src/main/scala/.../instances/ElementFactory_15544_Instances.scala`
- `modules/engine/src/main/scala/.../instances/ElementFactory_13384_Instances.scala`

---

## Step 8: Update cas type definitions

Replace `roll = X.degrees` with `absDir = AbsoluteDirection(AzimuthDirection.Foo, InclinationDirection.Bar)` on every direction change call. Replace `setInitialDirection(azimuth = X.degrees, inclination = Y.degrees)` with enum values.

Each existing `roll` value has a comment documenting the final direction — use that to determine the correct `AbsoluteDirection`.

**Files** (search for `roll =` and `setInitialDirection`):
- `modules/engine/src/main/scala/.../cas_types/en15544/CasType_15544_C1.scala`
- `modules/engine/src/main/scala/.../cas_types/en15544/CasType_15544_C2.scala`
- `modules/engine/src/main/scala/.../cas_types/en15544/CasType_15544_C3.scala`
- `modules/engine/src/main/scala/.../cas_types/en15544/CasType_15544_C3_FDIM.scala`
- `modules/engine/src/main/scala/.../cas_types/en15544/ExampleProject_15544.scala`
- `modules/engine/src/main/scala/.../cas_types/en13384/CasType_13384_C16.scala`
- `modules/engine/src/main/scala/.../cas_types/en13384/CasType_13384_C2.scala`

---

## Step 9: Update engine tests

Replace `roll = X.degrees` with `absDir = AbsoluteDirection(...)` in all test files that use the DSL.

**Files**:
- `modules/engine/src/test/scala/.../en15544/incremental.scala`
- `modules/engine/src/test/scala/.../ops/en15544/FlowOnlyDynamicFrictionCoeff_15544.scala`
- Any other test files using `roll`

---

## Step 10: Update UI — refactor DirectionBadgeComponent

### 10a. Delete `RollAngleInput.scala`

Delete: `modules/ui/src/main/scala/afpma/firecalc/ui/daisyui/RollAngleInput.scala`

### 10b. Refactor `DirectionBadgeComponent`

**File**: `modules/ui/src/main/scala/afpma/firecalc/ui/components/DirectionBadgeComponent.scala`

Replace `rollVar: Option[Var[Option[QtyD[Degree]]]]` with `absDirVar: Option[Var[Option[AbsoluteDirection]]]`.

Add `deflectionAngle: Signal[Option[Double]]` parameter.

Dropdown changes:
- Lists reachable `AbsoluteDirection` values from `frame.reachableCardinals(deflDeg)`
- Each item maps to a `AbsoluteDirection` value via `AbsoluteDirection.fromVec3`
- Clicking a preset: compute required deflection, if mismatched → show dialog:
  - If angle can be adjusted: "Adjust angle to XX°?" + "Cancel"
  - If angle type is fixed: "Change element type to reach this direction"
- On confirmation, write `Some(fd)` to `absDirVar`

Remove roll display from tooltip. Keep azimuth/elevation display.

### 10c. Update `PipePanel.scala` base

Replace `badgeRollVar` parameter with `badgeFinalDirVar: Var[AA] => Option[Var[Option[AbsoluteDirection]]]`.

Add `deflectionAngleSig(idx: Int): Signal[Option[Double]]` base method (returns element's angle field).

### 10d. Update panel subclasses

**Files**:
- `modules/ui/src/main/scala/afpma/firecalc/ui/panels/FluePipePanel.scala`
- `modules/ui/src/main/scala/afpma/firecalc/ui/panels/PipePanel_13384_Thermal.scala`
- `modules/ui/src/main/scala/afpma/firecalc/ui/panels/PipePanel_13384_FlowOnly.scala`

In each:
1. Delete `rollExtra` helper
2. Replace `rollBadgeVar` with `absDirBadgeVar` (zoom into `.absDir` instead of `.roll`)
3. Update `frameBeforeByIdx` signal: replace `dc.roll` → `dc.absDir` + use `PipeFrame.applyBendForFinalDir`
4. Update `directionAfterByIdx` signal: same change
5. Update all `renderElemTyped` calls: remove `extra = rollExtra(...)`, use `badgeFinalDirVar = absDirBadgeVar(...)`

### 10e. Update Defaultable instances

Replace `roll = None` with `absDir = None` in default values.

**Files**:
- `modules/ui/src/main/scala/.../defaultable_15544.scala`
- `modules/ui/src/main/scala/.../defaultable_13384.scala`
- `modules/ui/src/main/scala/.../FlowOnlyDefaultable_13384.scala`

### 10f. Update HorizontalForm instances

Replace hidden `roll` field with hidden `absDir` field.

---

## Step 11: I18n keys

Add new keys, remove/deprecate `terms.roll`:
- `terms.absolute_direction` (FR: "Direction absolue", EN: "Final direction")
- `direction_badge.label` — may already exist, check
- Named direction labels (Rear, Front, etc.) — already exist in `direction_badge.cardinal_*`

**Files**: `modules/i18n/src/main/resources/i18n/*.conf` (EN + FR)

---

## Verification

1. **Compile**: `compile-module(engine)` then `compile-module(ui)` then `compile-full()`
2. **Engine tests**: `sbt --client 'engine/test'` — all 123+ tests pass
3. **DTO tests**: `sbt --client 'dto/test'` — serialization round-trips work
4. **UI compile**: `sbt --client 'ui/fastLinkJS'` — no errors
5. **Manual check**: Run cas type exports, verify identical engine output (elevation gains, pressures, compliance results should be unchanged from current state)

---

## Implementation Order (dependency chain)

```
Step 1 (enums)  ─┐
Step 2 (PipeFrame)┤── additive, no breakage
Step 11 (i18n)   ─┘

Step 3 (DTOs) ──── breaks everything downstream; must be followed immediately by:
  Step 4 (DSL typeclasses)
  Step 5 (DSL instances)
  Step 6 (builders)
  Step 7 (factories)
  Step 8 (cas types)
  Step 9 (tests)
  Step 10 (UI)
```

Steps 1, 2, 11 can be committed separately (additive). Steps 3-10 must be one atomic batch.

---

## Implementation Log

### Completed — 2026-03-14

All steps implemented and verified. 6 commits on `feat-replace-roll-with-finaldir`:

| Commit | Step | Description |
|--------|------|-------------|
| `d79adb0` | 11 | i18n: added `terms.absolute_direction` (EN/FR) + `absolute_direction` field in `I18nData.Terms` |
| `af78d04` | 1 | dto: `AzimuthDirection`, `InclinationDirection`, `AbsoluteDirection` enums + Circe codecs + exports |
| `696bc38` | 2 | engine: PipeFrame generalized — `rollAngleForOutputDirection(target, deflDeg)`, `computeRequiredDeflection`, `applyBendForFinalDir`, `reachableCardinals(deflDeg)` with 8 diagonal+cardinal directions. 65 PipeFrame tests pass. |
| `dfaff0f` | 3-9 | Atomic batch: DTOs, DSL typeclasses, DSL instances, builders, element factories, cas types, engine tests. 144 engine tests pass, 29 DTO tests pass. |
| `de9ecc6` | 10 | UI: deleted `RollAngleInput.scala`, refactored `DirectionBadgeComponent` to use `AbsoluteDirection`, updated all 3 panel subclasses, defaultables, horizontal form instances. `ui/fastLinkJS` compiles clean. |
| `62c1830` | fix | labo + fdim modules: migrated `roll =` → `absDir =` in 8 exercise/lab files (not covered by plan). |

### Deviations from Plan

1. **`toVec3`/`fromVec3` placement**: Plan specified these on `AbsoluteDirection` companion in `dto` module. Since `Vec3` lives in `engine` (not a dto dependency), implemented as:
   - `AbsoluteDirection.toAzimuthElevationDeg(fd): (Double, Double)` in dto (pure degree conversion)
   - `AbsoluteDirectionOps` extension in `engine/models/geometry/AbsoluteDirectionOps.scala` for `Vec3` conversions
   - Builders/UI use `Vec3.fromAzimuthElevation(azDeg, elDeg)` via the degree helpers

2. **`SetInitialDirection` type change**: Plan said `(azimuth: AzimuthDirection, inclination: InclinationDirection)`. Implemented as-is. Defaultable now uses `AzimuthDirection.Rear, InclinationDirection.Up` instead of `0.0.degrees, 90.0.degrees`.

3. **`labo` and `fdim` modules**: Not mentioned in the original plan but used the same DSL methods. Required a follow-up commit to fix 71 compilation errors across 8 files.

4. **`PositionTracker.scala`**: Engine utility file not mentioned in plan but referenced `dc.roll` — updated to `dc.absDir` in Step 9 batch.

5. **`EngineState.scala`**: UI file with hardcoded DSL calls for 15544 default pipe — updated in Step 10.

6. **UI HorizontalForm additions**: Added `DaisyUIHorizontalForm[AzimuthDirection]` (select dropdown with translated cardinal names) and `DaisyUIHorizontalForm[InclinationDirection]` (select dropdown: Up/Down/Horizontal) in `HorizontalFormCommonInstances.scala`.

### Known Issue (post-implementation)

**ClassCastException at runtime**: `AzimuthDirection$$anon$1 cannot be cast to java.lang.Double` — occurs in browser when loading projects saved with old schema (pre-migration). Old localStorage data stores `roll` as a `Double` where `AbsoluteDirection` is now expected. Fix in progress — likely needs a migration path for V3→V4 stored project data, or a guard in the Circe decoder.
