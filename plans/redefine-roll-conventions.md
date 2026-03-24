# Plan: Redefine Roll Convention

**Status**: Not yet implemented
**Prerequisite**: review-direction-3d.md items done

---

## Motivation

The current roll convention is counterintuitive:
- Vertical pipe (up): `Right=0°`, `Rear=90°`, `Left=180°`, `Front=270°`
- Horizontal pipe (rear): `Left=0°`, `Up=90°`, `Right=180°`, `Down=270°`

The desired convention follows the **right-hand rule** (positive angles counterclockwise
when looking into the pipe from outside in the direction of flow):
- Vertical pipe (up): `Rear=0°`, `Left=90°`, `Front=180°`, `Right=270°`
- Horizontal pipe (rear): `Up=0°`, `Right=90°`, `Down=180°`, `Left=270°`

The first preset (0°) always corresponds to the "natural" direction:
- For a vertical pipe going up, 0° bends toward the Rear of the stove.
- For a horizontal pipe going rearward, 0° bends upward.

---

## Coordinate System (unchanged)

```
Facing the stove:
  +X = right
  +Y = rear (away from you)
  +Z = up (gravity)

upRef convention (unchanged):
  Vertical pipe   → upRef = Rear (+Y)
  Horizontal pipe → upRef = Up   (+Z)
```

---

## Right-Hand Rule Convention

**Roll angle follows the right-hand rule around the pipe axis:**

> Point your right thumb in the **direction of flow** (pipe axis).
> Your fingers curl in the direction of **increasing positive roll angle**.
> Equivalently: positive roll = counterclockwise when looking from outside
> into the pipe opening (i.e., looking in the direction of flow).

Examples:
- (i) Vertical pipe (up, axis = +Z): looking upward from below into the pipe:
  - 0° = Rear (+Y, 12 o'clock)
  - 90° = Left (-X, 9 o'clock)
  - 180° = Front (-Y, 6 o'clock)
  - 270° = Right (+X, 3 o'clock)

- (ii) Horizontal pipe (rear, axis = +Y): looking toward rear from the front into the pipe:
  - 0° = Up (+Z, 12 o'clock)
  - 90° = Right (+X, 3 o'clock)
  - 180° = Down (-Z, 6 o'clock)
  - 270° = Left (-X, 9 o'clock)

---

## Required Code Changes

### 1. `PipeFrame.applyBend` — Formula change

**File**: `modules/engine/src/main/scala/.../geometry/PipeFrame.scala`

Current formula:
```scala
val bendAxis = (upRef * math.cos(rollRad) + rightRef * math.sin(rollRad)).normalized
```

New formula:
```scala
val bendAxis = (rightRef * math.cos(rollRad) - upRef * math.sin(rollRad)).normalized
```

**Derivation**: With `rightRef = direction.cross(upRef)`, the new formula satisfies:
- `applyBend(90°, 0°)` on vertical up → output direction `+Y` (Rear) ✓
- `applyBend(90°, 90°)` on vertical up → output direction `-X` (Left) ✓
- `applyBend(90°, 0°)` on horizontal rear → output direction `+Z` (Up) ✓
- `applyBend(90°, 90°)` on horizontal rear → output direction `+X` (Right) ✓

The new formula equals the old formula evaluated at `roll + 90°`. Existing stored roll
values must therefore be decremented by 90° (mod 360°) to preserve pipe geometry.

### 2. `PipeFrame.rollAngleForOutputDirection` — Inverse formula update

**File**: `modules/engine/src/main/scala/.../geometry/PipeFrame.scala`

The inverse must match the new forward formula.

Current:
```scala
val cosR = upRef.dot(bendAxis)
val sinR = rightRef.dot(bendAxis)
Some(math.toDegrees(math.atan2(sinR, cosR)))
```

New (derived from `bendAxis = rightRef*cos(roll) - upRef*sin(roll)`):
```scala
val cosR = rightRef.dot(bendAxis)
val sinR = -upRef.dot(bendAxis)
Some(math.toDegrees(math.atan2(sinR, cosR)))
```

### 3. `PipeFrame.scala` doc comment — Update roll convention table

**File**: `modules/engine/src/main/scala/.../geometry/PipeFrame.scala`, lines 13–17

Replace current comment block with:
```scala
/**
 * Local frame that rides along a pipe.
 * direction: unit vector in the direction of flow
 * upRef: unit vector perpendicular to direction, tracking the "up" reference for roll measurement
 *
 * Roll convention (right-hand rule around pipe axis):
 *   Positive roll = counterclockwise when looking INTO the pipe from outside (in direction of flow).
 *   Equivalently: right thumb along flow direction → fingers point in direction of increasing roll.
 *
 *   Vertical pipe (up, dir=+Z, upRef=Rear=+Y):
 *     roll 0°=Rear, 90°=Left, 180°=Front, 270°=Right
 *
 *   Horizontal pipe (rear, dir=+Y, upRef=Up=+Z):
 *     roll 0°=Up, 90°=Right, 180°=Down, 270°=Left
 */
```

Also update the `applyBend` parameter comment:
```scala
 * @param rollDeg roll angle in degrees (which way — 0°=upRef cross direction, right-hand rule)
```

Wait — with the new formula, roll=0° gives `bendAxis = rightRef`, and the output direction is
`rightRef × direction`. For vertical up: `rightRef = +Z × +Y = -X`, `-X × +Z = +Y` = Rear. So
"0°=Rear side" is the common human description, but technically 0° corresponds to the bend axis
being `rightRef`. The description should say "0°=toward the side that rightRef points away from".
Keep it simple: just use the cardinal examples shown in the class doc comment.

### 4. `plans/direction-3d.md` — Update coordinate system section

**File**: `plans/direction-3d.md`

Update the "Coordinate System" section (lines 84–112):
- Replace the roll=0° mapping table — old: `Right=0°`, new: `Rear=0°` for vertical; old: `Left=0°`, new: `Up=0°` for horizontal.
- Replace the `applyBend` walkthrough examples.
- Update the "Preset → roll angle mapping" table.
- Remove the "Known deviations" entry about the comment discrepancy (it will be fixed).

### 5. `Vec3.toDisplayString` — No change needed

The coordinate system (+X=right, +Y=rear, +Z=up) and cardinal names are unchanged.

### 6. `PipeFrameSuite` tests — Update expected values

**File**: `modules/engine/src/test/scala/.../PipeFrameSuite.scala`

All tests that assert specific roll angles or bend output directions need updating:
- Tests that use `applyBend(90, 0)` and expect Right (+X) → now expect Rear (+Y)
- Tests for `rollAngleForOutputDirection` that assert specific degree values → subtract 90°
- Tests for `reachableCardinals` that check roll angle labels → update mapping

**Strategy**: Run the test suite after the formula change; failing tests will enumerate every
assertion that needs updating. Fix systematically.

### 7. DTO migration — Adjust stored roll values

**Impact**: All existing YAML/saved pipes that have a `roll` value on any DC element.

**Migration rule**: `new_roll = ((old_roll - 90) % 360 + 360) % 360`

**Where to implement**:
- In the YAML version migration transformer (v1→v2 or a new v2→v3 step, depending on
  whether this constitutes a breaking DTO change).
- Check: `modules/engine/src/.../yaml/v0_2024_10.scala` or equivalent migration file
  for where existing roll-bearing migrations live.
- The migration must apply to all `AddDirectionChange` subtypes in all three pipe hierarchies
  (13384 thermal, 13384 flow-only, 15544).

**Note**: If only `roll = Some(...)` values exist (not None), only non-None roll angles need
migrating. `roll = None` means no direction tracking → unaffected.

---

## Summary of Changed Files

| File | Change |
|------|--------|
| `geometry/PipeFrame.scala` | Formula in `applyBend`, inverse in `rollAngleForOutputDirection`, doc comment |
| `plans/direction-3d.md` | Roll convention table, examples, known deviations |
| `PipeFrameSuite.scala` | Update test expected values for roll angles |
| YAML migration transformer | `new_roll = old_roll - 90°` for all stored DC roll values |

---

## Rollout Checklist

- [ ] Change `applyBend` formula
- [ ] Change `rollAngleForOutputDirection` inverse
- [ ] Fix `PipeFrame.scala` doc comment
- [ ] Update `plans/direction-3d.md`
- [ ] Run `PipeFrameSuite` → fix failing tests
- [ ] Add/update YAML migration step
- [ ] Manual smoke test: create a pipe with `SetInitialDirection` + DC + roll, verify cardinal labels
      match the new convention
