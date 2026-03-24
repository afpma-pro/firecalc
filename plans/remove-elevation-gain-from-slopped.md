# Plan: Remove `elevation_gain` from `AddSectionSlopped`

## Context

With the `roll → absDir` migration complete, the engine tracks pipe direction via `PipeFrame` and auto-computes `elevation_gain = length × frame.direction.z` in the element factories. The `elevation_gain` field on `AddSectionSlopped` is now redundant — the UI never needs to expose it. Removing it simplifies the DTO, DSL, and UI layers.

`AddSectionSloppedForceManualElevationGain` is **kept** as an escape hatch for cas_types that need explicit overrides (e.g., `CasType_13384_C16`).

Still V3 naming (unreleased).

---

## Step 1: DTOs — Remove `elevation_gain` from `AddSectionSlopped` (3 files)

Remove the `elevation_gain: Length` field and its `@Transl` annotation from `AddSectionSlopped`. Goes from 3 fields `(name, length, elevation_gain)` to 2 fields `(name, length)`.

`AddSectionSloppedForceManualElevationGain` unchanged in all 3 files.

**Files:**
- `modules/dto/src/main/scala/afpma/firecalc/dto/v4/FlowOnlyPipeDescr_15544_V3.scala` (lines 59-67)
- `modules/dto/src/main/scala/afpma/firecalc/dto/v4/FlowOnlyPipeDescr_13384_V3.scala` (lines 59-67)
- `modules/dto/src/main/scala/afpma/firecalc/dto/v4/ThermalPipeDescr_13384_V3.scala` (lines 134-142)

---

## Step 2: SectionDSL typeclass — Split into two methods (1 file)

Replace single `addSectionSlopped(name, length, elevation_gain, auto_compute_elev_gain)` with:

```scala
def addSectionSlopped(name: String, length: Length): Descr

def addSectionSloppedForceManualElevationGain(
    name: String, length: Length, elevation_gain: Length
): Descr
```

Keep deprecated `addSectionHorizontal` and `addSectionVertical` as-is.

**File:** `modules/engine/src/main/scala/afpma/firecalc/engine/impl/common/typeclasses/SectionDSL.scala`

---

## Step 3: SectionDSL instances — Implement both methods (2 files)

Each `given` instance (3 total: `flowOnly15544`, `thermal13384`, `flowOnly13384`) gets two method implementations instead of one with an if/else branch.

`addSectionSlopped` constructs `AddSectionSlopped(name, length)` (2 args).
`addSectionSloppedForceManualElevationGain` constructs `AddSectionSloppedForceManualElevationGain(name, length, elevation_gain)`.

**Files:**
- `modules/engine/src/main/scala/.../instances/SectionDSL_15544_Instances.scala`
- `modules/engine/src/main/scala/.../instances/SectionDSL_13384_Instances.scala`

---

## Step 4: Element factories — Update pattern extraction (2 files)

Update the `(len, elev_gain, auto_compute_elev_gain)` pattern match. `AddSectionSlopped` now extracts `(_, len)` and always yields `(len, 0.0.m, true)`.

3 factory givens to update:
- `flowOnlyStraightSection15544` in `ElementFactory_15544_Instances.scala`
- `flowOnlyStraightSection13384` in `ElementFactory_13384_Instances.scala`
- `thermalStraightSection13384` in `ElementFactory_13384_Instances.scala`

**Files:**
- `modules/engine/src/main/scala/.../instances/ElementFactory_15544_Instances.scala`
- `modules/engine/src/main/scala/.../instances/ElementFactory_13384_Instances.scala`

---

## Step 5: Builders — Update signatures and pattern matches (3 files)

### 5a. Convenience methods

Change `addSectionSlopped(name, length, elevation_gain)` → `addSectionSlopped(name, length)`.
Add `addSectionSloppedForceManualElevationGain(name, length, elevation_gain)`.

### 5b. `nextSectionLengthOpt` pattern matches

`AddSectionSlopped(_, l, _)` → `AddSectionSlopped(_, l)` — in all 3 builders.

### 5c. `updateStateAfterConversionStep` pattern matches

`AddSectionSlopped(_, _, _)` → `AddSectionSlopped(_, _)` — in all 3 builders.

### 5d. Type union matches

`case _: (AddSectionSlopped | ...)` — no field extraction, unchanged.

**Files:**
- `modules/engine/.../en15544/common/FlowOnlyIncrementalBuilder_15544.scala` (lines 116, 138, 200)
- `modules/engine/.../en13384/FlowOnlyIncrementalBuilder_13384.scala` (lines 99, 105, 138, 189)
- `modules/engine/.../en13384/ThermalIncrementalBuilder_13384.scala` (lines 111, 117, 153, 211)

---

## Step 6: PositionTracker — Compute elevation from frame (1 file)

3 occurrences of `AddSectionSlopped(_, length, elevGain)` → `AddSectionSlopped(_, length)`.

Compute elevation from frame direction:
```scala
case AddSectionSlopped(_, length) =>
    val l  = length.toUnit[Meter].value
    val eg = frame.map(f => l * f.direction.z).getOrElse(0.0)
    // rest unchanged
```

Note: `AddSectionSloppedForceManualElevationGain` is NOT currently handled in PositionTracker. Add a case for it that uses its explicit `elevation_gain` (same logic as old `AddSectionSlopped`).

**File:** `modules/engine/src/main/scala/.../models/geometry/PositionTracker.scala` (lines 78, 158, 244)

---

## Step 7: DTO transformers — Drop elevation_gain in V2→V3 migration (1 file)

### 7a. FlowOnly V2→V3 (explicit transformers)

Lines 190 and 224: `El2.AddSectionSlopped(n, l, e) => El3.AddSectionSlopped(n, l, e)` → `El3.AddSectionSlopped(n, l)` (drop `e`).

### 7b. Thermal V2→V3 (Chimney auto-derivation → explicit transformer)

Line 170-174: The `thermalV2ToV3` transformer uses `Transformer.define[...].enableOptionDefaultsToNone.buildTransformer`. Chimney auto-derivation will **fail** because V2's `AddSectionSlopped` has 3 fields and V3's has 2.

Replace with an explicit match-based transformer (like the flow-only ones). The `elevation_gain` is simply dropped — users migrating from old versions will need to set `absDir` or relative direction anyway. Acceptable trade-off.

### 7c. V1→V2 (no change)

Lines 80 and 108 map V1→V2, and V2 still has `elevation_gain`. No change needed.

**File:** `modules/dto/src/main/scala/afpma/firecalc/dto/transformers.scala`

---

## Step 8: Cas types — Update call sites (3 files)

| File | Before | After |
|------|--------|-------|
| `cas_types/en15544/ExampleProject_15544.scala` | `addSectionSlopped("conduit...", 707.mm, elevation_gain = 500.mm)` | `addSectionSlopped("conduit...", 707.mm)` |
| `cas_types/en15544/CasType_15544_C2.scala` | `addSectionSlopped("Car. 12", 50.cm, elevation_gain = 50.cm * cos(π/4))` | `addSectionSlopped("Car. 12", 50.cm)` |
| `cas_types/en13384/CasType_13384_C16.scala` | `addSectionSlopped("dévoiement", 70.cm, elevation_gain = 70.cm, auto_compute_elev_gain = false)` | `addSectionSloppedForceManualElevationGain("dévoiement", 70.cm, 70.cm)` |

---

## Step 9: fdim module — Update call site (1 file)

`modules/fdim/.../p2_cf/strict_ex02_kachelofen.scala` line 116:
`addSectionSlopped("conduit simple peau 2", 58.cm, elevation_gain = 48.8.cm)` → `addSectionSlopped("conduit simple peau 2", 58.cm)`

---

## Step 10: UI defaultables — Remove 3rd argument (2-3 files)

`AddSectionSlopped(name, 1.meters, 0.meters)` → `AddSectionSlopped(name, 1.meters)`.

**Files:**
- `modules/ui/.../instances/defaultable_15544.scala`
- `modules/ui/.../instances/defaultable_13384.scala`
- `modules/ui/.../instances/FlowOnlyDefaultable_13384.scala` (if exists)

---

## Step 11: UI horizontal forms — No code changes expected

Auto-derived via `autoDeriveAndOverwriteFieldNames_AddElement_Subtype[AddSectionSlopped]`. Field removal is automatic.

---

## Step 12: Tests — Update (2 files)

### 12a. `incremental.scala` (en15544)

Line 120: `addSectionSlopped("s1", 2.meters, 0.meters)` → `addSectionSlopped("s1", 2.meters)`

### 12b. `PositionTrackerSuite.scala`

Line 63: `AddSectionSlopped("s", 5.0.meters, 3.0.meters)` → `AddSectionSlopped("s", 5.0.meters)`.

This test constructs DTO objects directly (no frame). After the change, PositionTracker computes `eg = frame.map(f => l * f.direction.z).getOrElse(0.0)`. With `frame = None`, elevation is 0, so the section goes purely horizontal → endpoint `(0, 5, 0)`. The test expects `(0, 4, 3)`.

**Fix:** Set up an initial direction before the slopped section so that `frame.direction.z` produces the expected elevation. E.g., `SetInitialDirection(AzimuthDirection.Rear, InclinationDirection.Custom(~36.87.degrees))` where `sin(36.87°) = 0.6`, giving `eg = 5 * 0.6 = 3.0`.

---

## Verification

1. `compile-module(dto)` + `compile-module(dtoJS)` — cross-compile DTOs
2. `compile-module(engine)` — engine compiles
3. `compile-module(ui)` — UI compiles
4. `compile-module(fdim)` — fdim compiles
5. `sbt --client 'engine/test'` — all engine tests pass (including PositionTrackerSuite)
6. `sbt --client 'dto/test'` — DTO serialization round-trips
7. `compile-full()` — full project compiles

---

## Implementation Order

```
Step 1 (DTOs)  ──── breaks everything downstream; must be followed immediately by:
  Step 2 (SectionDSL typeclass)
  Step 3 (SectionDSL instances)
  Step 4 (Element factories)
  Step 5 (Builders)
  Step 6 (PositionTracker)
  Step 7 (Transformers)
  Step 8 (Cas types)
  Step 9 (fdim)
  Step 10 (UI defaultables)
  Step 12 (Tests)
```

All steps are one atomic batch (single commit).
