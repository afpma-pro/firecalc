# Task: Fix Engine Elevation Gain / Height Mismatch After angleN2→roll Refactoring

## Status: Investigation Complete, Ready for Fix

## Problem Summary

After the `angleN2 → roll` refactoring (commits `f085b11`, `5706853`, `7556244`), the engine
validation exports show incorrect elevation gain (`h` column) and cascading pressure mismatches
in multiple cas types:

- **EN 13384 C16**: dévoiement `h` went from 0.700m → 0.495m, "avant plafond" `h` went from 0.700m → `-` (zero)
- **EN 15544 C2 (Kachelofen)**: height subtotals changed, pressure results degraded
- **EN 15544 C3 (Cas pratique)**: total height dropped 9.309m → 5.065m (46% drop), buoyancy halved

## Root Cause Analysis

### Issue 1: `finalElevGain` always overrides explicit `elevation_gain` when direction tracking is active

**Location**: `ElementFactory_13384_Instances.scala:94-96`, `ElementFactory_15544_Instances.scala:91-93`

```scala
val finalElevGain = ctx.currentFrame match
    case Some(frame) => (len.value * frame.direction.z).m   // <-- always wins
    case None        => elev_gain                            // <-- fallback only
```

When `currentFrame` exists (because `SetInitialDirection` was called), the explicit
`elevation_gain` parameter from `addSectionSlopped` is **completely ignored**. The height is
always computed as `length × direction.z`.

**Example**: C16 connector pipe has `addSectionSlopped("dévoiement", 70.cm, elevation_gain = 70.cm)`.
The explicit `elevation_gain = 70.cm` was an intentional override (see code comment: `// approx to
match C16 (50cm otherwise)`). But direction tracking computes `0.70 × sin(45°) = 0.495m` instead.

This is arguably correct geometrically (the pipe really does gain only 0.495m of height at 45°),
but **the cas type definitions were authored with explicit overrides that assumed they'd be used**.

### Issue 2: Direction tracking produces wrong direction through dévoiement (45°+straight+45°) sequences

**Traced math for C16 connector pipe**:

| Step | Element | Expected Direction | Actual Direction |
|------|---------|-------------------|-----------------|
| 0 | `setInitialDirection(0°, 0°)` | Rear | Rear ✓ |
| 1 | `addSharpAngle_90deg(roll=0°)` | Up | Up ✓ |
| 2 | `addSharpAngle_45deg(roll=0°)` — "dévoiement 45°" | Rear ↑45° | **Front ↑45°** |
| 3 | `addSharpAngle_45deg(roll=0°)` — "fin dévoiement 45°" | Up | **Front (horizontal!)** |

**Why**: After step 1 (90° bend from Rear to Up), the frame is `dir=Up, upRef=Front`.
With `roll=0°`, the bend axis is `rightRef = Up × Front = Right`, and rotating Up around
Right goes toward **Front** (not Rear). So the dévoiement points Front-Up instead of Rear-Up.
After the return 45° bend, the direction becomes purely Front (horizontal, z=0).

This means `addSectionVertical("avant plafond", 70.cm)` gets `h = 70cm × 0 = 0`, which
explains why it shows `h = -` in the export.

**The roll values in the cas type DSL are wrong for the intended geometry.** The comment says
`// Rear-Up (azimuth=0° inclination=45°)` but `roll=0°` from the vertical Up frame produces
Front-Up instead.

### Issue 3: Pipe ordering change in C16 air intake

The diff shows pipes 8 and 9 swapped (a horizontal section and a 90° bend exchanged positions).
This is a separate issue from elevation — likely related to how direction changes are interleaved
with straight sections in the incremental builder.

## Impact

These issues cascade through the entire calculation:
- Wrong `h` → wrong buoyancy pressure (`pH = ρ × g × h × (1 - ρ_cold/ρ_hot)`)
- Wrong pH → wrong total draft (`P_Z`, `P_Zmax`)
- Wrong draft → wrong compliance results (previously passing checks now fail)

## Fix Plan

### Step 1: Decide on elevation_gain override policy

Two options:

**Option A**: When `addSectionSlopped` provides an explicit `elevation_gain`, use it even when
direction tracking is active. Only auto-compute for `addSectionHorizontal` and `addSectionVertical`.

```scala
val finalElevGain = op match
    case AddSectionSlopped(_, _, explicitElev) =>
        // Explicit elevation_gain always takes precedence
        explicitElev
    case _ =>
        ctx.currentFrame match
            case Some(frame) => (len.value * frame.direction.z).m
            case None        => elev_gain
```

**Option B**: Fix the roll values in the cas type DSL to produce the correct geometry, and let
direction tracking compute the correct elevation_gain. Remove explicit overrides.

**Recommendation**: **Option B** — this is the more correct approach. The direction tracking
*should* be the source of truth. The explicit `elevation_gain` was a workaround from before
direction tracking existed. However, this requires carefully fixing every roll value in every
cas type definition.

### Step 2: Fix roll values in cas type pipe descriptions

For each cas type, trace the expected 3D geometry and compute the correct `roll` angle at
each direction change. The key insight: roll is relative to the current frame's `upRef`, not
to absolute coordinates.

**C16 connector pipe fix**: The dévoiement should go Rear-Up (away from the stove), not Front-Up.
After the 90° bend from Rear→Up, the frame is `dir=Up, upRef=Front`. To bend toward Rear from
this frame, need `roll=180°` (not 0°):
- `roll=0°` → bend toward Front (current behavior)
- `roll=180°` → bend toward Rear (intended behavior)

```scala
addSharpAngle_45deg("dévoiement 45°",     roll = 180.degrees),  // was: 0.degrees
addSectionSlopped  ("dévoiement",         70.cm, elevation_gain = 70.cm),
addSharpAngle_45deg("fin dévoiement 45°", roll = 180.degrees),  // was: 0.degrees
```

After this fix, the direction at "avant plafond" should be `Up` again, and the auto-computed
elevation gain will be `70cm × 1.0 = 70cm` ✓.

The `elevation_gain = 70.cm` override on the slopped section still won't match the auto-computed
value (which would be `70cm × cos(45°) ≈ 49.5cm`), so either:
- Remove the explicit override and accept 49.5cm as the geometrically correct value
- Or keep the explicit override but only if Option A is implemented

**For each affected cas type**, similar roll value corrections are needed.

### Step 3: Fix or accept the slopped section elevation_gain discrepancy

The C16 reference data uses `h = 0.700m` for the dévoiement (treating a 70cm pipe at 45° as
having 70cm of height gain). This is **physically incorrect** — the actual height gain is
`70cm × sin(45°) ≈ 49.5cm`. The original spreadsheet (QC2) likely used a simplified model.

**Decision needed**: Accept the geometrically correct value (49.5cm) or maintain compatibility
with the reference spreadsheet (70cm)? If the reference spreadsheet is the ground truth for
validation, we may need Option A.

### Step 4: Investigate and fix the pipe ordering issue in C16 air intake

The air intake pipes 8/9 swapped order. This needs separate investigation — likely the
incremental builder is processing direction changes in a different order than expected.

### Step 5: Validate all cas types

After fixes, regenerate all exports:
```bash
modules/engine/sbt-run-cas-types-13384.sh
modules/engine/sbt-run-cas-types-15544.sh
```

Compare with the reference values and verify compliance results match expected outcomes.

## Key Files

| File | Role |
|------|------|
| `modules/engine/.../instances/ElementFactory_13384_Instances.scala:94-96,181-183` | elevation_gain computation |
| `modules/engine/.../instances/ElementFactory_15544_Instances.scala:91-93` | elevation_gain computation |
| `modules/engine/.../geometry/PipeFrame.scala:31-44` | applyBend (Rodrigues rotation) |
| `modules/engine/.../geometry/Vec3.scala` | direction vector math |
| `modules/engine/.../cas_types/en13384/CasType_13384_C16.scala:157-180` | C16 connector pipe description |
| `modules/engine/.../cas_types/en15544/CasType_15544_C2.scala` | Kachelofen pipe descriptions |
| `modules/engine/.../cas_types/en15544/CasType_15544_C3.scala` | Cas pratique pipe descriptions |

## Open Questions

1. Should explicit `elevation_gain` in `addSectionSlopped` override direction tracking or not?
2. Is the reference spreadsheet (QC2) the ground truth, or is geometric correctness preferred?
3. Do the same roll value issues affect the air intake pipe descriptions?
