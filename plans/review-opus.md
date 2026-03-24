# Review: `roll` / `angleN2` Refactoring — Problems & Revised Plan

**Reviewer**: Claude Opus 4.6
**Date**: 2026-03-11
**Reviewing**: commit `f085b11` on `feat-autoslope` + `plans/refactor-incr-builder-methods.md`

---

## 1. Diagnosis: What Went Wrong

The previous refactoring took a **backward-compatible** approach — keeping `roll: Option[Angle] = None` everywhere and silently renaming `angleN2` → `roll` at call sites. This creates three problems:

### Problem A: Optional `roll` defeats migration enforcement

With `roll = None` as default, callers are **not forced** to think about roll angles. Any direction change without `roll` silently skips frame tracking (`case None => propsState.validNel` at `FlowOnlyIncrementalBuilder_15544.scala:214`). This means:

- A caller using `SetInitialDirection` but forgetting `roll` on a bend → frame silently stops updating → wrong elevation gains, wrong `angleN2` computations downstream
- No compile-time signal that something is missing
- The "migration" is invisible: old code compiles fine but produces wrong 3D geometry

### Problem B: `legacyAngleN2` fallback is a hack

In `ElementFactory_15544_Instances.scala:133-138`:
```scala
case AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(_, angle, legacyAngleN2) =>
    .AngleVifDe0A180(angle, computedAngleN2.orElse(legacyAngleN2))
```

The DTO `roll` field is read as `legacyAngleN2` — interpreting a roll angle as an angleN2. This is semantically wrong. It "works" only because:
- Legacy callers pass values that happen to be the same as what `angleN2` would be (they were originally `angleN2` values from the V2 era)
- When direction tracking is active, `computedAngleN2` takes precedence anyway

But it means the DTO's `roll` field has **two contradictory meanings** depending on context. This is confusing and error-prone.

### Problem C: No clean separation of concerns

The V3 DTO has `roll: Option[Angle]` doing double duty:
1. **Roll** (rotation around pipe axis) for 3D frame tracking
2. **Legacy angleN2 override** for EN15544 short section ζ calculation

Since V3 is **not yet released**, we can fix this properly.

---

## 2. Key Insight: V3 Is Unreleased

The V2→V3 transformer at `transformers.scala:227` already **discards** the V2 `angle_to_original_direction`:

```scala
case El2.AddSharpeAngle_0_to_180(n, a, angleN2) => El3.AddSharpeAngle_0_to_180(n, a, None)
```

This confirms:
- V3 was designed to drop the legacy `angleN2` concept from the DTO
- `roll` in V3 is meant to be **only roll** (for frame tracking)
- `angleN2` should be **computed** from direction tracking, never manually specified in V3
- We are free to change V3 types since they are unreleased

---

## 3. Revised Plan

### Philosophy

> "Make wrong code fail to compile."

Force all direction change callers to provide `roll` (mandatory). Remove the `legacyAngleN2` fallback. Callers that previously used `angleN2` values disguised as `roll` must migrate to proper `SetInitialDirection` + mandatory `roll` values that actually represent roll angles.

### Step 1: Make `roll` mandatory in DSL typeclasses

**Files:**
- `modules/engine/.../typeclasses/DirectionChangeDSL_13384.scala`
- `modules/engine/.../typeclasses/DirectionChangeDSL_15544.scala`

Change `roll: Option[Angle] = None` → `roll: Angle` on all abstract methods.

Convenience methods like `addSharpAngle_90deg` must now require `roll`:
```scala
// Before:
def addSharpAngle_90deg(name: String, roll: Option[Angle] = None): Descr =
    addSharpAngle_0_to_180deg(name, 90.degrees, roll)

// After:
def addSharpAngle_90deg(name: String, roll: Angle): Descr =
    addSharpAngle_0_to_180deg(name, 90.degrees, roll)
```

### Step 2: Make `roll` mandatory in DSL instances

**Files:**
- `modules/engine/.../instances/DirectionChangeDSL_13384_Instances.scala`
- `modules/engine/.../instances/DirectionChangeDSL_15544_Instances.scala`

Same change: `roll: Option[QtyD[Degree]] = None` → `roll: QtyD[Degree]`, wrapping in `Some(roll)` when constructing the DTO (since the DTO still has `Option[Angle]` for serialization reasons).

```scala
// Before:
def addSharpAngle_0_to_180deg(name: String, angle: QtyD[Degree], roll: Option[QtyD[Degree]] = None) =
    AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(name, angle, roll)

// After:
def addSharpAngle_0_to_180deg(name: String, angle: QtyD[Degree], roll: QtyD[Degree]) =
    AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(name, angle, Some(roll))
```

### Step 3: Make `roll` mandatory in builder helpers

**Files:**
- `modules/engine/.../en13384/ThermalIncrementalBuilder_13384.scala`
- `modules/engine/.../en13384/FlowOnlyIncrementalBuilder_13384.scala`
- `modules/engine/.../en15544/common/FlowOnlyIncrementalBuilder_15544.scala`

Same pattern: `roll: Option[Angle] = None` → `roll: Angle`.

### Step 4: Remove `legacyAngleN2` fallback in factory

**File:** `modules/engine/.../instances/ElementFactory_15544_Instances.scala`

The factory should **only** use `computedAngleN2` from direction tracking. If direction tracking is not active and `computedAngleN2` is `None`, the factory should still work (short section logic handles `None` gracefully or it's a validation error that forces the caller to use `SetInitialDirection`).

```scala
// Before:
case AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(_, angle, legacyAngleN2) =>
    .AngleVifDe0A180(angle, computedAngleN2.orElse(legacyAngleN2))

// After:
case AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(_, angle, _) =>
    .AngleVifDe0A180(angle, computedAngleN2)
```

The `_` wildcard on the roll field makes it explicit: the DTO roll is consumed by frame tracking (in `updateStateAfterConversionStep`), not by the factory.

### Step 5: Fix all callers — require `SetInitialDirection` + proper `roll` values

**Files (cas_types, strict, tests):**
- `CasType_15544_C2.scala`
- `CasType_15544_C3.scala`
- `CasType_15544_C3_FDIM.scala`
- `EcolabeledToFireboxInternalPipes_15544_Strict.scala`
- `AFPMA_PRSEToFireboxInternalPipes_15544_Strict.scala`
- `FlowOnlyDynamicFrictionCoeff_15544.scala` (test)

Each caller must:
1. Add `setInitialDirection(azimuth, inclination)` at the start of the pipe definition
2. Replace `roll = X.degrees.some` → `roll = X.degrees` (unwrap Option)
3. Add `roll = X.degrees` where previously omitted (the user will provide proper roll angles)

The compilation will **fail** on all these files, which is exactly the desired behavior — each failure is a migration point that needs a proper roll angle.

### Step 6: Update `updateStateAfterConversionStep` — make frame update mandatory when tracking is active

**Files:**
- `FlowOnlyIncrementalBuilder_15544.scala`
- `ThermalIncrementalBuilder_13384.scala`
- `FlowOnlyIncrementalBuilder_13384.scala`

Currently `roll = None` silently skips frame update. With mandatory roll, we should still handle the DTO's `Option[Angle]` (for V2→V3 migration paths), but when `currentFrame` is active and `roll` is `None`, it should be a validation error:

```scala
case Some(addDC: AddDirectionChange) =>
    propsState.currentFrame match
        case Some(frame) =>
            addDC.roll match
                case Some(rollAngle) =>
                    val rollDeg  = rollAngle.toUnit[Degree].value
                    val deflDeg  = addDC.angle.toUnit[Degree].value
                    val newFrame = frame.applyBend(deflDeg, rollDeg)
                    propsState.copy(
                        dirBeforePreviousDC = Some(frame.direction),
                        currentFrame        = Some(newFrame)
                    ).validNel
                case None =>
                    MissingRollAngleForDirectionChange(addDC.name).invalidNel
        case None =>
            // No frame tracking active — skip (legacy mode)
            propsState.validNel
```

This is a **runtime safety net** for V2→V3 migrated data where roll was discarded. It catches the error early rather than producing wrong geometry silently.

### Step 7: DTO V3 types — keep `roll: Option[Angle]` in the DTO

The DTO `roll` field stays `Option[Angle]` because:
- V2→V3 transformer produces `roll = None` (no roll info in V2 data)
- Serialization needs backward compat for stored data
- The DSL wraps in `Some(...)` — callers always provide it through the DSL

No DTO changes needed.

---

## 4. Migration Impact Summary

| Area | What breaks | What to do |
|------|-------------|------------|
| **DSL typeclasses** | `roll` becomes mandatory | Add `roll: Angle` parameter |
| **DSL instances** | Same | Wrap in `Some(roll)` for DTO |
| **Builder helpers** | Same | Forward mandatory `roll` |
| **cas_types (C2, C3, C3_FDIM)** | Compilation fails — missing `roll` + `setInitialDirection` | User provides proper roll angles + initial direction |
| **strict (Ecolabeled, AFPMA_PRSE)** | Same | Same |
| **test (FlowOnlyDynamicFrictionCoeff)** | Same | Same |
| **Factory 15544** | `legacyAngleN2` fallback removed | Tests must use `SetInitialDirection` so `computedAngleN2` works |
| **V2→V3 transformer** | No change needed | `roll = None` in migrated data, caught at runtime if tracking is active |

---

## 5. Open Question for User

The roll values currently in cas_types and tests are **not real roll angles** — they were originally `angleN2` values. The user stated they will provide proper roll angles. This is the key manual step.

Additionally: should `addCircularArc60` also require mandatory roll? (Currently it's a fixed 60° arc — roll determines the plane of the arc.)

---

## 6. Verification

1. **Compile**: After making roll mandatory, expect compilation failures in all caller files. Each failure = a migration point.
2. **User provides roll values**: Update each caller with proper roll angles + `setInitialDirection`.
3. **`compile-full`**: Must pass cleanly.
4. **`sbt engine/test`**: All 123 tests must pass.
5. **Behavioral check**: With `SetInitialDirection` + proper roll, `computedAngleN2` should be auto-derived and match expected ζ values.

---

## 7. Implementation Progress (updated 2026-03-13)

### What was done (commits after this review was written)

#### Step 1–4: DSL mandatory roll + factory cleanup (Claude Code)

All four initial steps from the plan above were implemented:

- **`DirectionChangeDSL_13384.scala` / `DirectionChangeDSL_15544.scala`**: `roll: Option[Angle] = None` → `roll: Angle` on all abstract methods and convenience methods (replace_all).
- **`DirectionChangeDSL_13384_Instances.scala` / `DirectionChangeDSL_15544_Instances.scala`**: `roll: QtyD[Degree]` (non-optional), wraps in `Some(roll)` when constructing DTOs. Handles `addAngleSpecifique` separately (had extra spaces in signature).
- **`ElementFactory_15544_Instances.scala`**: `legacyAngleN2` fallback removed. Pattern match now ignores the DTO roll field (`case AddSharpeAngle_0_to_180(_, angle, _) => .AngleVifDe0A180(angle, computedAngleN2)`).
- **Builder helpers** (`ThermalIncrementalBuilder_13384`, `FlowOnlyIncrementalBuilder_13384`, `FlowOnlyIncrementalBuilder_15544`): all direction change methods updated to `roll: Angle`.

All callers in `engine`, `fdim`, `labo`, `ui` modules were fixed (30 files total, ~533 insertions). Full compile passes with only unused-import warnings.

#### Step 5: Proper direction information added by user (commit `2594e9f`)

The user manually provided semantically correct `SetInitialDirection` + `roll` values for all hardcoded pipe descriptions. This was the key manual step that could not be automated — these represent real physical pipe geometries. Files reworked with actual direction tracking:

- `CasType_15544_C1/C2/C3/C3_FDIM.scala` — initial direction + proper roll angles per turn
- `ExampleProject_15544.scala`
- `EcolabeledToFireboxInternalPipes_15544_Strict.scala` — fully restructured into `start_00/01/end` Seq composition
- `AFPMA_PRSEToFireboxInternalPipes_15544_Strict.scala`
- `AFPMA_PRSE_Firebox_To_FireboxInternalPipes_15544_MCE.scala`
- `EcoLabeled_To_FireboxInternalPipes_15544_MCE.scala`
- `FlowOnlyDynamicFrictionCoeff_15544.scala` (test) — roll angles updated to reflect actual alternating/successive geometry
- `fdim` and `labo` exercise files

### Current test status (2026-03-13): **8 tests failing**

Run: `sbt engine/test` → 115 passed, 8 failed

#### Failure group A: `DynamicFrictionCoeffOp_EN15544_Suite` (5 failures)

File: `modules/engine/src/test/scala/afpma/firecalc/engine/ops/en15544/FlowOnlyDynamicFrictionCoeff_15544.scala`

| Test | Expected ζ | Actual ζ | Computed angleN2 |
|------|-----------|---------|-----------------|
| C2 alternating 90° (virage 4-5) | 0.44 | 0.82 | Some(90°) |
| C2 alternating 90° (virage 5-6) | 0.44 | 0.82 | Some(90°) |
| dh/2 alternating 90° (virage 2) | 0.60 | 0.90 | Some(90°) |
| dh/4 alternating 90° (virage 2) | 0.30 | 0.75 | Some(90°) |
| dh/2 successive 45° (virage 2)  | 0.50 | 0.30 | Some(45°) |
| dh/2 successive 30° (virage 2)  | 0.35 | 0.125 | Some(30°) |

Root cause: The user's physical roll angles produce `angleN2` values that are geometrically correct, but the EN15544 §4.9.5 short-section formula produces different ζ reduction factors than what was manually coded before. The expected values in the tests were historically derived from a specific interpretation of the standard. The subagent needs to either (a) find the roll angles that produce the old `angleN2` → expected ζ, or (b) verify whether the new computed values are physically correct and update expectations.

#### Failure group B: `Pipes_15544_IncrementalBuilder` (2 failures)

File: `modules/engine/src/test/scala/afpma/firecalc/engine/impl/en15544/incremental.scala`

**Test "case 2" (L124)**: After one 45° horizontal bend with `roll = 90.degrees` (towards Right direction), the next `StraightSection` has `elevation_gain = 4.329780281177466E-17` instead of `0.0`. This is floating-point near-zero from `sin(90°) * cos(45°) * sin(something)` in the direction tracking math. The section is effectively horizontal; the test hardcodes `0.0`.

**Test "case 3" (L223)**: Two successive 45° leftward bends (`roll = -90.degrees`). The second "turn left" direction change now has `angleN2 = Some(45.0)` (direction tracking computes the angle between approach directions), but the test expected `angleN2 = None`. This expectation is now wrong — direction tracking is active so `angleN2` IS computed.

#### Failure group C: `cas_types_15544_C3_Suite` (1 failure)

File: `modules/engine/src/main/scala/afpma/firecalc/engine/impl/en15544/strict/EcolabeledToFireboxInternalPipes_15544_Strict.scala`

The refactored file computes `air_intake_equivalent_shape` as a top-level `val` that calls `arriveeAirGeometryOpt.getOrElse(throw ...)`. This is evaluated eagerly for **all** firebox versions, but `arriveeAirGeometryOpt` is `None` for V1 fireboxes. The C3 test uses an Ecolabeled V1 firebox → exception. The val must be moved inside the `start_01_version_2 = Seq(...)` block (which is only used for V2).

### Exploration paths

#### Path A (recommended for group B): Update incremental test expectations
- Case 2: Use `shouldEqual(0.meters +- 1e-10.meters)` instead of exact `0.0`, or verify that the direction-tracked horizontal elevation_gain is ε-close to zero.
- Case 3: Update expected `DirectionChange.AngleVifDe0A180(45.degrees)` to `DirectionChange.AngleVifDe0A180(45.degrees, angleN2 = Some(45.degrees))`. Validate this matches the standard.

#### Path B (for group C): Fix eager evaluation bug
Move `air_intake_equivalent_shape` inside the `start_01_version_2` Seq literal. One-line fix.

#### Path C (for group A): Understand the ζ formula and roll geometry
The `FlowOnlyDynamicFrictionCoeff_15544` formula computes ζ reduction from `angleN2` via EN15544 §4.9.5 Table/formula. Read `shortsection.scala` to understand the exact function. Then determine: what `angleN2` values reproduce the expected ζ? Then determine: what `roll` angles produce those `angleN2` values given horizontal pipes with known initial direction? The goal is either to fix the roll angles or to accept the new computed ζ as the physically correct answer.

See `tasks/fix-tests-angleN2-roll-migration.md` for full subagent instructions.

## 8. Files Changed (same 14 + potential new validation error type)

```
modules/engine/.../typeclasses/DirectionChangeDSL_13384.scala          # roll: Angle (mandatory)
modules/engine/.../typeclasses/DirectionChangeDSL_15544.scala          # roll: Angle (mandatory)
modules/engine/.../instances/DirectionChangeDSL_13384_Instances.scala  # Some(roll) to DTO
modules/engine/.../instances/DirectionChangeDSL_15544_Instances.scala  # Some(roll) to DTO
modules/engine/.../instances/ElementFactory_15544_Instances.scala      # remove legacyAngleN2 fallback
modules/engine/.../en13384/ThermalIncrementalBuilder_13384.scala       # roll: Angle (mandatory)
modules/engine/.../en13384/FlowOnlyIncrementalBuilder_13384.scala      # roll: Angle (mandatory)
modules/engine/.../en15544/common/FlowOnlyIncrementalBuilder_15544.scala  # roll: Angle (mandatory)
modules/engine/.../cas_types/en15544/CasType_15544_C2.scala            # + setInitialDirection + roll values
modules/engine/.../cas_types/en15544/CasType_15544_C3.scala            # + setInitialDirection + roll values
modules/engine/.../cas_types/en15544/CasType_15544_C3_FDIM.scala       # + setInitialDirection + roll values
modules/engine/.../strict/EcolabeledToFireboxInternalPipes_*.scala     # + setInitialDirection + roll values
modules/engine/.../strict/AFPMA_PRSEToFireboxInternalPipes_*.scala     # + setInitialDirection + roll values
modules/engine/src/test/.../FlowOnlyDynamicFrictionCoeff_15544.scala   # + setInitialDirection + roll values
```
