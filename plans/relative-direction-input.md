# Plan: Relative Left/Right + Theta Rotation in DirectionBadgeComponent — DONE (`a308d5c`)

## Context

After the `roll → absDir` migration, the `DirectionBadgeComponent` dropdown lists reachable **cardinal** directions via `PipeFrame.reachableCardinals(deflDeg)`. When the deflection angle doesn't match any cardinal (e.g. 65° from horizontal), the list is empty and the user can't set a direction.

**Goal**: Add a "relative direction" UI section below each direction-change form row, with:
- **Side toggle**: Left / Right (relative to the pipe's local frame)
- **Theta rotation**: angle (-90° to +90°) rotating the bend plane around the pipe axis
- **Reverse recovery**: on load, recover `(side, theta)` from the stored absolute AbsoluteDirection

The cardinal dropdown in the badge **coexists** with the new relative section. Both write to the same `absDirVar`. No DTO schema change — the relative specification is converted to/from absolute `AbsoluteDirection`.

## Local Frame Convention

**`localRight`**: horizontal unit vector, 90° clockwise from the pipe's horizontal heading (viewed from above).

```
localRight =
  if horizontal projection is near-zero (vertical pipe) → Right(+X)
  else → (hDir.y, -hDir.x, 0)  where hDir = normalize(direction.x, direction.y, 0)
```

Examples: Rear→Right, Right→Front, Up→Right.

## Math

### Forward: `(side, theta, frame, deflDeg) → targetVec`

1. `lr = frame.localRight`
2. `bentRight = rodriguesRotate(lr, frame.direction, theta)` — rotate localRight around pipe axis
3. `bendAxis = frame.direction.cross(bentRight).normalized`
4. `sideSign = if side == Right then +1 else -1`
5. `targetVec = rodriguesRotate(frame.direction, bendAxis, sideSign * deflDeg)`

When theta=0° and side=Right: bend toward localRight. When side=Left: bend toward -localRight.

### Reverse: `(targetVec, frame, deflDeg) → (side, theta)`

1. `lr = frame.localRight`
2. `bendAxis = frame.direction.cross(targetVec).normalized` — the axis that produced this bend
3. `bentRight = bendAxis.cross(frame.direction).normalized` — the "right" direction of this bend plane
4. `rawTheta = signedAngle(lr, bentRight, frame.direction)` — angle from localRight to bentRight, around pipe axis
5. If `rawTheta ∈ [-90°, +90°]`: side = Right, theta = rawTheta
6. Else: side = Left, theta = normalize(rawTheta ± 180°) into [-90°, +90°]

Edge case: if `direction.cross(targetVec)` is near-zero (target ≈ direction or opposite), default to `(Right, 0°)`.

## Steps

### Step 1: Engine — Add methods to PipeFrame

**File**: `modules/engine/.../geometry/PipeFrame.scala`

```scala
def localRight: Vec3
def relativeTarget(side: Double, thetaDeg: Double, deflectionDeg: Double): Vec3
def recoverRelative(targetDir: Vec3, deflectionDeg: Double): (Double, Double)
```

### Step 2: Engine — Tests

**File**: `modules/engine/src/test/.../geometry/PipeFrameSuite.scala`

### Step 3: I18n — Add labels

**Files**: `en.conf`, `fr.conf`, `I18nData_UI.scala`

### Step 4: UI — New RelativeDirectionInput component

**New file**: `modules/ui/.../components/RelativeDirectionInput.scala`

### Step 5: UI — Wire into panel subclasses

**Files**: `PipePanel_13384_FlowOnly.scala`, `PipePanel_13384_Thermal.scala`, `FluePipePanel.scala`

### Step 6: UI — Keep cardinal dropdown in badge (no change needed)

## Files Modified

| File | Change |
|------|--------|
| `modules/engine/.../geometry/PipeFrame.scala` | Add `localRight`, `relativeTarget`, `recoverRelative` |
| `modules/engine/src/test/.../PipeFrameSuite.scala` | Add tests for new methods |
| `modules/ui-i18n/.../I18nData_UI.scala` | Add fields to `DirectionBadge` |
| `modules/ui-i18n/.../i18n/en.conf` | Add `relative_left`, `relative_right`, `relative_theta` |
| `modules/ui-i18n/.../i18n/fr.conf` | Same |
| `modules/ui/.../components/RelativeDirectionInput.scala` | **New** — relative direction input component |
| `modules/ui/.../panels/PipePanel_13384_FlowOnly.scala` | Wire `extra` for DC elements |
| `modules/ui/.../panels/PipePanel_13384_Thermal.scala` | Wire `extra` for DC elements |
| `modules/ui/.../panels/FluePipePanel.scala` | Wire `extra` for DC elements |
