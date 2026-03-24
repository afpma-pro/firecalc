# Refactor DirectionChange Helper Methods — Add `roll`, Fix `angleN2` Conflation

## Context

Two distinct angle concepts exist for direction changes:

- **`roll`** (DTO field on `AddDirectionChange`): Rotation around the pipe axis. Used by `updateStateAfterConversionStep` to update the 3D `PipeFrame` via `frame.applyBend(deflectionDeg, rollDeg)`.
- **`angleN2`** (engine model field on `FlowOnlyPipeDescr_15544.DirectionChange`): Angle between direction before DC(n-1) and direction after DC(n). Used for EN15544 ζ calculations. Auto-computed from direction tracking.

**Bug**: `DirectionChangeDSL_15544` has a parameter called `angleN2` that positionally maps to the DTO's `roll` field. The factory then reads this `roll` field and uses it as `angleN2`. This conflation means:
- Setting "angleN2" via DSL actually sets the `roll` field (unintended 3D frame side-effects)
- No way to set `roll` independently for 3D tracking
- 13384 DSL has no `roll` at all

**History**: V2 DTO had `angle_to_original_direction` on `AddSharpeAngle_0_to_180`. The V2→V3 transformer already drops it: `El3.AddSharpeAngle_0_to_180(n, a, None)`. V3 DTO only has `roll`. The DSL's `angleN2` parameter is a vestige.

## Changes

### 1. DSL Typeclasses — rename `angleN2` → `roll` (15544), add `roll` (13384)

**`DirectionChangeDSL_15544.scala`**: Rename `angleN2` → `roll` on all methods.
**`DirectionChangeDSL_13384.scala`**: Add `roll: Option[Angle] = None` to all methods.

### 2. DSL Instances — pass `roll` correctly

**`DirectionChangeDSL_15544_Instances.scala`**: Rename `angleN2` → `roll`, pass to DTO `roll` field.
**`DirectionChangeDSL_13384_Instances.scala`**: Forward `roll` to DTO constructors (both instances).

### 3. Factory fix — stop reading `angleN2` from DTO `roll` field

**`ElementFactory_15544_Instances.scala`**: The pattern match currently extracts the 3rd positional field (DTO `roll`) and calls it `angleN2`. Fix: ignore DTO `roll` in pattern match, rely solely on `computedAngleN2` from direction tracking.

```scala
// Before (buggy conflation):
case AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(_, angle, angleN2) =>
    .AngleVifDe0A180(angle, computedAngleN2.orElse(angleN2))

// After (correct):
case AddFlowOnlyPipeElement_15544.AddSharpeAngle_0_to_180(_, angle, _) =>
    .AngleVifDe0A180(angle, computedAngleN2)
```

### 4. Builder helpers — add `roll` parameter

All 3 builders: add `roll: Option[Angle] = None` to direction change helpers.
15544 builder: also rename `angleN2` → `roll`.

## Files

| # | File | Change |
|---|------|--------|
| 1 | `.../typeclasses/DirectionChangeDSL_13384.scala` | Add `roll: Option[Angle] = None` to all methods |
| 2 | `.../typeclasses/DirectionChangeDSL_15544.scala` | Rename `angleN2` → `roll` |
| 3 | `.../instances/DirectionChangeDSL_13384_Instances.scala` | Forward `roll` to DTO constructors |
| 4 | `.../instances/DirectionChangeDSL_15544_Instances.scala` | Rename `angleN2` → `roll`, add to `addCircularArc60` |
| 5 | `.../instances/ElementFactory_15544_Instances.scala` | Drop `angleN2` fallback from DTO `roll` field |
| 6 | `.../en13384/ThermalIncrementalBuilder_13384.scala` | Add `roll` to DC helpers |
| 7 | `.../en13384/FlowOnlyIncrementalBuilder_13384.scala` | Add `roll` to DC helpers |
| 8 | `.../en15544/common/FlowOnlyIncrementalBuilder_15544.scala` | Rename `angleN2` → `roll` on DC helpers |

## Backward Compatibility

- All callers default `roll = None` → no breakage
- Zero callers pass `angleN2` explicitly (grep confirmed)
- V2→V3 transformer already drops `angle_to_original_direction` → `None`
- Factory fix only changes behavior when `roll` was manually set AND direction tracking off — never happens

## Verification

1. `compile-full` — must pass
2. `sbt engine/test` — all tests pass
3. Verify `addCoudeCourbe90("turn", 50.mm, roll = Some(90.degrees))` correctly sets DTO `roll` and updates 3D frame
